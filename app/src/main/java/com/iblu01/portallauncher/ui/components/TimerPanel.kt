package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.formatTimerDuration
import com.iblu01.portallauncher.timerStateOf
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

@Composable
fun TimerControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }

    var nowMs by remember(entity) { mutableLongStateOf(System.currentTimeMillis()) }
    val timer = timerStateOf(entity, nowMs)
    LaunchedEffect(entity, timer.running) {
        if (timer.running) while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    val status = when {
        timer.running -> stringResource(R.string.timer_state_running)
        timer.paused -> stringResource(R.string.timer_state_paused)
        else -> stringResource(R.string.timer_state_idle)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(224.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2f
                drawArc(
                    AppleColors.quaternary, -90f, 360f, false,
                    Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    AppleColors.active, -90f, timer.progress * 360f, false,
                    Offset(inset, inset), Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatTimerDuration(timer.remainingSeconds),
                    style = AppleTypography.displayLarge.copy(
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Light,
                        fontFeatureSettings = "tnum",
                    ),
                    color = AppleColors.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Text(status, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        ) {
            TimerActionButton(
                label = if (timer.running) stringResource(R.string.action_pause) else stringResource(R.string.action_start),
                active = true,
                icon = if (timer.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onClick = {
                    callService("timer", if (timer.running) "pause" else "start", chip.entityId)
                },
            )
            TimerActionButton(
                label = stringResource(R.string.timer_action_cancel),
                active = false,
                icon = Icons.Filled.Close,
                onClick = { callService("timer", "cancel", chip.entityId) },
            )
        }
    }
}

@Composable
private fun TimerActionButton(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val fill = if (active) AppleColors.active else AppleColors.frostedFill
    val tint = if (active) androidx.compose.ui.graphics.Color.Black else AppleColors.primary
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(fill)
            .appleClickable(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
    }
}
