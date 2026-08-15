package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.GroupCollectiveAction
import com.iblu01.portallauncher.domain.home.PillGroupSnapshot
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.HaStates
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.panel.PanelState
import com.iblu01.portallauncher.ui.panel.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "fr-w800dp-h600dp")
class GroupBrowserAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private fun device(
        id: String,
        availability: Availability = Availability.AVAILABLE,
        kind: PillKind = PillKind.GENERIC,
    ): ResolvedPill {
        val ref = PillRef.Device(id)
        return ResolvedPill(
            ref = ref,
            chip = LauncherChip(
                id = id.substringAfter('.'),
                icon = "switch",
                label = id.substringAfter('.').replaceFirstChar(Char::uppercase),
                value = "Prêt",
                // Empty avoids any dependency on a live HA entity in the generic sub-panel.
                entityId = if (kind == PillKind.GENERIC) "" else id,
                kind = kind,
            ),
            availability = availability,
            sourceEntityIds = setOf(id),
        )
    }

    private fun group(
        members: List<ResolvedPill>,
        collectiveAction: GroupCollectiveAction? = null,
    ): PillGroupSnapshot {
        val ref = PillRef.ManualGroup("evening")
        return PillGroupSnapshot(
            ref = ref,
            chip = LauncherChip(ref.stableKey, "home", "Soirée", "${members.size} appareils"),
            members = members.map { it.ref as PillRef.Device },
            resolvedMembers = members,
            collectiveAction = collectiveAction,
        )
    }

    @Test
    fun `group to device to back to group and close follows the typed panel stack`() {
        val member = device("sensor.device")
        val snapshot = group(listOf(member))
        var state by mutableStateOf(
            reduce(PanelState(), PanelEvent.OpenGroup(PanelRequest.Group(snapshot.ref))),
        )
        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides remember { HaStates() }) {
                val request = state.request as? PanelRequest.Group
                GroupBrowserPanel(
                    group = snapshot,
                    selectedDevice = request?.device?.let { member.chip },
                    deviceRequested = request?.device != null,
                    onSelectMember = {
                        state = reduce(
                            state,
                            PanelEvent.OpenGroupDevice(PanelRequest.Chip(it.chip.id, it.chip.toPanelKind())),
                        )
                    },
                    onBack = { state = reduce(state, PanelEvent.Back) },
                    onDismiss = { state = reduce(state, PanelEvent.Dismiss) },
                    onCollectiveAction = {},
                )
            }
        }

        rule.onNodeWithTag("groupBrowserPanel").assertIsDisplayed()
        rule.onNodeWithContentDescription("Device, Prêt").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("groupDevicePanel").assertIsDisplayed()

        rule.onNodeWithContentDescription("Retour au groupe Soirée").performClick()
        rule.waitForIdle()
        assertEquals(snapshot.ref, (state.request as PanelRequest.Group).destination)
        assertNull((state.request as PanelRequest.Group).device)
        rule.onNodeWithTag("groupBrowserPanel").assertIsDisplayed()

        rule.onNodeWithContentDescription("Fermer le groupe Soirée").performClick()
        rule.waitForIdle()
        assertNull(state.request)
    }

    @Test
    fun `stale group remains readable but exposes neither member nor collective commands`() {
        val stale = device("switch.cached", Availability.STALE, PillKind.SWITCH)
        val snapshot = group(listOf(stale), GroupCollectiveAction.TURN_OFF)
        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides remember { HaStates() }) {
                GroupBrowserPanel(
                    group = snapshot,
                    selectedDevice = null,
                    deviceRequested = false,
                    onSelectMember = {},
                    onBack = {},
                    onDismiss = {},
                    onCollectiveAction = {},
                )
            }
        }

        rule.onNodeWithTag("groupStaleState").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cached, Prêt, Données figées · commandes suspendues")
            .assertIsDisplayed()
            .assertIsNotEnabled()
        rule.onNodeWithTag("groupCollectiveAction").assertDoesNotExist()
    }

    @Test
    fun `empty group and vanished selected device have explicit recoverable states`() {
        val snapshot = group(emptyList())
        var back = 0
        var close = 0
        var deviceRequested by mutableStateOf(false)

        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides remember { HaStates() }) {
                GroupBrowserPanel(
                    group = snapshot,
                    selectedDevice = null,
                    deviceRequested = deviceRequested,
                    onSelectMember = {},
                    onBack = { back++ },
                    onDismiss = { close++ },
                    onCollectiveAction = {},
                )
            }
        }
        rule.onNodeWithTag("groupEmptyState").assertIsDisplayed()

        rule.runOnIdle { deviceRequested = true }
        rule.waitForIdle()
        rule.onNodeWithTag("groupUnavailableDevice").assertIsDisplayed()
        rule.onNodeWithText("Appareil indisponible").assertIsDisplayed()
        rule.onNodeWithContentDescription("Retour au groupe Soirée").performClick()
        rule.waitForIdle()
        assertEquals(1, back)

        rule.onNodeWithContentDescription("Fermer le panneau").performClick()
        rule.waitForIdle()
        assertEquals(1, close)
    }
}
