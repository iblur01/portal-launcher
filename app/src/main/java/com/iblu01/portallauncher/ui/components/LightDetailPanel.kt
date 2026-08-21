package com.iblu01.portallauncher.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.LocalAreas
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.ui.components.controls.kelvinToColor
import com.iblu01.portallauncher.ui.components.controls.VerticalColorTempSlider
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val colorPresets = listOf(
    Triple(255, 120, 72),  // coucher de soleil
    Triple(255, 178, 102), // bougie
    Triple(255, 105, 135), // rose feutré
    Triple(125, 210, 160), // forêt douce
    Triple(72, 190, 205),  // lagon
    Triple(80, 130, 255),  // crépuscule
    Triple(155, 110, 230), // lavande
    Triple(220, 85, 155),  // soirée
)

private val whitePresets = listOf(
    2200 to Color(0xFFFFB46B), 2700 to Color(0xFFFFD1A3),
    4000 to Color(0xFFFFEBD2), 6500 to Color(0xFFEFF4FF),
)

private val colorModes = setOf("hs", "rgb", "xy", "rgbw", "rgbww")

private enum class LightSliderMode { COLOR, COLOR_TEMPERATURE }

@Composable
fun LightDetailContent(
    detail: PillDetail,
    onBack: () -> Unit,
    closePanel: Boolean = false,
) {
    val callService = LocalCallService.current
    val entity = LocalHaStates.current[detail.entityId]
    val attrs = entity?.attributes
    val isOn = entity?.state?.equals("on", true) ?: detail.active
    val modes = attrs?.optJSONArray("supported_color_modes")
        ?.let { array -> List(array.length()) { array.optString(it).lowercase() } }
        .orEmpty()
    val supportsColor = modes.any { it in colorModes }
    val supportsWhite = "color_temp" in modes
    val supportsBrightness = modes.isEmpty() || modes.any { it != "onoff" }
    val onOffOnly = modes.isNotEmpty() && modes.all { it == "onoff" }
    val minKelvin = attrs?.optInt("min_color_temp_kelvin", 2000)?.coerceAtLeast(1000) ?: 2000
    val maxKelvin = max(minKelvin + 1, attrs?.optInt("max_color_temp_kelvin", 6500) ?: 6500)
    val initialKelvin = (attrs?.optInt("color_temp_kelvin", 4000) ?: 4000).coerceIn(minKelvin, maxKelvin)
    val rgbColor = attrs?.optJSONArray("rgb_color")?.let { array ->
        if (array.length() >= 3) Color(array.optInt(0), array.optInt(1), array.optInt(2)) else null
    }
    val hs = attrs?.optJSONArray("hs_color")
    val initialHsv = when {
        hs != null && hs.length() >= 2 -> Triple(hs.optDouble(0).toFloat(), hs.optDouble(1).toFloat() / 100f, 1f)
        rgbColor != null -> colorToHsv(rgbColor)
        else -> Triple(0f, 0f, 1f)
    }
    var brightness by remember(detail.entityId) {
        mutableFloatStateOf(if (!isOn) 0f else ((attrs?.optInt("brightness", 255) ?: 255) / 255f).coerceIn(0.04f, 1f))
    }
    var kelvin by remember(detail.entityId) { mutableIntStateOf(initialKelvin) }
    var selectedHsv by remember(detail.entityId) { mutableStateOf(initialHsv) }
    var selectedColor by remember(detail.entityId) {
        mutableStateOf(rgbColor ?: if (supportsWhite) kelvinToColor(initialKelvin) else Color.White)
    }
    var lightOn by remember(detail.entityId) { mutableStateOf(isOn) }
    var sliderMode by remember(detail.entityId) {
        mutableStateOf(if (supportsColor) LightSliderMode.COLOR else LightSliderMode.COLOR_TEMPERATURE)
    }
    var wheelVisible by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }
    val deviceLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(Unit) { entered = true }
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(AppleMotion.FADE_DURATION)) +
            slideInVertically(tween(AppleMotion.SLIDE_DURATION, easing = FastOutSlowInEasing)) { it / 12 },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DetailHeader(detail.label, onBack, closePanel)
            if (onOffOnly) {
                OnOffLightControl(
                    checked = lightOn,
                    onCheckedChange = { on ->
                        lightOn = on
                        callService("light", if (on) "turn_on" else "turn_off", detail.entityId)
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.height(16.dp))

                // Live-while-dragging: send throttled updates on every change and the exact final
                // value on release, so the bulb tracks the finger instead of jumping on let-go.
                val brightnessThrottle = remember(detail.entityId) { LiveThrottle() }
                val kelvinThrottle = remember(detail.entityId) { LiveThrottle() }
                val onBrightnessLive: (Float) -> Unit = {
                    brightness = it; lightOn = it > 0f
                    if (brightnessThrottle.allow()) commitBrightness(callService, detail.entityId, it)
                }
                val onBrightnessFinal: (Float) -> Unit = {
                    brightnessThrottle.reset(); lightOn = it > 0f; commitBrightness(callService, detail.entityId, it)
                }
                val onKelvinLive: (Int) -> Unit = {
                    kelvin = it; selectedColor = kelvinToColor(it); lightOn = true
                    if (kelvinThrottle.allow()) callService("light", "turn_on", detail.entityId, mapOf("color_temp_kelvin" to it))
                }
                val onKelvinFinal: (Int) -> Unit = {
                    kelvinThrottle.reset(); lightOn = true
                    callService("light", "turn_on", detail.entityId, mapOf("color_temp_kelvin" to it))
                }
                val onPreset: (Color, () -> Unit) -> Unit = { color, action ->
                    lightOn = true; brightness = max(brightness, 0.5f); selectedColor = color; selectedHsv = colorToHsv(color); action()
                }
                val onOpenWheel: (Color?) -> Unit = { preset -> preset?.let { selectedHsv = colorToHsv(it) }; wheelVisible = true }
                AdaptiveLightDetail(
                    brightness, onBrightnessLive, onBrightnessFinal,
                    selectedColor, lightOn, sliderMode, { sliderMode = it },
                    supportsBrightness, supportsColor, supportsWhite,
                    kelvin, minKelvin, maxKelvin,
                    onKelvinChange = onKelvinLive,
                    onKelvinCommit = onKelvinFinal,
                    currentColor = selectedColor,
                    onPreset = onPreset,
                    onOpenWheel = onOpenWheel,
                    entityId = detail.entityId,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (wheelVisible && supportsColor) {
        ColorWheelOverlay(
            initialHue = selectedHsv.first,
            initialSaturation = selectedHsv.second,
            landscape = deviceLandscape,
            onDismiss = { wheelVisible = false },
            onPreview = { hue, saturation -> selectedHsv = Triple(hue, saturation, 1f); selectedColor = hsvToColor(hue, saturation) },
            onCommit = { hue, saturation ->
                lightOn = true
                brightness = max(brightness, 0.5f)
                callService("light", "turn_on", detail.entityId, mapOf("hs_color" to listOf(hue.toDouble(), saturation.toDouble())))
            },
        )
    }
}

/** The complete detail surface for lights whose only HA colour mode is `onoff`. */
@Composable
private fun OnOffLightControl(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(AppleShapes.card)
                .background(AppleColors.frostedFill, AppleShapes.card)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (checked) R.string.light_state_on else R.string.light_state_off),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.primary,
                )
                Text(
                    text = stringResource(if (checked) R.string.action_turn_off else R.string.action_turn_on),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                )
            }
            IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DetailHeader(label: String, onBack: () -> Unit, closePanel: Boolean = false) {
    PanelHeader(
        title = label,
        onNavigation = if (closePanel) null else onBack,
        navigationIcon = if (closePanel) null else Icons.AutoMirrored.Filled.ArrowBack,
        navigationContentDescription = if (closePanel) null else stringResource(R.string.lights_back),
        onClose = if (closePanel) onBack else null,
    )
}

