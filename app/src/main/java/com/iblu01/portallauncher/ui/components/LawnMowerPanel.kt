package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.PortalThreeWayControl
import com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Capability-gated mower transport; unsupported HA commands remain visibly disabled. */
@Composable
internal fun LawnMowerControl(entity: HaEntity, modifier: Modifier = Modifier) {
    val contract = remember(entity) { entity.toGenericControlContract() }
    val callService = LocalCallService.current
    val control: @Composable (Modifier) -> Unit = { area -> Box(area, contentAlignment = Alignment.Center) {
        PortalThreeWayControl(
            leadingIcon = Icons.Outlined.PlayArrow,
            leadingContentDescription = "Démarrer",
            onLeadingClick = { callService("lawn_mower", "start_mowing", entity.entityId) },
            leadingLabel = "Démarrer",
            leadingEnabled = "start_mowing" in contract.actions,
            centerIcon = Icons.Outlined.Pause,
            centerContentDescription = "Pause",
            onCenterClick = { callService("lawn_mower", "pause", entity.entityId) },
            centerEnabled = "pause" in contract.actions,
            trailingIcon = Icons.Outlined.Home,
            trailingContentDescription = "Retour base",
            onTrailingClick = { callService("lawn_mower", "dock", entity.entityId) },
            trailingLabel = "Base",
            trailingEnabled = "dock" in contract.actions,
            size = if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) ThreeWayControlSize.Large else ThreeWayControlSize.Regular,
        )
    } }
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        AdaptivePanelSplit(
            modifier = modifier.fillMaxSize(),
            primaryWeight = 0.58f,
            primary = control,
            secondary = { area ->
                Column(area, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(entity.state.replace('_', ' ').replaceFirstChar(Char::uppercase), style = AppleTypography.headlineLarge, color = AppleColors.primary)
                    Text(entity.attributes.optString("activity").replace('_', ' '), style = AppleTypography.bodyLarge, color = AppleColors.secondary)
                }
            },
        )
    } else control(modifier.fillMaxWidth())
}
