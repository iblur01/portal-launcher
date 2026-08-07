package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.ControlNeutral
import com.iblu01.portallauncher.ui.components.controls.ThermostatActivity
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors
import kotlin.math.roundToInt

/** Home Assistant adapter around the reusable, backend-agnostic thermostat controls. */
@Composable
fun ThermostatControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val climate = entity.toClimateContract()

    val initialTarget = climate.targetTemperature ?: climate.currentTemperature ?: climate.minimumTemperature
    val initialLow = climate.targetLow ?: initialTarget
    val initialHigh = climate.targetHigh ?: initialTarget
    var target by remember(chip.entityId, climate.targetTemperature) { mutableFloatStateOf(initialTarget) }
    var low by remember(chip.entityId, climate.targetLow) { mutableFloatStateOf(initialLow) }
    var high by remember(chip.entityId, climate.targetHigh) { mutableFloatStateOf(initialHigh) }
    var pendingMode by remember(chip.entityId) { mutableStateOf<String?>(null) }

    // Move immediately under the finger, then let HA confirm the choice. If the integration never
    // acknowledges it, fall back to the last real snapshot instead of leaving a false UI state.
    LaunchedEffect(climate.hvacMode, pendingMode) {
        val pending = pendingMode ?: return@LaunchedEffect
        if (climate.hvacMode == pending) {
            pendingMode = null
        } else {
            kotlinx.coroutines.delay(5_000)
            if (pendingMode == pending && climate.hvacMode != pending) pendingMode = null
        }
    }
    val displayedMode = pendingMode ?: climate.hvacMode

    val usesRange = climate.supportsTargetRange &&
        displayedMode in setOf("heat_cool", "auto") &&
        climate.targetLow != null && climate.targetHigh != null
    val dialMode = when {
        displayedMode == "off" -> ThermostatMode.OFF
        usesRange -> ThermostatMode.HEAT_COOL
        displayedMode == "heat" -> ThermostatMode.HEAT
        displayedMode == "cool" -> ThermostatMode.COOL
        else -> ThermostatMode.AUTO
    }
    val activity = when (climate.hvacAction) {
        "heating", "preheating" -> ThermostatActivity.HEATING
        "cooling" -> ThermostatActivity.COOLING
        "idle" -> ThermostatActivity.IDLE
        "off" -> ThermostatActivity.OFF
        else -> null
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (climate.hasTemperatureControl && displayedMode !in setOf("dry", "fan_only")) {
            TemperatureArcControl(
                mode = dialMode,
                activity = activity,
                target = target,
                onTargetChange = { target = it },
                lowTarget = low,
                highTarget = high,
                onRangeChange = { nextLow, nextHigh -> low = nextLow; high = nextHigh },
                valueRange = climate.minimumTemperature..climate.maximumTemperature,
                step = climate.temperatureStep,
                current = climate.currentTemperature,
                unit = climate.temperatureUnit,
                modifier = Modifier.fillMaxWidth().height(240.dp),
                onCommit = {
                    val data = if (usesRange) {
                        mapOf("target_temp_low" to low, "target_temp_high" to high)
                    } else {
                        mapOf("temperature" to target)
                    }
                    callService("climate", "set_temperature", chip.entityId, data)
                },
            )
            Spacer(Modifier.height(12.dp))
        } else if (climate.currentTemperature != null) {
            PanelDetailRow(PillDetail(stringResource(R.string.thermostat_room_temperature), formatTemperature(climate.currentTemperature, climate.temperatureStep, climate.temperatureUnit)))
            Spacer(Modifier.height(12.dp))
        }

        if (climate.availableModes.isNotEmpty()) {
            val labels = climate.availableModes.associateWith { mode ->
                hvacModePresentation(mode).label?.let { stringResource(it) }
                    ?: mode.replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            val modeAccent = when (displayedMode) {
                "heat" -> AppleColors.thermostatHeat
                "cool" -> AppleColors.thermostatCool
                "off" -> ControlNeutral
                else -> AppleColors.active
            }
            WheelPicker(
                options = climate.availableModes,
                selected = displayedMode,
                onSelect = { mode ->
                    if (mode != displayedMode) {
                        pendingMode = mode
                        callService("climate", "set_hvac_mode", chip.entityId, mapOf("hvac_mode" to mode))
                    }
                },
                label = { labels.getValue(it) },
                accent = modeAccent,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }
}

private data class HvacModePresentation(val label: Int?, val icon: ImageVector)

private fun hvacModePresentation(mode: String): HvacModePresentation = when (mode) {
    "off" -> HvacModePresentation(R.string.hvac_mode_off, Icons.Outlined.PowerSettingsNew)
    "heat" -> HvacModePresentation(R.string.hvac_mode_heat, Icons.Outlined.LocalFireDepartment)
    "cool" -> HvacModePresentation(R.string.hvac_mode_cool, Icons.Outlined.AcUnit)
    "heat_cool", "auto" -> HvacModePresentation(R.string.hvac_mode_auto, Icons.Outlined.AutoMode)
    "dry" -> HvacModePresentation(R.string.hvac_mode_dry, Icons.Outlined.WaterDrop)
    "fan_only" -> HvacModePresentation(R.string.hvac_mode_fan_only, Icons.Outlined.Air)
    else -> HvacModePresentation(null, Icons.Outlined.AutoMode)
}

private fun formatTemperature(value: Float, step: Float, unit: String): String {
    val number = if (step < 1f) String.format("%.1f", value) else value.roundToInt().toString()
    return "$number $unit"
}
