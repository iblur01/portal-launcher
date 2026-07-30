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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.scaled
import com.iblu01.portallauncher.ui.theme.stateColor
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/**
 * What the side panel is currently showing. Media auto-opens when playback starts
 * (dismissable), while chip actions are user-requested and take precedence.
 */
sealed interface PanelContent {
    data class Media(val session: PlayingMedia) : PanelContent
    data class ChipActions(val chip: LauncherChip) : PanelContent
    data class Weather(val weather: WeatherUi) : PanelContent
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
) {
    val accent = stateColor(chip.state)
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
            var lightDetail by remember(chip.id) { mutableStateOf<PillDetail?>(null) }
            val detail = lightDetail
            if (chip.toPanelKind() == PanelKind.AIR_QUALITY) {
                AirQualityContent(chip = chip, onBack = onDismiss)
            } else if (detail != null) {
                LightDetailContent(detail = detail, onBack = { lightDetail = null })
            } else {
                ChipActionsContent(chip, accent, onDismiss, onOpenLight = { lightDetail = it })
            }
        }
    }
}

@Composable
private fun ChipActionsContent(
    chip: LauncherChip,
    accent: Color,
    onDismiss: () -> Unit,
    onOpenLight: (PillDetail) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp.scaled(), vertical = 20.dp.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(36.dp.scaled())) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp.scaled())
                    .clip(CircleShape)
                    .background(AppleColors.frostedFill)
                    .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.side_panel_close_desc),
                    tint = AppleColors.secondary,
                    modifier = Modifier.size(18.dp.scaled()),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(AppleShapes.pill)
                    .background(AppleColors.frostedFill, AppleShapes.pill)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                    .padding(horizontal = 14.dp.scaled(), vertical = 7.dp.scaled()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp.scaled()),
            ) {
                Icon(launcherIcon(chip.icon), null, tint = accent, modifier = Modifier.size(16.dp.scaled()))
                Text(chip.label, style = AppleTypography.bodySmall.copy(fontSize = 13.sp.scaled()), color = AppleColors.secondary)
            }
        }

        Spacer(Modifier.height(14.dp.scaled()))
        Text(
            chip.value,
            style = AppleTypography.titleLarge.copy(fontSize = AppleTypography.titleLarge.fontSize.scaled()),
            color = AppleColors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp.scaled()))

        // Center the control block vertically in the remaining space; it still scrolls if a
        // panel's content is taller than the panel (keypads, long detail lists).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            // Exhaustive typed router (design §4/Finding 8): no string-id branching, no catch-all
            // `else` — every PanelKind is handled, GENERIC_DETAILS is the explicit detail-rows case.
            when (chip.toPanelKind()) {
                PanelKind.LIGHTS -> LightsActions(chip, onOpenLight)
                PanelKind.PURIFIER -> PurifierActions(chip)
                PanelKind.SCENES -> ScenesActions(chip)
                PanelKind.PRESENCE -> PresenceActions(chip)
                PanelKind.ENERGY -> EnergyActions(chip)
                PanelKind.LOCK -> LockControl(chip)
                PanelKind.COVER -> CoverControl(chip)
                PanelKind.THERMOSTAT -> ThermostatControl(chip)
                PanelKind.VACUUM -> VacuumControl(chip)
                PanelKind.FAN -> FanControl(chip)
                PanelKind.SWITCH -> SwitchControl(chip)
                PanelKind.ALARM -> AlarmControl(chip)
                // AIR_QUALITY is rendered full-screen upstream in ChipActionsPanel; MEDIA/WEATHER
                // are separate PanelContent types and never reach the chip-actions router.
                PanelKind.AIR_QUALITY, PanelKind.MEDIA, PanelKind.WEATHER,
                PanelKind.GENERIC_DETAILS -> chip.details.forEach { PanelDetailRow(it) }
            }
            }
        }
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

