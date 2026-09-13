package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Which screen edge an [EdgeGlow] hugs. Also decides which way its scrim fades. */
enum class GlowEdge { TOP, BOTTOM }

/**
 * The launcher's "something is happening" surface: a scrim that fades into one screen edge, a
 * travelling aura, and a breathing light bar along the edge itself.
 *
 * Shared by the voice assistant (bottom, blue) and MQTT notifications (top, amber) so the two read
 * as one family while staying instantly distinguishable — the edge and the hue say which is which
 * before a word is read.
 *
 * Every time-varying value is read inside [drawBehind], so the infinite transitions invalidate the
 * draw pass only, never composition or layout. That matters: these panels run Android 9 with no
 * live blur and, often, a single CPU core online.
 */
@Composable
fun EdgeGlow(
    color: Color,
    edge: GlowEdge,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    shimmerMillis: Int = 2800,
    breatheMillis: Int = 1500,
) {
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(shimmerMillis, easing = LinearEasing)),
        label = "edgeGlowShimmer",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(breatheMillis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "edgeGlowBreathe",
    )

    Box(
        modifier.drawBehind {
            val w = size.width
            val h = size.height
            val bottom = edge == GlowEdge.BOTTOM
            // Gradient stops run top-to-bottom, so a top-edge glow reads them in reverse.
            fun stops(vararg fromEdge: Pair<Float, Color>): Array<Pair<Float, Color>> =
                if (bottom) arrayOf(*fromEdge) else Array(fromEdge.size) { i ->
                    val (d, c) = fromEdge[fromEdge.size - 1 - i]
                    (1f - d) to c
                }

            // Legibility scrim: whatever is behind fades to near-black under the text.
            drawRect(
                Brush.verticalGradient(
                    *stops(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
            )

            val edgeY = if (bottom) h else 0f
            val pulse = 0.75f + 0.25f * breathe

            // Soft aura pool sliding along the edge; two pools so the wrap is seamless.
            val auraRadius = w * 0.45f
            val auraAlpha = 0.20f * intensity * pulse
            for (offset in floatArrayOf(shimmer, shimmer - 1f)) {
                val cx = w * offset
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = auraAlpha),
                        1f to Color.Transparent,
                        center = Offset(cx, edgeY),
                        radius = auraRadius,
                    ),
                    radius = auraRadius,
                    center = Offset(cx, edgeY),
                )
            }

            // Ambient wash, so the edge still glows between the travelling pools.
            drawRect(
                Brush.verticalGradient(
                    *stops(
                        0f to Color.Transparent,
                        1f to color.copy(alpha = 0.16f * intensity * pulse),
                    ),
                ),
            )

            // The light bar itself, breathing along the edge.
            val barHeight = (2.5f + 2.5f * breathe).dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to color.copy(alpha = 0f),
                    0.2f to color.copy(alpha = 0.9f * intensity),
                    0.8f to color.copy(alpha = 0.9f * intensity),
                    1f to color.copy(alpha = 0f),
                ),
                topLeft = Offset(0f, if (bottom) h - barHeight else 0f),
                size = Size(w, barHeight),
            )
        },
    )
}
