package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

/**
 * Reusable, theme-agnostic controls in the Apple Home style. Every control is driven by a
 * single [accent] colour and derives every other tone from it (fill, thumb, text contrast),
 * so dropping in a new colour is enough to re-skin it — no per-call-site styling needed.
 *
 * Nothing here reaches into Home Assistant or app state: these are pure, controlled widgets
 * ([value] in, [onValueChange] out) meant to replace the hand-rolled sliders scattered across
 * the panels. See `PlaygroundScreen` for a live gallery.
 */

// ---------------------------------------------------------------------------------------------
// Shared colour maths
// ---------------------------------------------------------------------------------------------

/** Black or white — whichever stays legible on top of [surface]. */
internal fun contentColorOn(surface: Color): Color =
    if (surface.luminance() > 0.55f) Color.Black.copy(alpha = 0.82f) else Color.White

private fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
}

private fun valueOf(fraction: Float, range: ClosedFloatingPointRange<Float>): Float =
    range.start + (range.endInclusive - range.start) * fraction.coerceIn(0f, 1f)

/** Default "45 %"-style label. */
fun percentLabel(value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f): String =
    "${(fractionOf(value, range) * 100).roundToInt()} %"

/** Control corners scale with the shortest axis instead of relying on a frozen dp radius. */
private val VerticalSliderSquircle: Shape = RoundedCornerShape(percent = 30)

/** Neutral tint for an "off"/disabled selection — used in place of the accent (iOS systemGray4). */
val ControlNeutral: Color = Color(0xFFC7C7CC)

/** How an icon and its text stack inside a control: icon above the text, or beside it. */
enum class ControlContentLayout { Vertical, Horizontal }

/**
 * The shared icon-and-text block every control uses. Renders whichever of [icon] / [text] is
 * present, centred, stacked per [layout]. Both null → nothing. Keeps positioning identical
 * across the slider, selector and switch so callers only pick content and orientation.
 */
@Composable
private fun ControlLabel(
    icon: ImageVector?,
    iconContent: (@Composable (Color, Dp) -> Unit)? = null,
    text: String?,
    color: Color,
    textStyle: TextStyle,
    layout: ControlContentLayout,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    gap: Dp = 5.dp,
    textModifier: Modifier = Modifier,
) {
    if (icon == null && iconContent == null && text == null) return
    val iconSlot: @Composable () -> Unit = {
        when {
            iconContent != null -> iconContent(color, iconSize)
            icon != null -> Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
        }
    }
    val textSlot: @Composable () -> Unit = {
        if (text != null) {
            Text(text, style = textStyle, color = color, maxLines = 1, softWrap = false, modifier = textModifier)
        }
    }
    when (layout) {
        ControlContentLayout.Vertical -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) { iconSlot(); textSlot() }
        ControlContentLayout.Horizontal -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) { iconSlot(); textSlot() }
    }
}

/**
 * The one footprint every control shares (the vertical slider's): width ÷ height ≈ 0.4.
 * Size any control with [controlSize] so a wall of them stays visually uniform regardless
 * of type — slider, selector or switch.
 */
const val ControlAspectRatio: Float = 88f / 220f

/** Give a control the canonical [ControlAspectRatio]; only [width] varies. */
fun Modifier.controlSize(width: Dp = 88.dp): Modifier = this.width(width).aspectRatio(ControlAspectRatio)

/** Most options a [VerticalSegmentedSelector] holds before it would grow uncomfortably tall. */
const val MaxSegments: Int = 8

/**
 * Height of one selector segment. Chosen so a 4-option selector matches the canonical control
 * footprint; fewer options make a shorter control, more make a taller one.
 */
val SegmentHeight: Dp = 55.dp

// ---------------------------------------------------------------------------------------------
// 1 · Vertical fill slider (brightness / volume / position)
// ---------------------------------------------------------------------------------------------

/** Which edge the fill grows from. [BOTTOM] = classic brightness, [TOP] = drains downward. */
enum class FillOrigin { BOTTOM, TOP }

/**
 * A tall pill that fills with [accent] from [origin] up to [value]. Drag or tap anywhere to set.
 * A grip line rides the fill edge and a label sits at the base.
 *
 * @param value current value, inside [valueRange].
 * @param onValueChange fired continuously while dragging.
 * @param onValueChangeFinished fired once on release / tap, for committing to a backend.
 * @param accent the fill colour — drives grip and label contrast automatically.
 * @param trackColor the empty portion behind the fill.
 * @param label formats the base caption; pass `null` to hide it.
 * @param hapticSteps number of detents that tick a haptic while dragging (0 disables).
 */
