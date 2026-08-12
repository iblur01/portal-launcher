package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iblu01.portallauncher.LauncherChip

/** Shared renderer for HA controls whose shape is defined entirely by entity capabilities. */
@Composable
fun GenericHaEntityControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    when (entity.domain) {
        "humidifier" -> HumidifierControl(entity, modifier)
        "lawn_mower" -> LawnMowerControl(entity, modifier)
        else -> PanelUnavailable()
    }
}
