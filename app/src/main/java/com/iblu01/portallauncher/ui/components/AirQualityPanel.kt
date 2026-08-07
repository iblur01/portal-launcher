package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

@Composable
fun AirQualityContent(chip: LauncherChip, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        AirQualityHeader(onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            AirHero(chip.value, chip.details.size)
            Spacer(Modifier.height(28.dp))
            SensorGrid(chip)
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Overall status → the single accent that drives the hero + tinting. */
private fun statusColor(value: String): Color = when {
    value.contains("Mauvaise", ignoreCase = true) -> AppleColors.error
    value.contains("Moyenne", ignoreCase = true) -> AppleColors.warning
    else -> AppleColors.active
}

@Composable
private fun AirQualityHeader(onBack: () -> Unit) {
    PanelHeader(
        title = stringResource(R.string.air_quality_panel_title),
        onNavigation = onBack,
        navigationIcon = Icons.Filled.ArrowBack,
        navigationContentDescription = stringResource(R.string.air_quality_back_desc),
    )
}

/** Centered status word with a soft colored glow — the emotional anchor of the panel. */
@Composable
private fun AirHero(value: String, sensorCount: Int) {
    val color = statusColor(value)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Soft radial glow (no live blur on API 28 — gradient carries the depth).
        Box(
            Modifier
                .size(200.dp)
                .background(
                    Brush.radialGradient(
                        0f to color.copy(alpha = 0.22f),
                        0.7f to color.copy(alpha = 0.04f),
                        1f to Color.Transparent,
                    ),
                    CircleShape,
                )
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = AppleTypography.displayLarge.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = color,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (sensorCount <= 1) "$sensorCount capteur" else "$sensorCount capteurs",
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.tertiary,
            )
        }
    }
}

/** Responsive glass-tile grid: 3 columns on wide panels, 2 otherwise. */
@Composable
private fun ColumnScope.SensorGrid(chip: LauncherChip) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = if (maxWidth > 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            chip.details.chunked(cols).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { detail ->
                        SensorTile(detail.label, detail.value, Modifier.weight(1f))
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SensorTile(label: String, rawValue: String, modifier: Modifier = Modifier) {
    val parsed = parseAirValue(rawValue)
    val reading = parsed.first
    val unit = parsed.second
    val fraction = parsed.third
    val color = parsed.fourth

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                reading,
                style = AppleTypography.titleLarge.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.5).sp,
                ),
                color = AppleColors.primary,
                maxLines = 1,
            )
            if (unit.isNotBlank()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    unit,
                    style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                    color = AppleColors.tertiary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(AppleColors.quaternary)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

private fun parseAirValue(raw: String): Quadruple<String, String, Float, Color> {
    val digits = raw.replace(",", ".").trim()
    val number = Regex("""([\d.]+)""").find(digits)?.value
    val value = number?.toFloatOrNull() ?: 0f
    val reading = number ?: digits
    val unit = raw.substringAfter(number ?: "").trim()

    return when {
        Regex("µg|ug|μg", RegexOption.IGNORE_CASE).containsMatchIn(unit) -> {
            val (fraction, color) = when {
                value > 50f -> 1f to AppleColors.error
                value > 25f -> (value / 50f) to AppleColors.warning
                else -> (value / 25f) to AppleColors.active
            }
            Quadruple(reading, unit, fraction, color)
        }
        unit.contains("ppm", ignoreCase = true) || raw.lowercase().contains("co") -> {
            val (fraction, color) = when {
                value > 1000f -> 1f to AppleColors.error
                value > 800f -> (value / 1000f) to AppleColors.warning
                else -> (value / 1000f) to AppleColors.active
            }
            Quadruple(reading, unit, fraction, color)
        }
        unit.contains("aqi", ignoreCase = true) || raw.lowercase().contains("aqi") -> {
            val (fraction, color) = when {
                value > 100f -> 1f to AppleColors.error
                value > 50f -> (value / 100f) to AppleColors.warning
                else -> (value / 100f) to AppleColors.active
            }
            Quadruple(reading, unit, fraction, color)
        }
        unit.contains("ppb", ignoreCase = true) || raw.lowercase().contains("voc") -> {
            val (fraction, color) = when {
                value > 800f -> 1f to AppleColors.error
                value > 500f -> (value / 800f) to AppleColors.warning
                else -> (value / 800f) to AppleColors.active
            }
            Quadruple(reading, unit, fraction, color)
        }
        else -> {
            val (fraction, color) = when {
                value > 800f -> 1f to AppleColors.error
                value > 400f -> (value / 800f) to AppleColors.warning
                else -> (value / 800f) to AppleColors.active
            }
            Quadruple(reading, unit, fraction, color)
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