@Composable
fun VerticalFillSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    origin: FillOrigin = FillOrigin.BOTTOM,
    accent: Color = AppleColors.accent,
    trackColor: Color = AppleColors.frostedFill,
    enabled: Boolean = true,
    hapticSteps: Int = 20,
    icon: ImageVector? = null,
    /** Optional entity-aware icon renderer; takes precedence over [icon] when supplied. */
    iconContent: (@Composable (Color, Dp) -> Unit)? = null,
    label: ((Float) -> String)? = { percentLabel(it, valueRange) },
    contentLayout: ControlContentLayout = ControlContentLayout.Vertical,
    shape: Shape = VerticalSliderSquircle,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val fraction = fractionOf(value, valueRange)
    var dragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableIntStateOf((fraction * hapticSteps).roundToInt()) }

    // The finger owns the fill while dragging; the incoming value only takes over once the
    // gesture is done, so a slow round-trip to the backend can never drag the thumb backwards.
    var targetFraction by remember { mutableFloatStateOf(fraction) }
    LaunchedEffect(fraction) {
        if (!dragging) targetFraction = fraction
    }

    val animatedFraction by animateFloatAsState(
        targetFraction,
        // No easing under the finger: 1:1 tracking. Animate only for external changes.
        if (dragging) snap() else tween(180, easing = FastOutSlowInEasing),
        label = "fillFraction",
    )
    val fillColor by animateColorAsState(
        if (!enabled) AppleColors.inactive else if (targetFraction > 0.001f) accent else AppleColors.inactive,
        tween(250), label = "fillColor",
    )
    val gripScale by animateFloatAsState(if (dragging) 1.18f else 1f, AppleMotion.spring(), label = "gripScale")

    fun commit(rawFraction: Float, finished: Boolean) {
        val safe = rawFraction.coerceIn(0f, 1f)
        targetFraction = safe
        if (hapticSteps > 0) {
            val step = (safe * hapticSteps).roundToInt()
            if (step != lastStep) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                lastStep = step
            }
        }
        val mapped = valueOf(safe, valueRange)
        onValueChange(mapped)
        if (finished) onValueChangeFinished?.invoke(mapped)
    }

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(trackColor)
            .border(0.5.dp, AppleColors.frostedBorder, shape)
            .semantics { contentDescription = "Curseur ${(fraction * 100).roundToInt()} pour cent" }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(origin, valueRange) {
                    fun fracAt(y: Float): Float {
                        val t = (y / size.height).coerceIn(0f, 1f)
                        return if (origin == FillOrigin.BOTTOM) 1f - t else t
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var current = fracAt(down.position.y)
                        dragging = true
                        commit(current, finished = false)
                        down.consume()

                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                            } else {
                                current = fracAt(change.position.y)
                                commit(current, finished = false)
                                change.consume()
                            }
                        }
                        dragging = false
                        commit(current, finished = true)
                    }
                },
            ),
    ) {
        val trackHeight = maxHeight
        val fillAlignment = if (origin == FillOrigin.BOTTOM) Alignment.BottomCenter else Alignment.TopCenter

        // The fill.
        Box(
            Modifier
                .align(fillAlignment)
                .fillMaxWidth()
                .fillMaxHeight(animatedFraction.coerceIn(0f, 1f))
                .background(fillColor.copy(alpha = if (enabled) 0.95f else 0.5f)),
        )

        // Keep the grip slightly inside the coloured fill, like Apple's vertical controls,
        // instead of letting it straddle the hard boundary between fill and track.
        val edgeFromTop = when (origin) {
            FillOrigin.BOTTOM -> trackHeight * (1f - animatedFraction)
            FillOrigin.TOP -> trackHeight * animatedFraction
        }
        // All grip geometry follows the slider width, preserving the reference 88 dp control's
        // proportions when the cover panel scales it up or down.
        val gripInset = maxWidth * (8f / 88f)
        val gripWidth = maxWidth * (30f / 88f)
        val gripHeight = maxWidth * (4f / 88f)
        val gripTopLimit = maxWidth * (8f / 88f)
        val gripBottomClearance = maxWidth * (12f / 88f)
        val gripCenterFromTop = when (origin) {
            FillOrigin.BOTTOM -> edgeFromTop + gripInset
            FillOrigin.TOP -> edgeFromTop - gripInset
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(
                    y = (gripCenterFromTop - gripHeight / 2f).coerceIn(
                        gripTopLimit,
                        (trackHeight - gripBottomClearance).coerceAtLeast(gripTopLimit),
                    ),
                )
                .graphicsLayer { scaleX = gripScale }
                .width(gripWidth)
                .height(gripHeight)
                .clip(CircleShape)
                .background(contentColorOn(fillColor).copy(alpha = 0.55f)),
        )

        // Base caption (+ optional icon). Contrast follows whichever tone sits behind the base.
        val baseIsFilled = origin == FillOrigin.BOTTOM && targetFraction > 0.14f
        val captionColor = if (baseIsFilled) contentColorOn(fillColor) else AppleColors.secondary
        val contentScale = (maxWidth / 88.dp).coerceAtLeast(0.75f)
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp * contentScale)) {
            ControlLabel(
                icon = icon,
                iconContent = iconContent,
                text = label?.invoke(valueOf(targetFraction, valueRange)),
                color = captionColor,
                textStyle = AppleTypography.bodySmall.copy(
                    fontSize = AppleTypography.bodySmall.fontSize * contentScale,
                ),
                layout = contentLayout,
                iconSize = 18.dp * contentScale,
                gap = 5.dp * contentScale,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2 · Vertical gradient slider (colour temperature / any continuous colour scale)
// ---------------------------------------------------------------------------------------------

/** Sample a top-to-bottom [colors] ramp at [t] (0 = first colour, 1 = last). */
private fun sampleRamp(colors: List<Color>, t: Float): Color {
    if (colors.isEmpty()) return Color.Transparent
    if (colors.size == 1) return colors.first()
    val pos = t.coerceIn(0f, 1f) * (colors.size - 1)
    val i = pos.toInt().coerceAtMost(colors.size - 2)
    return lerp(colors[i], colors[i + 1], pos - i)
}

/**
 * Approximate sRGB colour of a black body at [kelvin] (Tanner Helland's fit). Used to paint
 * colour-temperature ramps, so a 2700 K reading looks warm and a 6500 K one looks cold.
 */
fun kelvinToColor(kelvin: Int): Color {
    val temperature = kelvin.coerceIn(1000, 40000) / 100.0
    val red = if (temperature <= 66) 255.0 else 329.698727446 * Math.pow(temperature - 60, -0.1332047592)
    val green = if (temperature <= 66) 99.4708025861 * kotlin.math.ln(temperature) - 161.1195681661
    else 288.1221695283 * Math.pow(temperature - 60, -0.0755148492)
    val blue = when {
        temperature >= 66 -> 255.0
        temperature <= 19 -> 0.0
        else -> 138.5177312231 * kotlin.math.ln(temperature - 10) - 305.0447927307
    }
    return Color(
        (red / 255.0).coerceIn(0.0, 1.0).toFloat(),
        (green / 255.0).coerceIn(0.0, 1.0).toFloat(),
        (blue / 255.0).coerceIn(0.0, 1.0).toFloat(),
    )
}

/**
 * Same pill, same gestures and same haptics as [VerticalFillSlider], but the track carries a
 * fixed [colors] ramp instead of a growing fill, and a thumb rides it to mark the value. Use it
 * whenever the *position* itself means a colour — colour temperature, hue, a heat scale.
 *
 * [colors] are laid out top-to-bottom, so the first colour sits at the top of the track and is
 * reached at the top of [valueRange]. The thumb tints itself by sampling the ramp at the current
 * value (override with [thumbColor]) and its icon/label contrast follows automatically.
 *
 * @param value current value, inside [valueRange].
 * @param onValueChange fired continuously while dragging.
 * @param onValueChangeFinished fired once on release / tap, for committing to a backend.
 * @param hapticSteps number of detents that tick a haptic while dragging (0 disables).
 */
@Composable
fun VerticalGradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    hapticSteps: Int = 20,
    icon: ((Float) -> ImageVector?)? = null,
    label: ((Float) -> String)? = { percentLabel(it, valueRange) },
    contentLayout: ControlContentLayout = ControlContentLayout.Vertical,
    shape: Shape = VerticalSliderSquircle,
    thumbColor: ((Float) -> Color)? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    require(colors.isNotEmpty()) { "VerticalGradientSlider needs at least one colour" }
    val haptic = LocalHapticFeedback.current
    val fraction = fractionOf(value, valueRange)
    var dragging by remember { mutableStateOf(false) }
    var lastStep by remember { mutableIntStateOf((fraction * hapticSteps).roundToInt()) }

    // Same ownership rule as the fill slider: the finger wins until the gesture ends.
    var targetFraction by remember { mutableFloatStateOf(fraction) }
    LaunchedEffect(fraction) {
        if (!dragging) targetFraction = fraction
    }
    val animatedFraction by animateFloatAsState(
        targetFraction,
        if (dragging) snap() else tween(180, easing = FastOutSlowInEasing),
        label = "gradientFraction",
    )

    val currentValue = valueOf(targetFraction, valueRange)
    val markerColor by animateColorAsState(
        when {
            !enabled -> AppleColors.inactive
            thumbColor != null -> thumbColor(currentValue)
            // The ramp is top-first, the fraction bottom-first: flip before sampling.
            else -> sampleRamp(colors, 1f - targetFraction)
        },
        tween(200), label = "gradientThumb",
    )

    fun commit(rawFraction: Float, finished: Boolean) {
        val safe = rawFraction.coerceIn(0f, 1f)
        targetFraction = safe
        if (hapticSteps > 0) {
            val step = (safe * hapticSteps).roundToInt()
            if (step != lastStep) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                lastStep = step
            }
        }
        val mapped = valueOf(safe, valueRange)
        onValueChange(mapped)
        if (finished) onValueChangeFinished?.invoke(mapped)
    }

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(
                if (colors.size == 1) SolidColor(colors.first()) else Brush.verticalGradient(colors),
                alpha = if (enabled) 1f else 0.4f,
            )
            .border(0.5.dp, AppleColors.frostedBorder, shape)
            .semantics { contentDescription = "Curseur ${(fraction * 100).roundToInt()} pour cent" }
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(valueRange) {
                    fun fracAt(y: Float): Float = 1f - (y / size.height).coerceIn(0f, 1f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var current = fracAt(down.position.y)
                        dragging = true
                        commit(current, finished = false)
                        down.consume()

                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                pressed = false
                            } else {
                                current = fracAt(change.position.y)
                                commit(current, finished = false)
                                change.consume()
                            }
                        }
                        dragging = false
                        commit(current, finished = true)
                    }
                },
            ),
    ) {
        // Thumb: inset from the track edge, rounded concentrically — same rule as the switch.
        val inset = maxWidth * (6f / 88f)
        val thumbHeight = (maxWidth * (57f / 88f)).coerceAtMost((maxHeight - inset * 2).coerceAtLeast(0.dp))
        val travel = (maxHeight - thumbHeight - inset * 2).coerceAtLeast(0.dp)
        val outerRadius = maxWidth * 0.30f
        val thumbRadius = minOf(
            (outerRadius - inset).coerceAtLeast(0.dp),
            (maxWidth - inset * 2) / 2,
            thumbHeight / 2,
        )
        val thumbShape = RoundedCornerShape(thumbRadius)
        val contentScale = minOf(
            maxWidth / 88.dp,
            thumbHeight / 57.dp,
        ).coerceIn(0.65f, 1.5f)
        val contentColor = contentColorOn(markerColor)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = inset + travel * (1f - animatedFraction.coerceIn(0f, 1f)))
                .width(maxWidth - inset * 2)
                .height(thumbHeight)
                .shadow(4.dp, thumbShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(thumbShape)
                .background(markerColor.copy(alpha = if (enabled) 0.96f else 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            ControlLabel(
                icon = icon?.invoke(currentValue),
                text = label?.invoke(currentValue),
                color = contentColor,
                textStyle = AppleTypography.bodySmall.copy(
                    fontSize = AppleTypography.bodySmall.fontSize * contentScale,
                ),
                layout = contentLayout,
                iconSize = 17.dp * contentScale,
                gap = 3.dp * contentScale,
                textModifier = Modifier.padding(horizontal = 5.dp * contentScale),
            )
        }
    }
}

