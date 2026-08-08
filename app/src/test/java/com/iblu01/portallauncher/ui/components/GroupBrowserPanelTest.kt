package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.CompositionLocalProvider
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.GroupCollectiveAction
import com.iblu01.portallauncher.domain.home.PillGroupSnapshot
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.LocalHaStates
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w900dp-h600dp")
class GroupBrowserPanelTest {
    @get:Rule val rule = createComposeRule()

    private fun member(
        entityId: String,
        kind: PillKind,
        availability: Availability = Availability.AVAILABLE,
    ) = ResolvedPill(
        ref = PillRef.Device(entityId),
        chip = LauncherChip(
            id = entityId,
            icon = kind.icon,
            label = entityId.substringAfter('.'),
            value = "Actif",
            entityId = entityId,
            kind = kind,
        ),
        availability = availability,
        sourceEntityIds = setOf(entityId),
    )

    private fun group(
        members: List<ResolvedPill>,
        action: GroupCollectiveAction?,
    ) = PillGroupSnapshot(
        ref = PillRef.AreaGroup("salon"),
        chip = LauncherChip("area:salon", "home", "Salon", "2 actifs"),
        members = members.map { it.ref as PillRef.Device },
        resolvedMembers = members,
        collectiveAction = action,
    )

    @Test
    fun `collective calls are mapped only from typed kind and entity domain`() {
        val calls = collectiveServiceCalls(
            group(
                listOf(
                    member("light.lampe", PillKind.LIGHTS),
                    member("input_boolean.mode", PillKind.SWITCH),
                    member("fan.air", PillKind.PURIFIER),
                ),
                GroupCollectiveAction.TURN_OFF,
            ),
        )

        assertEquals(
            listOf(
                GroupServiceCall("light", "turn_off", "light.lampe"),
                GroupServiceCall("input_boolean", "turn_off", "input_boolean.mode"),
                GroupServiceCall("fan", "turn_off", "fan.air"),
            ),
            calls,
        )
    }

    @Test
    fun `group level renders summary collective action and available members`() {
        val light = member("light.lampe", PillKind.LIGHTS)
        val snapshot = group(listOf(light), GroupCollectiveAction.TURN_OFF)
        val selected = mutableListOf<ResolvedPill>()
        val calls = mutableListOf<GroupServiceCall>()
        var dismissed = 0
        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides emptyMap()) {
                GroupBrowserPanel(
                    group = snapshot,
                    selectedDevice = null,
                    deviceRequested = false,
                    onSelectMember = { selected += it },
                    onBack = {},
                    onDismiss = { dismissed++ },
                    onCollectiveAction = { calls += it },
                )
            }
        }

        rule.onNodeWithTag("groupSummary").assertIsDisplayed()
        rule.onNodeWithTag("groupCollectiveAction").performClick()
        rule.onNodeWithTag("groupMember:${light.ref.stableKey}").performClick()
        rule.onNodeWithContentDescription("Fermer le groupe Salon").performClick()
        rule.waitForIdle()

        assertEquals(listOf(light), selected)
        assertEquals(listOf(GroupServiceCall("light", "turn_off", "light.lampe")), calls)
        assertEquals(1, dismissed)
    }

    @Test
    fun `stale members remain visible but disabled and collective commands disappear`() {
        val stale = member("lock.porte", PillKind.LOCK, Availability.STALE)
        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides emptyMap()) {
                GroupBrowserPanel(
                    group = group(listOf(stale), GroupCollectiveAction.LOCK),
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
        rule.onNodeWithTag("groupMember:${stale.ref.stableKey}").assertIsNotEnabled()
        rule.onNodeWithTag("groupCollectiveAction").assertDoesNotExist()
    }
}