/** A device light bypasses the redundant one-row browser and opens this detail immediately. */
internal fun LauncherChip.individualLightDetailOrNull(): PillDetail? =
    if (
        kind == PillKind.LIGHTS &&
        entityId.startsWith("light.") &&
        ',' !in entityId
    ) {
        PillDetail(
            label = label,
            value = value,
            entityId = entityId,
            active = deviceState.equals("on", ignoreCase = true),
        )
    } else {
        null
    }

@Composable
private fun AdaptiveLightDetail(
    brightness: Float, onBrightnessChange: (Float) -> Unit, onBrightnessCommit: (Float) -> Unit,
    trackColor: Color, isOn: Boolean, sliderMode: LightSliderMode, onSliderModeChange: (LightSliderMode) -> Unit,
    supportsBrightness: Boolean, supportsColor: Boolean, supportsWhite: Boolean,
    kelvin: Int, minKelvin: Int, maxKelvin: Int,
    onKelvinChange: (Int) -> Unit, onKelvinCommit: (Int) -> Unit,
    currentColor: Color, onPreset: (Color, () -> Unit) -> Unit, onOpenWheel: (Color?) -> Unit,
    entityId: String,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (maxWidth <= maxHeight) {
            VerticalLightDetail(
                brightness, onBrightnessChange, onBrightnessCommit, trackColor, isOn,
                sliderMode, onSliderModeChange, supportsBrightness, supportsColor, supportsWhite,
                kelvin, minKelvin, maxKelvin, onKelvinChange, onKelvinCommit, currentColor,
                onPreset, onOpenWheel, entityId,
            )
        } else {
            HorizontalLightDetail(
                brightness, onBrightnessChange, onBrightnessCommit, trackColor, isOn,
                sliderMode, onSliderModeChange, supportsBrightness, supportsColor, supportsWhite,
                kelvin, minKelvin, maxKelvin, onKelvinChange, onKelvinCommit, currentColor,
                onPreset, onOpenWheel, entityId,
            )
        }
    }
}

