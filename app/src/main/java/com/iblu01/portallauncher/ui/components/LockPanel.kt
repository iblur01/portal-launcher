package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/**
 * HomeKit-style lock as a 2-position toggle using the lock's dedicated turquoise accent.
 * Tap or drag flips it; a jammed lock goes red and retries a lock on the next flip.
 */
@Composable
fun LockControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val state = entity.state.lowercase()
    val locked = state == "locked"
    val jammed = state == "jammed"

    val accent = if (jammed) AppleColors.error else AppleColors.lockAccent
    val caption = when { jammed -> stringResource(R.string.lock_state_jammed); locked -> stringResource(R.string.lock_state_locked); else -> stringResource(R.string.lock_state_unlocked) }

    val control: @Composable (Modifier) -> Unit = { controlModifier ->
        BoxWithConstraints(controlModifier, contentAlignment = Alignment.Center) {
            val ratio = 96f / 240f
            val controlHeight = minOf(maxHeight, maxWidth / ratio, 300.dp)
            VerticalSwitch(
                checked = locked,
                onCheckedChange = { wantLocked -> callService("lock", if (wantLocked) "lock" else "unlock", chip.entityId) },
                accent = accent,
                icon = { on -> if (jammed) Icons.Outlined.ErrorOutline else if (on) Icons.Outlined.Lock else Icons.Outlined.LockOpen },
                modifier = Modifier.size(controlHeight * ratio, controlHeight),
            )
        }
    }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
            control(Modifier.weight(0.52f).fillMaxSize())
            Column(Modifier.weight(0.48f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(caption, style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Spacer(Modifier.height(18.dp))
                chip.details.take(4).forEach { PanelDetailRow(it) }
            }
        }
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Text(caption, style = AppleTypography.headlineLarge, color = AppleColors.primary)
        Spacer(Modifier.height(16.dp))
        control(Modifier.fillMaxWidth(0.54f).weight(1f))
        Spacer(Modifier.height(20.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
