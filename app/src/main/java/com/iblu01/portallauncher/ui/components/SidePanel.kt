package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.domain.home.PillGroupSnapshot
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.scaled
import com.iblu01.portallauncher.ui.theme.stateColor
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/**
 * What the side panel is currently showing. Media auto-opens when playback starts
 * (dismissable), while chip actions are user-requested and take precedence.
 */
sealed interface PanelContent {
    data class Media(val session: PlayingMedia) : PanelContent
    data object MediaBrowser : PanelContent
    data class ChipActions(val chip: LauncherChip) : PanelContent
    data class Weather(val weather: WeatherUi) : PanelContent
    data class Group(
        val group: PillGroupSnapshot,
        val selectedDevice: LauncherChip?,
        val deviceRequested: Boolean,
    ) : PanelContent
}

/**
 * Action zone shown in the side panel when a status pill is tapped: header with the
 * pill identity, then per-kind actions (light toggles, purifier modes) or read-only
 * detail rows for informational chips.
 */
@Composable
fun ChipActionsPanel(
    chip: LauncherChip,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector = Icons.Filled.Close,
    navigationContentDescription: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val accent = launcherChipAccent(chip)
    // Same large frosted card as the media player panel.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp.scaled(), vertical = 16.dp.scaled())
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(AppleShapes.panel)
                .background(Color.Black.copy(alpha = 0.72f))
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f),
                        0.48f to Color.Black.copy(alpha = 0.54f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    )
                )
            )
            val directLightDetail = chip.individualLightDetailOrNull()
            var lightDetail by remember(chip.id) { mutableStateOf(directLightDetail) }
            val detail = lightDetail
            if (detail != null) {
                LightDetailContent(
                    detail = detail,
                    onBack = if (directLightDetail != null) onDismiss else fun() { lightDetail = null },
                    closePanel = directLightDetail != null,
                )
            } else {
                ChipActionsContent(
                    chip = chip,
                    accent = accent,
                    onDismiss = onDismiss,
                    navigationIcon = navigationIcon,
                    navigationContentDescription = navigationContentDescription,
                    onClose = onClose,
                    onOpenLight = { lightDetail = it },
                )
            }
        }
    }
}

