package com.iblu01.portallauncher.ui

import com.iblu01.portallauncher.ui.components.PageIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

/** What back does, innermost surface first — and that it never escapes the launcher. */
class LauncherBackTest {

    @Test
    fun `back closes the innermost surface first`() {
        // Everything open at once: the item menu wins, then the hidden list, then the menu.
        assertEquals(
            BackAction.CloseItemMenu,
            backAction(itemMenuOpen = true, hiddenListOpen = true, quickActionsOpen = true, userPanelOpen = true, onClockPage = false),
        )
        assertEquals(
            BackAction.CloseHiddenList,
            backAction(itemMenuOpen = false, hiddenListOpen = true, quickActionsOpen = true, userPanelOpen = true, onClockPage = false),
        )
        assertEquals(
            BackAction.CloseQuickActions,
            backAction(itemMenuOpen = false, hiddenListOpen = false, quickActionsOpen = true, userPanelOpen = true, onClockPage = false),
        )
        assertEquals(
            BackAction.DismissPanel,
            backAction(itemMenuOpen = false, hiddenListOpen = false, quickActionsOpen = false, userPanelOpen = true, onClockPage = false),
        )
    }

    @Test
    fun `with nothing open back returns to the clock page`() {
        assertEquals(
            BackAction.GoToClockPage,
            backAction(itemMenuOpen = false, hiddenListOpen = false, quickActionsOpen = false, userPanelOpen = false, onClockPage = false),
        )
    }

    @Test
    fun `on the resting screen back does nothing at all`() {
        // Never "let it through": the default finishes the home activity, which shows a black flash
        // while the system restarts it.
        assertEquals(
            BackAction.Nothing,
            backAction(itemMenuOpen = false, hiddenListOpen = false, quickActionsOpen = false, userPanelOpen = false, onClockPage = true),
        )
    }

    @Test
    fun `Maison and every app page return to the logical main accueil`() {
        fun action(page: PageIdentity) = backAction(
            itemMenuOpen = false,
            hiddenListOpen = false,
            quickActionsOpen = false,
            userPanelOpen = false,
            currentPage = page,
        )

        assertEquals(BackAction.GoToClockPage, action(PageIdentity.House))
        assertEquals(BackAction.GoToClockPage, action(PageIdentity.Apps(0)))
        assertEquals(BackAction.GoToClockPage, action(PageIdentity.Apps(4)))
        assertEquals(BackAction.Nothing, action(PageIdentity.Clock))
    }
}
