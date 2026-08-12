package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.home.HomeComposition
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h600dp")
class HomeClockTrayTest {
    @get:Rule val rule = createComposeRule()

    private fun pill(index: Int): ResolvedPill {
        val ref = PillRef.Device("switch.$index")
        return ResolvedPill(
            ref,
            LauncherChip(index.toString(), "switch", "Pill $index", "Prête", entityId = ""),
        )
    }

    @Test
    fun `collapsed renders primary only and expand reveals secondary`() {
        val primary = (1..3).map(::pill)
        val secondary = (4..6).map(::pill)
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

        rule.onNodeWithText("Pill 1").assertIsDisplayed()
        rule.onNodeWithText("Pill 4").assertDoesNotExist()
        rule.onNodeWithText("Show more info").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Pill 4").assertIsDisplayed()
    }

    @Test
    fun `Maison shortcut is not rendered in the bottom tray`() {
        rule.setContent {
            ClockTray(
                composition = HomeComposition(emptyList(), emptyList(), emptyList()),
                pinnedRefs = emptySet(),
                manualGroups = emptyList(),
                actions = HomePillActions(),
                pillsExpanded = false,
                onPillsExpandedChange = {},
            )
        }

        rule.onNodeWithText("Ouvrir Maison").assertDoesNotExist()
    }
}
