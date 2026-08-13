package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SensorDoor
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Read-only panel for a door/window opening sensor. Shows the live open/closed state and the
 * date of the last change (opening or closing, derived from the entity's `last_changed`), in both
 * the horizontal and vertical compositions the other control panels share.
 */
@Composable
fun DoorControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val open = entity.state.lowercase() in setOf("on", "open", "opening")
    val accent = if (open) AppleColors.warning else AppleColors.active
    val stateLabel = stringResource(if (open) R.string.opening_state_open else R.string.opening_state_closed)
    val lastActivity = remember(entity.lastChanged) { formatLastChanged(entity.lastChanged) }
    val sinceLabel = lastActivity?.let {
        stringResource(if (open) R.string.door_opened_since else R.string.door_closed_since, it)
    }

    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DoorStatus(stateLabel, accent, chip.entityId, Modifier.weight(0.52f).fillMaxSize())
            Column(
                Modifier.weight(0.48f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (sinceLabel != null) ActivityCard(sinceLabel)
                Spacer(Modifier.height(16.dp))
                chip.details.take(3).forEach { PanelDetailRow(it) }
            }
        }
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DoorStatus(stateLabel, accent, chip.entityId, Modifier.fillMaxWidth(0.54f).weight(1f))
        Spacer(Modifier.height(16.dp))
        if (sinceLabel != null) ActivityCard(sinceLabel)
        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}

/** Large state visual: the door glyph in an accent ring, with the open/closed caption beneath. */
@Composable
private fun DoorStatus(
    stateLabel: String,
    accent: Color,
    entityId: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                HaEntityIcon(entityId, null, accent, 56.dp, Icons.Outlined.SensorDoor)
            }
            Spacer(Modifier.height(18.dp))
            Text(stateLabel, style = AppleTypography.headlineLarge, color = AppleColors.primary)
        }
    }
}

/** Frosted row carrying the last activity date. */
@Composable
internal fun ActivityCard(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Schedule, null, tint = AppleColors.secondary, modifier = Modifier.size(20.dp))
        Text(text, style = AppleTypography.bodyLarge, color = AppleColors.primary)
    }
}

/** Parses an HA ISO-8601 timestamp into a short localized date+time, or null when unparseable. */
internal fun formatLastChanged(raw: String): String? {
    if (raw.isBlank()) return null
    val instant = runCatching { java.time.Instant.parse(raw) }.getOrElse {
        runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    } ?: return null
    return runCatching {
        java.time.format.DateTimeFormatter
            .ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
            .withLocale(java.util.Locale.getDefault())
            .format(instant.atZone(java.time.ZoneId.systemDefault()))
    }.getOrNull()
}
