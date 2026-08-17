package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var forecastPage by remember { mutableStateOf(ForecastPage.HOURLY) }
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
                val showForecastSwitcher = disclosure.emphasizeSummary && weather.hourly.isNotEmpty() && weather.daily.isNotEmpty()

                Column(Modifier.fillMaxSize().padding(outerInset)) {
                    WeatherPanelHeader(
                        title = stringResource(R.string.weather_panel_title),
                        onDismiss = onDismiss,
                        page = forecastPage,
                        showSwitcher = showForecastSwitcher,
                        onPageChange = { forecastPage = it },
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
                                modifier = Modifier.weight(0.62f).fillMaxHeight().then(
                                    if (disclosure.emphasizeSummary) Modifier
                                    else Modifier.verticalScroll(rememberScrollState())
                                ),
                                verticalArrangement = Arrangement.spacedBy(sectionGap),
                            ) {
                                WeatherForecastSections(weather, disclosure, compactRows = true, page = forecastPage)
                            }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(sectionGap),
                        ) {
                            WeatherSummary(weather, summaryIconSize, temperatureSize, disclosure.showCondition, stacked = false)
                            WeatherForecastSections(weather, disclosure, compactRows = false, page = forecastPage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherPanelHeader(
    title: String,
    onDismiss: () -> Unit,
    page: ForecastPage,
    showSwitcher: Boolean,
    onPageChange: (ForecastPage) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = AppleTypography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            color = AppleColors.primary,
            modifier = Modifier.weight(1f),
        )
        if (showSwitcher) {
            ForecastPageSwitcher(page = page, onPageChange = onPageChange)
            Spacer(Modifier.size(12.dp))
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AppleColors.frostedFill)
                .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.weather_close_desc),
                tint = AppleColors.primary,
                modifier = Modifier.size(24.dp),
            )
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
            WeatherIcon(weather.glyph, Modifier.size(iconSize))
            Spacer(Modifier.height(8.dp))
            Text(
                weather.temp,
                style = AppleTypography.displayLarge.copy(
                    fontSize = temperatureSize,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = AppleColors.primary,
            )
            weather.daily.firstOrNull()?.tempLow?.let { minimum ->
                Text(
                    stringResource(
                        R.string.weather_today_range_format,
                        minimum.roundToInt(),
                        weather.daily.first().temp.roundToInt(),
                    ),
                    style = AppleTypography.bodyLarge.copy(fontSize = 17.sp),
                    color = AppleColors.secondary,
                )
            }
            if (weather.city.isNotBlank()) {
                Text(
                    weather.city,
                    style = AppleTypography.bodyLarge.copy(fontSize = 17.sp),
                    color = AppleColors.secondary,
                )
            }
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
                if (weather.city.isNotBlank()) {
                    Text(weather.city, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
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
private fun WeatherForecastSections(
    weather: WeatherUi,
    disclosure: WeatherDisclosure,
    compactRows: Boolean,
    page: ForecastPage,
) {
    if (disclosure.emphasizeSummary && weather.hourly.isNotEmpty() && weather.daily.isNotEmpty()) {
        CompactForecastSwitcher(weather, disclosure, page)
        return
    }
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

private enum class ForecastPage { HOURLY, DAILY }

@Composable
private fun CompactForecastSwitcher(weather: WeatherUi, disclosure: WeatherDisclosure, page: ForecastPage) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
            val visibleItems = when (page) {
                ForecastPage.HOURLY -> disclosure.hourlyCount
                ForecastPage.DAILY -> disclosure.dailyCount
            }
            val itemWidth = maxWidth / visibleItems
            val itemHeight = maxHeight
            Row(
                modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (page) {
                    ForecastPage.HOURLY -> weather.hourly.forEach { point ->
                        Box(Modifier.size(width = itemWidth, height = itemHeight), contentAlignment = Alignment.Center) {
                            HourTile(point, enlarged = true)
                        }
                    }
                    ForecastPage.DAILY -> weather.daily.forEach { point ->
                        Box(Modifier.size(width = itemWidth, height = itemHeight), contentAlignment = Alignment.Center) {
                            DayTile(point)
                        }
                    }
                }
            }
    }
}

@Composable
private fun ForecastPageSwitcher(page: ForecastPage, onPageChange: (ForecastPage) -> Unit) {
    Row(
        modifier = Modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .padding(3.dp),
    ) {
        ForecastPageButton(
            label = stringResource(R.string.weather_tab_hourly),
            selected = page == ForecastPage.HOURLY,
            onClick = { onPageChange(ForecastPage.HOURLY) },
        )
        ForecastPageButton(
            label = stringResource(R.string.weather_tab_daily),
            selected = page == ForecastPage.DAILY,
            onClick = { onPageChange(ForecastPage.DAILY) },
        )
    }
}

@Composable
private fun ForecastPageButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(AppleShapes.pill)
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppleTypography.labelSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
            color = if (selected) AppleColors.primary else AppleColors.secondary,
        )
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
private fun DayTile(p: ForecastPoint) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            forecastPointLabel(p.datetime, hourly = false),
            style = AppleTypography.bodyLarge.copy(fontSize = 18.sp),
            color = AppleColors.primary,
        )
        WeatherIcon(weatherGlyph(p.condition, night = false), Modifier.size(54.dp))
        Text(
            p.tempLow?.let { "${p.temp.roundToInt()}° / ${it.roundToInt()}°" } ?: "${p.temp.roundToInt()}°",
            style = AppleTypography.bodyLarge.copy(fontSize = 20.sp),
            color = AppleColors.secondary,
        )
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
