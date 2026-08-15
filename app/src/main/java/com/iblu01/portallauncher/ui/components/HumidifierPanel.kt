package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

private data class HumidifierMode(val value: String?, val label: String)

/** Quick humidifier controls: power, HA-advertised target humidity and optional modes. */
@Composable
internal fun HumidifierControl(entity: HaEntity, modifier: Modifier = Modifier) {
    val contract = remember(entity) { entity.toGenericControlContract() }
    val callService = LocalCallService.current
    var value by remember(entity.entityId, contract.value) { mutableFloatStateOf(contract.value ?: contract.min) }
    val advertisedModes = remember(contract.options) { contract.options.filterNot { it.equals("off", true) } }
    val hasModes = "set_mode" in contract.actions && advertisedModes.isNotEmpty()
    val offLabel = stringResource(R.string.action_turn_off)
    val choices = remember(advertisedModes, offLabel) {
        listOf(HumidifierMode(null, offLabel)) + advertisedModes.map { option ->
            HumidifierMode(option, option.replace('_', ' ').replaceFirstChar(Char::uppercase))
        }
    }
    var selectedChoice by remember(entity.entityId, choices) {
        mutableStateOf(
            if (entity.state.equals("off", true)) choices.first()
            else choices.firstOrNull { it.value == contract.selectedOption } ?: choices.getOrElse(1) { choices.first() },
        )
    }
    LaunchedEffect(entity.state, contract.selectedOption) {
        selectedChoice = if (entity.state.equals("off", true)) choices.first()
        else choices.firstOrNull { it.value == contract.selectedOption } ?: choices.getOrElse(1) { choices.first() }
    }

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val spacing = maxHeight * 0.025f
        val pickerItemHeight = maxHeight * 0.075f
        val compactIconSize = minOf(maxWidth, maxHeight) * 0.055f

        val target: @Composable (Modifier) -> Unit = { area ->
            BoxWithConstraints(
                area,
                contentAlignment = Alignment.Center,
            ) {
                val ratio = 96f / 240f
                val sliderHeight = minOf(maxHeight, maxWidth / ratio)
                VerticalFillSlider(
                    value = value,
                    onValueChange = { value = contract.normalized(it) },
                    onValueChangeFinished = { raw ->
                        callService("humidifier", "set_humidity", entity.entityId, mapOf("humidity" to contract.normalized(raw).roundToInt()))
                    },
                    valueRange = contract.min..contract.max,
                    hapticSteps = (((contract.max - contract.min) / contract.step).roundToInt()).coerceIn(1, 100),
                    icon = Icons.Outlined.WaterDrop,
                    label = { "${it.roundToInt()} %" },
                    accent = AppleColors.accent,
                    modifier = Modifier.size(sliderHeight * ratio, sliderHeight),
                )
            }
        }
        val modes: @Composable (Modifier) -> Unit = { area ->
            Box(area, contentAlignment = Alignment.Center) {
            if (hasModes) {
                key(selectedChoice) {
                    WheelPicker(
                        options = choices,
                        selected = selectedChoice,
                        onSelect = { choice ->
                            if (choice == selectedChoice) return@WheelPicker
                            selectedChoice = choice
                            if (choice.value == null) {
                                callService("humidifier", "turn_off", entity.entityId)
                            } else {
                                if (entity.state.equals("off", true)) callService("humidifier", "turn_on", entity.entityId)
                                callService("humidifier", "set_mode", entity.entityId, mapOf("mode" to choice.value))
                            }
                        },
                        label = HumidifierMode::label,
                        visibleCount = 3,
                        itemHeight = pickerItemHeight,
                        accent = AppleColors.accent,
                        modifier = Modifier.fillMaxWidth(if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) 0.9f else 0.72f),
                    )
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        modifier = Modifier
                            .clip(AppleShapes.pill)
                            .background(AppleColors.frostedFill, AppleShapes.pill)
                            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                            .appleClickable {
                                callService(
                                    "humidifier",
                                    if (entity.state.equals("off", true)) "turn_on" else "turn_off",
                                    entity.entityId,
                                )
                            }
                            .padding(horizontal = compactIconSize * 0.8f, vertical = compactIconSize * 0.45f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(compactIconSize * 0.35f),
                        ) {
                            Icon(
                                Icons.Outlined.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (entity.state.equals("off", true)) AppleColors.secondary else AppleColors.active,
                                modifier = Modifier.size(compactIconSize),
                            )
                            Text(
                                if (entity.state.equals("off", true)) stringResource(R.string.action_turn_on) else stringResource(R.string.action_turn_off),
                                style = AppleTypography.bodySmall,
                                color = AppleColors.primary,
                            )
                        }
                    }
                }
            }
            }
        }
        if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
            AdaptivePanelSplit(Modifier.fillMaxSize(), primaryWeight = 0.45f, primary = target, secondary = modes)
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                target(Modifier.fillMaxWidth(0.54f).weight(1f))
                Spacer(Modifier.height(spacing))
                modes(Modifier.fillMaxWidth())
            }
        }
    }
}
