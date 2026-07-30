package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/**
 * The big round HomeKit-style accessory button used by simple on/off accessories
 * (lock, switch, fan). Tap toggles; colour and glyph reflect the live state.
 */
@Composable
fun BigCircleButton(
    active: Boolean,
    color: Color,
    icon: ImageVector,
    caption: String,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(color.copy(alpha = if (active) 0.22f else 0.08f), AppleMotion.spring(), label = "bigBtnBg")
    val tint by animateColorAsState(if (active) color else AppleColors.secondary, AppleMotion.spring(), label = "bigBtnTint")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(156.dp)
                .clip(CircleShape)
                .background(bg, CircleShape)
                .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                .appleClickable(onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(66.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(caption, style = AppleTypography.titleMedium.copy(fontSize = 17.sp), color = AppleColors.primary)
    }
}

@Composable
fun SwitchControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val on = entity.state.equals("on", true)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        BigCircleButton(on, AppleColors.active, Icons.Outlined.PowerSettingsNew, if (on) stringResource(R.string.switch_state_on) else stringResource(R.string.switch_state_off)) {
            callService("switch", "toggle", chip.entityId)
        }
        Spacer(Modifier.height(20.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}

@Composable
fun FanControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val on = entity.state.equals("on", true)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        BigCircleButton(on, AppleColors.accent, Icons.Outlined.Air, if (on) stringResource(R.string.fan_state_on) else stringResource(R.string.fan_state_off)) {
            callService("fan", "toggle", chip.entityId)
        }
        Spacer(Modifier.height(20.dp))

        if (entity.supports(FanFeature.SET_SPEED)) {
            val committed = entity.attributes.optInt("percentage", 0)
            var slider by remember(committed) { mutableFloatStateOf(committed.toFloat()) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.fan_speed_label), style = AppleTypography.bodyLarge, color = AppleColors.secondary, modifier = Modifier.weight(1f))
                Text("${slider.toInt()}%", style = AppleTypography.bodyLarge, color = AppleColors.primary)
            }
            Slider(
                value = slider,
                onValueChange = { slider = it },
                onValueChangeFinished = { callService("fan", "set_percentage", chip.entityId, mapOf("percentage" to slider.toInt())) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = AppleColors.accent, activeTrackColor = AppleColors.accent),
            )
        }

        if (entity.supports(FanFeature.OSCILLATE)) {
            val oscillating = entity.attributes.optBoolean("oscillating", false)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PanelModeButton(stringResource(R.string.fan_oscillation_label), Icons.Outlined.Sync, oscillating) {
                    callService("fan", "oscillate", chip.entityId, mapOf("oscillating" to !oscillating))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
