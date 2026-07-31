package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

/**
 * Vacuum control: start / pause / stop / dock / locate, gated by the entity's
 * `supported_features`. Status is shown as a read-only row; battery lives in the shared header.
 */
@Composable
fun VacuumControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val cleaning = entity.state.lowercase() in setOf("cleaning", "returning")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PanelModeButton(stringResource(R.string.vacuum_button_start), Icons.Outlined.PlayArrow, cleaning) {
                callService("vacuum", "start", chip.entityId)
            }
            if (entity.supports(VacuumFeature.PAUSE)) {
                PanelModeButton(stringResource(R.string.vacuum_button_pause), Icons.Outlined.Pause, entity.state.equals("paused", true)) {
                    callService("vacuum", "pause", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.STOP)) {
                PanelModeButton(stringResource(R.string.vacuum_button_stop), Icons.Filled.Stop, false) {
                    callService("vacuum", "stop", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.RETURN_HOME)) {
                PanelModeButton(stringResource(R.string.vacuum_button_dock), Icons.Outlined.Home, entity.state.equals("returning", true)) {
                    callService("vacuum", "return_to_base", chip.entityId)
                }
            }
            if (entity.supports(VacuumFeature.LOCATE)) {
                PanelModeButton(stringResource(R.string.vacuum_button_locate), Icons.Outlined.MyLocation, false) {
                    callService("vacuum", "locate", chip.entityId)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        val status = entity.attributes.optString("status").ifBlank { chip.value }
        if (status.isNotBlank()) PanelDetailRow(PillDetail(stringResource(R.string.vacuum_detail_status), status))
        chip.details.forEach {
            Spacer(Modifier.height(8.dp))
            PanelDetailRow(it)
        }
    }
}
