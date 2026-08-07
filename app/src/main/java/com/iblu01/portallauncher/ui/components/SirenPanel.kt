package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.components.controls.WheelPicker
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

@Composable
fun SirenControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val callService = LocalCallService.current
    val contract = remember(entity) { entity.toGenericControlContract() }
    val active = entity.state.equals("on", true)
    val canStart = "turn_on" in contract.actions
    val canStop = "turn_off" in contract.actions
    var tone by remember(entity.entityId) { mutableStateOf(contract.options.firstOrNull()) }
    val triggerLabel = stringResource(R.string.siren_action_trigger)
    val stopLabel = stringResource(R.string.siren_action_stop)

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (active) stringResource(R.string.siren_state_active) else stringResource(R.string.siren_state_inactive),
            style = AppleTypography.titleMedium, color = if (active) AppleColors.error else AppleColors.primary,
        )
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth(0.54f).weight(1f), contentAlignment = Alignment.Center) {
            val widthToHeightRatio = 96f / 240f
            val height = minOf(maxHeight, maxWidth / widthToHeightRatio)
            val width = height * widthToHeightRatio
            VerticalSwitch(
                checked = active,
                onCheckedChange = { wanted ->
                    val service = if (wanted) "turn_on" else "turn_off"
                    val data = if (wanted && tone != null && "tone" in contract.actions) mapOf("tone" to tone!!) else null
                    callService("siren", service, entity.entityId, data)
                },
                enabled = if (active) canStop else canStart,
                accent = AppleColors.error,
                icon = { if (it) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff },
                label = { if (it) stopLabel else triggerLabel },
                modifier = Modifier.size(width, height),
            )
        }
        if ("tone" in contract.actions && contract.options.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            WheelPicker(
                options = contract.options, selected = tone ?: contract.options.first(), onSelect = { tone = it },
                label = { it.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                accent = AppleColors.error, modifier = Modifier.fillMaxWidth(0.72f),
            )
        }
    }
}
