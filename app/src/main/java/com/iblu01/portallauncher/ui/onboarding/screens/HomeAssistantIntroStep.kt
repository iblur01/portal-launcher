package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.HomeCandidate
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold

/**
 * Offers — never demands — a Home Assistant connection.
 *
 * The first decision is the input device: a phone is easier for credentials, while the panel remains
 * available for users who prefer to finish in place. Discovery still runs in the background so the
 * manual path can use a detected home without turning this choice into a network-status screen.
 */
@Composable
fun HomeAssistantIntroStep(
    state: OnboardingUiState,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onSelectHome: (HomeCandidate) -> Unit,
    onManual: () -> Unit,
    onConfigureWithPhone: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalOnboardingLayout.current
    var showCompactManualWarning by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_ha_intro_title),
        modifier = modifier,
        description = stringResource(R.string.onb_ha_intro_body),
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_ha_intro_action_phone),
                onPrimary = onConfigureWithPhone,
                skipLabel = stringResource(R.string.onb_common_nav_skip),
                onSkip = onSkip,
            )
        },
    ) {
        // Credential entry is deliberately handed to the phone on compact-height displays. A
        // landscape panel keyboard would cover most of the task and make error recovery painful.
        if (layout.short) {
            ChoiceTile(
                title = stringResource(R.string.onb_ha_intro_phone_title),
                subtitle = stringResource(R.string.onb_ha_intro_phone_body),
                icon = Icons.Outlined.PhoneAndroid,
                selected = false,
                onClick = onConfigureWithPhone,
                modifier = Modifier.fillMaxWidth(),
                compact = true,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { showCompactManualWarning = true }) {
                    Text(stringResource(R.string.onb_ha_intro_action_manual_compact))
                }
            }
            return@OnboardingScaffold
        }

        val horizontal = layout.twoColumn
        val tiles: @Composable (Modifier, Modifier) -> Unit = { phoneModifier, manualModifier ->
            ChoiceTile(
                title = stringResource(R.string.onb_ha_intro_phone_title),
                subtitle = stringResource(R.string.onb_ha_intro_phone_body),
                icon = Icons.Outlined.PhoneAndroid,
                selected = false,
                onClick = onConfigureWithPhone,
                modifier = phoneModifier,
                compact = layout.short,
            )
            ChoiceTile(
                title = stringResource(R.string.onb_ha_intro_manual_title),
                subtitle = stringResource(R.string.onb_ha_intro_manual_body),
                icon = Icons.Outlined.TouchApp,
                selected = false,
                onClick = {
                    state.discoveredHomes.singleOrNull()?.let(onSelectHome) ?: onManual()
                },
                modifier = manualModifier,
                compact = layout.short,
            )
        }
        if (horizontal) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) { tiles(Modifier.weight(1f), Modifier.weight(1f)) }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles(Modifier.fillMaxWidth(), Modifier.fillMaxWidth())
            }
        }
    }

    if (showCompactManualWarning) {
        AlertDialog(
            onDismissRequest = { showCompactManualWarning = false },
            title = { Text(stringResource(R.string.onb_ha_manual_warning_title)) },
            text = { Text(stringResource(R.string.onb_ha_manual_warning_body)) },
            dismissButton = {
                TextButton(onClick = { showCompactManualWarning = false }) {
                    Text(stringResource(R.string.onb_ha_manual_warning_phone))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCompactManualWarning = false
                        state.discoveredHomes.singleOrNull()?.let(onSelectHome) ?: onManual()
                    }
                ) {
                    Text(stringResource(R.string.onb_ha_manual_warning_continue))
                }
            },
        )
    }
}
