package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.LocalHaStates
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/**
 * Live view of a single HA entity for a control panel. Reads [LocalHaStates] (a stable per-entity
 * store), so panels always reflect real device state (position, target temperature, alarm
 * code_format, …) without touching the PillHub singleton. The `[]` read subscribes the calling
 * recompose scope to *this entity only* — an unrelated push invalidates nothing here.
 */
@Composable
fun rememberEntity(entityId: String): HaEntity? = LocalHaStates.current[entityId]

/** True when the entity is missing or reporting an unusable state (unavailable/unknown). */
fun HaEntity?.isUnavailable(): Boolean = this == null || state.lowercase() in setOf("unavailable", "unknown", "none", "")

/** Shown by a control panel when its entity is missing or unavailable, instead of a blank body. */
@Composable
fun PanelUnavailable(message: String = stringResource(R.string.panel_device_unavailable)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = AppleColors.secondary, modifier = Modifier.size(20.dp))
        Text(message, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
    }
}

/** `supported_features` bitmask flags, shared across accessory panels. */
object AlarmFeature { const val ARM_HOME = 1; const val ARM_AWAY = 2; const val ARM_NIGHT = 4; const val TRIGGER = 8; const val ARM_CUSTOM_BYPASS = 16; const val ARM_VACATION = 32 }
object CoverFeature { const val OPEN = 1; const val CLOSE = 2; const val SET_POSITION = 4; const val STOP = 8 }
object FanFeature { const val SET_SPEED = 1; const val OSCILLATE = 2; const val DIRECTION = 4; const val PRESET_MODE = 8 }
object VacuumFeature { const val PAUSE = 4; const val STOP = 8; const val RETURN_HOME = 16; const val BATTERY = 64; const val STATUS = 128; const val LOCATE = 512; const val CLEAN_SPOT = 1024; const val START = 8192 }

/** True when the entity advertises [bit] in its `supported_features` attribute. */
fun HaEntity.supports(bit: Int): Boolean = (attributes.optInt("supported_features") and bit) != 0

/**
 * Whether an alarm action needs a code, from the HA entity model:
 * - `code_format` null/blank → no code is ever used.
 * - disarm always needs the code when a format is set (HA has no per-disarm flag).
 * - arming needs it only when `code_arm_required` is true (default true).
 */
fun HaEntity.alarmCodeRequired(arming: Boolean): Boolean {
    val format = attributes.optString("code_format")
    if (format.isBlank() || format == "null") return false
    return !arming || attributes.optBoolean("code_arm_required", true)
}

/** "number" → ten-key pad; "text" → free text. Null/blank when no code is used. */
fun HaEntity.alarmCodeFormat(): String? = attributes.optString("code_format").takeIf { it.isNotBlank() && it != "null" }
