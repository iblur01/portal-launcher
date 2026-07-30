package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The surface menu: what it offers, and what it only offers when the home role is missing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class QuickActionsOverlayTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun `wallpaper and settings are always offered`() {
        var wallpaper = 0
        var settings = 0
        rule.setContent {
            QuickActionsOverlay(
                visible = true,
                onDismiss = {},
                onSettings = { settings++ },
                onOpenPlayground = {},
                onSetWallpaper = { wallpaper++ },
                isDefaultHome = true,
            )
        }

        rule.onNodeWithText("Wallpaper").performClick()
        rule.onNodeWithText("Settings").performClick()
        rule.waitForIdle()

        assertEquals(1, wallpaper)
        assertEquals(1, settings)
    }

    @Test
    fun `the home-role fix is offered first when the role is missing`() {
        var homeSettings = 0
        rule.setContent {
            QuickActionsOverlay(
                visible = true,
                onDismiss = {},
                onSettings = {},
                onOpenPlayground = {},
                onOpenHomeSettings = { homeSettings++ },
                isDefaultHome = false,
            )
        }

        rule.onNodeWithText("Set as default launcher").assertIsDisplayed()
        rule.onNodeWithText("Set as default launcher").performClick()
        rule.waitForIdle()

        assertEquals(1, homeSettings)
    }

    @Test
    fun `swiping the panel down still dismisses it`() {
        var dismissed = 0
        rule.setContent {
            QuickActionsOverlay(
                visible = true,
                onDismiss = { dismissed++ },
                onSettings = {},
                onOpenPlayground = {},
            )
        }

        // Started off-row, in the panel's own padding: a row's tap detector consumes the down, so a
        // drag begun on a row can never become a swipe. The panel's tap swallow must not do that.
        val row = rule.onNodeWithText("Settings").getUnclippedBoundsInRoot()
        rule.onRoot().performTouchInput {
            val x = with(rule.density) { row.left.toPx() } + 4f
            val y = with(rule.density) { row.top.toPx() } - 4f
            down(Offset(x, y))
            moveTo(Offset(x, y + 200f))
            advanceEventTime(16)
            moveTo(Offset(x, y + 400f))
            up()
        }
        rule.waitForIdle()

        assertEquals(1, dismissed)
    }

    @Test
    fun `tapping a gap inside the panel does not dismiss it`() {
        var dismissed = 0
        rule.setContent {
            QuickActionsOverlay(
                visible = true,
                onDismiss = { dismissed++ },
                onSettings = {},
                onOpenPlayground = {},
            )
        }

        // The panel's own padding: inside the panel, on no row. It must not fall through to the
        // dismissing backdrop underneath.
        val panel = rule.onNodeWithTag("quickActionsPanel").getUnclippedBoundsInRoot()
        rule.onRoot().performTouchInput {
            click(
                Offset(
                    with(rule.density) { panel.left.toPx() } + 3f,
                    with(rule.density) { panel.top.toPx() } + 3f,
                )
            )
        }
        rule.waitForIdle()

        assertEquals(0, dismissed)
    }

    @Test
    fun `nothing about the home role when we already have it`() {
        rule.setContent {
            QuickActionsOverlay(
                visible = true,
                onDismiss = {},
                onSettings = {},
                onOpenPlayground = {},
                isDefaultHome = true,
            )
        }

        rule.onNodeWithText("Set as default launcher").assertDoesNotExist()
    }
}
