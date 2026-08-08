package com.iblu01.portallauncher.ui

import com.iblu01.portallauncher.ui.components.PageIdentity

/** What the back gesture should do, given what is open. */
enum class BackAction {
    CloseItemMenu,
    CloseHiddenList,
    CloseWidgetPicker,
    CloseQuickActions,
    DismissPanel,
    GoToClockPage,
    /** Consume and do nothing: a home activity must never be finished by back. */
    Nothing,
}

/**
 * Resolves back to a single action, innermost surface first.
 *
 * A launcher must always consume back: the default behaviour finishes the activity, and finishing
 * the home activity gives a black flash while the system restarts it. So the last case is
 * [BackAction.Nothing], never "let it through".
 *
 * Only a USER-opened panel is dismissed — an AUTO (media) panel is the resting state while
 * something plays, and dismissing it is read as a user dismissal that suppresses it for the session.
 */
fun backAction(
    itemMenuOpen: Boolean,
    hiddenListOpen: Boolean,
    widgetPickerOpen: Boolean = false,
    quickActionsOpen: Boolean,
    userPanelOpen: Boolean,
    onClockPage: Boolean,
): BackAction = when {
    itemMenuOpen -> BackAction.CloseItemMenu
    hiddenListOpen -> BackAction.CloseHiddenList
    widgetPickerOpen -> BackAction.CloseWidgetPicker
    quickActionsOpen -> BackAction.CloseQuickActions
    userPanelOpen -> BackAction.DismissPanel
    !onClockPage -> BackAction.GoToClockPage
    else -> BackAction.Nothing
}

/**
 * Logical-page overload used by the Maison-aware pager.
 *
 * Both Maison and every application page return to the main accueil. Only [PageIdentity.Clock] is
 * already the resting destination, so Back is consumed without changing pages there.
 */
fun backAction(
    itemMenuOpen: Boolean,
    hiddenListOpen: Boolean,
    widgetPickerOpen: Boolean = false,
    quickActionsOpen: Boolean,
    userPanelOpen: Boolean,
    currentPage: PageIdentity,
): BackAction = backAction(
    itemMenuOpen = itemMenuOpen,
    hiddenListOpen = hiddenListOpen,
    widgetPickerOpen = widgetPickerOpen,
    quickActionsOpen = quickActionsOpen,
    userPanelOpen = userPanelOpen,
    onClockPage = currentPage == PageIdentity.Clock,
)
