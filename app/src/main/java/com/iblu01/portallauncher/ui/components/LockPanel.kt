package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
 * HomeKit-style lock as a 2-position toggle: up/green is locked, down/amber is unlocked.
 * Tap or drag flips it; a jammed lock goes red and retries a lock on the next flip.
 */
@Composable
fun LockControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val state = entity.state.lowercase()
    val locked = state == "locked"
    val jammed = state == "jammed"

    val accent = if (jammed) AppleColors.error else AppleColors.active
    val caption = when { jammed -> stringResource(R.string.lock_state_jammed); locked -> stringResource(R.string.lock_state_locked); else -> stringResource(R.string.lock_state_unlocked) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        Text(caption, style = AppleTypography.headlineLarge, color = AppleColors.primary)
        Spacer(Modifier.height(16.dp))
        VerticalSwitch(
            checked = locked,
            onCheckedChange = { wantLocked ->
                callService("lock", if (wantLocked) "lock" else "unlock", chip.entityId)
            },
            accent = accent,
            icon = { on -> if (jammed) Icons.Outlined.ErrorOutline else if (on) Icons.Outlined.Lock else Icons.Outlined.LockOpen },
            modifier = Modifier.height(220.dp).width(96.dp),
        )
        Spacer(Modifier.height(20.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