@Composable
private fun HorizontalLightDetail(
    brightness: Float, onBrightnessChange: (Float) -> Unit, onBrightnessCommit: (Float) -> Unit,
    trackColor: Color, isOn: Boolean, sliderMode: LightSliderMode, onSliderModeChange: (LightSliderMode) -> Unit,
    supportsBrightness: Boolean, supportsColor: Boolean, supportsWhite: Boolean,
    kelvin: Int, minKelvin: Int, maxKelvin: Int,
    onKelvinChange: (Int) -> Unit, onKelvinCommit: (Int) -> Unit,
    currentColor: Color, onPreset: (Color, () -> Unit) -> Unit, onOpenWheel: (Color?) -> Unit,
    entityId: String,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val showTemperature = sliderMode == LightSliderMode.COLOR_TEMPERATURE && supportsWhite
        val sliderCount = (if (supportsBrightness) 1 else 0) + (if (showTemperature) 1 else 0)
        val compactWide = maxHeight <= 430.dp
        val columnGap = if (compactWide) 28.dp else 22.dp
        val sliderGap = if (compactWide) 22.dp else 18.dp
        val controlsWidth = maxWidth * if (compactWide) 0.52f else 0.5f
        val availableSliderWidth = controlsWidth - sliderGap * (sliderCount - 1).coerceAtLeast(0)
        val sliderWidthFromSpace = if (sliderCount > 0) availableSliderWidth / sliderCount else 0.dp
        val ratio = 96f / 240f
        val sliderHeight = minOf(
            maxHeight - if (compactWide) 12.dp else 20.dp,
            sliderWidthFromSpace / ratio,
            if (compactWide) 290.dp else 320.dp,
        )
        val sliderWidth = sliderHeight * ratio

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(columnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.width(controlsWidth).fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(sliderGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (supportsBrightness) {
                    VerticalFillSlider(
                        value = if (isOn) brightness else 0f,
                        onValueChange = onBrightnessChange,
                        onValueChangeFinished = onBrightnessCommit,
                        accent = trackColor,
                        icon = Icons.Filled.WbSunny,
                        iconContent = { tint, size ->
                            HaEntityIcon(
                                entityId = entityId,
                                contentDescription = null,
                                tint = tint,
                                size = size,
                                fallback = Icons.Filled.WbSunny,
                            )
                        },
                        hapticSteps = 10,
                        modifier = Modifier.size(sliderWidth, sliderHeight),
                    )
                }
                if (showTemperature) {
                    VerticalColorTempSlider(
                        kelvin = kelvin,
                        onKelvinChange = onKelvinChange,
                        onKelvinChangeFinished = onKelvinCommit,
                        minKelvin = minKelvin,
                        maxKelvin = maxKelvin,
                        modifier = Modifier.size(sliderWidth, sliderHeight),
                    )
                }
            }
            LightSelectorsPane(
                mode = sliderMode,
                onModeChange = onSliderModeChange,
                supportsColor = supportsColor,
                supportsWhite = supportsWhite,
                currentColor = currentColor,
                minKelvin = minKelvin,
                maxKelvin = maxKelvin,
                entityId = entityId,
                onPreset = onPreset,
                onOpenWheel = onOpenWheel,
                compact = compactWide,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun VerticalLightDetail(
    brightness: Float, onBrightnessChange: (Float) -> Unit, onBrightnessCommit: (Float) -> Unit,
    trackColor: Color, isOn: Boolean, sliderMode: LightSliderMode, onSliderModeChange: (LightSliderMode) -> Unit,
    supportsBrightness: Boolean, supportsColor: Boolean, supportsWhite: Boolean,
    kelvin: Int, minKelvin: Int, maxKelvin: Int,
    onKelvinChange: (Int) -> Unit, onKelvinCommit: (Int) -> Unit,
    currentColor: Color, onPreset: (Color, () -> Unit) -> Unit, onOpenWheel: (Color?) -> Unit,
    entityId: String,
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val showTemperature = sliderMode == LightSliderMode.COLOR_TEMPERATURE && supportsWhite
            val sliderCount = (if (supportsBrightness) 1 else 0) + (if (showTemperature) 1 else 0)
            val gap = 18.dp
            val widthFraction = if (sliderCount > 1) 0.78f else 0.54f
            val availableWidth = maxWidth * widthFraction - gap * (sliderCount - 1).coerceAtLeast(0)
            val sliderWidthFromSpace = if (sliderCount > 0) availableWidth / sliderCount else 0.dp
            val ratio = 96f / 240f
            val sliderHeight = minOf(maxHeight, sliderWidthFromSpace / ratio)
            val sliderWidth = sliderHeight * ratio
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (supportsBrightness) {
                    VerticalFillSlider(
                        value = if (isOn) brightness else 0f,
                        onValueChange = onBrightnessChange,
                        onValueChangeFinished = onBrightnessCommit,
                        accent = trackColor,
                        icon = Icons.Filled.WbSunny,
                        iconContent = { tint, size ->
                            HaEntityIcon(entityId, null, tint, size, Icons.Filled.WbSunny)
                        },
                        hapticSteps = 10,
                        modifier = Modifier.size(sliderWidth, sliderHeight),
                    )
                }
                if (showTemperature) {
                    VerticalColorTempSlider(
                        kelvin = kelvin,
                        onKelvinChange = onKelvinChange,
                        minKelvin = minKelvin,
                        maxKelvin = maxKelvin,
                        modifier = Modifier.size(sliderWidth, sliderHeight),
                        onKelvinChangeFinished = onKelvinCommit,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LightPresetsBar(
            sliderMode, supportsColor, supportsWhite, currentColor, minKelvin, maxKelvin,
            entityId, onPreset, onOpenWheel,
        )
        if (supportsColor && supportsWhite) {
            Spacer(Modifier.height(10.dp))
            SliderModeSwitch(sliderMode, onSliderModeChange)
        }
    }
}

@Composable
private fun LightSelectorsPane(
    mode: LightSliderMode,
    onModeChange: (LightSliderMode) -> Unit,
    supportsColor: Boolean,
    supportsWhite: Boolean,
    currentColor: Color,
    minKelvin: Int,
    maxKelvin: Int,
    entityId: String,
    onPreset: (Color, () -> Unit) -> Unit,
    onOpenWheel: (Color?) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val callService = LocalCallService.current
    val dotSize = if (compact) 58.dp else 52.dp
    val presets: List<Pair<Color, () -> Unit>> = when {
        mode == LightSliderMode.COLOR && supportsColor -> colorPresets.map { (r, g, b) ->
            val color = Color(r, g, b)
            color to { onPreset(color) { callService("light", "turn_on", entityId, mapOf("rgb_color" to listOf(r, g, b))) } }
        }
        supportsWhite -> whitePresets.map { (rawKelvin, swatch) ->
            val value = rawKelvin.coerceIn(minKelvin, maxKelvin)
            swatch to { onPreset(swatch) { callService("light", "turn_on", entityId, mapOf("color_temp_kelvin" to value)) } }
        }
        else -> emptyList()
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (supportsColor && supportsWhite) {
            SliderModeSwitch(mode, onModeChange, compact = false)
            Spacer(Modifier.height(if (compact) 20.dp else 16.dp))
        }
        presets.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { (color, action) ->
                    ColorDot(
                        color = color,
                        active = colorsNear(color, currentColor),
                        onClick = action,
                        onLongClick = if (mode == LightSliderMode.COLOR) ({ onOpenWheel(color) }) else null,
                        size = dotSize,
                    )
                }
            }
            Spacer(Modifier.height(if (compact) 14.dp else 12.dp))
        }
        if (mode == LightSliderMode.COLOR && supportsColor) {
            PaletteButton(size = dotSize) { onOpenWheel(null) }
        }
    }
}

@Composable
private fun LightPresetsBar(
    mode: LightSliderMode, supportsColor: Boolean, supportsWhite: Boolean,
    currentColor: Color, minKelvin: Int, maxKelvin: Int, entityId: String,
    onPreset: (Color, () -> Unit) -> Unit, onOpenWheel: (Color?) -> Unit,
) {
    val callService = LocalCallService.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 3.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (mode == LightSliderMode.COLOR && supportsColor) {
            colorPresets.forEach { (r, g, b) ->
                val color = Color(r, g, b)
                ColorDot(color, colorsNear(color, currentColor), { onPreset(color) { callService("light", "turn_on", entityId, mapOf("rgb_color" to listOf(r, g, b))) } }, { onOpenWheel(color) })
            }
        }
        if (mode == LightSliderMode.COLOR_TEMPERATURE && supportsWhite) {
            whitePresets.forEach { (rawKelvin, swatch) ->
                val value = rawKelvin.coerceIn(minKelvin, maxKelvin)
                ColorDot(swatch, colorsNear(swatch, currentColor), { onPreset(swatch) { callService("light", "turn_on", entityId, mapOf("color_temp_kelvin" to value)) } }, null)
            }
        }
        }
        if (mode == LightSliderMode.COLOR && supportsColor) {
            Spacer(Modifier.width(12.dp))
            PaletteButton { onOpenWheel(null) }
        }
    }
}

