package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalSegmentedSelector
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

private enum class PurifierMode(val preset: String?, val labelRes: Int, val icon: ImageVector) {
    AUTO("auto", R.string.purifier_mode_auto, Icons.Outlined.AutoMode),
    SLEEP("sleep", R.string.purifier_mode_sleep, Icons.Outlined.Bedtime),
    MANUAL("manual", R.string.purifier_mode_manual, Icons.Outlined.Tune),
    PET("pet", R.string.purifier_mode_pet, Icons.Outlined.Pets),
    OFF(null, R.string.purifier_mode_off, Icons.Outlined.PowerSettingsNew),
}

@Composable
fun PurifierActions(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    val running = entity?.state?.equals("on", true) == true
    val currentPreset = entity?.attributes?.optString("preset_mode")?.takeIf { it.isNotBlank() }

    val currentMode = when {
        !running -> PurifierMode.OFF
        currentPreset != null -> PurifierMode.entries.firstOrNull { it.preset == currentPreset.lowercase() }
            ?: PurifierMode.MANUAL
        else -> PurifierMode.MANUAL
    }
    var optimisticMode by remember(chip.entityId) { mutableStateOf<PurifierMode?>(null) }

    LaunchedEffect(currentMode, optimisticMode) {
        val pending = optimisticMode ?: return@LaunchedEffect
        if (currentMode == pending) optimisticMode = null
        else {
            kotlinx.coroutines.delay(5000)
            optimisticMode = null
        }
    }

    val purifierLabels = mapOf(
        PurifierMode.AUTO to stringResource(R.string.purifier_mode_auto),
        PurifierMode.SLEEP to stringResource(R.string.purifier_mode_sleep),
        PurifierMode.MANUAL to stringResource(R.string.purifier_mode_manual),
        PurifierMode.PET to stringResource(R.string.purifier_mode_pet),
        PurifierMode.OFF to stringResource(R.string.purifier_mode_off),
    )

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalSegmentedSelector(
            options = PurifierMode.entries.toList(),
            selected = optimisticMode ?: currentMode,
            onSelect = { mode ->
                if (mode != currentMode) {
                    optimisticMode = mode
                    if (mode == PurifierMode.OFF) {
                        callService("fan", "turn_off", chip.entityId)
                    } else {
                        callService("fan", "set_preset_mode", chip.entityId, mapOf("preset_mode" to mode.preset!!))
                    }
                }
            },
            label = { purifierLabels[it]!! },
            icon = { it.icon },
            accent = AppleColors.active,
            isNeutral = { it == PurifierMode.OFF },
            enabled = entity != null,
            segmentHeight = 66.dp,
            segmentPadding = 4.dp,
            modifier = Modifier.width(88.dp),
        )
        if (chip.details.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chip.details.forEach { PanelDetailRow(it) }
            }
        }
    }
}
