package com.iblu01.portallauncher.ui.model

/**
 * What tapping a chip does (design §4). Resolved by the chip mapper so `onChipClick` is a dumb
 * dispatcher — zero `chip.id == "…"` / `chip.kind` branching at the call site.
 */
sealed interface ChipAction {
    /** Fire-and-forget service call on tap (currently used by fans); does not open the panel. */
    data class ServiceToggle(val domain: String, val service: String) : ChipAction
    /** Open the side panel for this [PanelKind]. */
    data class OpenPanel(val panelKind: PanelKind) : ChipAction
}
