package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import kotlin.math.round

private object HumidifierFeature { const val MODES = 1 }
private object WaterHeaterFeature { const val TARGET_TEMPERATURE = 1; const val OPERATION_MODE = 2; const val AWAY_MODE = 4; const val ON_OFF = 8 }
private object ValveFeature { const val OPEN = 1; const val CLOSE = 2; const val SET_POSITION = 4; const val STOP = 8 }
private object SirenFeature { const val TONES = 1; const val DURATION = 2; const val VOLUME_SET = 4 }
private object LawnMowerFeature { const val START_MOWING = 1; const val PAUSE = 2; const val DOCK = 4 }

/** Feature-gated UI contract for HA domains that share value, option and action controls. */
data class GenericHaEntityContract(
    val domain: String,
    val value: Float? = null,
    val min: Float = 0f,
    val max: Float = 100f,
    val step: Float = 1f,
    val unit: String = "",
    val options: List<String> = emptyList(),
    val selectedOption: String? = null,
    val actions: List<String> = emptyList(),
) {
    fun normalized(raw: Float): Float {
        val safeStep = step.takeIf { it > 0f } ?: 1f
        val snapped = min + round((raw - min) / safeStep) * safeStep
        return snapped.coerceIn(min, max)
    }
}

fun HaEntity.toGenericControlContract(): GenericHaEntityContract {
    val features = attributes.optInt("supported_features", 0)
    fun options(key: String): List<String> = attributes.optJSONArray(key)?.let { array ->
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.orEmpty()
    fun number(vararg keys: String): Float? = keys.firstNotNullOfOrNull { key ->
        if (attributes.has(key)) attributes.optDouble(key).takeUnless(Double::isNaN)?.toFloat() else null
    }
    return when (domain) {
        "number", "input_number" -> GenericHaEntityContract(domain, state.toFloatOrNull(),
            number("min") ?: 0f, number("max") ?: 100f, number("step") ?: 1f,
            attributes.optString("unit_of_measurement"), actions = listOf("set_value"))
        "select", "input_select" -> GenericHaEntityContract(domain, options = options("options"),
            selectedOption = state, actions = listOf("select_option"))
        "button", "input_button" -> GenericHaEntityContract(domain, actions = listOf("press"))
        "humidifier" -> GenericHaEntityContract(domain, number("humidity", "target_humidity"),
            number("min_humidity") ?: 0f, number("max_humidity") ?: 100f,
            number("target_humidity_step") ?: 1f, "%", options("available_modes"),
            attributes.optString("mode").takeIf(String::isNotBlank),
            buildList { add("turn_on"); add("turn_off"); add("set_humidity"); if (features and HumidifierFeature.MODES != 0) add("set_mode") })
        "water_heater" -> GenericHaEntityContract(domain, number("temperature", "target_temperature"),
            number("min_temp") ?: 0f, number("max_temp") ?: 100f, number("target_temperature_step") ?: 1f,
            attributes.optString("temperature_unit"), options("operation_list"),
            attributes.optString("current_operation").takeIf(String::isNotBlank),
            buildList { if (features and WaterHeaterFeature.TARGET_TEMPERATURE != 0) add("set_temperature"); if (features and WaterHeaterFeature.OPERATION_MODE != 0) add("set_operation_mode"); if (features and WaterHeaterFeature.AWAY_MODE != 0) add("set_away_mode"); if (features and WaterHeaterFeature.ON_OFF != 0) { add("turn_on"); add("turn_off") } })
        "valve" -> GenericHaEntityContract(domain, number("current_valve_position"), 0f, 100f, 1f, "%",
            actions = buildList { if (features and ValveFeature.OPEN != 0) add("open_valve"); if (features and ValveFeature.CLOSE != 0) add("close_valve"); if (features and ValveFeature.SET_POSITION != 0) add("set_valve_position"); if (features and ValveFeature.STOP != 0) add("stop_valve") })
        "siren" -> GenericHaEntityContract(domain, options = options("available_tones"),
            selectedOption = attributes.optString("tone").takeIf(String::isNotBlank),
            actions = buildList { add("turn_on"); add("turn_off"); if (features and SirenFeature.TONES != 0) add("tone"); if (features and SirenFeature.DURATION != 0) add("duration"); if (features and SirenFeature.VOLUME_SET != 0) add("volume_level") })
        "lawn_mower" -> GenericHaEntityContract(domain, actions = buildList { if (features and LawnMowerFeature.START_MOWING != 0) add("start_mowing"); if (features and LawnMowerFeature.PAUSE != 0) add("pause"); if (features and LawnMowerFeature.DOCK != 0) add("dock") })
        "camera" -> GenericHaEntityContract(domain, actions = buildList { if (features and 1 != 0) { add("turn_on"); add("turn_off") }; if (features and 2 != 0) add("stream") })
        else -> GenericHaEntityContract(domain)
    }
}
