package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Shared renderer for HA controls whose shape is defined entirely by entity capabilities. */
@Composable
fun GenericHaEntityControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val contract = remember(entity) { entity.toGenericControlContract() }
    val callService = LocalCallService.current
    var value by remember(entity.entityId, contract.value) { mutableFloatStateOf(contract.value ?: contract.min) }
    val valueAction = contract.actions.firstOrNull { it in setOf("set_value", "set_humidity", "set_temperature", "set_valve_position") }
    val valueKey = when (valueAction) {
        "set_humidity" -> "humidity"
        "set_temperature" -> "temperature"
        "set_valve_position" -> "position"
        else -> "value"
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (contract.value != null && valueAction != null && contract.max > contract.min) {
            Text("${contract.normalized(value)} ${contract.unit}".trim(), style = AppleTypography.titleLarge, color = AppleColors.primary)
            Slider(
                value = value.coerceIn(contract.min, contract.max),
                onValueChange = { value = contract.normalized(it) },
                onValueChangeFinished = { callService(contract.domain, valueAction, entity.entityId, mapOf(valueKey to contract.normalized(value))) },
                valueRange = contract.min..contract.max,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
        if (contract.options.isNotEmpty()) {
            contract.options.chunked(3).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    options.forEach { option ->
                        Button(
                            onClick = {
                                val service = if (contract.domain == "humidifier") "set_mode" else if (contract.domain == "water_heater") "set_operation_mode" else "select_option"
                                val key = if (service == "set_mode") "mode" else if (service == "set_operation_mode") "operation_mode" else "option"
                                callService(contract.domain, service, entity.entityId, mapOf(key to option))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (option == contract.selectedOption) AppleColors.active else AppleColors.frostedFill),
                        ) { Text(option) }
                    }
                }
            }
        }
        val directActions = contract.actions.filterNot { it in setOf(valueAction, "set_mode", "set_operation_mode", "select_option", "tone", "duration", "volume_level", "stream") }
        directActions.chunked(3).forEach { actions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                actions.forEach { action ->
                    Button(onClick = { callService(contract.domain, action, entity.entityId) }) {
                        Text(actionLabel(action))
                    }
                }
            }
        }
        chip.details.forEach { PanelDetailRow(it) }
    }
}

private fun actionLabel(action: String): String = when (action) {
    "press" -> "Exécuter"
    "turn_on" -> "Allumer"
    "turn_off" -> "Éteindre"
    "open_valve" -> "Ouvrir"
    "close_valve" -> "Fermer"
    "stop_valve" -> "Arrêter"
    "start_mowing" -> "Démarrer"
    "pause" -> "Pause"
    "dock" -> "Retour base"
    "set_away_mode" -> "Mode absence"
    else -> action.replace('_', ' ').replaceFirstChar(Char::uppercase)
}