@Composable
private fun ChipActionsContent(
    chip: LauncherChip,
    accent: Color,
    onDismiss: () -> Unit,
    navigationIcon: ImageVector,
    navigationContentDescription: String?,
    onClose: (() -> Unit)?,
    onOpenLight: (PillDetail) -> Unit,
) {
    val entity = rememberEntity(chip.entityId)
    val batteryPercent = chip.batteryPercent ?: entity?.headerBatteryPercent()
    val showHeadlineValue = when (chip.toPanelKind()) {
        PanelKind.THERMOSTAT -> false // The dial owns the target and room temperatures.
        PanelKind.SWITCH -> false // The switch thumb already carries the on/off state.
        PanelKind.LOCK -> false // The lock control owns the translated state; avoid raw "locked".
        PanelKind.PURIFIER -> false // The mode selector already displays the active state.
        PanelKind.VACUUM -> false // The reusable vacuum status chip owns the active state.
        PanelKind.WASHER -> false // The washer dial owns progress, phase and remaining time.
        PanelKind.HUMIDIFIER, PanelKind.WATER_HEATER, PanelKind.VALVE, PanelKind.SIREN,
        PanelKind.LAWN_MOWER -> false
        PanelKind.COVER -> entity?.let {
            !it.supports(CoverFeature.SET_POSITION) || it.attributes.optInt("current_position", -1) !in 0..100
        } != false
        else -> true
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp.scaled(), vertical = 20.dp.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PanelHeader(
            title = chip.label,
            onNavigation = onDismiss,
            navigationIcon = navigationIcon,
            navigationContentDescription = navigationContentDescription
                ?: stringResource(R.string.side_panel_close_desc),
            titleIcon = launcherIcon(chip.icon),
            titleEntityId = chip.entityId,
            accent = accent,
            batteryPercent = batteryPercent,
            onClose = onClose,
        )

        Spacer(Modifier.height(if (showHeadlineValue) 14.dp.scaled() else 8.dp.scaled()))
        if (showHeadlineValue) {
            Text(
                chip.value,
                style = AppleTypography.titleLarge.copy(fontSize = AppleTypography.titleLarge.fontSize.scaled()),
                color = AppleColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp.scaled()))
        }

        // Center the control block vertically in the remaining space; it still scrolls if a
        // panel's content is taller than the panel (keypads, long detail lists).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (chip.toPanelKind() in setOf(PanelKind.COVER, PanelKind.FAN, PanelKind.SWITCH, PanelKind.LOCK, PanelKind.PURIFIER, PanelKind.VACUUM, PanelKind.HUMIDIFIER, PanelKind.WATER_HEATER, PanelKind.VALVE, PanelKind.SIREN, PanelKind.LAWN_MOWER)) {
                // Vertical controls need finite panel constraints so they can fill the available
                // height while preserving the same proportions as lights and covers.
                when (chip.toPanelKind()) {
                    PanelKind.COVER -> CoverControl(chip, Modifier.fillMaxSize())
                    PanelKind.FAN -> FanControl(chip, Modifier.fillMaxSize())
                    PanelKind.SWITCH -> SwitchControl(chip, Modifier.fillMaxSize())
                    PanelKind.LOCK -> LockControl(chip, Modifier.fillMaxSize())
                    PanelKind.PURIFIER -> PurifierActions(chip, Modifier.fillMaxSize())
                    PanelKind.VACUUM -> VacuumControl(chip, Modifier.fillMaxSize())
                    PanelKind.HUMIDIFIER -> GenericHaEntityControl(chip, Modifier.fillMaxSize())
                    PanelKind.WATER_HEATER -> WaterHeaterControl(chip, Modifier.fillMaxSize())
                    PanelKind.VALVE -> ValveControl(chip, Modifier.fillMaxSize())
                    PanelKind.SIREN -> SirenControl(chip, Modifier.fillMaxSize())
                    PanelKind.LAWN_MOWER -> GenericHaEntityControl(chip, Modifier.fillMaxSize())
                    else -> Unit
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Exhaustive typed router (design §4/Finding 8): no string-id branching, no
                    // catch-all `else` — every PanelKind is handled explicitly.
                    when (chip.toPanelKind()) {
                PanelKind.LIGHTS -> LightsActions(chip, onOpenLight)
                PanelKind.PURIFIER -> Unit // Bounded branch above.
                PanelKind.LOCK -> Unit // Bounded branch above.
                PanelKind.COVER, PanelKind.FAN, PanelKind.SWITCH -> Unit // Bounded branch above.
                PanelKind.THERMOSTAT -> ThermostatControl(chip)
                PanelKind.VACUUM -> Unit // Bounded branch above.
                PanelKind.ALARM -> AlarmControl(chip)
                PanelKind.WASHER -> WasherControl(chip)
                PanelKind.HUMIDIFIER, PanelKind.WATER_HEATER, PanelKind.VALVE, PanelKind.SIREN,
                PanelKind.LAWN_MOWER -> Unit // Bounded branch above.
                // Direct media pills are routed to MediaPlayerPanel upstream. A media member opened
                // from a group can still reach this nested renderer, so retain a meaningful status
                // row instead of presenting a completely empty panel.
                PanelKind.MEDIA -> chip.details
                    .ifEmpty { listOf(PillDetail("État", chip.value, chip.entityId)) }
                    .forEach { PanelDetailRow(it) }
                PanelKind.WEATHER, PanelKind.GENERIC_DETAILS ->
                    chip.details.forEach { PanelDetailRow(it) }
                    }
                }
            }
        }
    }
}