/**
 * [VerticalGradientSlider] pre-wired for white-balance: the track is a black-body ramp from
 * [maxKelvin] at the top down to [minKelvin], and the thumb wears the matching white and reads
 * the temperature — "2700 K", nothing else.
 */
@Composable
fun VerticalColorTempSlider(
    kelvin: Int,
    onKelvinChange: (Int) -> Unit,
    minKelvin: Int,
    maxKelvin: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = VerticalSliderSquircle,
    onKelvinChangeFinished: ((Int) -> Unit)? = null,
) {
    val range = minKelvin.toFloat()..maxKelvin.toFloat()
    val ramp = remember(minKelvin, maxKelvin) {
        // Enough stops that the ramp reads smooth; the fit is non-linear, 2 stops would band.
        List(7) { i -> kelvinToColor(maxKelvin - (maxKelvin - minKelvin) * i / 6) }
    }
    VerticalGradientSlider(
        value = kelvin.toFloat().coerceIn(range),
        onValueChange = { onKelvinChange(it.roundToInt()) },
        colors = ramp,
        modifier = modifier,
        valueRange = range,
        enabled = enabled,
        label = { v -> "${v.roundToInt()} K" },
        shape = shape,
        thumbColor = { v -> kelvinToColor(v.roundToInt()) },
        onValueChangeFinished = onKelvinChangeFinished?.let { cb -> { v -> cb(v.roundToInt()) } },
    )
}

