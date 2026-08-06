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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.onboarding.BrokerCandidate
import com.iblu01.portallauncher.ui.onboarding.DiscoveryState
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.OnboardingUrls
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingSpacerLine
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * The broker form.
 *
 * Manual entry is the main path and is always available: Home Assistant exposes no API that hands
 * out its broker's address or credentials, so mDNS can at best offer a host and a port, and the
 * username and password can only ever be typed. The note above the fields says exactly that, so the
 * prefilled host is never mistaken for a discovered account.
 */
@Composable
fun MqttConfigurationStep(
    state: OnboardingUiState,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onSelectBroker: (BrokerCandidate) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
    onAuthEnabledChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onTest: () -> Unit,
    onBack: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    // The field is text, the state is an Int: keep what was typed so a half-finished port ("18")
    // or an out-of-range one stays on screen instead of snapping back to the last valid value.
    var portText by rememberSaveable(state.mqttPort) { mutableStateOf(state.mqttPort.toString()) }
    var showPassword by remember { mutableStateOf(false) }

    val portValid = OnboardingUrls.isValidPort(portText)
    val hostSuggested = state.mqttHost.isNotBlank() &&
        state.mqttHost == OnboardingUrls.hostOf(state.haUrl)

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_mqtt_config_title),
        modifier = modifier,
        description = stringResource(R.string.onb_mqtt_config_body),
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_test_connection),
                onPrimary = if (state.canTestMqtt) onTest else null,
                secondaryLabel = stringResource(R.string.onb_common_nav_set_up_later),
                onSecondary = onLater,
            )
        },
    ) {
        CredentialsNote()

        BrokerDiscoverySection(
            discovery = state.brokerDiscovery,
            brokers = state.discoveredBrokers,
            selectedHost = state.mqttHost,
            selectedPort = state.mqttPort,
            onSelectBroker = onSelectBroker,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.card)
                .background(AppleColors.frostedFill, AppleShapes.card)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                .padding(vertical = 8.dp),
        ) {
            SettingsTextField(
                label = stringResource(R.string.onb_mqtt_config_field_server),
                value = state.mqttHost,
                onValueChange = onHostChange,
                keyboardType = KeyboardType.Uri,
            )
            if (hostSuggested) {
                FieldNote(stringResource(R.string.onb_mqtt_config_suggestion_hint))
            }
            if (state.mqttHost.isBlank()) {
                FieldError(stringResource(R.string.onb_mqtt_config_error_server_required))
            }

            SettingsTextField(
                label = stringResource(R.string.onb_mqtt_config_field_port),
                value = portText,
                onValueChange = { typed ->
                    portText = typed
                    if (OnboardingUrls.isValidPort(typed)) onPortChange(typed.trim().toInt())
                },
                keyboardType = KeyboardType.Number,
            )
            if (!portValid) {
                FieldError(stringResource(R.string.onb_mqtt_config_error_invalid_port))
            }

            OnboardingSpacerLine(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SettingsToggle(
                label = stringResource(R.string.onb_mqtt_config_field_auth),
                checked = state.mqttAuthEnabled,
                onCheckedChange = onAuthEnabledChange,
            )
            if (state.mqttAuthEnabled) {
                SettingsTextField(
                    label = stringResource(R.string.onb_mqtt_config_field_username),
                    value = state.mqttUsername,
                    onValueChange = onUsernameChange,
                )
                SettingsTextField(
                    label = stringResource(R.string.onb_mqtt_config_field_password),
                    value = state.mqttPassword,
                    onValueChange = onPasswordChange,
                    isPassword = !showPassword,
                )
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Box(Modifier.width(140.dp)) {
                        PillButton(
                            label = stringResource(
                                if (showPassword) R.string.onb_ha_creds_action_hide
                                else R.string.onb_ha_creds_action_show
                            ),
                            onClick = { showPassword = !showPassword },
                        )
                    }
                }
            }

            OnboardingSpacerLine(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            SettingsTextField(
                label = stringResource(R.string.onb_mqtt_config_field_device_name),
                value = state.mqttDeviceName,
                onValueChange = onDeviceNameChange,
                placeholder = stringResource(R.string.onb_mqtt_config_device_name_placeholder),
            )
        }
    }
}

/**
 * Says out loud that the credentials are not discovered. Placed above the fields on purpose: it is
 * the one thing a user coming from a working Home Assistant connection will assume wrongly.
 */
@Composable
private fun CredentialsNote(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.section)
            .background(AppleColors.frostedFill, AppleShapes.section)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Filled.Info,
            contentDescription = null,
            tint = AppleColors.warning,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.onb_mqtt_config_note_credentials),
            style = AppleTypography.bodyLarge,
            color = AppleColors.primary,
        )
    }
}

/**
 * mDNS results, when there are any. Never blocks the form: a broker that answers `_mqtt._tcp` only
 * fills the host and the port, and a network that answers nothing costs the user nothing either.
 */
@Composable
private fun BrokerDiscoverySection(
    discovery: DiscoveryState,
    brokers: List<BrokerCandidate>,
    selectedHost: String,
    selectedPort: Int,
    onSelectBroker: (BrokerCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = when (discovery) {
        DiscoveryState.Searching -> stringResource(R.string.onb_mqtt_config_discovery_searching)
        is DiscoveryState.Found -> stringResource(R.string.onb_mqtt_config_discovery_found)
        DiscoveryState.NothingFound -> stringResource(R.string.onb_mqtt_config_discovery_not_found)
        DiscoveryState.Idle -> null
    } ?: return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(status, style = AppleTypography.bodySmall, color = AppleColors.secondary)
        brokers.forEach { broker ->
            ChoiceTile(
                title = broker.name,
                subtitle = "${broker.host}:${broker.port}",
                selected = broker.host == selectedHost && broker.port == selectedPort,
                onClick = { onSelectBroker(broker) },
            )
        }
    }
}

/** A quiet explanation under a field. */
@Composable
private fun FieldNote(text: String) {
    Text(
        text,
        style = AppleTypography.bodySmall,
        color = AppleColors.tertiary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
    )
}

/** The same slot, in the error colour. */
@Composable
private fun FieldError(text: String) {
    Text(
        text,
        style = AppleTypography.bodySmall,
        color = AppleColors.error,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
    )
}
