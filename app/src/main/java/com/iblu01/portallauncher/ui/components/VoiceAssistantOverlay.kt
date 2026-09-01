package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import com.iblu01.portallauncher.voice.VoicePhase
import com.iblu01.portallauncher.voice.VoiceUiState

/**
 * Full-screen voice session layer, modelled on smart displays (Echo Show / Nest Hub) rather than
 * a floating widget: a scrim rises from the bottom edge with a live blue aura, and the current
 * exchange is typeset directly on it — no card, no border.
 *
 * The layer owns every touch while a session is live, so the launcher behind it cannot be poked
 * through the overlay; tapping anywhere is the hang-up gesture (plus an explicit close button for
 * accessibility). Everything animated lives in the draw phase ([drawBehind] reading animated
 * state), so the glow never triggers recomposition — the panel runs Android 9 with no live blur,
 * and this is the cheapest path to 60 fps there.
 */
@Composable
fun VoiceAssistantOverlay(
    state: VoiceUiState,
    onStop: () -> Unit,
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
                .pointerInput(Unit) { detectTapGestures { onStop() } },
        ) {
            val compact = maxWidth < 600.dp
            val glowHeight = if (compact) 160.dp else 220.dp

            VoiceGlow(
                phase = state.phase,
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

/** Glow colour per phase; the aura is the primary status channel, text merely confirms it. */
private fun glowColor(phase: VoicePhase): Color = when (phase) {
    VoicePhase.ERROR -> AppleColors.warning
    else -> AppleColors.accent
}

/**
 * Bottom scrim + animated aura. All time-varying values are read inside [drawBehind], so the
 * infinite transitions invalidate the draw pass only, never composition or layout.
 */
@Composable
private fun VoiceGlow(phase: VoicePhase, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "voiceGlow")
    // A slow travelling highlight, wrapped around the bottom edge like a light bar.
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "voiceGlowShimmer",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "voiceGlowBreathe",
    )
    // Intensity follows the session phase so listening reads brighter than thinking.
    val intensity by animateFloatAsState(
        targetValue = when (phase) {
            VoicePhase.SPEAKING -> 1f
            VoicePhase.LISTENING -> 0.85f
            VoicePhase.ERROR -> 0.7f
            VoicePhase.THINKING -> 0.5f
            else -> 0.4f
        },
        animationSpec = tween(400),
        label = "voiceGlowIntensity",
    )
    val color = glowColor(phase)

    Box(
        modifier.drawBehind {
            // Legibility scrim: any wallpaper fades to near-black under the text.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Black.copy(alpha = 0.55f),
                    1f to Color.Black.copy(alpha = 0.92f),
                ),
            )

            val w = size.width
            val h = size.height
            val pulse = 0.75f + 0.25f * breathe

            // Soft aura pool sliding along the bottom edge (two pools so the wrap is seamless).
            val auraRadius = w * 0.45f
            val auraAlpha = 0.20f * intensity * pulse
            for (offset in floatArrayOf(shimmer, shimmer - 1f)) {
                val cx = w * offset
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = auraAlpha),
                        1f to Color.Transparent,
                        center = Offset(cx, h),
                        radius = auraRadius,
                    ),
                    radius = auraRadius,
                    center = Offset(cx, h),
                )
            }

            // Ambient wash so the edge glows even between the travelling pools.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to color.copy(alpha = 0.16f * intensity * pulse),
                ),
            )

            // The light bar itself: a thin breathing line hugging the bottom edge.
            val barHeight = (2.5f + 2.5f * breathe).dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to color.copy(alpha = 0.0f),
                    0.2f to color.copy(alpha = 0.9f * intensity),
                    0.8f to color.copy(alpha = 0.9f * intensity),
                    1f to color.copy(alpha = 0.0f),
                ),
                topLeft = Offset(0f, h - barHeight),
                size = androidx.compose.ui.geometry.Size(w, barHeight),
            )
        },
    )
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