// ---------------------------------------------------------------------------------------------
// 3 · Vertical segmented selector (2–4 stacked options)
// ---------------------------------------------------------------------------------------------

/**
 * A stack of mutually-exclusive options with a sliding highlight, à la the Home app's mode
 * picker. Hold and drag up/down to sweep the selection, or tap an option.
 *
 * The highlight is [accent] for a normal choice, but switches to [neutralColor] whenever the
 * selected option satisfies [isNeutral] — e.g. an "off"/"disabled" choice that shouldn't wear
 * the accent. Works for any [T]; render each with [label] and an optional per-option [icon]
 * ([contentLayout] stacks it above or beside the text).
 *
 * Takes 1–8 options. Rather than squeezing them into a fixed footprint, the control keeps every
 * segment [segmentHeight] tall and grows downward — so the caller only sets its width; the height
 * follows the option count. Its outer radius follows the live width and the highlight radius is
 * derived from it so both curves remain concentric; [cornerRadius] can override the outer radius.
 */
@Composable
fun <T> VerticalSegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    accent: Color = AppleColors.accent,
    neutralColor: Color = ControlNeutral,
    isNeutral: (T) -> Boolean = { false },
    icon: (T) -> ImageVector? = { null },
    contentLayout: ControlContentLayout = ControlContentLayout.Vertical,
    enabled: Boolean = true,
    cornerRadius: Dp? = null,
    segmentHeight: Dp = SegmentHeight,
    segmentPadding: Dp = 0.dp,
) {
    require(options.size in 1..MaxSegments) {
        "VerticalSegmentedSelector takes 1–$MaxSegments options, got ${options.size}"
    }
    // While a sweep is in progress this holds the finger's index so the highlight + labels
    // track live, but onSelect (the Home Assistant write) only fires once, on release.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = (dragIndex ?: options.indexOf(selected)).coerceAtLeast(0)
    val highlightInset = 5.dp
    val textPadding = 14.dp
    // Extra top/bottom inset the stacked icon+text needs to sit inside the moving highlight,
    // beyond whatever segmentPadding the caller already applies. Never negative, so short
    // segments (small segmentHeight) keep every pixel of vertical room for the content.
    val stackClearance = (highlightInset - segmentPadding).coerceAtLeast(0.dp)
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier.height(segmentHeight * options.size),
    ) {
        // By default the selector's corners scale with its live width. The moving highlight
        // follows the concentric-corner rule: inner radius = outer radius - actual inset.
        // A selected segment can be shorter than the selector is wide (notably with 5–6
        // options). Keep the outer radius within half a segment so Compose never has to clamp
        // the inner curve independently, which would break the parallel inset and appear to
        // pinch against the container edge.
        val radiusFromWidth = cornerRadius ?: maxWidth * 0.30f
        val outerRadius = minOf(radiusFromWidth, segmentHeight / 2)
        val outerShape = RoundedCornerShape(outerRadius)
        val highlightShape = RoundedCornerShape(
            (outerRadius - highlightInset).coerceAtLeast(0.dp),
        )

        BoxWithConstraints(
            Modifier
            .fillMaxSize()
            .clip(outerShape)
            .background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, outerShape)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(options) {
                    fun indexAt(y: Float) =
                        (y / (size.height.toFloat() / options.size)).toInt().coerceIn(0, options.lastIndex)
                    detectTapGestures { onSelect(options[indexAt(it.y)]) }
                },
            )
            .then(
                // Hold and sweep: the option under the finger becomes selected, ticking a detent.
                if (!enabled) Modifier else Modifier.pointerInput(options) {
                    fun indexAt(y: Float) =
                        (y / (size.height.toFloat() / options.size)).toInt().coerceIn(0, options.lastIndex)
                    var emitted = -1
                    detectVerticalDragGestures(
                        onDragStart = {
                            emitted = indexAt(it.y)
                            dragIndex = emitted
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onVerticalDrag = { change, _ ->
                            val i = indexAt(change.position.y)
                            if (i != emitted) {
                                emitted = i
                                dragIndex = i
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        // Commit once, on release — not on every detent crossed.
                        onDragEnd = {
                            if (emitted >= 0) onSelect(options[emitted])
                            dragIndex = null
                        },
                        onDragCancel = { dragIndex = null },
                    )
                },
            ),
        ) {
        val segmentHeight = maxHeight / options.size
        val highlightOffset by animateDpAsState(
            segmentHeight * selectedIndex, AppleMotion.spring(), label = "segmentOffset",
        )
        val highlightColor by animateColorAsState(
            when {
                !enabled -> AppleColors.inactive
                isNeutral(selected) -> neutralColor
                else -> accent
            },
            tween(200), label = "segmentColor",
        )

        val measuredContentScale = minOf(
            maxWidth / 88.dp,
            segmentHeight / 55.dp,
        )
        // Multi-option labels should grow more slowly than the control itself: preserve the
        // spatial hierarchy and leave breathing room around the moving highlight.
        val contentScale = (1f + (measuredContentScale - 1f) * 0.45f).coerceIn(0.8f, 1.2f)
        val iconGap = 5.dp * contentScale
        val iconSize = 18.dp * contentScale
        val scaledTextPadding = textPadding * contentScale
        val labels = options.map(label)
        val anyHorizontalIcon = contentLayout == ControlContentLayout.Horizontal && options.any { icon(it) != null }
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val fittedStyle = remember(labels, maxWidth, anyHorizontalIcon, density, contentScale) {
            // Measure at the selected weight (SemiBold) so the bold row never overflows.
            val base = AppleTypography.bodyLarge.copy(
                fontSize = AppleTypography.bodyLarge.fontSize * contentScale,
                fontWeight = FontWeight.SemiBold,
            )
            val reserved = scaledTextPadding * 2 + if (anyHorizontalIcon) iconSize + iconGap else 0.dp
            val availablePx = with(density) { (maxWidth - reserved).toPx() }
            val widest = labels.maxOfOrNull {
                measurer.measure(it, base, maxLines = 1, softWrap = false).size.width
            } ?: 0
            if (widest <= 0 || widest <= availablePx) base
            else base.copy(fontSize = base.fontSize * (availablePx / widest).coerceIn(0.5f, 1f))
        }

        // Sliding highlight, inset from the container edge (concentric corners).
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = highlightOffset)
                .fillMaxWidth()
                .height(segmentHeight)
                .padding(highlightInset)
                .clip(highlightShape)
                .background(highlightColor.copy(alpha = if (enabled) 1f else 0.4f)),
        )

        Column(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val textColor by animateColorAsState(
                    if (isSelected) contentColorOn(highlightColor) else AppleColors.secondary,
                    tween(200), label = "segmentText",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(segmentHeight)
                        .padding(vertical = segmentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    ControlLabel(
                        icon = icon(option),
                        text = labels[index],
                        color = textColor,
                        textStyle = fittedStyle.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        layout = contentLayout,
                        iconSize = iconSize,
                        gap = iconGap,
                        // Keep the stacked icon+text clear of the moving highlight's top/bottom
                        // edges. Horizontal spacing is handled by textPadding below.
                        modifier = Modifier.padding(vertical = stackClearance),
                        textModifier = Modifier.padding(horizontal = scaledTextPadding),
                    )

                    // Hairline divider below this row, hidden when it touches the highlight.
                    if (index < options.lastIndex) {
                        val dividerVisible = index != selectedIndex && index + 1 != selectedIndex
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(0.5.dp)
                                .background(
                                    AppleColors.quaternary.copy(alpha = if (dividerVisible) 1f else 0f),
                                ),
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Compact Apple Home-style mode picker. Options share one quiet capsule; the selected option is
 * the only raised surface, so the control reads as one choice instead of a row of unrelated
 * buttons. Backend-specific values and labels remain the caller's responsibility.
 */
@Composable
fun <T> HorizontalSegmentedSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: (T) -> ImageVector? = { null },
    accent: Color = AppleColors.accent,
    neutralColor: Color = ControlNeutral,
    isNeutral: (T) -> Boolean = { false },
    contentLayout: ControlContentLayout = ControlContentLayout.Vertical,
    enabled: Boolean = true,
    cornerRadius: Dp? = null,
    controlHeight: Dp = 62.dp,
) {
    require(options.size in 1..MaxSegments) {
        "HorizontalSegmentedSelector takes 1–$MaxSegments options, got ${options.size}"
    }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    val selectedIndex = (dragIndex ?: options.indexOf(selected)).coerceAtLeast(0)
    val highlightInset = 4.dp
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .height(controlHeight),
    ) {
        val segmentWidth = maxWidth / options.size
        val radiusFromHeight = cornerRadius ?: maxHeight * 0.30f
        val outerRadius = minOf(radiusFromHeight, segmentWidth / 2)
        val outerShape = RoundedCornerShape(outerRadius)
        val highlightShape = RoundedCornerShape(
            (outerRadius - highlightInset).coerceAtLeast(0.dp),
        )
        val highlightOffset by animateDpAsState(
            segmentWidth * selectedIndex, AppleMotion.spring(), label = "horizontalSegmentOffset",
        )
        val effectiveSelection = options[selectedIndex]
        val contentScale = minOf(
            maxHeight / 62.dp,
            segmentWidth / 88.dp,
        ).coerceIn(0.65f, 1.5f)
        val highlightColor by animateColorAsState(
            when {
                !enabled -> AppleColors.inactive
                isNeutral(effectiveSelection) -> neutralColor
                else -> accent
            },
            tween(200), label = "horizontalSegmentColor",
        )

        Box(
            Modifier
            .fillMaxSize()
            .clip(outerShape)
            .background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, outerShape)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(options) {
                    fun indexAt(x: Float) =
                        (x / (size.width.toFloat() / options.size)).toInt().coerceIn(0, options.lastIndex)
                    detectTapGestures { onSelect(options[indexAt(it.x)]) }
                },
            )
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(options) {
                    fun indexAt(x: Float) =
                        (x / (size.width.toFloat() / options.size)).toInt().coerceIn(0, options.lastIndex)
                    var emitted = -1
                    detectHorizontalDragGestures(
                        onDragStart = {
                            emitted = indexAt(it.x)
                            dragIndex = emitted
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onHorizontalDrag = { change, _ ->
                            val index = indexAt(change.position.x)
                            if (index != emitted) {
                                emitted = index
                                dragIndex = index
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = {
                            if (emitted >= 0) onSelect(options[emitted])
                            dragIndex = null
                        },
                        onDragCancel = { dragIndex = null },
                    )
                },
            ),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = highlightOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .padding(highlightInset)
                    .clip(highlightShape)
                    .background(highlightColor.copy(alpha = if (enabled) 1f else 0.4f)),
            )

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                options.forEachIndexed { index, option ->
                    val active = index == selectedIndex
                    val content by animateColorAsState(
                        if (active) contentColorOn(highlightColor) else AppleColors.secondary,
                        tween(180), label = "horizontalSegmentContent",
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics { contentDescription = label(option) },
                        contentAlignment = Alignment.Center,
                    ) {
                        ControlLabel(
                            icon = icon(option),
                            text = label(option),
                            color = content,
                            textStyle = AppleTypography.labelSmall.copy(
                                fontSize = 10.sp * contentScale,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            layout = contentLayout,
                            iconSize = 19.dp * contentScale,
                            gap = 2.dp * contentScale,
                            textModifier = Modifier.padding(horizontal = 6.dp * contentScale),
                        )

                        if (index < options.lastIndex) {
                            val dividerVisible = index != selectedIndex && index + 1 != selectedIndex
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(vertical = 14.dp * contentScale)
                                    .width(0.5.dp)
                                    .fillMaxHeight()
                                    .background(
                                        AppleColors.quaternary.copy(alpha = if (dividerVisible) 1f else 0f),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4 · Vertical switch
// ---------------------------------------------------------------------------------------------

/**
 * A tall on/off switch: a thumb spanning half the track rides to the top and turns [accent]
 * when [checked], and drops to the bottom in a muted tone when off. Tap to toggle, or hold and
 * drag the thumb up/down — it follows the finger and snaps to the nearest end on release.
 *
 * The thumb can carry an [icon] and/or a [label] (both resolved from the on/off state), auto-laid
 * out per [contentLayout]. Their colour follows the thumb's contrast automatically.
 */
@Composable
fun VerticalSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppleColors.iosSwitchGreen,
    icon: (Boolean) -> ImageVector? = { null },
    label: (Boolean) -> String? = { null },
    contentLayout: ControlContentLayout = ControlContentLayout.Vertical,
    enabled: Boolean = true,
    shape: Shape = VerticalSliderSquircle,
) {
    val inset = 6.dp
    val haptic = LocalHapticFeedback.current
    // While dragging, 0f = pinned to the top (on) … 1f = pinned to the bottom (off).
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val effectiveChecked = dragFraction?.let { it < 0.5f } ?: checked

    val trackColor by animateColorAsState(
        Color.White.copy(alpha = 0.06f), tween(200), label = "switchTrack",
    )
    val thumbColor by animateColorAsState(
        when {
            !enabled -> AppleColors.inactive.copy(alpha = 0.4f)
            effectiveChecked -> accent
            else -> Color.White.copy(alpha = 0.16f)
        },
        tween(220), label = "switchThumb",
    )

    BoxWithConstraints(
        modifier
            .clip(shape)
            .background(trackColor)
            .border(0.5.dp, AppleColors.frostedBorder, shape)
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(checked) {
                    detectTapGestures { onCheckedChange(!checked) }
                },
            )
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                    fun fractionAt(y: Float): Float {
                        val thumbPx = size.height * 0.5f
                        val insetPx = inset.toPx()
                        val travelPx = (size.height - thumbPx - insetPx * 2).coerceAtLeast(1f)
                        return ((y - insetPx - thumbPx / 2f) / travelPx).coerceIn(0f, 1f)
                    }
                    detectVerticalDragGestures(
                        onDragStart = { dragFraction = fractionAt(it.y) },
                        onVerticalDrag = { change, _ ->
                            val previous = dragFraction
                            val next = fractionAt(change.position.y)
                            dragFraction = next
                            if (previous != null && (previous < 0.5f) != (next < 0.5f)) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = { dragFraction?.let { onCheckedChange(it < 0.5f) }; dragFraction = null },
                        onDragCancel = { dragFraction = null },
                    )
                },
            )
            .semantics { contentDescription = if (effectiveChecked) "Activé" else "Désactivé" },
    ) {
        // Thumb takes half the track's height and rides between the two insets.
        val thumbWidth = maxWidth - inset * 2
        val thumbHeight = maxHeight * 0.5f
        val travel = (maxHeight - thumbHeight - inset * 2).coerceAtLeast(0.dp)
        // Follow the finger immediately while dragging; spring to the resting end otherwise.
        val restingFraction = if (checked) 0f else 1f
        val settledFraction by animateFloatAsState(
            dragFraction ?: restingFraction, AppleMotion.spring(), label = "switchFraction",
        )
        val fraction = dragFraction ?: settledFraction
        val outerRadius = maxWidth * 0.30f
        val thumbRadius = minOf(
            (outerRadius - inset).coerceAtLeast(0.dp),
            thumbWidth / 2,
            thumbHeight / 2,
        )
        val thumbShape = RoundedCornerShape(thumbRadius)
        // Scale the thumb content from the canonical 88 × 220 control. Width and height both
        // participate so compact or unusually proportioned switches cannot overflow.
        val contentScale = minOf(
            maxWidth / 88.dp,
            thumbWidth / 76.dp,
            thumbHeight / 110.dp,
        ).coerceIn(0.55f, 1.5f)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = inset + travel * fraction)
                .width(thumbWidth)
                .height(thumbHeight)
                .shadow(4.dp, thumbShape, spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(thumbShape)
                .background(thumbColor),
            contentAlignment = Alignment.Center,
        ) {
            ControlLabel(
                icon = icon(effectiveChecked),
                text = label(effectiveChecked),
                color = contentColorOn(thumbColor),
                textStyle = AppleTypography.labelSmall.copy(
                    fontSize = AppleTypography.labelSmall.fontSize * contentScale,
                ),
                layout = contentLayout,
                iconSize = 20.dp * contentScale,
                gap = 5.dp * contentScale,
                textModifier = Modifier.padding(horizontal = 6.dp * contentScale),
            )
        }
    }
}
