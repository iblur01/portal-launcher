package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NightShelter
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.AccessoryGrid
import com.iblu01.portallauncher.ui.components.controls.AccessoryItem
import com.iblu01.portallauncher.ui.components.controls.PinKeypad
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource

private const val ALARM_DOMAIN = "alarm_control_panel"

/**
 * Alarm control. Arm options come from `supported_features`; whether a keypad is shown for
 * an action is decided from the entity's `code_format` / `code_arm_required` (see
 * [alarmCodeRequired]). While armed and a code is required the disarm keypad is shown
 * directly; triggered/pending states surface it prominently. A wrong code (HA leaves the
 * state unchanged) shakes the pad.
 */
@Composable
fun AlarmControl(chip: LauncherChip) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) { PanelUnavailable(); return }
    val state = entity.state.lowercase()
    val armed = state != "disarmed"
    val alerting = state == "triggered" || state == "pending"

    var pendingArm by remember(chip.entityId) { mutableStateOf<String?>(null) }

    // The armed/mode label is already shown in the panel header (chip.value), so it isn't
    // repeated here — this composable renders only the keypad / arm actions.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            // Armed + code required, or an arm action is awaiting its code → keypad.
            (armed && entity.alarmCodeRequired(arming = false)) || pendingArm != null -> {
                val service = pendingArm ?: "alarm_disarm"
                AlarmKeypad(
                    entityId = chip.entityId,
                    service = service,
                    currentState = state,
                    prompt = if (service == "alarm_disarm") stringResource(R.string.alarm_disarm_prompt) else stringResource(R.string.alarm_arm_prompt),
                    onCancel = if (pendingArm != null) ({ pendingArm = null }) else null,
                )
            }
            // Armed, no code needed → single disarm button.
            armed -> {
                DisarmButton(highlight = alerting) { callService(ALARM_DOMAIN, "alarm_disarm", chip.entityId) }
            }
            // Disarmed → arm options.
            else -> {
                val arm: (String) -> Unit = { svc ->
                    if (entity.alarmCodeRequired(arming = true)) pendingArm = svc
                    else callService(ALARM_DOMAIN, svc, chip.entityId)
                }
                AccessoryGrid(
                    items = ArmOption.entries
                        .filter { entity.supports(it.feature) }
                        .map { option ->
                            AccessoryItem(
                                id = option.service,
                                title = stringResource(option.labelRes),
                                icon = option.icon,
                                // Disarmed here, so no option is live — the tiles read as actions.
                                on = state == option.armedState,
                                onToggle = { arm(option.service) },
                            )
                        },
                )
            }
        }
    }
}

/** The arm actions an entity can expose, in the order they're offered. */
private enum class ArmOption(
    val feature: Int,
    val service: String,
    val armedState: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    AWAY(AlarmFeature.ARM_AWAY, "alarm_arm_away", "armed_away", R.string.alarm_mode_away, Icons.Outlined.Luggage),
    HOME(AlarmFeature.ARM_HOME, "alarm_arm_home", "armed_home", R.string.alarm_mode_home, Icons.Outlined.Home),
    NIGHT(AlarmFeature.ARM_NIGHT, "alarm_arm_night", "armed_night", R.string.alarm_mode_night, Icons.Outlined.Bedtime),
    VACATION(AlarmFeature.ARM_VACATION, "alarm_arm_vacation", "armed_vacation", R.string.alarm_mode_vacation, Icons.Outlined.NightShelter),
}

@Composable
private fun DisarmButton(highlight: Boolean, onClick: () -> Unit) {
    val color = if (highlight) AppleColors.error else AppleColors.accent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.pill)
            .background(color.copy(alpha = 0.16f), AppleShapes.pill)
            .border(0.5.dp, color.copy(alpha = 0.4f), AppleShapes.pill)
            .appleClickable(onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.alarm_disarm_button), style = AppleTypography.titleMedium.copy(fontSize = 17.sp), color = color)
    }
}

/**
 * The shared [PinKeypad] wired to an alarm service call. HA never tells us a code was wrong —
 * it simply leaves the state alone — so a wrong entry is inferred: submit, wait, and if the
 * state still hasn't moved, flag the pad, which shakes and clears itself.
 *
 * The code length is unknown (`code_format` only says number vs text), hence the variable-length
 * mode with a ✓ key.
 */
@Composable
private fun AlarmKeypad(
    entityId: String,
    service: String,
    currentState: String,
    prompt: String,
    onCancel: (() -> Unit)?,
) {
    val callService = LocalCallService.current
    var submitCount by remember(service) { mutableIntStateOf(0) }
    var wrongCode by remember(service) { mutableStateOf(false) }
    val stateAtSubmit = remember { mutableStateOf("") }
    val liveState by rememberUpdatedState(currentState)

    LaunchedEffect(submitCount) {
        if (submitCount > 0) {
            kotlinx.coroutines.delay(2600)
            if (liveState == stateAtSubmit.value) wrongCode = true
        }
    }

    PinKeypad(
        onSubmit = { code ->
            stateAtSubmit.value = liveState
            callService(ALARM_DOMAIN, service, entityId, mapOf("code" to code))
            submitCount++
        },
        codeLength = 0,
        subtitle = prompt,
        error = wrongCode,
        onErrorConsumed = { wrongCode = false },
        onCancel = onCancel,
    )
}
