package com.iblu01.portallauncher.ui.components

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.PillRepository
import java.util.Calendar
import kotlin.math.roundToInt

/** UI snapshot shown on the clock screen, sourced from the Home Assistant weather entity. */
data class WeatherUi(
    val temp: String = "--°",
    val indoorTemp: String = "22°",
    val city: String = "",
    val condition: String = "",
    val glyph: WeatherGlyph = WeatherGlyph(),
    val hourly: List<ForecastPoint> = emptyList(),
    val daily: List<ForecastPoint> = emptyList(),
)

/**
 * Derives [WeatherUi] from the HA `weather.*` entity and its subscribed forecasts,
 * observed through [PillRepository]'s lightweight change notifier.
 */
class WeatherController(private val context: Context, private val pills: PillRepository) {
    var state by mutableStateOf(WeatherUi())
        private set

    private val listener = PillRepository.Listener { rebuild() }

    fun start() { pills.addListener(listener) }
    fun stop() { pills.removeListener(listener) }
    fun refreshNow() = rebuild()

    private fun rebuild() {
        val entity = pills.weatherEntityId?.let { pills.latestStates[it] } ?: return
        val condition = entity.state
        val temp = entity.attributes.optDouble("temperature").let { if (it.isNaN()) null else it }
        state = WeatherUi(
            temp = temp?.let { "${it.roundToInt()}°" } ?: "--°",
            condition = weatherLabel(context, condition),
            glyph = weatherGlyph(condition, isNight()),
            hourly = pills.hourlyForecast,
            daily = pills.dailyForecast,
        )
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 7 || h >= 21
    }
}

/** Home Assistant condition → bundled Meteocons asset. */
fun weatherGlyph(condition: String, night: Boolean): WeatherGlyph = when (condition.lowercase()) {
    "sunny", "clear" -> WeatherGlyph(if (night) "clear-night" else "clear-day")
    "clear-night" -> WeatherGlyph("clear-night")
    "partlycloudy" -> WeatherGlyph(if (night) "partly-cloudy-night" else "partly-cloudy-day")
    "cloudy" -> WeatherGlyph("cloudy")
    "fog" -> WeatherGlyph("fog")
    "rainy" -> WeatherGlyph("rain")
    "pouring" -> WeatherGlyph("extreme-rain")
    "lightning", "lightning-rainy" -> WeatherGlyph(if (night) "thunderstorms-night" else "thunderstorms-day")
    "snowy" -> WeatherGlyph("snow")
    "snowy-rainy" -> WeatherGlyph("sleet")
    "hail" -> WeatherGlyph("hail")
    "windy", "windy-variant" -> WeatherGlyph("wind")
    else -> WeatherGlyph("not-available")
}

/** HA condition string → localized label. */
fun weatherLabel(context: Context, condition: String): String = when (condition.lowercase()) {
    "sunny", "clear" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_sunny)
    "clear-night" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_clear_night)
    "partlycloudy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_partly_cloudy)
    "cloudy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_cloudy)
    "fog" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_fog)
    "rainy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_rainy)
    "pouring" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_pouring)
    "lightning" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_lightning)
    "lightning-rainy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_lightning_rainy)
    "snowy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_snowy)
    "snowy-rainy" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_snowy_rainy)
    "hail" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_hail)
    "windy", "windy-variant" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_windy)
    "exceptional" -> context.getString(com.iblu01.portallauncher.R.string.weather_condition_exceptional)
    else -> condition.replaceFirstChar { it.uppercase() }
}