@Composable
private fun PaletteButton(size: Dp = 46.dp, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, AppleMotion.spring(), label = "paletteScale")
    val colorWheelDesc = stringResource(R.string.light_open_color_wheel_desc)
    Box(
        Modifier.scale(scale).size(size).clip(CircleShape).background(AppleColors.frostedFill)
            .border(1.dp, AppleColors.frostedBorder, CircleShape).semantics { contentDescription = colorWheelDesc }
            .pointerInput(onClick) { detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() }) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Palette, null, tint = AppleColors.primary, modifier = Modifier.size(21.dp))
        Icon(Icons.Filled.Add, null, tint = AppleColors.primary, modifier = Modifier.align(Alignment.BottomEnd).size(14.dp))
    }
}

@Composable
private fun ColorDot(color: Color, active: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, size: Dp = 46.dp) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, AppleMotion.spring(), label = "dotScale")
    val activeDesc = stringResource(R.string.light_color_active_desc)
    val applyDesc = stringResource(R.string.light_color_apply_desc)
    Box(
        Modifier.scale(scale).size(size)
            .then(if (active) Modifier.shadow(10.dp, CircleShape, ambientColor = color, spotColor = color) else Modifier)
            .clip(CircleShape).background(color).border(if (active) 2.dp else 1.dp, Color.White.copy(alpha = if (active) 0.95f else 0.35f), CircleShape)
            .semantics { contentDescription = if (active) activeDesc else applyDesc }
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            },
    )
}

