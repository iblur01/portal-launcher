package com.iblu01.portallauncher.ui.components

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillRepository
import com.iblu01.portallauncher.TemperatureUnit
import com.iblu01.portallauncher.toTemperatureUnit
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors
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
    val temperatureUnit: String = TemperatureUnit.CELSIUS.symbol,
)

/**
 * Derives [WeatherUi] from the HA `weather.*` entity and its subscribed forecasts,
 * observed through [PillRepository]'s lightweight change notifier.
 */
class WeatherController(private val context: Context, private val pills: PillRepository) {
    var state by mutableStateOf(WeatherUi())
        private set

    private val listener = PillRepository.Listener { rebuild() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cityExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "weather-city").apply { isDaemon = true }
    }
    private var resolvedCity = ""
    private var attemptedLocationKey: String? = null

    fun start() { pills.addListener(listener) }
    fun stop() { pills.removeListener(listener) }
    fun refreshNow() = rebuild()

    private fun rebuild() {
        val entity = pills.weatherEntityId?.let { pills.latestStates[it] }
        if (entity == null) {
            state = WeatherUi()
            return
        }
        val condition = entity.state
        val temp = entity.attributes.optDouble("temperature").let { if (it.isNaN()) null else it }
        val unit = entity.attributes.optString("temperature_unit").toTemperatureUnit()
            ?: TemperatureUnit.CELSIUS
        state = WeatherUi(
            temp = temp?.let { "${it.roundToInt()}${unit.symbol}" } ?: "--${unit.symbol}",
            city = explicitWeatherCity(entity).ifBlank { resolvedCity },
            condition = weatherLabel(context, condition),
            glyph = weatherGlyph(condition, isNight()),
            hourly = pills.hourlyForecast,
            daily = pills.dailyForecast,
            temperatureUnit = unit.symbol,
        )
        // Check the location key even while HA exposes an explicit city. If that attribute is
        // removed later, the reverse-geocoded fallback must already belong to this server/home.
        resolveHomeCity()
    }

    private fun isNight(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h < 7 || h >= 21
    }

    private fun resolveHomeCity() {
        val home = pills.latestStates["zone.home"] ?: return
        val latitude = home.attributes.optDouble("latitude", Double.NaN)
        val longitude = home.attributes.optDouble("longitude", Double.NaN)
        if (latitude.isNaN() || longitude.isNaN()) return
        val key = "${pills.connectionGeneration}:$latitude,$longitude"
        if (attemptedLocationKey == key) return
        if (attemptedLocationKey != null) {
            resolvedCity = ""
            if (explicitWeatherCity(pills.weatherEntityId?.let { pills.latestStates[it] } ?: return).isBlank()) {
                state = state.copy(city = "")
            }
        }
        attemptedLocationKey = key
        cityExecutor.execute {
            val city = reverseGeocodeCity(context, latitude, longitude)
            if (city.isNotBlank()) mainHandler.post {
                if (attemptedLocationKey != key) return@post
                resolvedCity = city
                val currentEntity = pills.weatherEntityId?.let { pills.latestStates[it] }
                if (currentEntity == null || explicitWeatherCity(currentEntity).isBlank()) {
                    state = state.copy(city = city)
                }
            }
        }
    }
}

/** Only real location attributes qualify; an entity name such as "Forecast Home" does not. */
internal fun explicitWeatherCity(entity: HaEntity): String =
    sequenceOf("city", "location")
        .map { entity.attributes.optString(it).trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

@Suppress("DEPRECATION")
internal fun reverseGeocodeCity(context: Context, latitude: Double, longitude: Double): String {
    if (!Geocoder.isPresent()) return ""
    return runCatching {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { address ->
                sequenceOf(address.locality, address.subAdminArea, address.adminArea)
                    .firstOrNull { !it.isNullOrBlank() }
                    .orEmpty()
            }
    }.getOrNull().orEmpty()
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
