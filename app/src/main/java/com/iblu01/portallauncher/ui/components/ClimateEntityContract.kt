package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity

/** Home Assistant `ClimateEntityFeature` flags used by the thermostat adapter. */
object ClimateFeature {
    const val TARGET_TEMPERATURE = 1
    const val TARGET_TEMPERATURE_RANGE = 2
}

/**
 * UI-neutral view of Home Assistant's climate contract. The panel consumes this model instead of
 * knowing which optional attributes a particular thermostat integration happens to expose.
 */
data class ClimateEntityContract(
    val hvacMode: String,
    val hvacAction: String?,
    val availableModes: List<String>,
    val currentTemperature: Float?,
    val targetTemperature: Float?,
    val targetLow: Float?,
    val targetHigh: Float?,
    val minimumTemperature: Float,
    val maximumTemperature: Float,
    val temperatureStep: Float,
    val temperatureUnit: String,
    val supportsSingleTarget: Boolean,
    val supportsTargetRange: Boolean,
) {
    val hasTemperatureControl: Boolean get() = supportsSingleTarget || supportsTargetRange
}

fun HaEntity.toClimateContract(): ClimateEntityContract {
    fun number(name: String): Float? = attributes.optDouble(name, Double.NaN)
        .takeUnless(Double::isNaN)
        ?.toFloat()

    val featureMask = attributes.optInt("supported_features", 0)
    val target = number("temperature")
    val low = number("target_temp_low")
    val high = number("target_temp_high")
    val modesArray = attributes.optJSONArray("hvac_modes")
    val modes = if (modesArray == null) emptyList() else buildList {
        for (index in 0 until modesArray.length()) {
            modesArray.optString(index).trim().lowercase().takeIf(String::isNotBlank)?.let(::add)
        }
    }.distinct()

    val min = number("min_temp") ?: 7f
    val max = (number("max_temp") ?: 35f).coerceAtLeast(min + 1f)
    return ClimateEntityContract(
        hvacMode = state.trim().lowercase(),
        hvacAction = attributes.optString("hvac_action").trim().lowercase().takeIf(String::isNotBlank),
        availableModes = modes,
        currentTemperature = number("current_temperature"),
        targetTemperature = target,
        targetLow = low,
        targetHigh = high,
        minimumTemperature = min,
        maximumTemperature = max,
        temperatureStep = (number("target_temp_step") ?: 0.5f).coerceAtLeast(0.1f),
        temperatureUnit = attributes.optString("temperature_unit")
            .ifBlank { attributes.optString("unit_of_measurement") }
            .ifBlank { "°" },
        // Some integrations omit the feature mask but expose the corresponding attributes.
        supportsSingleTarget = featureMask and ClimateFeature.TARGET_TEMPERATURE != 0 || target != null,
        supportsTargetRange = featureMask and ClimateFeature.TARGET_TEMPERATURE_RANGE != 0 || (low != null && high != null),
    )
}
