package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f   // bottom-left
private const val SWEEP = 270f          // 90° gap at the bottom

/**
 * HomeKit-style thermostat: a circular ring the user drags to set the target temperature,
 * current temperature in the centre, and an HVAC-mode row built from the entity's `hvac_modes`.
 */
@Composable
fun ThermostatControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val minTemp = entity.attributes.optDouble("min_temp", 7.0).toFloat()
    val maxTemp = entity.attributes.optDouble("max_temp", 35.0).toFloat()
    val step = entity.attributes.optDouble("target_temp_step", 0.5).toFloat().coerceAtLeast(0.1f)
    val current = entity.attributes.optDouble("current_temperature").let { if (it.isNaN()) null else it.toFloat() }
    val committed = entity.attributes.optDouble("temperature").let { if (it.isNaN()) (current ?: minTemp) else it.toFloat() }
    val mode = entity.state.lowercase()
    val action = entity.attributes.optString("hvac_action").lowercase()

    var target by remember(committed) { mutableFloatStateOf(committed) }

    val ringColor = when {
        action == "heating" || mode == "heat" -> Color(0xFFFF9F0A)
        action == "cooling" || mode == "cool" -> AppleColors.accent
        mode == "off" -> AppleColors.inactive
        else -> AppleColors.active
    }

    fun fmt(t: Float) = if (t % 1f == 0f) "${t.toInt()}°" else "%.1f°".format(t)
    fun commit() = callService("climate", "set_temperature", chip.entityId, mapOf("temperature" to target))
    fun snap(fraction: Float): Float {
        val raw = minTemp + fraction.coerceIn(0f, 1f) * (maxTemp - minTemp)
        return (((raw / step).roundToInt()) * step).coerceIn(minTemp, maxTemp)
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(240.dp)
                .pointerInput(minTemp, maxTemp, step) {
                    detectDragGestures(onDragEnd = { commit() }) { change, _ ->
                        change.consume()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        var deg = Math.toDegrees(atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble())).toFloat()
                        if (deg < 0) deg += 360f
                        val delta = (deg - START_ANGLE + 360f) % 360f
                        val fraction = when {
                            delta <= SWEEP -> delta / SWEEP
                            delta < (SWEEP + (360f - SWEEP) / 2f) -> 1f   // past open end → snap max
                            else -> 0f                                    // in bottom gap toward closed end → min
                        }
                        target = snap(fraction)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(240.dp)) {
                val stroke = 18.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                drawArc(AppleColors.frostedFill, START_ANGLE, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val fraction = ((target - minTemp) / (maxTemp - minTemp)).coerceIn(0f, 1f)
                drawArc(ringColor, START_ANGLE, SWEEP * fraction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                // Knob
                val knobAngle = Math.toRadians((START_ANGLE + SWEEP * fraction).toDouble())
                val radius = diameter / 2f
                val knob = Offset(center.x + (radius * cos(knobAngle)).toFloat(), center.y + (radius * sin(knobAngle)).toFloat())
                drawCircle(Color.White, stroke / 2f - 2.dp.toPx(), knob)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fmt(target), style = AppleTypography.displayLarge.copy(fontSize = 64.sp), color = AppleColors.primary)
                if (current != null) Text(stringResource(R.string.thermostat_current_temp_format, fmt(current)), style = AppleTypography.bodyLarge, color = AppleColors.secondary)
            }
        }
        Spacer(Modifier.height(20.dp))

        val modes = entity.attributes.optJSONArray("hvac_modes")
        if (modes != null && modes.length() > 0) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until modes.length()) {
                    val m = modes.optString(i).lowercase()
                    val label = when (m) {
                        "off" -> stringResource(R.string.hvac_mode_off)
                        "heat" -> stringResource(R.string.hvac_mode_heat)
                        "cool" -> stringResource(R.string.hvac_mode_cool)
                        "heat_cool", "auto" -> stringResource(R.string.hvac_mode_auto)
                        "dry" -> stringResource(R.string.hvac_mode_dry)
                        "fan_only" -> stringResource(R.string.hvac_mode_fan_only)
                        else -> m.replaceFirstChar { it.uppercase() }
                    }
                    val icon = when (m) {
                        "off" -> Icons.Outlined.PowerSettingsNew
                        "heat" -> Icons.Outlined.LocalFireDepartment
                        "cool" -> Icons.Outlined.AcUnit
                        "heat_cool", "auto" -> Icons.Outlined.AutoMode
                        "dry" -> Icons.Outlined.WaterDrop
                        "fan_only" -> Icons.Outlined.Air
                        else -> Icons.Outlined.AutoMode
                    }
                    PanelModeButton(label, icon, active = m == mode) {
                        callService("climate", "set_hvac_mode", chip.entityId, mapOf("hvac_mode" to m))
                    }
                }
            }
        }
    }
}
