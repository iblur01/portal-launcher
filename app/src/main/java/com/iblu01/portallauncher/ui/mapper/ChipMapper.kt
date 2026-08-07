package com.iblu01.portallauncher.ui.mapper

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.ChipPlacement
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
    id == "scenes_group" -> PanelKind.SCENES
    id == "presence_group" -> PanelKind.PRESENCE
    id == "energy_group" -> PanelKind.ENERGY
    id == "air_group" -> PanelKind.AIR_QUALITY
    kind == PillKind.LOCK -> PanelKind.LOCK
    kind == PillKind.COVER -> PanelKind.COVER
    kind == PillKind.THERMOSTAT -> PanelKind.THERMOSTAT
    kind == PillKind.VACUUM -> PanelKind.VACUUM
    kind == PillKind.FAN -> PanelKind.FAN
    kind == PillKind.SWITCH -> PanelKind.SWITCH
    kind == PillKind.APPLIANCE -> PanelKind.WASHER
    // alarm-vs-generic-safety split resolved here (was SidePanel.kt:179).
    kind == PillKind.SAFETY && entityId.startsWith("alarm_control_panel.") -> PanelKind.ALARM
    else -> PanelKind.GENERIC_DETAILS
}

/**
 * Tap behaviour: simple power accessories toggle on tap (HomeKit-true); everything else opens
 * its panel. Long-press always opens (handled at the call site via [toPanelKind]).
 */
fun LauncherChip.toChipAction(): ChipAction = when (kind) {
    PillKind.SWITCH -> ChipAction.ServiceToggle("switch", "toggle")
    PillKind.FAN -> ChipAction.ServiceToggle("fan", "toggle")
    else -> ChipAction.OpenPanel(toPanelKind())
}

/** Presence floats as a top-left avatar badge; every other chip sits in the tray (design §7). */
fun LauncherChip.chipPlacement(): ChipPlacement =
    if (id == "presence_group") ChipPlacement.FLOATING else ChipPlacement.TRAY
