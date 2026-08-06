package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsInfoDialog
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.OnboardingUrls
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * The one form of the Home Assistant chapter: an address and a long-lived token.
 *
 * It adapts to how the user got here — a home picked from the mDNS list only needs its token, a
 * manual setup needs both — and it never lets the test run on input that cannot possibly work, so a
 * typo is caught here instead of coming back as a connection failure.
 */
@Composable
fun HomeAssistantCredentialsStep(
    state: OnboardingUiState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tokenVisible by remember { mutableStateOf(false) }
    var tokenTouched by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmAbandon by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val detected = state.selectedHome
    val addressValid = OnboardingUrls.isValidHaUrl(state.haUrl)
    val tokenValid = state.haToken.isNotBlank()
    val canTest = addressValid && tokenValid

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_ha_creds_title),
        modifier = modifier,
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_test_connection),
                onPrimary = if (canTest) onTest else null,
                skipLabel = stringResource(R.string.onb_common_nav_skip),
                onSkip = { confirmAbandon = true },
            )
        },
    ) {
        if (detected != null) {
            DetectedHomeSummary(name = detected.name, url = detected.url)
        } else {
            Column {
                SettingsTextField(
                    label = stringResource(R.string.onb_ha_creds_label_address),
                    value = state.haUrl,
                    onValueChange = { onUrlChange(it.trim()) },
                    placeholder = stringResource(R.string.onb_ha_creds_placeholder_address),
                    keyboardType = KeyboardType.Uri,
                )
                if (state.haUrl.isNotBlank() && !addressValid) {
                    FieldError(stringResource(R.string.onb_ha_creds_error_invalid_address))
                }
            }
        }

        Column {
            SettingsTextField(
                label = stringResource(R.string.onb_ha_creds_label_token),
                value = state.haToken,
                onValueChange = {
                    tokenTouched = true
                    onTokenChange(it.trim())
                },
                placeholder = stringResource(R.string.onb_ha_creds_placeholder_token),
                isPassword = !tokenVisible,
            )
            if (tokenTouched && !tokenValid) {
                FieldError(stringResource(R.string.onb_ha_creds_error_token_required))
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(
                    label = stringResource(
                        if (tokenVisible) R.string.onb_ha_creds_action_hide
                        else R.string.onb_ha_creds_action_show
                    ),
                    onClick = { tokenVisible = !tokenVisible },
                )
                // Only offered when there is actually something to paste — an inert button here
                // would read as "paste is broken".
                if (clipboard.hasText()) {
                    TextAction(
                        label = stringResource(R.string.onb_ha_creds_action_paste),
                        onClick = {
                            val pasted = clipboard.getText()?.text?.trim().orEmpty()
                            if (pasted.isNotEmpty()) {
                                tokenTouched = true
                                onTokenChange(pasted)
                            }
                        },
                    )
                }
                Spacer(Modifier.width(0.dp))
                TextAction(
                    label = stringResource(R.string.onb_ha_creds_action_find_token),
                    onClick = { showHelp = true },
                )
            }
        }
    }

    if (showHelp) {
        SettingsInfoDialog(
            title = stringResource(R.string.onb_ha_creds_action_find_token),
            lines = listOf(
                stringResource(R.string.onb_ha_creds_help_step1),
                stringResource(R.string.onb_ha_creds_help_step2),
                stringResource(R.string.onb_ha_creds_help_step3),
                stringResource(R.string.onb_ha_creds_help_step4),
                stringResource(R.string.onb_ha_creds_help_step5),
            ),
            onDismiss = { showHelp = false },
        )
    }

    if (confirmAbandon) {
        ConfirmDiscardDialog(
            onKeepEditing = { confirmAbandon = false },
            onDiscard = {
                confirmAbandon = false
                onAbandon()
            },
        )
    }
}

/** The instance mDNS found, restated so the user knows which home the token is for. */
@Composable
private fun DetectedHomeSummary(name: String, url: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Home,
            contentDescription = null,
            tint = AppleColors.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(name, style = AppleTypography.titleMedium, color = AppleColors.primary)
            Text(url, style = AppleTypography.bodySmall, color = AppleColors.secondary)
        }
    }
}

/** Inline validation message, aligned with the field it belongs to. */
@Composable
private fun FieldError(message: String) {
    Text(
        message,
        style = AppleTypography.bodySmall,
        color = AppleColors.error,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp),
    )
}

/** A borderless textual action; the form already carries enough buttons. */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = AppleTypography.bodySmall,
        color = AppleColors.accent,
        modifier = Modifier
            .clip(AppleShapes.pill)
            .appleClickable(onClick)
            .padding(vertical = 6.dp),
    )
}

/**
 * Guards the skip: leaving this step throws away the address and the token, and a token is tedious
 * enough to obtain that losing it to a mistapped button would be its own small disaster.
 */
@Composable
private fun ConfirmDiscardDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    Dialog(
        onDismissRequest = onKeepEditing,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .padding(24.dp)
                .clip(AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
            color = AppleColors.elevated,
            shape = AppleShapes.panel,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.onb_ha_creds_skip_dialog_title),
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                )
                Text(
                    stringResource(R.string.onb_ha_creds_skip_dialog_body),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        PillButton(
                            label = stringResource(R.string.onb_ha_creds_skip_dialog_discard),
                            onClick = onDiscard,
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        PillButton(
                            label = stringResource(R.string.onb_ha_creds_skip_dialog_continue),
                            onClick = onKeepEditing,
                            primary = true,
                        )
                    }
                }
            }
        }
    }
}
