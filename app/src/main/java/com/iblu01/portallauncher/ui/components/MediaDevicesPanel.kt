package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.ui.theme.*

/** First level of media navigation: every HA player, including idle and off devices. */
@Composable
fun MediaDevicesPanel(devices: List<PlayingMedia>, onSelect: (PlayingMedia) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = 14.dp.scaled(), vertical = 16.dp.scaled())) {
        Box(Modifier.fillMaxSize().clip(AppleShapes.panel).background(Color.Black.copy(alpha = 0.72f)).border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.94f)))))
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp.scaled(), vertical = 20.dp.scaled())) {
                PanelHeader("Lecteurs média", onDismiss, Icons.Filled.Close, "Fermer", titleIcon = Icons.Outlined.Speaker, accent = AppleColors.accent)
                Spacer(Modifier.height(14.dp))
                Text("Choisis un lecteur", style = AppleTypography.bodySmall, color = AppleColors.secondary)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    devices.forEach { device -> MediaDeviceRow(device, onSelect) }
                    if (devices.isEmpty()) Text("Aucun lecteur disponible", style = AppleTypography.bodyLarge, color = AppleColors.secondary)
                }
            }
        }
    }
}

@Composable
private fun MediaDeviceRow(device: PlayingMedia, onSelect: (PlayingMedia) -> Unit) {
    val active = device.state.lowercase() in setOf("playing", "buffering")
    val accent = if (active) AppleColors.accent else AppleColors.inactive
    Row(
        Modifier.fillMaxWidth().clip(AppleShapes.section).background(AppleColors.frostedFill, AppleShapes.section)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section).appleClickable { onSelect(device) }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).background(accent.copy(alpha = 0.18f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
            // A Sonos stays a Sonos: whatever icon the user picked in HA (`mdi:sonos`, `phu:sonos-arc`)
            // is what the dashboard shows, so it is what this row shows.
            HaEntityIcon(device.entityId, null, accent, 22.dp, Icons.Outlined.Speaker)
        }
        Column(Modifier.weight(1f)) {
            Text(device.playerNames.firstOrNull() ?: device.artist, style = AppleTypography.bodyLarge, color = AppleColors.primary, maxLines = 1)
            Text(if (active) device.title else when (device.state.lowercase()) { "paused" -> "En pause"; "off" -> "Éteint"; else -> "À l’arrêt" }, style = AppleTypography.bodySmall, color = if (active) AppleColors.secondary else AppleColors.tertiary, maxLines = 1)
        }
        Text("${device.volumePercent} %", style = AppleTypography.bodySmall, color = AppleColors.secondary)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = AppleColors.tertiary, modifier = Modifier.size(18.dp))
    }
}
