package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/** Side panel opened from the clock's temperature pill: current condition + hourly & daily forecast. */
@Composable
fun WeatherPanel(
    weather: WeatherUi,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelInset = if (fullScreen) 0.dp else {
            (minOf(maxWidth, maxHeight) * 0.03f).coerceIn(10.dp, 20.dp)
        }
        val panelShape = AppleShapes.panel
        Box(
            modifier = Modifier.fillMaxSize().padding(panelInset).clip(panelShape)
                .background(Color.Black.copy(alpha = 0.72f))
                .then(
                    if (fullScreen) Modifier
                    else Modifier.border(0.5.dp, AppleColors.frostedBorder, panelShape)
                ),
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.16f),
                        0.48f to Color.Black.copy(alpha = 0.54f),
                        1f to Color.Black.copy(alpha = 0.94f),
                    )
                )
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth > maxHeight
                val shortEdge = minOf(maxWidth, maxHeight)
                val outerInset = (shortEdge * 0.04f).coerceIn(14.dp, 28.dp)
                val sectionGap = (shortEdge * 0.045f).coerceIn(14.dp, 30.dp)
                val disclosure = weatherDisclosureFor(maxWidth.value, maxHeight.value)
                val summaryIconSize = if (disclosure.emphasizeSummary) 92.dp else (shortEdge * 0.18f).coerceIn(56.dp, 104.dp)
                val temperatureSize = if (disclosure.emphasizeSummary) 84.sp else (shortEdge.value * 0.13f).coerceIn(42f, 72f).sp

                Column(Modifier.fillMaxSize().padding(outerInset)) {
                    PanelHeader(
                        title = stringResource(R.string.weather_panel_title),
                        onNavigation = onDismiss,
                        navigationIcon = Icons.Filled.Close,
                        navigationContentDescription = stringResource(R.string.weather_close_desc),
                    )
                    Spacer(Modifier.height((shortEdge * 0.035f).coerceIn(10.dp, 22.dp)))
                    if (wide) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(sectionGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WeatherSummary(
                                weather = weather,
                                iconSize = summaryIconSize,
                                temperatureSize = temperatureSize,
                                showCondition = disclosure.showCondition,
                                stacked = disclosure.emphasizeSummary,
                                modifier = Modifier.weight(0.38f),
                            )
                            Column(
                                modifier = Modifier.weight(0.62f).fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(sectionGap),
                            ) {
                                WeatherForecastSections(weather, disclosure, compactRows = true)
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(sectionGap),
                        ) {
                            WeatherSummary(weather, summaryIconSize, temperatureSize, disclosure.showCondition, stacked = false)
                            WeatherForecastSections(weather, disclosure, compactRows = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSummary(
    weather: WeatherUi,
    iconSize: androidx.compose.ui.unit.Dp,
    temperatureSize: androidx.compose.ui.unit.TextUnit,
    showCondition: Boolean,
    stacked: Boolean,
    modifier: Modifier = Modifier,
) {
    if (stacked) {
        Column(
            modifier = modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                weather.temp,
                style = AppleTypography.displayLarge.copy(
                    fontSize = temperatureSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AppleColors.primary,
            )
            Spacer(Modifier.height(12.dp))
            WeatherIcon(weather.glyph, Modifier.size(iconSize))
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((iconSize * 0.22f).coerceIn(12.dp, 22.dp)),
        ) {
            WeatherIcon(weather.glyph, Modifier.size(iconSize))
            Column {
                Text(
                    weather.temp,
                    style = AppleTypography.displayLarge.copy(
                        fontSize = temperatureSize,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = AppleColors.primary,
                )
                if (showCondition && weather.condition.isNotBlank()) {
                    Text(weather.condition, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
                }
            }
        }
    }
}

internal data class WeatherDisclosure(
    val hourlyCount: Int,
    val dailyCount: Int,
    val showCondition: Boolean,
    val emphasizeSummary: Boolean,
)

/** Progressive disclosure based on the panel's actual space, not a hard-coded device model. */
internal fun weatherDisclosureFor(widthDp: Float, heightDp: Float): WeatherDisclosure = when {
    heightDp <= 420f -> WeatherDisclosure(hourlyCount = 6, dailyCount = 3, showCondition = false, emphasizeSummary = true)
    heightDp <= 560f || widthDp <= 480f -> WeatherDisclosure(hourlyCount = 8, dailyCount = 4, showCondition = true, emphasizeSummary = false)
    else -> WeatherDisclosure(hourlyCount = 12, dailyCount = 7, showCondition = true, emphasizeSummary = false)
}

@Composable
private fun WeatherForecastSections(weather: WeatherUi, disclosure: WeatherDisclosure, compactRows: Boolean) {
    if (weather.hourly.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.weather_section_hourly),
                style = AppleTypography.labelSmall.copy(fontSize = if (disclosure.emphasizeSummary) 14.sp else 12.sp),
                color = AppleColors.tertiary,
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(if (compactRows) 14.dp else 18.dp),
            ) {
                weather.hourly.take(disclosure.hourlyCount).forEach { HourTile(it, disclosure.emphasizeSummary) }
            }
        }
    }
    if (weather.daily.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.weather_section_daily),
                style = AppleTypography.labelSmall.copy(fontSize = if (disclosure.emphasizeSummary) 14.sp else 12.sp),
                color = AppleColors.tertiary,
            )
            weather.daily.take(disclosure.dailyCount).forEach { DayRow(it, disclosure.emphasizeSummary) }
        }
    }
}

@Composable
private fun HourTile(p: ForecastPoint, enlarged: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (enlarged) 7.dp else 6.dp)) {
        Text(forecastPointLabel(p.datetime, hourly = true), style = AppleTypography.bodySmall.copy(fontSize = if (enlarged) 14.sp else 12.sp), color = AppleColors.secondary)
        WeatherIcon(weatherGlyph(p.condition, night = false), Modifier.size(if (enlarged) 40.dp else 30.dp))
        Text("${p.temp.roundToInt()}°", style = AppleTypography.bodyLarge.copy(fontSize = if (enlarged) 20.sp else 17.sp), color = AppleColors.primary)
    }
}

@Composable
private fun DayRow(p: ForecastPoint, enlarged: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(forecastPointLabel(p.datetime, hourly = false), style = AppleTypography.bodyLarge.copy(fontSize = if (enlarged) 19.sp else 17.sp), color = AppleColors.primary, modifier = Modifier.weight(1f))
        WeatherIcon(weatherGlyph(p.condition, night = false), Modifier.size(if (enlarged) 38.dp else 28.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            p.tempLow?.let { "${p.temp.roundToInt()}° / ${it.roundToInt()}°" } ?: "${p.temp.roundToInt()}°",
            style = AppleTypography.bodyLarge.copy(fontSize = if (enlarged) 19.sp else 17.sp), color = AppleColors.secondary,
        )
    }
}

private fun forecastPointLabel(datetime: String, hourly: Boolean): String = runCatching {
    val odt = java.time.OffsetDateTime.parse(datetime)
    if (hourly) "${odt.hour}h"
    else odt.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }
}.getOrDefault("")
