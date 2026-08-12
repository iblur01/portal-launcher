package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.ThermostatActivity
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors

@Composable
fun WaterHeaterControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val callService = LocalCallService.current
    val contract = remember(entity) { entity.toGenericControlContract() }
    val current = entity.attributes.optDouble("current_temperature", Double.NaN).takeUnless(Double::isNaN)?.toFloat()
    var target by remember(entity.entityId, contract.value) { mutableFloatStateOf(contract.value ?: current ?: contract.min) }
    var selectedMode by remember(entity.entityId, contract.selectedOption) { mutableStateOf(contract.selectedOption) }
    val canSetTemperature = "set_temperature" in contract.actions

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (canSetTemperature) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val dialSize = minOf(maxWidth, maxHeight)
                TemperatureArcControl(
                    target = target, current = current,
                    valueRange = contract.min..contract.max, step = contract.step, unit = contract.unit.ifBlank { "°" },
                    onTargetChange = { target = contract.normalized(it) },
                    onCommit = { callService("water_heater", "set_temperature", entity.entityId, mapOf("temperature" to contract.normalized(target))) },
                    mode = if (entity.state == "off") ThermostatMode.OFF else ThermostatMode.HEAT,
                    activity = when (entity.attributes.optString("current_operation").lowercase()) {
                        "off" -> ThermostatActivity.OFF
                        "eco" -> ThermostatActivity.IDLE
                        else -> ThermostatActivity.HEATING
                    },
                    modifier = Modifier.size(dialSize),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if ("set_operation_mode" in contract.actions && contract.options.isNotEmpty()) {
            WheelPicker(
                options = contract.options,
                selected = selectedMode ?: contract.options.first(),
                onSelect = { mode -> selectedMode = mode; callService("water_heater", "set_operation_mode", entity.entityId, mapOf("operation_mode" to mode)) },
                label = { it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                accent = AppleColors.thermostatHeat,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }
}
