package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectTapGestures
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

/** Outer corner radius of every pill-shaped control. */
private val ControlCornerRadius: Dp = 26.dp

/** Continuous-corner squircle used by the pill-shaped controls. */
private val ControlSquircle: Shape = RoundedCornerShape(ControlCornerRadius)

/** Slider-only corners scale with the control width instead of staying frozen at 26 dp. */
private val VerticalSliderSquircle: Shape = RoundedCornerShape(percent = 30)

/**
 * Concentric-corner rule (Apple HIG): a shape nested inside another, [inset] away from its
 * edge, must round by `outer − inset` so the curves stay parallel. Never reuse the outer radius.
 */
private fun innerCorner(inset: Dp): Shape =
    RoundedCornerShape((ControlCornerRadius - inset).coerceAtLeast(0.dp))

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
    text: String?,
    color: Color,
    textStyle: TextStyle,
    layout: ControlContentLayout,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    gap: Dp = 5.dp,
    textModifier: Modifier = Modifier,
) {
    if (icon == null && text == null) return
    val iconSlot: @Composable () -> Unit = {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(iconSize))
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
const val MaxSegments: Int = 6

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
                    detectTapGestures { commit(fracAt(it.y), finished = true) }
                },
            )
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(origin, valueRange) {
                    fun fracAt(y: Float): Float {
                        val t = (y / size.height).coerceIn(0f, 1f)
                        return if (origin == FillOrigin.BOTTOM) 1f - t else t
                    }
                    var current = fraction
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; current = fracAt(it.y); commit(current, finished = false) },
                        onVerticalDrag = { change, _ -> current = fracAt(change.position.y); commit(current, finished = false) },
                        onDragEnd = { dragging = false; commit(current, finished = true) },
                        onDragCancel = { dragging = false },
                    )
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
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
            ControlLabel(
                icon = icon,
                text = label?.invoke(valueOf(targetFraction, valueRange)),
                color = captionColor,
                textStyle = AppleTypography.bodySmall,
                layout = contentLayout,
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
    shape: Shape = ControlSquircle,
    thumbColor: ((Float) -> Color)? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    require(colors.isNotEmpty()) { "VerticalGradientSlider needs at least one colour" }
    val inset = 6.dp
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
                    detectTapGestures { commit(1f - (it.y / size.height).coerceIn(0f, 1f), finished = true) }
                },
            )
            .then(
                if (!enabled) Modifier else Modifier.pointerInput(valueRange) {
                    fun fracAt(y: Float): Float = 1f - (y / size.height).coerceIn(0f, 1f)
                    var current = fraction
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; current = fracAt(it.y); commit(current, finished = false) },
                        onVerticalDrag = { change, _ -> current = fracAt(change.position.y); commit(current, finished = false) },
                        onDragEnd = { dragging = false; commit(current, finished = true) },
                        onDragCancel = { dragging = false },
                    )
                },
            ),
    ) {
        // Thumb: inset from the track edge, rounded concentrically — same rule as the switch.
        val thumbHeight = (maxHeight * 0.26f).coerceIn(44.dp, 64.dp)
        val travel = (maxHeight - thumbHeight - inset * 2).coerceAtLeast(0.dp)
        val thumbShape = innerCorner(inset)
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
                textStyle = AppleTypography.bodySmall,
                layout = contentLayout,
                iconSize = 17.dp,
                gap = 3.dp,
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
    shape: Shape = ControlSquircle,
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
 * Takes 2–6 options. Rather than squeezing them into a fixed footprint, the control keeps every
 * segment [segmentHeight] tall and grows downward — so the caller only sets its width; the height
 * follows the option count.
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
    shape: Shape = ControlSquircle,
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
        modifier
            // Self-sizing height: one segment per option. Caller controls width only.
            .height(segmentHeight * options.size)
            .clip(shape)
            .background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, shape)
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

        val iconGap = 5.dp
        val iconSize = 18.dp
        val labels = options.map(label)
        val anyHorizontalIcon = contentLayout == ControlContentLayout.Horizontal && options.any { icon(it) != null }
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val fittedStyle = remember(labels, maxWidth, anyHorizontalIcon, density) {
            // Measure at the selected weight (SemiBold) so the bold row never overflows.
            val base = AppleTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            val reserved = textPadding * 2 + if (anyHorizontalIcon) iconSize + iconGap else 0.dp
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
                .clip(innerCorner(highlightInset))
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
                        textModifier = Modifier.padding(horizontal = textPadding),
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
    shape: Shape = ControlSquircle,
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
        val thumbShape = innerCorner(inset)
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
                textStyle = AppleTypography.labelSmall,
                layout = contentLayout,
                iconSize = 20.dp,
            )
        }
    }
}
