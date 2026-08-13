package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material.icons.outlined.NightShelter
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.components.controls.AccessoryGrid
import com.iblu01.portallauncher.ui.components.controls.AccessoryItem
import com.iblu01.portallauncher.ui.components.controls.PinKeypad
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

private const val ALARM_DOMAIN = "alarm_control_panel"

/** Stable state routing for the alarm panel. No transient state falls back to "disarmed". */
internal enum class AlarmPanelPhase {
    DISARMED,
    ARMING,
    ARMED,
    PENDING,
    TRIGGERED,
    DISARMING,
    OTHER,
}

internal fun alarmPanelPhase(state: String): AlarmPanelPhase = when (state.lowercase()) {
    "disarmed" -> AlarmPanelPhase.DISARMED
    "arming" -> AlarmPanelPhase.ARMING
    "pending" -> AlarmPanelPhase.PENDING
    "triggered" -> AlarmPanelPhase.TRIGGERED
    "disarming" -> AlarmPanelPhase.DISARMING
    "armed_away", "armed_home", "armed_night", "armed_vacation", "armed_custom_bypass" ->
        AlarmPanelPhase.ARMED
    else -> AlarmPanelPhase.OTHER
}

/**
 * Alarm control split into explicit screens:
 * - disarmed: arming choices only (there is no fake selected "disabled" segment),
 * - armed/arming: the disarm keypad is immediately visible,
 * - pending/triggered: a dedicated incident card is kept above that keypad,
 * - disarming: progress feedback replaces controls until HA confirms the new state.
 */
