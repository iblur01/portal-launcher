package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "fr-w640dp-h600dp")
class HomePillContextMenuTest {
    @get:Rule val rule = createComposeRule()

    private val target = ResolvedPill(
        ref = PillRef.Device("light.salon"),
        chip = LauncherChip(
            id = "salon",
            icon = "light",
            label = "Lampe salon",
            value = "Allumée",
            entityId = "light.salon",
            kind = PillKind.LIGHTS,
        ),
    )

    @Test
    fun `pin and manual group actions report stable intents then dismiss`() {
        val pinned = mutableListOf<Boolean>()
        val groups = mutableListOf<String>()
        var dismissed = 0
        var shownTarget by mutableStateOf<ResolvedPill?>(target)
        rule.setContent {
            HomePillContextMenu(
                target = shownTarget,
                isPinned = false,
                manualGroups = listOf(ManualGroupMenuOption("evening", "Soirée")),
                actions = HomePillActions(
                    onSetPinned = { _, value -> pinned += value },
                    onAddToManualGroup = { _, id -> groups += id },
                ),
                onDismiss = { dismissed++; shownTarget = null },
            )
        }

        rule.onNodeWithText("Épingler").performClick()
        rule.waitForIdle()
        assertEquals(listOf(true), pinned)
        assertEquals(1, dismissed)

        rule.runOnIdle { dismissed = 0; shownTarget = target }
        rule.onNodeWithText("Ajouter à un groupe manuel").performClick()
        rule.onNodeWithText("Soirée").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(listOf("evening"), groups)
        assertEquals(1, dismissed)
    }

    @Test
    fun `reorder exposes all four accessible alternatives`() {
        val moves = mutableListOf<HomePillMove>()
        rule.setContent {
            HomePillContextMenu(
                target = target,
                isPinned = true,
                manualGroups = emptyList(),
                actions = HomePillActions(onMove = { _, move -> moves += move }),
                onDismiss = {},
            )
        }

        rule.onNodeWithText("Réorganiser").performClick()
        rule.onNodeWithText("Placer en premier").assertIsDisplayed()
        rule.onNodeWithText("Déplacer avant").assertIsDisplayed()
        rule.onNodeWithText("Déplacer après").assertIsDisplayed()
        rule.onNodeWithText("Placer en dernier").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(listOf(HomePillMove.LAST), moves)
    }

    @Test
    fun `device can be hidden completely from the long press menu`() {
        val hidden = mutableListOf<PillRef>()
        var shownTarget by mutableStateOf<ResolvedPill?>(target)
        rule.setContent {
            HomePillContextMenu(
                target = shownTarget,
                isPinned = false,
                manualGroups = emptyList(),
                actions = HomePillActions(onHideDevice = { hidden += it.ref }),
                onDismiss = { shownTarget = null },
            )
        }

        rule.onNodeWithText("Ne plus afficher cet appareil").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(listOf(target.ref), hidden)
    }

    @Test
    fun `scene menu uses a scene-specific hiding label and exposes no useless controls`() {
        val scene = target.copy(
            ref = PillRef.Device("scene.evening"),
            chip = target.chip.copy(entityId = "scene.evening", kind = PillKind.SCENE),
        )
        rule.setContent {
            HomePillContextMenu(
                target = scene,
                isPinned = false,
                manualGroups = emptyList(),
                actions = HomePillActions(),
                onDismiss = {},
            )
        }

        rule.onAllNodesWithText("Ouvrir les commandes").assertCountEquals(0)
        rule.onAllNodesWithText("Ne plus afficher cet appareil").assertCountEquals(0)
        rule.onNodeWithText("Ne plus afficher cette scène").assertIsDisplayed()
    }

    @Test
    fun `drag stages relative steps until drop and cancel commits nothing`() {
        val dropped = HomePillDragAccumulator().apply {
            dragBy(130f, threshold = 50f)
        }
        assertEquals(2, dropped.drop())

        val cancelled = HomePillDragAccumulator().apply {
            dragBy(-130f, threshold = 50f)
        }
        assertEquals(0, cancelled.cancel())
        assertEquals("cancel clears every staged mutation", 0, cancelled.drop())
    }
}
