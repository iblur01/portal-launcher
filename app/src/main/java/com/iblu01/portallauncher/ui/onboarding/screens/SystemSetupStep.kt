package com.iblu01.portallauncher.ui.onboarding.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.Capability
import com.iblu01.portallauncher.ui.onboarding.CapabilityStatus
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.CapabilityCard
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

/** How long the check on a freshly granted capability is left on screen before it is acknowledged. */
private const val GRANT_ACKNOWLEDGE_MILLIS = 1200L

/**
 * The system permissions Portal needs to behave like a smart display, one card each.
 *
 * Nothing here advances the flow on its own — coming back from Android's settings shows the new
 * status (and its check) but the user stays in charge of moving on. "Continue" is always live: the
 * launcher works without any of these, just with less.
 */
@Composable
fun SystemSetupStep(
    state: OnboardingUiState,
    onOpenSetting: (Capability) -> Unit,
    onRequestMicrophone: () -> Unit,
    onAcknowledgeGrant: () -> Unit,
    adbCommand: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Let the check finish playing on the card that just turned green, then clear the highlight.
    LaunchedEffect(state.justGranted) {
        if (state.justGranted != null) {
            delay(GRANT_ACKNOWLEDGE_MILLIS)
            onAcknowledgeGrant()
        }
    }

    val capabilities = state.systemCapabilities

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_setup_title),
        modifier = modifier,
        description = stringResource(R.string.onb_setup_body),
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = onContinue,
            )
        },
    ) {
        val launcherStatus = capabilities[Capability.DEFAULT_LAUNCHER]
        CapabilityCard(
            title = stringResource(R.string.onb_setup_default_launcher_title),
            description = stringResource(R.string.onb_setup_default_launcher_desc),
            status = launcherStatus,
            statusLabel = stringResource(configurationStatusLabel(launcherStatus)),
            actionLabel = stringResource(R.string.onb_setup_default_launcher_action),
            onAction = { onOpenSetting(Capability.DEFAULT_LAUNCHER) },
            badge = stringResource(R.string.onb_setup_default_launcher_badge_recommended),
            footer = if (launcherStatus == CapabilityStatus.UNAVAILABLE) {
                { AdbHelp(command = adbCommand) }
            } else {
                null
            },
        )

        val screenStatus = capabilities[Capability.SCREEN_CONTROL]
        CapabilityCard(
            title = stringResource(R.string.onb_setup_screen_control_title),
            description = stringResource(R.string.onb_setup_screen_control_desc),
            status = screenStatus,
            statusLabel = stringResource(screenControlStatusLabel(screenStatus)),
            actionLabel = stringResource(R.string.onb_setup_screen_control_action),
            onAction = { onOpenSetting(Capability.SCREEN_CONTROL) },
            footer = {
                // An accessibility service is a large-sounding ask; say exactly what it is used for.
                Text(
                    stringResource(R.string.onb_setup_screen_control_explanation),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                )
            },
        )

        val brightnessStatus = capabilities[Capability.BRIGHTNESS]
        CapabilityCard(
            title = stringResource(R.string.onb_setup_brightness_title),
            description = stringResource(R.string.onb_setup_brightness_desc),
            status = brightnessStatus,
            statusLabel = stringResource(configurationStatusLabel(brightnessStatus)),
            actionLabel = stringResource(R.string.onb_setup_brightness_action),
            onAction = { onOpenSetting(Capability.BRIGHTNESS) },
        )

        val microphoneStatus = capabilities[Capability.MICROPHONE]
        CapabilityCard(
            title = stringResource(R.string.onb_setup_microphone_title),
            description = stringResource(R.string.onb_setup_microphone_desc),
            status = microphoneStatus,
            statusLabel = stringResource(configurationStatusLabel(microphoneStatus)),
            actionLabel = stringResource(R.string.onb_setup_microphone_action),
            // The runtime prompt is only ever raised by this button, never by opening the step.
            onAction = onRequestMicrophone,
            badge = stringResource(R.string.onb_setup_microphone_badge_optional),
            secondaryLabel = stringResource(R.string.onb_common_nav_later),
            onSecondary = onContinue,
        )
    }
}

/** Wording shared by the cards whose state reads as "configured or not". */
private fun configurationStatusLabel(status: CapabilityStatus): Int = when (status) {
    CapabilityStatus.GRANTED -> R.string.onb_setup_status_configured
    CapabilityStatus.MISSING -> R.string.onb_setup_status_not_configured
    CapabilityStatus.UNAVAILABLE -> R.string.onb_setup_status_unavailable
}

/** The screen control is a switch, not a setting: "enabled" reads better than "configured". */
private fun screenControlStatusLabel(status: CapabilityStatus): Int = when (status) {
    CapabilityStatus.GRANTED -> R.string.onb_setup_status_enabled
    CapabilityStatus.MISSING -> R.string.onb_setup_status_to_enable
    CapabilityStatus.UNAVAILABLE -> R.string.onb_setup_status_advanced_required
}

/**
 * The way out on devices that offer no launcher-selection screen at all: the command to run from a
 * computer, ready to copy.
 */
@Composable
private fun AdbHelp(command: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.onb_common_toast_copied)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.onb_setup_adb_help_title),
            style = AppleTypography.titleMedium,
            color = AppleColors.primary,
        )
        Text(
            stringResource(R.string.onb_setup_adb_help_body),
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(AppleShapes.section)
                .background(AppleColors.background.copy(alpha = 0.6f), AppleShapes.section)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                command,
                style = AppleTypography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = AppleColors.primary,
            )
        }
        PillButton(
            label = stringResource(R.string.onb_setup_adb_help_copy_command),
            onClick = {
                clipboard.setText(AnnotatedString(command))
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            },
        )
    }
}
