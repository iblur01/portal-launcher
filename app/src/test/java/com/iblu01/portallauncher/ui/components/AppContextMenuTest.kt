package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntRect
import com.iblu01.portallauncher.ui.apps.AppShortcut
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.apps.GridSpan
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the long-press menu offers, per item type and per launcher role. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppContextMenuTest {

    @get:Rule val rule = createComposeRule()

    private val appItem = GridItem(
        key = GridItem.appKey("com.spotify", "com.spotify.Main"),
        label = "Spotify",
        defaultLabel = "Spotify",
        icon = null,
        packageName = "com.spotify",
        activityName = "com.spotify.Main",
    )

    private val shortcutItem = GridItem(
        key = GridItem.shortcutKey("com.mail", "compose"),
        label = "Nouveau message",
        defaultLabel = "Nouveau message",
        icon = null,
        packageName = "com.mail",
        shortcutId = "compose",
    )

    @Test
    fun `an app offers its shortcuts and the launcher actions`() {
        val started = mutableListOf<String>()
        var dismissed = 0
        val shortcut = AppShortcut("liked", "com.spotify", "Titres likés", null)
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(appItem, IntRect(0, 0, 140, 140)),
                shortcuts = listOf(shortcut),
                canUninstall = true,
                isDefaultHome = true,
                onDismiss = { dismissed++ },
                onShortcut = { started.add(it.id) },
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
            )
        }

        rule.onNodeWithText("Hide").assertIsDisplayed()
        rule.onNodeWithText("App info").assertIsDisplayed()
        rule.onNodeWithText("Uninstall").assertIsDisplayed()
        rule.onNodeWithText("Titres likés").performClick()
        rule.waitForIdle()

        assertEquals(listOf("liked"), started)
        assertEquals("running a shortcut closes the menu", 1, dismissed)
    }

    @Test
    fun `a system app hides the uninstall entry rather than failing on tap`() {
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(appItem, IntRect(0, 0, 140, 140)),
                shortcuts = emptyList(),
                canUninstall = false,
                isDefaultHome = true,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
            )
        }

        rule.onNodeWithText("Uninstall").assertDoesNotExist()
    }

    @Test
    fun `not being the default home is explained, not silently hidden`() {
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(appItem, IntRect(0, 0, 140, 140)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = false,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
            )
        }

        // `getShortcuts()` only works for the selected home app, so an empty list here is a role
        // problem, not an app without shortcuts.
        rule.onNodeWithText(
            "Shortcuts unavailable — Portal is not the default launcher"
        ).assertIsDisplayed()
        // And it is actionable, not just an explanation.
        rule.onNodeWithText("Set Portal as default").assertIsDisplayed()
    }

    @Test
    fun `the menu can send the user to the home-app chooser`() {
        var homeSettings = 0
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(appItem, IntRect(0, 0, 140, 140)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = false,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
                onOpenHomeSettings = { homeSettings++ },
            )
        }

        rule.onNodeWithText("Set Portal as default").performClick()
        rule.waitForIdle()

        assertEquals(1, homeSettings)
    }

    @Test
    fun `a pinned shortcut can be removed, and offers no app-level action`() {
        var removed = 0
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(shortcutItem, IntRect(0, 0, 140, 140)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = true,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = { removed++ },
            )
        }

        rule.onNodeWithText("Hide").assertDoesNotExist()
        rule.onNodeWithText("Uninstall").assertDoesNotExist()
        rule.onNodeWithText("Remove shortcut").performClick()
        rule.waitForIdle()

        assertEquals(1, removed)
    }

    @Test
    fun `a widget offers its size and removal, not app actions`() {
        val resizes = mutableListOf<GridSpan>()
        var removed = 0
        val widget = GridItem(
            key = GridItem.widgetKey(7),
            label = "Météo",
            defaultLabel = "Météo",
            icon = null,
            packageName = "com.w",
            widgetId = 7,
            defaultSpan = GridSpan(2, 1),
        )
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(widget, IntRect(0, 0, 280, 140), GridSpan(2, 1)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = true,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
                onResize = { resizes.add(it) },
                onRemoveWidget = { removed++ },
                maxSpan = GridSpan(4, 3),
            )
        }

        rule.onNodeWithText("Width").assertIsDisplayed()
        rule.onNodeWithText("Rename").assertDoesNotExist()
        rule.onNodeWithText("Hide").assertDoesNotExist()
        rule.onNodeWithText("Uninstall").assertDoesNotExist()

        // Two "+" buttons (width, height): the first grows the width.
        rule.onAllNodesWithText("+")[0].performClick()
        rule.waitForIdle()
        assertEquals(listOf(GridSpan(3, 1)), resizes)

        rule.onNodeWithText("Remove widget").performClick()
        rule.waitForIdle()
        assertEquals(1, removed)
    }

    @Test
    fun `a widget at minimum size cannot shrink further`() {
        val resizes = mutableListOf<GridSpan>()
        val widget = GridItem(
            key = GridItem.widgetKey(7),
            label = "Météo",
            defaultLabel = "Météo",
            icon = null,
            packageName = "com.w",
            widgetId = 7,
        )
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(widget, IntRect(0, 0, 140, 140), GridSpan(1, 1)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = true,
                onDismiss = {},
                onShortcut = {},
                onRename = {},
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
                onResize = { resizes.add(it) },
                maxSpan = GridSpan(4, 3),
            )
        }

        rule.onAllNodesWithText("−")[0].performClick()
        rule.waitForIdle()

        assertEquals("a zero-cell widget is not a thing", emptyList<GridSpan>(), resizes)
    }

    @Test
    fun `renaming replaces the menu with a field and reports the new name`() {
        var renamed: String? = null
        rule.setContent {
            AppContextMenu(
                target = AppMenuTarget(appItem, IntRect(0, 0, 140, 140)),
                shortcuts = emptyList(),
                canUninstall = true,
                isDefaultHome = true,
                onDismiss = {},
                onShortcut = {},
                onRename = { renamed = it },
                onHide = {},
                onAppInfo = {},
                onUninstall = {},
                onRemoveShortcut = {},
            )
        }

        rule.onNodeWithText("Rename").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Display name").assertIsDisplayed()
        rule.onNodeWithText("Confirm").performClick()
        rule.waitForIdle()

        assertEquals("Spotify", renamed)
    }
}
