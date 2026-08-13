package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.ControlNeutral
import com.iblu01.portallauncher.ui.components.controls.ThermostatActivity
import com.iblu01.portallauncher.ui.components.controls.ThermostatMode
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

/** Home Assistant adapter around the reusable, backend-agnostic thermostat controls. */
@Composable
fun ThermostatControl(chip: LauncherChip, modifier: Modifier = Modifier) {
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

    val temperature: @Composable (Modifier) -> Unit = { temperatureModifier ->
        if (climate.hasTemperatureControl && displayedMode !in setOf("dry", "fan_only")) {
            BoxWithConstraints(temperatureModifier, contentAlignment = Alignment.Center) {
                val dialSize = minOf(maxWidth, maxHeight, 320.dp)
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
                    modifier = Modifier.size(dialSize),
                    onCommit = {
                        val data = if (usesRange) {
                            mapOf("target_temp_low" to low, "target_temp_high" to high)
                        } else {
                            mapOf("temperature" to target)
                        }
                        callService("climate", "set_temperature", chip.entityId, data)
                    },
                )
            }
        } else if (climate.currentTemperature != null) {
            Box(temperatureModifier, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val formatted = formatTemperatureNumber(climate.currentTemperature, climate.temperatureStep)
                    Text(
                        formatted,
                        style = AppleTypography.headlineLarge.copy(fontSize = 68.sp, fontWeight = FontWeight.Light),
                        color = AppleColors.primary,
                    )
                    Text(
                        climate.temperatureUnit,
                        style = AppleTypography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.Light),
                        color = AppleColors.secondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
    }
    val modes: @Composable (Modifier) -> Unit = { modeModifier ->
        Column(modeModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
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
                modifier = Modifier.fillMaxWidth(if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) 0.84f else 0.72f),
            )
        }
        }
    }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        AdaptivePanelSplit(modifier, primaryWeight = 0.56f, primary = { temperature(it) }, secondary = { modes(it) })
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        temperature(Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(12.dp))
        modes(Modifier.fillMaxWidth())
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

private fun formatTemperatureNumber(value: Float, step: Float): String =
    if (step < 1f) String.format("%.1f", value) else value.roundToInt().toString()
