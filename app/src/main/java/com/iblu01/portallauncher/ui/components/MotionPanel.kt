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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsRun
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
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Read-only panel for a motion/occupancy binary sensor. Shows the live detected/clear state and
 * the date of the last change, in both the horizontal and vertical compositions the other control
 * panels share.
 */
@Composable
fun MotionControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val detected = entity.state.equals("on", ignoreCase = true)
    val accent = if (detected) AppleColors.active else AppleColors.inactive
    val stateLabel = stringResource(if (detected) R.string.motion_state_detected else R.string.motion_state_clear)
    val lastActivity = remember(entity.lastChanged) { formatLastChanged(entity.lastChanged) }
    val sinceLabel = lastActivity?.let {
        stringResource(if (detected) R.string.motion_detected_since else R.string.motion_clear_since, it)
    }

    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MotionStatus(stateLabel, accent, chip.entityId, Modifier.weight(0.52f).fillMaxSize())
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
        MotionStatus(stateLabel, accent, chip.entityId, Modifier.fillMaxWidth(0.54f).weight(1f))
        Spacer(Modifier.height(16.dp))
        if (sinceLabel != null) ActivityCard(sinceLabel)
        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}

/** Large state visual: the motion glyph in an accent ring, with the detected/clear caption. */
@Composable
private fun MotionStatus(
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
                HaEntityIcon(entityId, null, accent, 56.dp, Icons.Outlined.DirectionsRun)
            }
            Spacer(Modifier.height(18.dp))
            Text(stateLabel, style = AppleTypography.headlineLarge, color = AppleColors.primary)
        }
    }
}