@Composable
private fun SliderModeSwitch(
    mode: LightSliderMode,
    onModeChange: (LightSliderMode) -> Unit,
    compact: Boolean = false,
) {
    Row(
        Modifier.clip(AppleShapes.pill).background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SliderModeButton(
            icon = Icons.Filled.Palette,
            label = if (compact) null else stringResource(R.string.light_mode_brightness),
            selected = mode == LightSliderMode.COLOR,
            onClick = { onModeChange(LightSliderMode.COLOR) },
        )
        SliderModeButton(
            icon = Icons.Filled.NightsStay,
            label = if (compact) null else stringResource(R.string.light_mode_temperature),
            selected = mode == LightSliderMode.COLOR_TEMPERATURE,
            onClick = { onModeChange(LightSliderMode.COLOR_TEMPERATURE) },
        )
    }
}

@Composable
private fun SliderModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(if (selected) AppleColors.primary else Color.Transparent, tween(180), label = "modeBackground")
    val foreground by animateColorAsState(if (selected) Color.Black else AppleColors.secondary, tween(180), label = "modeForeground")
    Row(
        Modifier.height(32.dp).clip(AppleShapes.pill).background(background).clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 9.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = foreground, modifier = Modifier.size(15.dp))
        label?.let { Text(it, style = AppleTypography.labelSmall, color = foreground) }
    }
}

