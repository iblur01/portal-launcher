package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.ui.icons.HaIcon
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.scaled
import com.iblu01.portallauncher.ui.theme.stateColor

private val NeutralDeviceStates = setOf(
    "off", "stopped", "stop", "idle", "standby", "paused", "docked",
    "disarmed", "éteint", "eteint", "arrêt", "arret",
)

private val SelectedChipContent = Color(0xFF1C1C1E)

/** Neutral glyphs need a dark tint on the selected chip's white surface. */
internal fun selectedChipAccent(accent: Color, selected: Boolean): Color =
    if (selected && (accent == AppleColors.inactive || accent.alpha < 1f)) SelectedChipContent else accent

/** One accent policy shared by pills and their panels. Inactive always wins over device colour. */
fun launcherChipAccent(chip: LauncherChip): Color = when {
    chip.deviceState?.trim()?.lowercase() in NeutralDeviceStates -> AppleColors.inactive
    else -> when (chip.kind) {
        PillKind.LOCK -> if (chip.state.lowercase() in setOf("critical", "error")) AppleColors.error else AppleColors.lockAccent
        PillKind.FAN -> AppleColors.fanAccent
        PillKind.VALVE -> AppleColors.accent
        PillKind.THERMOSTAT -> when (chip.deviceState?.lowercase()) {
            "heat" -> AppleColors.thermostatHeat
            "cool" -> AppleColors.thermostatCool
            else -> stateColor(chip.state)
        }
        else -> stateColor(chip.state)
    }
}

@Composable
fun StatusChip(chip: LauncherChip, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, onLongPress: (() -> Unit)? = null) {
    val target = selectedChipAccent(launcherChipAccent(chip), selected)
    val accent by animateColorAsState(target, AppleMotion.spring(), label = "chipAccent")
    val animatedProgress by animateFloatAsState(chip.progress, AppleMotion.spring(), label = "chipProgress")
    // Selected chip: iOS-style — white fill, dark text, matching border.
    val selectedSubtitle = Color(0xFF3C3C43).copy(alpha = 0.6f)
    val borderColor by animateColorAsState(if (selected) Color.White else AppleColors.frostedBorder, AppleMotion.spring(), label = "chipBorder")
    val fillColor by animateColorAsState(if (selected) Color.White else AppleColors.frostedFill, AppleMotion.spring(), label = "chipFill")
    Row(
        modifier = modifier
            .clip(AppleShapes.pill)
            .background(fillColor, AppleShapes.pill)
            .border(if (selected) 1.dp else 0.5.dp, borderColor, AppleShapes.pill)
            .then(if (onClick != null) Modifier.appleClickable(onClick, onLongPress) else Modifier)
            .padding(start = 12.dp.scaled(), end = 22.dp.scaled(), top = 12.dp.scaled(), bottom = 12.dp.scaled()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp.scaled()),
            contentAlignment = Alignment.Center
        ) {
            if (chip.progress > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    drawCircle(
                        color = accent.copy(alpha = 0.15f),
                        radius = size.minDimension / 2f - stroke / 2f,
                        center = center,
                        style = Stroke(width = stroke)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(accent.copy(alpha = 0.22f), CircleShape)
                )
            }
            ChipGlyph(chip, accent)
        }
        Spacer(Modifier.width(14.dp.scaled()))
        Column {
            Text(
                chip.label,
                style = AppleTypography.bodyLarge.copy(fontSize = AppleTypography.bodyLarge.fontSize.scaled()),
                color = if (selected) selectedSubtitle else AppleColors.secondary,
                maxLines = 1,
            )
            Text(
                chip.value,
                style = AppleTypography.titleLarge.copy(fontSize = AppleTypography.titleLarge.fontSize.scaled()),
                color = if (selected) SelectedChipContent else AppleColors.primary,
                maxLines = 1,
            )
        }
    }
}

/**
 * Whether this chip should show its entity's Home Assistant icon rather than a launcher glyph, so
 * a device customised on the dashboard looks the same here.
 *
 * Two chips keep their own glyph. A group chip aggregates many entities and has no single icon to
 * borrow. The washer's glyph animates through wash phases, which no static icon can express — lock
 * and fan lose nothing by deferring, since HA varies those by state itself.
 *
 * Even when this returns true the HA icon may not resolve, and the launcher glyph still wins; this
 * only decides whether it is worth asking.
 */
internal fun LauncherChip.defersToHaIcon(): Boolean = entityId.isNotBlank() && icon != "washer"

@Composable
private fun ChipGlyph(chip: LauncherChip, accent: Color, iconSize: Dp = 26.dp.scaled()) {
    val modifier = Modifier.size(iconSize)

    val haRef = if (chip.defersToHaIcon()) rememberHaIconRef(chip.entityId) else null
    if (haRef != null) {
        HaIcon(haRef, null, accent, iconSize, launcherIcon(chip.icon))
        return
    }

    // Nothing from HA: fall back to the launcher's own glyphs, bespoke ones included.
    when (chip.icon) {
        "washer" -> WasherGlyph(chip.value, accent, modifier)
        "air" -> AirGlyph(chip.state, accent, modifier)
        "lock" -> {
            val icon = if (chip.state == "error") Icons.Outlined.LockOpen else Icons.Outlined.Lock
            Icon(icon, null, tint = accent, modifier = modifier)
        }
        else -> Icon(launcherIcon(chip.icon), null, tint = accent, modifier = modifier)
    }
}

@Composable
private fun WasherGlyph(phase: String, accent: Color, modifier: Modifier = Modifier) {
    when (phase) {
        "Essorage" -> {
            val spin by rememberInfiniteTransition(label = "spin").animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(1200, easing = LinearEasing)),
                label = "a"
            )
            Icon(Icons.Outlined.Autorenew, null, tint = accent, modifier = modifier.rotate(spin))
        }
        "Terminé" -> Icon(Icons.Outlined.CheckCircle, null, tint = accent, modifier = modifier)
        else -> Icon(Icons.Outlined.WaterDrop, null, tint = accent, modifier = modifier)
    }
}

@Composable
private fun AirGlyph(state: String, accent: Color, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (state) {
        "active" -> Icons.Outlined.Eco
        "error" -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Autorenew
    }
    Icon(icon, null, tint = accent, modifier = modifier)
}
