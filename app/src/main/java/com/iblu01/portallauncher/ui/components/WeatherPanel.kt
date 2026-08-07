package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
fun WeatherPanel(weather: WeatherUi, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp)) {
        Box(
            modifier = Modifier.fillMaxSize().clip(AppleShapes.panel)
                .background(Color.Black.copy(alpha = 0.72f))
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
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
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp).verticalScroll(rememberScrollState()),
            ) {
                PanelHeader(
                    title = stringResource(R.string.weather_panel_title),
                    onNavigation = onDismiss,
                    navigationIcon = Icons.Filled.Close,
                    navigationContentDescription = stringResource(R.string.weather_close_desc),
                )

                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    WeatherIcon(weather.glyph, Modifier.size(64.dp))
                    Column {
                        Text(weather.temp, style = AppleTypography.displayLarge.copy(fontSize = 52.sp, fontWeight = FontWeight.SemiBold), color = AppleColors.primary)
                        Text(weather.condition, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
                    }
                }

                if (weather.hourly.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.weather_section_hourly), style = AppleTypography.labelSmall, color = AppleColors.tertiary)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        weather.hourly.take(12).forEach { HourTile(it) }
                    }
                }

                if (weather.daily.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    Text(stringResource(R.string.weather_section_daily), style = AppleTypography.labelSmall, color = AppleColors.tertiary)
                    Spacer(Modifier.height(6.dp))
                    weather.daily.take(7).forEach { DayRow(it) }
                }
            }
        }
    }
}

@Composable
private fun HourTile(p: ForecastPoint) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(forecastPointLabel(p.datetime, hourly = true), style = AppleTypography.bodySmall.copy(fontSize = 12.sp), color = AppleColors.secondary)
        WeatherIcon(weatherGlyph(p.condition, night = false), Modifier.size(30.dp))
        Text("${p.temp.roundToInt()}°", style = AppleTypography.bodyLarge, color = AppleColors.primary)
    }
}

@Composable
private fun DayRow(p: ForecastPoint) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(forecastPointLabel(p.datetime, hourly = false), style = AppleTypography.bodyLarge, color = AppleColors.primary, modifier = Modifier.weight(1f))
        WeatherIcon(weatherGlyph(p.condition, night = false), Modifier.size(28.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            p.tempLow?.let { "${p.temp.roundToInt()}° / ${it.roundToInt()}°" } ?: "${p.temp.roundToInt()}°",
            style = AppleTypography.bodyLarge, color = AppleColors.secondary,
        )
    }
}

private fun forecastPointLabel(datetime: String, hourly: Boolean): String = runCatching {
    val odt = java.time.OffsetDateTime.parse(datetime)
    if (hourly) "${odt.hour}h"
    else odt.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).replaceFirstChar { it.uppercase() }
}.getOrDefault("")