@Composable
private fun ColorWheelOverlay(
    initialHue: Float, initialSaturation: Float, landscape: Boolean,
    onDismiss: () -> Unit, onPreview: (Float, Float) -> Unit, onCommit: (Float, Float) -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    var hue by remember(initialHue) { mutableFloatStateOf(initialHue) }
    var saturation by remember(initialSaturation) { mutableFloatStateOf(initialSaturation.coerceIn(0f, 1f)) }
    var touching by remember { mutableStateOf(false) }
    val preview by animateColorAsState(hsvToColor(hue, saturation), tween(200), label = "wheelPreview")
    val wheelScale by animateFloatAsState(if (touching) AppleMotion.PRESS_SCALE else 1f, AppleMotion.spring(), label = "wheelPress")
    LaunchedEffect(Unit) { shown = true }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = shown,
                enter = fadeIn(tween(AppleMotion.FADE_DURATION)) + scaleIn(AppleMotion.spring(), initialScale = 0.92f),
                exit = fadeOut(tween(AppleMotion.FADE_DURATION)) + scaleOut(targetScale = 0.96f),
            ) {
                Column(
                    Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth()
                        .clip(AppleShapes.panel).background(AppleColors.elevated)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.light_color_wheel_title), style = AppleTypography.titleLarge, color = AppleColors.primary)
                        Box(Modifier.size(40.dp).clip(CircleShape).background(AppleColors.frostedFill).pointerInput(onDismiss) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, stringResource(R.string.light_color_wheel_close_desc), tint = AppleColors.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(if (landscape) 10.dp else 20.dp))
                    ColorWheel(
                        hue, saturation, if (landscape) 190.dp else 240.dp,
                        Modifier.scale(wheelScale),
                        onTouchState = { touching = it },
                        onChange = { h, s -> hue = h; saturation = s; onPreview(h, s) },
                        onCommit = { onCommit(hue, saturation * 100f) },
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(34.dp).shadow(5.dp, CircleShape).clip(CircleShape).background(preview).border(2.dp, Color.White, CircleShape))
                        Text("${hue.roundToInt()}°  ·  ${(saturation * 100).roundToInt()}%", style = AppleTypography.bodySmall, color = AppleColors.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorWheel(
    hue: Float, saturation: Float, diameter: Dp, modifier: Modifier = Modifier,
    onTouchState: (Boolean) -> Unit, onChange: (Float, Float) -> Unit, onCommit: () -> Unit,
) {
    val hueStops = remember { (0..12).map { hsvToColor(it * 30f, 1f) } }
    val wheelDescription = stringResource(R.string.lights_color_wheel_desc, hue.roundToInt())
    Canvas(
        modifier.size(diameter).semantics { contentDescription = wheelDescription }
            .pointerInput(Unit) {
                fun update(point: Offset) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = point.x - center.x
                    val dy = point.y - center.y
                    val radius = min(size.width, size.height) / 2f
                    val sat = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
                    val angle = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
                    onChange(angle, sat)
                }
                detectDragGestures(
                    onDragStart = { onTouchState(true); update(it) },
                    onDrag = { change, _ -> update(change.position) },
                    onDragEnd = { onTouchState(false); onCommit() },
                    onDragCancel = { onTouchState(false) },
                )
            },
    ) {
        val radius = size.minDimension / 2f
        drawCircle(Brush.sweepGradient(hueStops), radius)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), radius = radius), radius)
        drawCircle(Color.White.copy(alpha = 0.28f), radius, style = Stroke(1.dp.toPx()))
        val angle = hue / 180f * PI.toFloat()
        val thumb = Offset(center.x + cos(angle) * radius * saturation, center.y + sin(angle) * radius * saturation)
        drawCircle(Color.Black.copy(alpha = 0.28f), 12.dp.toPx(), thumb + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White, 9.dp.toPx(), thumb)
        drawCircle(hsvToColor(hue, saturation), 6.dp.toPx(), thumb)
    }
}

