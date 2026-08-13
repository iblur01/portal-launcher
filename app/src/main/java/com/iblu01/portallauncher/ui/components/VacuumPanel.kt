package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VacuumAction
import com.iblu01.portallauncher.ui.components.controls.VacuumActionChips
import com.iblu01.portallauncher.ui.components.controls.VacuumRunButton
import com.iblu01.portallauncher.ui.components.controls.VacuumStatusChip
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors

/** Home Assistant adapter for the reusable vacuum controls. */
@Composable
fun VacuumControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }

    val state = entity.state.lowercase()
    val running = state in setOf("cleaning", "returning")
    val status = entity.attributes.optString("status").ifBlank { chip.value }
    val speedOptions = entity.attributes.optJSONArray("fan_speed_list")?.let { values ->
        List(values.length()) { values.optString(it) }.filter(String::isNotBlank)
    }.orEmpty()
    val selectedSpeed = entity.attributes.optString("fan_speed").takeIf { it in speedOptions }
    val stopLabel = stringResource(R.string.vacuum_button_stop)
    val dockLabel = stringResource(R.string.vacuum_button_dock)
    val locateLabel = stringResource(R.string.vacuum_button_locate)

    val actions = buildList {
        if (running && entity.supports(VacuumFeature.STOP)) {
            add(VacuumAction("stop", stopLabel, Icons.Filled.Stop) {
                callService("vacuum", "stop", chip.entityId)
            })
        }
        if (entity.supports(VacuumFeature.RETURN_HOME)) {
            add(VacuumAction(
                "dock",
                dockLabel,
                Icons.Outlined.Home,
                active = state == "returning",
            ) { callService("vacuum", "return_to_base", chip.entityId) })
        }
        if (entity.supports(VacuumFeature.LOCATE)) {
            add(VacuumAction("locate", locateLabel, Icons.Outlined.MyLocation) {
                callService("vacuum", "locate", chip.entityId)
            })
        }
    }

    val runControl: @Composable (Modifier) -> Unit = { runModifier ->
        BoxWithConstraints(runModifier, contentAlignment = Alignment.Center) {
            val buttonSize = minOf(maxWidth, maxHeight).coerceIn(128.dp, 190.dp)
            VacuumRunButton(
                running = running,
                onToggle = { wasRunning ->
                    when {
                        wasRunning && entity.supports(VacuumFeature.PAUSE) ->
                            callService("vacuum", "pause", chip.entityId)
                        wasRunning && entity.supports(VacuumFeature.STOP) ->
                            callService("vacuum", "stop", chip.entityId)
                        !wasRunning -> callService("vacuum", "start", chip.entityId)
                    }
                },
                size = buttonSize,
            )
        }
    }
    val options: @Composable (Modifier) -> Unit = { optionsModifier ->
        Column(optionsModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (status.isNotBlank()) VacuumStatusChip(status, prominent = true)
        if (speedOptions.isNotEmpty() && selectedSpeed != null) {
            Spacer(Modifier.height(16.dp))
            WheelPicker(
                options = speedOptions,
                selected = selectedSpeed,
                onSelect = { speed ->
                    if (speed != selectedSpeed) {
                        callService("vacuum", "set_fan_speed", chip.entityId, mapOf("fan_speed" to speed))
                    }
                },
                label = { value -> value.replace('_', ' ').replaceFirstChar { it.uppercase() } },
                accent = AppleColors.primary,
                visibleCount = 3,
                modifier = Modifier.fillMaxWidth(if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) 0.84f else 0.72f),
            )
        }

        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            VacuumActionChips(actions, modifier = Modifier.fillMaxWidth(), accent = AppleColors.active)
        }
        }
    }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        AdaptivePanelSplit(modifier, primary = { runControl(it) }, secondary = { options(it) })
    } else Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (status.isNotBlank()) VacuumStatusChip(status, prominent = true)
        Spacer(Modifier.height(20.dp))
        runControl(Modifier.fillMaxWidth())
        if (speedOptions.isNotEmpty() && selectedSpeed != null) {
            Spacer(Modifier.height(12.dp))
            WheelPicker(
                options = speedOptions, selected = selectedSpeed,
                onSelect = { speed -> if (speed != selectedSpeed) callService("vacuum", "set_fan_speed", chip.entityId, mapOf("fan_speed" to speed)) },
                label = { value -> value.replace('_', ' ').replaceFirstChar { it.uppercase() } },
                accent = AppleColors.primary, visibleCount = 3, modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            VacuumActionChips(actions, Modifier.fillMaxWidth(), AppleColors.active)
        }
    }
}
