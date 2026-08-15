package com.iblu01.portallauncher.ui.components

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.HomeComposition
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h600dp")
class HomeTrayAcceptanceTest {
    @get:Rule val rule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun pill(index: Int): ResolvedPill {
        val ref = PillRef.Device("switch.device_$index")
        return ResolvedPill(
            ref = ref,
            chip = LauncherChip(
                id = "device_$index",
                icon = "switch",
                label = "Pill $index",
                value = "Prête",
                // Rendering tests do not need Home Assistant icon resolution.
                entityId = "",
                kind = PillKind.SWITCH,
            ),
            sourceEntityIds = setOf(ref.entityId),
        )
    }

    @Test
    fun `collapsed tray renders exactly three primary pills and expanded tray caps extras at six`() {
        val primary = (1..3).map(::pill)
        // Deliberately feed more than the domain invariant to lock the rendering safety cap too.
        val secondary = (4..11).map(::pill)
        var expanded by mutableStateOf(false)
        rule.setContent {
            ClockTray(
                composition = HomeComposition(primary, secondary, emptyList()),
                pinnedRefs = emptySet(),
                manualGroups = emptyList(),
                actions = HomePillActions(),
                pillsExpanded = expanded,
                onPillsExpandedChange = { expanded = it },
            )
        }

        (1..3).forEach { rule.onNodeWithText("Pill $it").assertIsDisplayed() }
        (4..11).forEach { rule.onNodeWithText("Pill $it").assertDoesNotExist() }

        rule.onNodeWithText("Show more info").assertHasClickAction().performClick()
        rule.waitForIdle()

        (1..9).forEach { rule.onNodeWithText("Pill $it").assertIsDisplayed() }
        rule.onNodeWithText("Pill 10").assertDoesNotExist()
        rule.onNodeWithText("Pill 11").assertDoesNotExist()
    }

    @Test
    fun `expand control is absent when there is no secondary pill`() {
        rule.setContent {
            ClockTray(
                composition = HomeComposition((1..3).map(::pill), emptyList(), emptyList()),
                pinnedRefs = emptySet(),
                manualGroups = emptyList(),
                actions = HomePillActions(),
                pillsExpanded = false,
                onPillsExpandedChange = {},
            )
        }

        rule.onNodeWithText("Show more info").assertDoesNotExist()
        rule.onNodeWithContentDescription("Expand").assertDoesNotExist()
        rule.onNodeWithContentDescription("Ouvrir Maison").assertDoesNotExist()
    }

    @Test
    fun `tray long press exposes the complete semantic menu without routing the simple tap`() {
        val target = pill(1)
        var opened = 0
        val label = trayPillAccessibilityLabel(context, target, pinned = false)
        rule.setContent {
            ClockTray(
                composition = HomeComposition(listOf(target), emptyList(), emptyList()),
                pinnedRefs = emptySet(),
                manualGroups = listOf(ManualGroupMenuOption("evening", "Soirée")),
                actions = HomePillActions(onOpen = { opened++ }),
                pillsExpanded = false,
                onPillsExpandedChange = {},
            )
        }

        rule.onNodeWithContentDescription(label)
            .performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitForIdle()

        listOf(
            "Pin",
            "Add to a manual group",
            "Reorder",
            "Open controls",
            "Stop showing this device",
        ).forEach { action ->
            rule.onNodeWithText(action).assertIsDisplayed().assertHasClickAction()
        }
        assertEquals(0, opened)
    }
}
