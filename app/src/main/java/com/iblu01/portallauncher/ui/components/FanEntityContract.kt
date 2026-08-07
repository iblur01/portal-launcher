package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity

sealed interface FanPrimaryControl {
    data object OnOff : FanPrimaryControl
    data class Percentage(val value: Int, val step: Int) : FanPrimaryControl
    data class Presets(val values: List<String>, val selected: String?) : FanPrimaryControl
}

data class FanEntityContract(
    val isOn: Boolean,
    val primaryControl: FanPrimaryControl,
    val supportsOscillation: Boolean,
    val isOscillating: Boolean,
)

/** Maps only capabilities and attributes defined by Home Assistant's fan entity contract. */
fun HaEntity.toFanContract(): FanEntityContract {
    val presetArray = attributes.optJSONArray("preset_modes")
    val presets = if (presetArray == null) emptyList() else buildList {
        for (index in 0 until presetArray.length()) {
            presetArray.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
        }
    }.distinct()
    val hasPercentage = supports(FanFeature.SET_SPEED) || attributes.has("percentage")
    val primary = when {
        presets.isNotEmpty() -> FanPrimaryControl.Presets(
            values = presets,
            selected = attributes.optString("preset_mode").takeIf { it.isNotBlank() },
        )
        hasPercentage -> FanPrimaryControl.Percentage(
            value = attributes.optInt("percentage", 0).coerceIn(0, 100),
            step = attributes.optDouble("percentage_step", 1.0).toInt().coerceIn(1, 100),
        )
        else -> FanPrimaryControl.OnOff
    }
    return FanEntityContract(
        isOn = state.equals("on", true),
        primaryControl = primary,
        supportsOscillation = supports(FanFeature.OSCILLATE) || attributes.has("oscillating"),
        isOscillating = attributes.optBoolean("oscillating", false),
    )
}