@Composable
fun AlarmControl(chip: LauncherChip, modifier: Modifier = Modifier) {
    val callService = LocalCallService.current
    val entity = rememberEntity(chip.entityId)
    if (entity == null || entity.isUnavailable()) {
        PanelUnavailable()
        return
    }

    val state = entity.state.lowercase()
    val phase = alarmPanelPhase(state)
    var pendingArmOption by remember(chip.entityId) { mutableStateOf<ArmOption?>(null) }

    // The arming keypad is a local sub-screen. As soon as HA moves away from disarmed, the live
    // alarm phase owns the panel again (usually "arming", then an armed state).
    LaunchedEffect(state) {
        if (state != "disarmed") pendingArmOption = null
    }

    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val requestedMode = pendingArmOption
        if (requestedMode != null) {
            AlarmStatusAndAction(
                status = {
                    AlarmStatusCard(
                        title = stringResource(R.string.alarm_code_to_arm_title, stringResource(requestedMode.labelRes)),
                        subtitle = stringResource(R.string.alarm_code_to_arm_subtitle),
                        icon = requestedMode.icon,
                        accent = AppleColors.accent,
                    )
                },
                action = {
                    AlarmKeypad(
                        entityId = chip.entityId,
                        service = requestedMode.service,
                        currentState = state,
                        prompt = stringResource(R.string.alarm_arm_prompt),
                        accent = AppleColors.accent,
                        onCancel = { pendingArmOption = null },
                    )
                },
            )
            return@Column
        }

        when (phase) {
            AlarmPanelPhase.DISARMED -> {
                val options = ArmOption.entries.filter { entity.supports(it.feature) }
                val intro: @Composable () -> Unit = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.alarm_choose_mode_title),
                            style = AppleTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = AppleColors.primary,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(R.string.alarm_disarmed_subtitle),
                            style = AppleTypography.bodySmall,
                            color = AppleColors.secondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                val modes: @Composable () -> Unit = {
                    if (options.isEmpty()) {
                        PanelUnavailable(stringResource(R.string.alarm_no_modes_available))
                    } else {
                        val labels = options.associateWith { stringResource(it.labelRes) }
                        AccessoryGrid(
                            items = options.map { option ->
                                AccessoryItem(
                                    id = option.service,
                                    title = labels.getValue(option),
                                    icon = option.icon,
                                    on = false,
                                    accent = AppleColors.active,
                                    onToggle = {
                                        if (entity.alarmCodeRequired(arming = true)) pendingArmOption = option
                                        else callService(ALARM_DOMAIN, option.service, chip.entityId)
                                    },
                                )
                            },
                            tileHeight = 72.dp,
                        )
                    }
                }
                if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
                    AdaptivePanelSplit(
                        primaryWeight = 0.36f,
                        primary = { area -> Box(area, contentAlignment = Alignment.Center) { intro() } },
                        secondary = { area -> Box(area, contentAlignment = Alignment.Center) { modes() } },
                    )
                } else {
                    intro()
                    Spacer(Modifier.height(18.dp))
                    modes()
                }
            }

            AlarmPanelPhase.DISARMING -> AlarmStatusCard(
                title = stringResource(R.string.alarm_disarming_title),
                subtitle = stringResource(R.string.alarm_disarming_subtitle),
                icon = Icons.Outlined.LockOpen,
                accent = AppleColors.accent,
            )

            AlarmPanelPhase.ARMING,
            AlarmPanelPhase.ARMED,
            AlarmPanelPhase.PENDING,
            AlarmPanelPhase.TRIGGERED,
            AlarmPanelPhase.OTHER -> {
                val visual = alarmVisual(phase, state)
                AlarmStatusAndAction(
                    status = { AlarmStatusCard(visual.title, visual.subtitle, visual.icon, visual.accent) },
                    action = {
                        if (entity.alarmCodeRequired(arming = false)) {
                            AlarmKeypad(
                                entityId = chip.entityId,
                                service = "alarm_disarm",
                                currentState = state,
                                prompt = stringResource(R.string.alarm_disarm_prompt),
                                accent = visual.accent,
                                onCancel = null,
                            )
                        } else {
                            PillButton(
                                label = stringResource(R.string.alarm_disarm_button),
                                onClick = { callService(ALARM_DOMAIN, "alarm_disarm", chip.entityId) },
                                primary = true,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AlarmStatusAndAction(
    status: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    if (LocalPanelLayoutMode.current == PanelLayoutMode.HORIZONTAL) {
        AdaptivePanelSplit(
            modifier = Modifier.fillMaxSize(),
            primaryWeight = 0.4f,
            primary = { area -> Box(area, contentAlignment = Alignment.Center) { status() } },
            secondary = { area -> Box(area, contentAlignment = Alignment.Center) { action() } },
        )
    } else {
        status()
        Spacer(Modifier.height(12.dp))
        action()
    }
}

private data class AlarmVisual(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
)

@Composable
private fun alarmVisual(phase: AlarmPanelPhase, state: String): AlarmVisual = when (phase) {
    AlarmPanelPhase.ARMING -> AlarmVisual(
        stringResource(R.string.alarm_arming_title),
        stringResource(R.string.alarm_arming_subtitle),
        Icons.Outlined.Schedule,
        AppleColors.warning,
    )
    AlarmPanelPhase.PENDING -> AlarmVisual(
        stringResource(R.string.alarm_pending_title),
        stringResource(R.string.alarm_pending_subtitle),
        Icons.Filled.Warning,
        AppleColors.warning,
    )
    AlarmPanelPhase.TRIGGERED -> AlarmVisual(
        stringResource(R.string.alarm_triggered_title),
        stringResource(R.string.alarm_triggered_subtitle),
        Icons.Outlined.NotificationsActive,
        AppleColors.error,
    )
    AlarmPanelPhase.ARMED -> {
        val mode = ArmOption.entries.firstOrNull { it.armedState == state }
        AlarmVisual(
            stringResource(R.string.alarm_armed_title),
            mode?.let { stringResource(R.string.alarm_armed_mode_format, stringResource(it.labelRes)) }
                ?: stringResource(R.string.alarm_armed_subtitle),
            mode?.icon ?: Icons.Outlined.Shield,
            AppleColors.active,
        )
    }
    else -> AlarmVisual(
        stringResource(R.string.alarm_event_title),
        state.replace('_', ' ').replaceFirstChar { it.uppercase() },
        Icons.Outlined.Security,
        AppleColors.warning,
    )
}

@Composable
private fun AlarmStatusCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(accent.copy(alpha = 0.13f), AppleShapes.card)
            .border(1.dp, accent.copy(alpha = 0.48f), AppleShapes.card)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(AppleShapes.card).background(accent.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppleTypography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                color = AppleColors.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = AppleTypography.bodySmall, color = AppleColors.secondary)
        }
    }
}

/** The arming actions an entity can expose, in the order offered on the disarmed screen. */
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
    CUSTOM_BYPASS(AlarmFeature.ARM_CUSTOM_BYPASS, "alarm_arm_custom_bypass", "armed_custom_bypass", R.string.alarm_mode_custom_bypass, Icons.Outlined.Security),
}

/**
 * Shared numeric keypad wired to an alarm service. Home Assistant does not explicitly report an
 * invalid code, so an unchanged state after the response window is treated as a rejected entry.
 */
@Composable
private fun AlarmKeypad(
    entityId: String,
    service: String,
    currentState: String,
    prompt: String,
    accent: Color,
    onCancel: (() -> Unit)?,
) {
    val callService = LocalCallService.current
    var submitCount by remember(service) { mutableIntStateOf(0) }
    var wrongCode by remember(service) { mutableStateOf(false) }
    var waitingForHa by remember(service) { mutableStateOf(false) }
    val stateAtSubmit = remember(service) { mutableStateOf("") }
    val liveState by rememberUpdatedState(currentState)

    LaunchedEffect(submitCount) {
        if (submitCount > 0) {
            kotlinx.coroutines.delay(2600)
            if (liveState == stateAtSubmit.value) wrongCode = true
            waitingForHa = false
        }
    }

    LaunchedEffect(currentState, submitCount) {
        if (submitCount > 0 && currentState != stateAtSubmit.value) waitingForHa = false
    }

    PinKeypad(
        onSubmit = { code ->
            stateAtSubmit.value = liveState
            waitingForHa = true
            callService(ALARM_DOMAIN, service, entityId, mapOf("code" to code))
            submitCount++
        },
        codeLength = 0,
        subtitle = prompt,
        mergeSubtitleWithEntry = true,
        accent = accent,
        error = wrongCode,
        loading = waitingForHa,
        onErrorConsumed = { wrongCode = false },
        onCancel = onCancel,
    )
}
