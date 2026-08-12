package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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

/** Capability-gated mower transport; unsupported HA commands remain visibly disabled. */
@Composable
internal fun LawnMowerControl(entity: HaEntity, modifier: Modifier = Modifier) {
    val contract = remember(entity) { entity.toGenericControlContract() }
    val callService = LocalCallService.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
        )
    }
}
