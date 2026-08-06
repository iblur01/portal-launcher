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
import androidx.compose.runtime.mutableIntStateOf
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
import com.iblu01.portallauncher.ui.onboarding.SystemCapabilities
import com.iblu01.portallauncher.ui.onboarding.components.CapabilityCard
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingSize
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
    val layout = LocalOnboardingLayout.current
    // A small or short window cannot show four permission cards at once, and a scrolled list of
    // them reads as a settings page. There, the step becomes one capability per page — which is the
    // "one decision per screen" rule the rest of the flow already follows.
    val paged = layout.size == OnboardingSize.COMPACT || layout.short
    // Compact flows start with a dedicated introduction. Combining that explanation with the
    // first permission recreated the tablet layout in miniature and made both hard to scan.
    var page by remember { mutableIntStateOf(0) }
    val compactPageCount = Capability.values().size + 1

    val advance: () -> Unit = { if (paged && page < compactPageCount - 1) page++ else onContinue() }
    val retreat: () -> Unit = { if (paged && page > 0) page-- else onBack() }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_setup_title),
        modifier = modifier,
        description = if (!paged || page == 0) stringResource(R.string.onb_setup_body) else null,
        showHeader = !paged || page == 0,
        navigation = {
            OnboardingNavigationBar(
                onBack = retreat,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = advance,
            )
        },
    ) {
        if (paged) {
            if (page > 0) {
                CapabilityPage(
                    capability = Capability.values()[page - 1],
                    capabilities = capabilities,
                    adbCommand = adbCommand,
                    onOpenSetting = onOpenSetting,
                    onLater = advance,
                )
            }
        } else {
            Capability.values().forEach { capability ->
                CapabilityPage(
                    capability = capability,
                    capabilities = capabilities,
                    adbCommand = adbCommand,
                    onOpenSetting = onOpenSetting,
                    onLater = onContinue,
                )
            }
        }
    }
}

/** One capability's card, wherever it is shown: on its own page, or in the full list. */
@Composable
private fun CapabilityPage(
    capability: Capability,
    capabilities: SystemCapabilities,
    adbCommand: String,
    onOpenSetting: (Capability) -> Unit,
    onLater: () -> Unit,
) {
    val status = capabilities[capability]
    when (capability) {
        Capability.DEFAULT_LAUNCHER -> CapabilityCard(
            title = stringResource(R.string.onb_setup_default_launcher_title),
            description = stringResource(R.string.onb_setup_default_launcher_desc),
            status = status,
            statusLabel = stringResource(configurationStatusLabel(status)),
            actionLabel = stringResource(R.string.onb_setup_default_launcher_action),
            onAction = { onOpenSetting(Capability.DEFAULT_LAUNCHER) },
            badge = stringResource(R.string.onb_setup_default_launcher_badge_recommended),
            footer = if (status == CapabilityStatus.UNAVAILABLE) {
                { AdbHelp(command = adbCommand) }
            } else {
                null
            },
        )

        Capability.SCREEN_CONTROL -> CapabilityCard(
            title = stringResource(R.string.onb_setup_screen_control_title),
            description = stringResource(R.string.onb_setup_screen_control_desc),
            status = status,
            statusLabel = stringResource(screenControlStatusLabel(status)),
            actionLabel = stringResource(R.string.onb_setup_screen_control_action),
            onAction = { onOpenSetting(Capability.SCREEN_CONTROL) },
            footer = {
                Text(
                    stringResource(R.string.onb_setup_screen_control_explanation),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                )
            },
        )

        Capability.BRIGHTNESS -> CapabilityCard(
            title = stringResource(R.string.onb_setup_brightness_title),
            description = stringResource(R.string.onb_setup_brightness_desc),
            status = status,
            statusLabel = stringResource(configurationStatusLabel(status)),
            actionLabel = stringResource(R.string.onb_setup_brightness_action),
            onAction = { onOpenSetting(Capability.BRIGHTNESS) },
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
