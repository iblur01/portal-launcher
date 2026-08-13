package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Plumbing
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.PortalThreeWayControl
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.VerticalSwitch
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

@Composable
fun ValveControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val callService = LocalCallService.current
    val contract = remember(entity) { entity.toGenericControlContract() }
    val canOpen = "open_valve" in contract.actions
    val canClose = "close_valve" in contract.actions
    val canStop = "stop_valve" in contract.actions
    val canPosition = "set_valve_position" in contract.actions
    val hasPrimaryControl = canPosition || (canOpen && canClose && !canStop)
    var position by remember(entity.entityId, contract.value) { mutableFloatStateOf(contract.value ?: if (entity.state == "closed") 0f else 100f) }
    val openLabel = stringResource(R.string.valve_action_open)
    val closeLabel = stringResource(R.string.valve_action_close)

    val stateLabel = when (entity.state.lowercase()) {
                "opening" -> stringResource(R.string.valve_state_opening)
                "closing" -> stringResource(R.string.valve_state_closing)
                "open" -> stringResource(R.string.valve_state_open)
                else -> stringResource(R.string.valve_state_closed)
            }
    val primaryControl: @Composable (Modifier) -> Unit = { controlModifier ->
        if (canPosition) {
            BoxWithConstraints(controlModifier, contentAlignment = Alignment.Center) {
                val widthToHeightRatio = 96f / 240f
                val height = minOf(maxHeight, maxWidth / widthToHeightRatio, 300.dp)
                val width = height * widthToHeightRatio
                VerticalFillSlider(
                    value = position, onValueChange = { position = contract.normalized(it) },
                    onValueChangeFinished = { callService("valve", "set_valve_position", entity.entityId, mapOf("position" to contract.normalized(it).roundToInt())) },
                    valueRange = 0f..100f, hapticSteps = 20, icon = Icons.Outlined.WaterDrop,
                    label = { "${it.roundToInt()} %" }, accent = AppleColors.accent,
                    modifier = Modifier.size(width, height),
                )
            }
        } else if (canOpen && canClose && !canStop) {
            BoxWithConstraints(controlModifier, contentAlignment = Alignment.Center) {
                val widthToHeightRatio = 96f / 240f
                val controlHeight = minOf(maxHeight, maxWidth / widthToHeightRatio, 300.dp)
                val controlWidth = controlHeight * widthToHeightRatio
                VerticalSwitch(
                    checked = entity.state.lowercase() in setOf("open", "opening"),
                    onCheckedChange = { callService("valve", if (it) "open_valve" else "close_valve", entity.entityId) },
                    accent = AppleColors.accent, icon = { if (it) Icons.Outlined.Plumbing else Icons.Outlined.Block },
                    label = { if (it) openLabel else closeLabel },
                    modifier = Modifier.size(controlWidth, controlHeight),
                )
            }
        }
    }
    val actions: @Composable (Modifier) -> Unit = { actionModifier ->
        Column(actionModifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stateLabel, style = AppleTypography.headlineLarge, color = AppleColors.primary)
        if (canOpen || canStop || canClose) {
            Spacer(Modifier.height(16.dp))
            PortalThreeWayControl(
                leadingIcon = Icons.Outlined.Plumbing, leadingContentDescription = stringResource(R.string.valve_action_open),
                onLeadingClick = { callService("valve", "open_valve", entity.entityId) }, leadingLabel = stringResource(R.string.valve_action_open), leadingEnabled = canOpen,
                centerIcon = Icons.Outlined.Stop, centerContentDescription = stringResource(R.string.valve_action_stop),
                onCenterClick = { callService("valve", "stop_valve", entity.entityId) }, centerEnabled = canStop,
                trailingIcon = Icons.Outlined.Block, trailingContentDescription = stringResource(R.string.valve_action_close),
                onTrailingClick = { callService("valve", "close_valve", entity.entityId) }, trailingLabel = stringResource(R.string.valve_action_close), trailingEnabled = canClose,
                size = if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize.Large else com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize.Regular,
            )
        }
        }
    }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        if (hasPrimaryControl) {
            AdaptivePanelSplit(modifier, primary = { primaryControl(it) }, secondary = { actions(it) })
        } else {
            actions(modifier.fillMaxSize())
        }
    } else Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stateLabel, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Spacer(Modifier.height(14.dp))
        primaryControl(Modifier.fillMaxWidth(0.54f).weight(1f))
        if (canOpen || canStop || canClose) {
            Spacer(Modifier.height(16.dp))
            PortalThreeWayControl(
                leadingIcon = Icons.Outlined.Plumbing, leadingContentDescription = stringResource(R.string.valve_action_open),
                onLeadingClick = { callService("valve", "open_valve", entity.entityId) }, leadingLabel = stringResource(R.string.valve_action_open), leadingEnabled = canOpen,
                centerIcon = Icons.Outlined.Stop, centerContentDescription = stringResource(R.string.valve_action_stop),
                onCenterClick = { callService("valve", "stop_valve", entity.entityId) }, centerEnabled = canStop,
                trailingIcon = Icons.Outlined.Block, trailingContentDescription = stringResource(R.string.valve_action_close),
                onTrailingClick = { callService("valve", "close_valve", entity.entityId) }, trailingLabel = stringResource(R.string.valve_action_close), trailingEnabled = canClose,
            )
        }
    }
}
