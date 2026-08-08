package com.iblu01.portallauncher.ui.mapper

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.PanelKind

/**
 * The single place that resolves a chip's typed [PanelKind] and [ChipAction] (design §4/§7).
 * All the string-id + `PillKind` + alarm-vs-safety branching lives HERE — call sites
 * (`onChipClick`, the SidePanel router) stay free of `chip.id == "…"`. Pure, no Compose.
 */
fun LauncherChip.toPanelKind(): PanelKind = when {
    id == "media_group" -> PanelKind.MEDIA
    id == "lights_group" -> PanelKind.LIGHTS
    id == "purifier_group" -> PanelKind.PURIFIER
    kind == PillKind.LIGHTS -> PanelKind.LIGHTS
    kind == PillKind.MEDIA -> PanelKind.MEDIA
    kind == PillKind.PURIFIER -> PanelKind.PURIFIER
    kind == PillKind.LOCK -> PanelKind.LOCK
    kind == PillKind.COVER -> PanelKind.COVER
    kind == PillKind.THERMOSTAT -> PanelKind.THERMOSTAT
    kind == PillKind.VACUUM -> PanelKind.VACUUM
    kind == PillKind.FAN -> PanelKind.FAN
    kind == PillKind.SWITCH -> PanelKind.SWITCH
    kind == PillKind.APPLIANCE -> PanelKind.WASHER
    kind == PillKind.HUMIDIFIER -> PanelKind.HUMIDIFIER
    kind == PillKind.WATER_HEATER -> PanelKind.WATER_HEATER
    kind == PillKind.VALVE -> PanelKind.VALVE
    kind == PillKind.SIREN -> PanelKind.SIREN
    kind == PillKind.LAWN_MOWER -> PanelKind.LAWN_MOWER
    // alarm-vs-generic-safety split resolved here (was SidePanel.kt:179).
    kind == PillKind.SAFETY && entityId.startsWith("alarm_control_panel.") -> PanelKind.ALARM
    else -> PanelKind.GENERIC_DETAILS
}

/**
 * Tap behaviour: switches open their control panel so an accidental tray tap never changes their
 * state. Fans retain their established direct toggle; everything else opens its typed panel.
 * Long-press always opens commands (handled at the call site via [toPanelKind]).
 */
fun LauncherChip.toChipAction(): ChipAction = when (kind) {
    PillKind.FAN -> ChipAction.ServiceToggle("fan", "toggle")
    else -> ChipAction.OpenPanel(toPanelKind())
}
