package com.iblu01.portallauncher.ui.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Density
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.AlertSeverity
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.HomePageModel
import com.iblu01.portallauncher.domain.home.HomeGridLayoutPolicy
import com.iblu01.portallauncher.domain.home.HomeSectionModel
import com.iblu01.portallauncher.domain.home.HomeSectionType
import com.iblu01.portallauncher.domain.home.PillAlert
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.components.HomePillActions
import com.iblu01.portallauncher.ui.components.trayPillAccessibilityLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w480dp-h600dp")
class HomeAccessibilityAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private fun pill(
        id: String = "front",
        availability: Availability = Availability.AVAILABLE,
        alert: PillAlert? = null,
    ): ResolvedPill {
        val ref = PillRef.Device("lock.$id")
        return ResolvedPill(
            ref = ref,
            chip = LauncherChip(
                id = id,
                icon = "lock",
                label = "Porte d’entrée",
                value = "Déverrouillée",
                state = if (alert == null) "active" else "critical",
                // Rendering tests do not need Home Assistant icon resolution.
                entityId = "",
                kind = PillKind.LOCK,
            ),
            availability = availability,
            sourceEntityIds = setOf(ref.entityId),
            alert = alert,
        )
    }

    @Test
    fun `TalkBack labels include identity state group pin and non-color critical status`() {
        val target = pill(
            availability = Availability.STALE,
            alert = PillAlert(AlertSeverity.CRITICAL, incidentEntityIds = setOf("lock.front")),
        )

        val tray = trayPillAccessibilityLabel(target, pinned = true)
        val house = homePillAccessibilityLabel(target, pinned = true, sectionTitle = "Favoris")
        listOf(tray, house).forEach { label ->
            assertTrue(label.contains("Porte d’entrée"))
            assertTrue(label.contains("Déverrouillée"))
            assertTrue(label.contains("Appareil"))
            assertTrue(label.contains("épinglé"))
            assertTrue(label.contains("alerte critique"))
            assertTrue(label.contains("données figées"))
        }
        assertTrue(house.contains("section Favoris"))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `large font preserves a focusable pill and a minimum 48dp touch target`() {
        val target = pill()
        var opened = 0
        val label = homePillAccessibilityLabel(target, pinned = true, sectionTitle = "Favoris")
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1.8f)) {
                HomePage(
                    model = HomePageModel(
                        sections = listOf(
                            HomeSectionModel(
                                sectionId = "favorites",
                                type = HomeSectionType.FAVORITES,
                                title = "Favoris",
                                items = listOf(target),
                            ),
                        ),
                        hasCompatibleDevices = true,
                    ),
                    pinnedRefs = setOf(target.ref),
                    actions = HomePillActions(onOpen = { opened++ }),
                )
            }
        }

        val node = rule.onNodeWithContentDescription(label)
        val bounds = node.getUnclippedBoundsInRoot()
        val touchHeight = bounds.bottom - bounds.top
        assertTrue("touch height was $touchHeight", touchHeight.value >= 48f)
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        node.performKeyInput { pressKey(Key.DirectionCenter) }
        rule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun `large text yields fewer columns instead of compressing pills`() {
        val dense = HomeGridLayoutPolicy.columns(availableWidthDp = 480f, fontScale = 1.8f)
        assertEquals(1, dense)

        val roomy = HomeGridLayoutPolicy.columns(availableWidthDp = 480f, fontScale = 1f)
        assertEquals(2, roomy)
    }
}
