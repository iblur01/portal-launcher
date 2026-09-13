package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import androidx.compose.foundation.layout.Arrangement
import com.iblu01.portallauncher.voice.VoicePhase
import com.iblu01.portallauncher.voice.VoicePlan
import com.iblu01.portallauncher.voice.VoiceTask
import com.iblu01.portallauncher.voice.VoiceTaskStatus
import com.iblu01.portallauncher.voice.VoiceUiState

/**
 * Full-screen voice session layer, modelled on smart displays (Echo Show / Nest Hub) rather than
 * a floating widget: a scrim rises from the bottom edge with a live blue aura, and the current
 * exchange is typeset directly on it — no card, no border.
 *
 * The layer owns every touch while a session is live, so the launcher behind it cannot be poked
 * through the overlay; tapping anywhere is the hang-up gesture (plus an explicit close button for
 * accessibility). The animated backdrop is [EdgeGlow], shared with MQTT notifications.
 */
@Composable
fun VoiceAssistantOverlay(
    state: VoiceUiState,
    onStop: () -> Unit,
    onConfirm: () -> Unit = {},
    onCancelConfirm: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state.phase.isOverlayVisible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(150)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Tap-anywhere hangs up, except while a guarded action is waiting: there the
                // whole point is a deliberate press, and a stray touch must not read as either
                // answer.
                .pointerInput(state.pendingConfirmation) {
                    detectTapGestures { if (state.pendingConfirmation == null) onStop() }
                },
        ) {
            val compact = maxWidth < 600.dp
            val glowHeight = if (compact) 160.dp else 220.dp

            val intensity by animateFloatAsState(
                targetValue = glowIntensity(state.phase),
                animationSpec = tween(400),
                label = "voiceGlowIntensity",
            )
            EdgeGlow(
                color = glowColor(state.phase),
                edge = GlowEdge.BOTTOM,
                intensity = intensity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(glowHeight)
                    .align(Alignment.BottomCenter),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 720.dp)
                    .padding(horizontal = if (compact) 24.dp else 48.dp)
                    .padding(bottom = if (compact) 28.dp else 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val userText = state.userText
                val botText = state.error ?: state.botText

                // The plan sits above the exchange: while the assistant works through it there
                // is no speech to read, and the checklist is the only thing telling someone
                // standing in front of the panel that anything is happening.
                if (!state.plan.isEmpty) {
                    TaskPlan(state.plan, compact)
                    Spacer(Modifier.height(if (compact) 14.dp else 20.dp))
                }
                if (userText.isNotBlank()) {
                    Text(
                        text = "« $userText »",
                        style = AppleTypography.titleMedium,
                        color = AppleColors.secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
                }
                if (botText.isNotBlank()) {
                    Text(
                        text = botText,
                        style = AppleTypography.titleLarge.copy(
                            fontSize = if (compact) 22.sp else 30.sp,
                            lineHeight = if (compact) 30.sp else 40.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = if (state.error != null) AppleColors.warning else AppleColors.primary,
                        textAlign = TextAlign.Center,
                        maxLines = if (compact) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                }
                state.pendingConfirmation?.let { label ->
                    ConfirmationRow(label, compact, onConfirm, onCancelConfirm)
                    Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
                }
                PhaseBadge(state.phase)
            }

            // Redundant with tap-anywhere, but TalkBack cannot reach a bare tap gesture.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .appleClickable(onStop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.voice_stop_desc),
                    tint = AppleColors.secondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The physical half of [com.iblu01.portallauncher.voice.VoiceGuard]: a lock, a garage or an alarm
 * is never opened on a voice alone, so the assistant's request lands here and waits for a press
 * from someone who is actually in front of the panel.
 */
@Composable
private fun ConfirmationRow(
    label: String,
    compact: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.voice_confirm_title),
            style = AppleTypography.labelSmall,
            color = AppleColors.tertiary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = if (compact) AppleTypography.titleMedium else AppleTypography.titleLarge,
            color = AppleColors.warning,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ConfirmationButton(
                text = stringResource(R.string.voice_confirm_cancel),
                color = AppleColors.secondary,
                onClick = onCancel,
            )
            ConfirmationButton(
                text = stringResource(R.string.voice_confirm_accept),
                color = AppleColors.warning,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun ConfirmationButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .appleClickable(onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = AppleTypography.bodyLarge, color = color)
    }
}

/**
 * The announced plan as a checklist. Deliberately plain: a done step goes grey with a tick, the
 * running one stays bright, a failed one is marked — nothing animates, because it is read at a
 * glance from across a room, not watched.
 */
@Composable
private fun TaskPlan(plan: VoicePlan, compact: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
    ) {
        plan.tasks.forEach { task -> TaskRow(task, compact) }
    }
}

@Composable
private fun TaskRow(task: VoiceTask, compact: Boolean) {
    val color = when (task.status) {
        VoiceTaskStatus.RUNNING -> AppleColors.primary
        VoiceTaskStatus.FAILED -> AppleColors.warning
        VoiceTaskStatus.DONE -> AppleColors.tertiary
        VoiceTaskStatus.PENDING -> AppleColors.secondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (task.status) {
                VoiceTaskStatus.DONE -> Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
                VoiceTaskStatus.FAILED -> Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp),
                )
                // The running step is the pulsing dot the phase badge uses, so the two read as
                // one status channel rather than two.
                VoiceTaskStatus.RUNNING -> Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                VoiceTaskStatus.PENDING -> Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.4f)),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.title,
            style = if (compact) AppleTypography.bodyLarge else AppleTypography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Glow colour per phase; the aura is the primary status channel, text merely confirms it. */
private fun glowColor(phase: VoicePhase): Color = when (phase) {
    VoicePhase.ERROR -> AppleColors.warning
    else -> AppleColors.accent
}

/** Intensity follows the session phase, so listening reads brighter than thinking. */
private fun glowIntensity(phase: VoicePhase): Float = when (phase) {
    VoicePhase.SPEAKING -> 1f
    VoicePhase.LISTENING -> 0.85f
    VoicePhase.ERROR -> 0.7f
    VoicePhase.THINKING -> 0.5f
    else -> 0.4f
}

/** Tiny pulsing dot + phase label, the only chrome besides the close button. */
@Composable
private fun PhaseBadge(phase: VoicePhase) {
    val pulsing = phase == VoicePhase.LISTENING || phase == VoicePhase.SPEAKING
    val alpha by rememberInfiniteTransition(label = "voiceBadge").animateFloat(
        initialValue = if (pulsing) 0.35f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
        label = "voiceBadgeAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(glowColor(phase).copy(alpha = if (pulsing) alpha else 1f)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = phaseLabel(phase),
            style = AppleTypography.labelSmall,
            color = AppleColors.tertiary,
        )
        Spacer(Modifier.width(10.dp))
        UtilityBadge(stringResource(R.string.voice_beta_badge))
    }
}

@Composable
private fun phaseLabel(phase: VoicePhase): String = when (phase) {
    VoicePhase.CONNECTING -> stringResource(R.string.voice_phase_connecting)
    VoicePhase.LISTENING -> stringResource(R.string.voice_phase_listening)
    VoicePhase.THINKING -> stringResource(R.string.voice_phase_thinking)
    VoicePhase.SPEAKING -> stringResource(R.string.voice_phase_speaking)
    VoicePhase.ERROR -> stringResource(R.string.voice_phase_error)
    else -> stringResource(R.string.voice_phase_listening)
}
