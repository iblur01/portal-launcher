package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.VerticalSegmentedSelector
import com.iblu01.portallauncher.ui.components.controls.ControlContentLayout
import com.iblu01.portallauncher.ui.components.controls.HorizontalSegmentedSelector

private sealed interface FanModeOption {
    data object Off : FanModeOption
    data class Preset(val value: String) : FanModeOption
}

@Composable
fun SwitchControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val on = entity.state.equals("on", true)
    var pending by remember(chip.entityId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(on, pending) {
        val expected = pending ?: return@LaunchedEffect
        if (on == expected) pending = null else {
            kotlinx.coroutines.delay(5_000)
            if (pending == expected && on != expected) pending = null
        }
    }
    val displayedOn = pending ?: on
    val onLabel = stringResource(R.string.switch_state_on)
    val offLabel = stringResource(R.string.switch_state_off)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.54f).weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = 96f / 240f
            val controlHeight = minOf(maxHeight, maxWidth / ratio)
            val controlWidth = controlHeight * ratio
            VerticalSwitch(
                checked = displayedOn,
                onCheckedChange = { wanted ->
                    if (wanted != displayedOn) {
                        pending = wanted
                        callService(entity.domain, if (wanted) "turn_on" else "turn_off", chip.entityId)
                    }
                },
                accent = AppleColors.active,
                label = { enabled -> if (enabled) onLabel else offLabel },
                modifier = Modifier.size(controlWidth, controlHeight),
            )
        }
        Spacer(Modifier.height(20.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}

@Composable
fun FanControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val fan = entity.toFanContract()
    val onLabel = stringResource(R.string.fan_state_on)
    val offLabel = stringResource(R.string.fan_state_off)
    val speedLabel = stringResource(R.string.fan_speed_label)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        if (fan.primaryControl !is FanPrimaryControl.OnOff) {
            Text(
                if (!fan.isOn) offLabel else speedLabel,
                style = AppleTypography.titleMedium.copy(fontSize = 17.sp),
                color = AppleColors.primary,
            )
            Spacer(Modifier.height(12.dp))
        }
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(0.54f).weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = 96f / 240f
            val controlHeight = minOf(maxHeight, maxWidth / ratio)
            val controlWidth = controlHeight * ratio
        when (val control = fan.primaryControl) {
            FanPrimaryControl.OnOff -> {
                VerticalSwitch(
                    checked = fan.isOn,
                    onCheckedChange = { wanted -> callService("fan", if (wanted) "turn_on" else "turn_off", chip.entityId) },
                    accent = AppleColors.fanAccent,
                    icon = { Icons.Outlined.Air },
                    label = { if (it) onLabel else offLabel },
                    modifier = Modifier.size(controlWidth, controlHeight),
                )
            }
            is FanPrimaryControl.Percentage -> {
                VerticalFillSlider(
                    // Some integrations retain their last percentage while reporting off/stopped.
                    // The control must still look fully disabled in that state.
                    value = if (fan.isOn) control.value.toFloat() else 0f,
                    onValueChange = {},
                    valueRange = 0f..100f,
                    accent = AppleColors.fanAccent,
                    hapticSteps = (100 / control.step).coerceAtLeast(1),
                    icon = Icons.Outlined.Air,
                    label = { "${it.toInt()} %" },
                    onValueChangeFinished = { value ->
                        callService("fan", "set_percentage", chip.entityId, mapOf("percentage" to value.toInt()))
                    },
                    modifier = Modifier.size(controlWidth, controlHeight),
                )
            }
            is FanPrimaryControl.Presets -> {
                // Active speeds run top-to-bottom; the neutral off/stopped state always rests at
                // the bottom, consistently with VerticalSwitch.
                val activeValues = control.values
                    .filterNot { it.lowercase() in setOf("off", "stopped", "stop", "éteint", "eteint") }
                val options = activeValues.map(FanModeOption::Preset) + FanModeOption.Off
                val selectedValue = control.selected?.takeIf(activeValues::contains) ?: activeValues.firstOrNull()
                val selected = if (!fan.isOn || selectedValue == null) FanModeOption.Off
                    else FanModeOption.Preset(selectedValue)
                VerticalSegmentedSelector(
                    options = options,
                    selected = selected,
                    onSelect = { option -> when (option) {
                        FanModeOption.Off -> callService("fan", "turn_off", chip.entityId)
                        is FanModeOption.Preset -> callService("fan", "set_preset_mode", chip.entityId, mapOf("preset_mode" to option.value))
                    } },
                    label = { option -> when (option) {
                        FanModeOption.Off -> offLabel
                        is FanModeOption.Preset -> option.value.replace('_', ' ').replaceFirstChar { it.uppercase() }
                    } },
                    icon = { option -> if (option == FanModeOption.Off) null else Icons.Outlined.Air },
                    accent = AppleColors.fanAccent,
                    isNeutral = { it == FanModeOption.Off },
                    contentLayout = ControlContentLayout.Horizontal,
                    segmentHeight = controlHeight / options.size,
                    modifier = Modifier.width(controlWidth),
                )
            }
        }
        }
        Spacer(Modifier.height(20.dp))

        if (fan.supportsOscillation) {
            Spacer(Modifier.height(4.dp))
            val fixedLabel = stringResource(R.string.fan_oscillation_off)
            val oscillatingLabel = stringResource(R.string.fan_oscillation_on)
            HorizontalSegmentedSelector(
                options = listOf(false, true),
                selected = fan.isOscillating,
                onSelect = { oscillating ->
                    if (oscillating != fan.isOscillating) {
                        callService("fan", "oscillate", chip.entityId, mapOf("oscillating" to oscillating))
                    }
                },
                label = { if (it) oscillatingLabel else fixedLabel },
                icon = { if (it) Icons.Outlined.Sync else Icons.Outlined.Air },
                accent = AppleColors.fanAccent,
                isNeutral = { !it },
                contentLayout = ControlContentLayout.Horizontal,
                enabled = fan.isOn,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }

        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
