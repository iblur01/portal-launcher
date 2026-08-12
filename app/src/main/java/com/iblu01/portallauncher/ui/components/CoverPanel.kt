package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Text(
                when {
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
        }

        PortalThreeWayControl(
            leadingIcon = Icons.Outlined.KeyboardArrowDown,
            leadingContentDescription = stringResource(R.string.cover_button_close),
            leadingLabel = stringResource(R.string.cover_button_close),
            onLeadingClick = { callService("cover", "close_cover", chip.entityId) },
            centerIcon = Icons.Filled.Pause.takeIf { canStop },
            centerContentDescription = stringResource(R.string.cover_button_stop).takeIf { canStop },
            onCenterClick = if (canStop) ({ callService("cover", "stop_cover", chip.entityId) }) else null,
            trailingIcon = Icons.Outlined.KeyboardArrowUp,
            trailingContentDescription = stringResource(R.string.cover_button_open),
            trailingLabel = stringResource(R.string.cover_button_open),
            onTrailingClick = { callService("cover", "open_cover", chip.entityId) },
        )

        Spacer(Modifier.height(12.dp))
        chip.details.forEach { PanelDetailRow(it) }
    }
}
