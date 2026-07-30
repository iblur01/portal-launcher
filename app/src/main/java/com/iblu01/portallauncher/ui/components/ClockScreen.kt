package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.clockFontFamily
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.scaled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ClockScreen(
    backgroundMode: String,
    weather: WeatherUi,
    temperatures: TemperatureSummary,
    chips: List<LauncherChip>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
    onChipClick: (LauncherChip) -> Unit = {},
    onChipLongPress: (LauncherChip) -> Unit = {},
    selectedChipKey: String? = null,
    onWeatherClick: () -> Unit = {},
    connected: Boolean = true,
    lastUpdateAt: Long = 0L,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
    clockTheme: ClockTheme = ClockTheme(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            }
    ) {
        if (drawBackground) {
            AmbientBackground(mode = backgroundMode, modifier = Modifier.fillMaxSize())

            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            )

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
            )
        }

        ClockHeader(
            weather = weather,
            temperatures = temperatures,
            onWeatherClick = onWeatherClick,
            connected = connected,
            lastUpdateAt = lastUpdateAt,
            clockTheme = clockTheme,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ClockTray(
            chips = chips,
            pillsExpanded = pillsExpanded,
            onPillsExpandedChange = onPillsExpandedChange,
            onChipClick = onChipClick,
            onChipLongPress = onChipLongPress,
            selectedChipKey = selectedChipKey,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Collapsed header height reserved on the apps page, i.e. the clock at [COLLAPSED_SCALE]. */
val ClockHeaderCollapsedHeight = 92.dp

/** Scale the header shrinks to when the pager is fully on the apps page. */
private const val COLLAPSED_SCALE = 0.34f

/**
 * The clock block (date, time, weather pill, stale banner). Pinned above the pager, it shrinks
 * toward the top as [collapse] goes 0→1 so the apps grid gets the room back.
 *
 * The shrink is a pure [graphicsLayer] transform (GPU, no relayout) — the Portal is API 28 with a
 * weak GPU, so per-frame text remeasuring during a drag would drop frames.
 */
@Composable
fun ClockHeader(
    weather: WeatherUi,
    temperatures: TemperatureSummary,
    onWeatherClick: () -> Unit,
    connected: Boolean,
    lastUpdateAt: Long,
    clockTheme: ClockTheme,
    modifier: Modifier = Modifier,
    collapse: Float = 0f,
) {
    val time by rememberClock(if (clockTheme.format24h) "HH:mm" else "h:mm a")
    val date by rememberClock("EEEE d MMMM")
    // Secondary rows fade out over the first half of the swipe, before the clock is tiny.
    val secondaryAlpha = (1f - collapse * 2f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - (1f - COLLAPSED_SCALE) * collapse
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scale
                scaleY = scale
            }
            .padding(top = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val timeWeight = FontWeight(clockTheme.weight)
        Text(
            text = titleCase(date).uppercase(Locale.getDefault()),
            style = AppleTypography.titleMedium.copy(
                fontFamily = clockFontFamily(clockTheme.font, FontWeight.Medium),
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                letterSpacing = 2.3.sp,
            ),
            color = clockTheme.tint.color.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(4.dp * clockTheme.elementSpacing))
        Text(
            text = time,
            style = AppleTypography.displayLarge.copy(
                fontFamily = clockFontFamily(clockTheme.font, timeWeight),
                fontSize = clockTheme.size.sp,
                fontWeight = timeWeight,
                letterSpacing = clockTheme.letterSpacing.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 2f),
                    blurRadius = 12f
                )
            ),
            color = clockTheme.tint.color,
            maxLines = 1,
            softWrap = false
        )
        if (secondaryAlpha > 0f) {
            Spacer(Modifier.height(8.dp * clockTheme.elementSpacing))
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = secondaryAlpha }
                    .clip(AppleShapes.pill)
                    .background(Color.White.copy(alpha = 0.15f), AppleShapes.pill)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                    .appleClickable(onWeatherClick)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.clock_indoor_temp_format, temperatures.indoorMin, temperatures.indoorMax), style = AppleTypography.bodySmall.copy(fontSize = 15.sp), color = AppleColors.primary)
                Text(stringResource(R.string.clock_outdoor_temp_format, temperatures.outdoor.takeUnless { it == "—" } ?: weather.temp), style = AppleTypography.bodySmall.copy(fontSize = 15.sp), color = AppleColors.secondary)
            }
            if (!connected && lastUpdateAt > 0L) {
                Spacer(Modifier.height(8.dp * clockTheme.elementSpacing))
                Box(Modifier.graphicsLayer { alpha = secondaryAlpha }) {
                    StaleBanner(lastUpdateAt = lastUpdateAt)
                }
            }
        }
    }
}