private fun commitBrightness(callService: CallService, entityId: String, value: Float) {
    val pct = (value.coerceIn(0f, 1f) * 100).roundToInt()
    if (pct == 0) callService("light", "turn_off", entityId)
    else callService("light", "turn_on", entityId, mapOf("brightness_pct" to pct))
}

/**
 * Rate-limiter for live-while-dragging service calls. Lets the first call through, then at most
 * one per [minIntervalMs] — the slider feels like it responds continuously (premium) without
 * spamming Home Assistant with a call per pixel. Always [reset] on release, then send the final
 * value so the light lands exactly where the finger left it.
 */
private class LiveThrottle(private val minIntervalMs: Long = 110L) {
    private var lastMs = 0L
    fun allow(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastMs < minIntervalMs) return false
        lastMs = now
        return true
    }
    fun reset() { lastMs = 0L }
}

private fun colorsNear(a: Color, b: Color): Boolean {
    val dr = a.red - b.red
    val dg = a.green - b.green
    val db = a.blue - b.blue
    return dr * dr + dg * dg + db * db < 0.025f
}

private fun hsvToColor(hue: Float, saturation: Float, value: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when (h.toInt() / 60) {
        0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

private fun colorToHsv(color: Color): Triple<Float, Float, Float> {
    val maxValue = max(color.red, max(color.green, color.blue))
    val minValue = min(color.red, min(color.green, color.blue))
    val delta = maxValue - minValue
    val hue = when {
        delta == 0f -> 0f
        maxValue == color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        maxValue == color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }
    return Triple((hue + 360f) % 360f, if (maxValue == 0f) 0f else delta / maxValue, maxValue)
}

/** Group the chip's lights by HA area, ordered by name with the unassigned room last. */
private fun lightRooms(chip: LauncherChip, noRoom: String, areaOf: (String) -> String?): List<Pair<String, List<PillDetail>>> =
    chip.details.groupBy { areaOf(it.entityId) ?: noRoom }
        .toList()
        .sortedWith(compareBy({ it.first == noRoom }, { it.first }))

/** Existing light list API consumed by SidePanel. */
@Composable
fun LightsActions(chip: LauncherChip, onOpenLight: (PillDetail) -> Unit) {
    val areas = LocalAreas.current
    // Group chips already carry one detail per light. Individual catalog pills intentionally do
    // not duplicate themselves in `details`, so materialise their own actionable row here.
    val lights = chip.details.ifEmpty { listOfNotNull(chip.individualLightDetailOrNull()) }
    val actionableChip = chip.copy(details = lights)
    val rooms = lightRooms(actionableChip, stringResource(R.string.lights_no_room)) { areas[it] }
    // Fewer than two rooms (or registries not loaded yet): keep the plain flat list.
    if (rooms.size <= 1) {
        LightRows(lights, onOpenLight)
        AllOffRow(lights, stringResource(R.string.lights_all_off))
        return
    }

    var selectedRoom by remember(chip.id) { mutableStateOf<String?>(null) }
    val current = selectedRoom?.let { name -> rooms.firstOrNull { it.first == name } }
    if (current == null) {
        RoomGrid(rooms, onSelect = { selectedRoom = it })
        AllOffRow(lights, stringResource(R.string.lights_all_off))
    } else {
        DetailHeader(current.first, onBack = { selectedRoom = null })
        Spacer(Modifier.height(12.dp))
        LightRows(current.second, onOpenLight)
        AllOffRow(current.second, stringResource(R.string.lights_room_off))
    }
}

@Composable
private fun LightRows(details: List<PillDetail>, onOpen: (PillDetail) -> Unit) {
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL && details.size > 1) {
        details.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { detail ->
                    Box(Modifier.weight(1f)) { LightRow(detail, onOpen = { onOpen(detail) }) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    } else {
        details.forEach { detail -> LightRow(detail, onOpen = { onOpen(detail) }) }
    }
}

@Composable
private fun RoomGrid(rooms: List<Pair<String, List<PillDetail>>>, onSelect: (String) -> Unit) {
    val columns = if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) 3 else 2
    rooms.chunked(columns).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (name, details) ->
                RoomCard(name, details, Modifier.weight(1f), onClick = { onSelect(name) })
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RoomCard(name: String, details: List<PillDetail>, modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) AppleMotion.PRESS_SCALE else 1f, AppleMotion.spring(), label = "roomScale")
    val onCount = details.count { it.active }
    Column(
        modifier.scale(scale).clip(AppleShapes.card)
            .background(if (onCount > 0) AppleColors.accent.copy(alpha = 0.14f) else AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, if (onCount > 0) AppleColors.accent.copy(alpha = 0.4f) else AppleColors.frostedBorder, AppleShapes.card)
            .pointerInput(onClick) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (onCount > 0) AppleColors.accent else AppleColors.inactive))
            Text(name, style = AppleTypography.bodyLarge, color = AppleColors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (onCount > 0) androidx.compose.ui.res.pluralStringResource(R.plurals.lights_room_on_count, onCount, onCount, details.size)
            else androidx.compose.ui.res.pluralStringResource(R.plurals.lights_room_count, details.size, details.size),
            style = AppleTypography.bodySmall, color = AppleColors.secondary,
        )
    }
}