/** Large, background-free header shared by full panels and nested panel pages. */
@Composable
internal fun PanelHeader(
    title: String,
    onNavigation: () -> Unit,
    navigationIcon: ImageVector,
    navigationContentDescription: String,
    modifier: Modifier = Modifier,
    titleIcon: ImageVector? = null,
    /** When the panel is about one entity, its Home Assistant icon replaces [titleIcon]. */
    titleEntityId: String? = null,
    accent: Color = AppleColors.primary,
    batteryPercent: Int? = null,
    onTitleClick: (() -> Unit)? = null,
    /** Optional second action used by nested group pages: Back pops, Close dismisses the stack. */
    onClose: (() -> Unit)? = null,
) {
    val batteryColor = when {
        batteryPercent == null -> AppleColors.secondary
        batteryPercent <= 10 -> AppleColors.error
        batteryPercent <= 20 -> AppleColors.warning
        else -> AppleColors.secondary
    }
    Row(
        modifier = modifier.fillMaxWidth().height(52.dp.scaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp.scaled())
                .clip(CircleShape)
                .background(AppleColors.frostedFill)
                .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                .clickable(onClick = onNavigation),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                navigationIcon,
                contentDescription = navigationContentDescription,
                tint = AppleColors.primary,
                modifier = Modifier.size(24.dp.scaled()),
            )
        }
        Spacer(Modifier.size(14.dp.scaled()))
        Row(
            modifier = Modifier
                .weight(1f)
                .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (titleIcon != null) {
                if (!titleEntityId.isNullOrBlank()) {
                    HaEntityIcon(titleEntityId, null, accent, 23.dp.scaled(), titleIcon)
                } else {
                    Icon(titleIcon, null, tint = accent, modifier = Modifier.size(23.dp.scaled()))
                }
                Spacer(Modifier.size(9.dp.scaled()))
            }
            Text(
                title,
                style = AppleTypography.titleLarge.copy(
                    fontSize = 22.sp.scaled(),
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AppleColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (batteryPercent != null) {
            Spacer(Modifier.size(10.dp.scaled()))
            Icon(
                Icons.Filled.BatteryFull,
                contentDescription = stringResource(R.string.side_panel_battery_desc, batteryPercent),
                tint = batteryColor,
                modifier = Modifier.size(17.dp.scaled()),
            )
            Spacer(Modifier.size(4.dp.scaled()))
            Text(
                stringResource(R.string.side_panel_battery_value, batteryPercent),
                style = AppleTypography.bodySmall.copy(
                    fontSize = 13.sp.scaled(),
                    fontWeight = FontWeight.Medium,
                ),
                color = batteryColor,
            )
        }
        if (onClose != null) {
            Spacer(Modifier.size(8.dp.scaled()))
            Box(
                modifier = Modifier
                    .size(48.dp.scaled())
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.side_panel_close_desc),
                    tint = AppleColors.secondary,
                    modifier = Modifier.size(22.dp.scaled()),
                )
            }
        }
    }
}

/** Battery attributes exposed directly by HA integrations for battery-powered devices. */
private fun HaEntity.headerBatteryPercent(): Int? {
    val keys = listOf("battery_level", "battery", "battery_percentage")
    return keys.firstNotNullOfOrNull { key ->
        if (!attributes.has(key)) return@firstNotNullOfOrNull null
        attributes.optDouble(key, Double.NaN)
            .takeUnless(Double::isNaN)
            ?.roundToInt()
            ?.takeIf { it in 0..100 }
    }
}

@Composable
fun PanelModeButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    val color = if (active) AppleColors.active else AppleColors.secondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(AppleShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp.scaled(), vertical = 6.dp.scaled()),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp.scaled()))
        Spacer(Modifier.height(4.dp.scaled()))
        Text(label, style = AppleTypography.bodySmall.copy(fontSize = 12.sp.scaled()), color = color)
    }
}

@Composable
fun PanelDetailRow(detail: PillDetail) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(horizontal = 16.dp.scaled(), vertical = 12.dp.scaled()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val rowStyle = AppleTypography.bodyLarge.copy(fontSize = AppleTypography.bodyLarge.fontSize.scaled())
        Text(
            detail.label,
            style = rowStyle,
            color = AppleColors.secondary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail.value, style = rowStyle, color = AppleColors.primary, maxLines = 1)
    }
}
