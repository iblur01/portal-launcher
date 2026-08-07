package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors
import kotlin.math.roundToInt

/** Quick humidifier controls: power, HA-advertised target humidity and optional modes. */
@Composable
internal fun HumidifierControl(entity: HaEntity, modifier: Modifier = Modifier) {
    val contract = remember(entity) { entity.toGenericControlContract() }
    val callService = LocalCallService.current
    var value by remember(entity.entityId, contract.value) { mutableFloatStateOf(contract.value ?: contract.min) }
    var mode by remember(entity.entityId, contract.selectedOption) { mutableStateOf(contract.selectedOption) }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth(0.54f).weight(1f), contentAlignment = Alignment.Center) {
            val ratio = 96f / 240f
            val height = minOf(maxHeight, maxWidth / ratio)
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
                modifier = Modifier.size(height * ratio, height),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GlassButton(
                label = if (entity.state.equals("off", true)) "Allumer" else "Éteindre",
                icon = Icons.Outlined.PowerSettingsNew,
                active = !entity.state.equals("off", true),
                onClick = { callService("humidifier", if (entity.state.equals("off", true)) "turn_on" else "turn_off", entity.entityId) },
            )
        }
        if ("set_mode" in contract.actions && contract.options.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            WheelPicker(
                options = contract.options,
                selected = mode ?: contract.options.first(),
                onSelect = { selected -> mode = selected; callService("humidifier", "set_mode", entity.entityId, mapOf("mode" to selected)) },
                label = { it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                accent = AppleColors.accent,
                modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }
}
