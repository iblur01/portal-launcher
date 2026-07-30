package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The launcher chrome that moved out of the grid and into the top bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherHeaderActionsTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `settings is always reachable from the top bar`() {
        var settings = 0
        rule.setContent {
            LauncherHeaderActions(hiddenCount = 0, onShowHidden = {}, onSettings = { settings++ })
        }

        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()

        assertEquals(1, settings)
    }

    @Test
    fun `the hidden-apps button appears only when something is hidden`() {
        var shown = 0
        rule.setContent {
            LauncherHeaderActions(hiddenCount = 2, onShowHidden = { shown++ }, onSettings = {})
        }

        // "Masquer" would be a one-way trip without this entry.
        rule.onNodeWithContentDescription("Hidden apps (2)").assertIsDisplayed()
        rule.onNodeWithContentDescription("Hidden apps (2)").performClick()
        rule.waitForIdle()

        assertEquals(1, shown)
    }

    @Test
    fun `nothing hidden means no button opening an empty list`() {
        rule.setContent {
            LauncherHeaderActions(hiddenCount = 0, onShowHidden = {}, onSettings = {})
        }

        rule.onNodeWithContentDescription("Hidden apps (0)").assertDoesNotExist()
    }
}