@Composable
private fun AllOffRow(details: List<PillDetail>, label: String) {
    val callService = LocalCallService.current
    if (details.none { it.active }) return
    Spacer(Modifier.height(4.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .clickable {
                details.filter { it.active && it.entityId.isNotBlank() }
                    .forEach { callService("light", "turn_off", it.entityId) }
            }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Outlined.PowerSettingsNew, null, tint = AppleColors.primary, modifier = Modifier.size(16.dp))
        Text(label, style = AppleTypography.bodySmall.copy(fontSize = 13.sp), color = AppleColors.primary)
    }
}

@Composable
private fun LightRow(detail: PillDetail, onOpen: () -> Unit) {
    val callService = LocalCallService.current
    var checked by remember(detail.entityId, detail.active) { mutableStateOf(detail.active) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .then(if (detail.entityId.isNotBlank()) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(detail.label, style = AppleTypography.bodyLarge, color = AppleColors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (checked) stringResource(R.string.light_state_on) else stringResource(R.string.light_state_off), style = AppleTypography.bodySmall, color = AppleColors.secondary)
        }
        if (detail.entityId.isNotBlank()) {
            IosSwitch(
                checked = checked,
                onCheckedChange = { on ->
                    checked = on
                    callService("light", if (on) "turn_on" else "turn_off", detail.entityId)
                },
            )
        } else {
            Text(detail.value, style = AppleTypography.bodySmall, color = AppleColors.secondary)
        }
    }
}
