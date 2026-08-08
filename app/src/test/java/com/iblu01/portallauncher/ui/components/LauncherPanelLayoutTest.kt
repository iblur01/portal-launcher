package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w900dp-h600dp")
class LauncherPanelLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `landscape panel reserves its side instead of covering launcher content`() {
        render()

        val content = rule.onNodeWithTag("launcherContent").getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("launcherPanel").getUnclippedBoundsInRoot()
        assertTrue("content overlaps panel: $content / $panel", content.right <= panel.left)
        assertTrue(abs(content.right.value - 900f * (1f - LAUNCHER_PANEL_FRACTION)) < 1f)
        assertEquals(900f, panel.right.value, 0.5f)
    }

    @Config(sdk = [28], qualifiers = "w600dp-h900dp")
    @Test fun `portrait panel reserves the bottom instead of covering launcher content`() {
        render()

        val content = rule.onNodeWithTag("launcherContent").getUnclippedBoundsInRoot()
        val panel = rule.onNodeWithTag("launcherPanel").getUnclippedBoundsInRoot()
        assertTrue("content overlaps panel: $content / $panel", content.bottom <= panel.top)
        assertTrue(abs(content.bottom.value - 900f * (1f - LAUNCHER_PANEL_FRACTION)) < 1f)
        assertEquals(900f, panel.bottom.value, 0.5f)
    }

    private fun render() {
        rule.setContent {
            LauncherPanelLayout(
                panelVisible = true,
                modifier = Modifier.fillMaxSize(),
                content = { Box(Modifier.fillMaxSize().testTag("launcherContent")) },
                panel = { Box(Modifier.fillMaxSize().testTag("launcherPanel")) },
            )
        }
    }
}
