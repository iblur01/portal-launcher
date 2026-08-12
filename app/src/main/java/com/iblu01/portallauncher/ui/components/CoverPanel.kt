package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
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
import com.iblu01.portallauncher.ui.components.controls.PortalThreeWayControl
import com.iblu01.portallauncher.ui.components.controls.FillOrigin
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

@Composable
fun CoverControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val state = entity.state.lowercase()
    val position = entity.attributes.optInt("current_position", -1)
    val canSetPosition = entity.supports(CoverFeature.SET_POSITION) && position in 0..100
    val canStop = entity.supports(CoverFeature.STOP)

    // While dragging, the finger owns the readout; otherwise it follows the entity.
    var sliderPos by remember(position) { mutableFloatStateOf(position.toFloat()) }

    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.weight(0.52f).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (canSetPosition) {
                    val ratio = 96f / 240f
                    val sliderHeight = minOf(maxHeight, maxWidth / ratio, 300.dp)
                    VerticalFillSlider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        onValueChangeFinished = {
                            callService("cover", "set_cover_position", chip.entityId, mapOf("position" to sliderPos.roundToInt()))
                        },
                        valueRange = 0f..100f,
                        origin = FillOrigin.TOP,
                        accent = AppleColors.active,
                        hapticSteps = 20,
                        modifier = Modifier.size(sliderHeight * ratio, sliderHeight),
                    )
                } else {
                    CoverStateLabel(state, position, chip.value)
                }
            }
            Column(
                Modifier.weight(0.48f).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (canSetPosition) CoverStateLabel(state, sliderPos.roundToInt(), chip.value)
                Spacer(Modifier.height(18.dp))
                CoverActions(chip.entityId, canStop)
                Spacer(Modifier.height(14.dp))
                chip.details.take(3).forEach { PanelDetailRow(it) }
            }
        }
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (canSetPosition) {
            BoxWithConstraints(
                // The slider lives in a centred viewport rather than consuming the whole panel.
                // 54% matches the visual footprint of the gallery control while still scaling
                // fluidly with the panel on different wall-tablet sizes.
                modifier = Modifier.fillMaxWidth(0.54f).weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                val widthToHeightRatio = 96f / 240f
                val sliderHeight = minOf(maxHeight, maxWidth / widthToHeightRatio)
                val sliderWidth = sliderHeight * widthToHeightRatio
                VerticalFillSlider(
                    value = sliderPos,
                    onValueChange = { sliderPos = it },
                    onValueChangeFinished = {
                        callService("cover", "set_cover_position", chip.entityId, mapOf("position" to sliderPos.roundToInt()))
                    },
                    valueRange = 0f..100f,
                    origin = FillOrigin.TOP,
                    accent = AppleColors.active,
                    hapticSteps = 20,
                    modifier = Modifier.size(sliderWidth, sliderHeight),
                )
            }
            Spacer(Modifier.height(20.dp))
        } else {
            Spacer(Modifier.height(4.dp))
            CoverStateLabel(state, position, chip.value)
            Spacer(Modifier.height(16.dp))
        }

        CoverActions(chip.entityId, canStop)

        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}

@Composable
private fun CoverStateLabel(state: String, position: Int, fallback: String) {
    Text(
        when {
            position in 0..100 -> "$position %"
            state == "open" -> stringResource(R.string.cover_state_open)
            state == "closed" -> stringResource(R.string.cover_state_closed)
            state == "opening" -> stringResource(R.string.cover_state_opening)
            state == "closing" -> stringResource(R.string.cover_state_closing)
            else -> fallback
        },
        style = AppleTypography.headlineLarge,
        color = AppleColors.primary,
    )
}

@Composable
private fun CoverActions(entityId: String, canStop: Boolean) {
    val callService = LocalCallService.current
    PortalThreeWayControl(
        leadingIcon = Icons.Outlined.KeyboardArrowDown,
        leadingContentDescription = stringResource(R.string.cover_button_close),
        leadingLabel = stringResource(R.string.cover_button_close),
        onLeadingClick = { callService("cover", "close_cover", entityId) },
        centerIcon = Icons.Filled.Pause.takeIf { canStop },
        centerContentDescription = stringResource(R.string.cover_button_stop).takeIf { canStop },
        onCenterClick = if (canStop) ({ callService("cover", "stop_cover", entityId) }) else null,
        trailingIcon = Icons.Outlined.KeyboardArrowUp,
        trailingContentDescription = stringResource(R.string.cover_button_open),
        trailingLabel = stringResource(R.string.cover_button_open),
        onTrailingClick = { callService("cover", "open_cover", entityId) },
        size = if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize.Large else com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize.Regular,
    )
}