/** The bottom chip tray: the "voir plus" toggle plus up to 3 (collapsed) or 9 (expanded) chips. */
@Composable
fun ClockTray(
    chips: List<LauncherChip>,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
    onChipClick: (LauncherChip) -> Unit,
    onChipLongPress: (LauncherChip) -> Unit,
    selectedChipKey: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp.scaled())
            .padding(bottom = 36.dp.scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp.scaled()),
    ) {
        if (chips.size > 3) {
            Row(
                modifier = Modifier.clip(AppleShapes.pill)
                    .appleClickable { onPillsExpandedChange(!pillsExpanded) }
                    .padding(horizontal = 12.dp.scaled(), vertical = 3.dp.scaled()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (pillsExpanded) stringResource(R.string.clock_collapse_pills) else stringResource(R.string.clock_expand_pills),
                    style = AppleTypography.bodySmall.copy(fontSize = 13.sp.scaled()),
                    color = AppleColors.secondary.copy(alpha = 0.62f),
                )
                Icon(
                    if (pillsExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                    contentDescription = if (pillsExpanded) stringResource(R.string.clock_collapse_content_desc) else stringResource(R.string.clock_expand_content_desc),
                    tint = AppleColors.secondary.copy(alpha = 0.62f),
                )
            }
        }
        val visible = if (pillsExpanded) chips.take(9) else chips.take(3)
        visible.chunked(3).forEach { rowChips ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp.scaled(), Alignment.CenterHorizontally)) {
                rowChips.forEach { chip ->
                    StatusChip(chip, selected = chip.id == selectedChipKey, onClick = { onChipClick(chip) }, onLongPress = { onChipLongPress(chip) })
                }
            }
        }
    }
}

@Composable
private fun StaleBanner(lastUpdateAt: Long) {
    val elapsed by produceState(initialValue = 0L, lastUpdateAt) {
        while (true) {
            value = (System.currentTimeMillis() - lastUpdateAt) / 1000L
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val ago = if (elapsed < 60) "${elapsed}s" else "${elapsed / 60}min"
    Row(
        modifier = Modifier.clip(AppleShapes.pill)
            .background(Color(0x33FF9F0A), AppleShapes.pill)
            .border(0.5.dp, Color(0x66FF9F0A), AppleShapes.pill)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            stringResource(R.string.clock_stale_banner_format, ago),
            style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
            color = Color(0xFFFFC062),
        )
    }
}

@Composable
private fun rememberClock(pattern: String) = produceState(initialValue = format(pattern), pattern) {
    while (true) {
        value = format(pattern)
        kotlinx.coroutines.delay(15_000L)
    }
}

private fun format(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

private fun titleCase(value: String): String =
    value.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

@Preview(widthDp = 640, heightDp = 400)
@Composable
private fun ClockScreenPreview() {
    PortalTheme {
        ClockScreen(
            backgroundMode = "neutral",
            weather = WeatherUi(temp = "21°", indoorTemp = "18°", city = "Nantes", condition = "Nuageux", glyph = WeatherGlyph()),
            temperatures = TemperatureSummary("18°", "22°", "21°"),
            chips = listOf(
                LauncherChip("washer", "washer", "Machine", "Rinçage", "active"),
                LauncherChip("vacuum", "vacuum", "Aspirateur", "Nettoyage", "active"),
                LauncherChip("doors", "door", "Portes", "Fermées", "ok"),
                LauncherChip("windows", "window", "Fenêtres", "Fermées", "ok"),
                LauncherChip("lock", "lock", "Serrure", "Verrouillée", "ok"),
                LauncherChip("air_q", "air", "Qualité d'air", "Bonne", "ok"),
            ),
            onTap = {},
            onLongPress = {},
            pillsExpanded = false,
            onPillsExpandedChange = {}
        )
    }
}
