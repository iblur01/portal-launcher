package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun CoverControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val state = entity.state.lowercase()
    val position = entity.attributes.optInt("current_position", -1)
    val canSetPosition = entity.supports(CoverFeature.SET_POSITION) && position in 0..100

    // While dragging, the finger owns the readout; otherwise it follows the entity.
    var sliderPos by remember(position) { mutableFloatStateOf(position.toFloat()) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                canSetPosition -> "${sliderPos.roundToInt()} %"
                position in 0..100 -> "$position %"
                state == "open" -> stringResource(R.string.cover_state_open)
                state == "closed" -> stringResource(R.string.cover_state_closed)
                state == "opening" -> stringResource(R.string.cover_state_opening)
                state == "closing" -> stringResource(R.string.cover_state_closing)
                else -> chip.value
            },
            style = AppleTypography.headlineLarge,
            color = AppleColors.primary,
        )
        Spacer(Modifier.height(16.dp))

        if (canSetPosition) {
            VerticalFillSlider(
                value = sliderPos,
                onValueChange = { sliderPos = it },
                onValueChangeFinished = {
                    callService("cover", "set_cover_position", chip.entityId, mapOf("position" to sliderPos.roundToInt()))
                },
                valueRange = 0f..100f,
                accent = AppleColors.active,
                hapticSteps = 20,
                modifier = Modifier.height(240.dp).width(96.dp),
            )
            Spacer(Modifier.height(20.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GlassButton(
                label = stringResource(R.string.cover_button_open),
                icon = Icons.Outlined.KeyboardArrowUp,
                active = state == "opening",
                onClick = { callService("cover", "open_cover", chip.entityId) },
            )
            GlassButton(
                label = stringResource(R.string.cover_button_stop),
                icon = Icons.Filled.Stop,
                active = false,
                onClick = { callService("cover", "stop_cover", chip.entityId) },
            )
            GlassButton(
                label = stringResource(R.string.cover_button_close),
                icon = Icons.Outlined.KeyboardArrowDown,
                active = state == "closing",
                onClick = { callService("cover", "close_cover", chip.entityId) },
            )
        }

        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
