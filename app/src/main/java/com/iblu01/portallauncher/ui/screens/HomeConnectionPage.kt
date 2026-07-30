package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaMdnsDiscovery
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.HaDiscoveryDialog
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsInfoDialog
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsStatusRow
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Step-by-step help shown next to the access-key field. */
internal val accessKeyHelpLines = listOf(
    "1. Ouvre Home Assistant dans un navigateur (même adresse qu'ici).",
    "2. Clique sur ton nom d'utilisateur en bas à gauche.",
    "3. Va dans l'onglet « Sécurité ».",
    "4. Tout en bas : « Jetons d'accès de longue durée » → « Créer un jeton ».",
    "5. Donne-lui un nom (ex. Portal), copie le jeton et colle-le ici.",
)

/**
 * « Ma maison » — single friendly page for everything connection-related:
 * HA address + access key with a live status row, network auto-discovery,
 * and the MQTT broker folded under an "Avancé" section (pre-filled from the address).
 */
@Composable
fun HomeConnectionPage(
    uiState: SettingsUiState,
    haUrl: String,
    haToken: String,
    mqttHost: String,
    mqttPort: String,
    mqttUsername: String,
    mqttPassword: String,
    deviceName: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSelectInstance: (HaInstance) -> Unit,
    onMqttHostChange: (String) -> Unit,
    onMqttPortChange: (String) -> Unit,
    onMqttUsernameChange: (String) -> Unit,
    onMqttPasswordChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onTestHa: () -> Unit,
    onTestMqtt: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    val discovered = remember { mutableStateListOf<HaInstance>() }
    var showDiscovery by remember { mutableStateOf(false) }
    var showKeyHelp by remember { mutableStateOf(false) }
    // Most MQTT brokers require a login; default the toggle to whatever is already saved.
    var mqttAuthEnabled by remember { mutableStateOf(mqttUsername.isNotBlank() || mqttPassword.isNotBlank()) }

    // Auto-scan the LAN while this page is on screen; always stop on dispose.
    DisposableEffect(Unit) {
        val discovery = HaMdnsDiscovery(context)
        discovery.start { instances ->
            discovered.clear()
            discovered.addAll(instances)
        }
        onDispose { discovery.stop() }
    }

    if (showDiscovery) {
        HaDiscoveryDialog(
            instances = discovered.toList(),
            onDismiss = { showDiscovery = false },
            onSelect = { instance ->
                onSelectInstance(instance)
                showDiscovery = false
            },
        )
    }

    if (showKeyHelp) {
        SettingsInfoDialog(
            title = stringResource(R.string.home_connection_label_key_help),
            lines = accessKeyHelpLines,
            onDismiss = { showKeyHelp = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = stringResource(R.string.home_connection_title), onBack = onBack, showBack = showBack)
        Text(
            stringResource(R.string.home_connection_subtitle),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary
        )

        SettingsSection(title = stringResource(R.string.home_connection_section_address)) {
            val suggestion = discovered.firstOrNull()
            if (suggestion != null) {
                SettingsRow(
                    label = stringResource(R.string.home_connection_ha_detected),
                    value = suggestion.name,
                    onClick = {
                        if (discovered.size == 1) onSelectInstance(suggestion) else showDiscovery = true
                    },
                )
                SettingsDivider()
            }
            SettingsTextField(
                label = stringResource(R.string.home_connection_label_address),
                value = haUrl,
                onValueChange = onUrlChange,
                placeholder = stringResource(R.string.home_connection_placeholder_address)
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.home_connection_label_scan_network),
                value = if (discovered.isEmpty()) stringResource(R.string.home_connection_scanning)
                    else stringResource(R.string.home_connection_scan_found_format, discovered.size),
                onClick = { showDiscovery = true },
            )
        }

        SettingsSection(title = stringResource(R.string.home_connection_section_access_key)) {
            SettingsTextField(
                label = stringResource(R.string.home_connection_label_access_key),
                value = haToken,
                onValueChange = onTokenChange,
                placeholder = stringResource(R.string.home_connection_placeholder_paste_key),
                isPassword = true
            )
            SettingsDivider()
            SettingsRow(label = stringResource(R.string.home_connection_label_key_help), onClick = { showKeyHelp = true })
        }

        SettingsSection(title = stringResource(R.string.home_connection_section_status)) {
            SettingsStatusRow(
                label = stringResource(R.string.home_connection_label_connection),
                status = uiState.haTest,
                detail = uiState.haTestMessage,
                onClick = onTestHa,
            )
        }

        // MQTT is used to receive live Home Assistant notifications on this device.
        // Always shown in full: almost every broker requires a login, so hiding it behind
        // a disclosure toggle just hid a mandatory field.
        SettingsSection(title = stringResource(R.string.home_connection_section_mqtt)) {
            SettingsTextField(
                label = stringResource(R.string.home_connection_label_mqtt_server),
                value = mqttHost,
                onValueChange = onMqttHostChange,
                placeholder = stringResource(R.string.home_connection_placeholder_mqtt_server)
            )
            SettingsDivider()
            SettingsTextField(
                label = stringResource(R.string.home_connection_label_mqtt_port),
                value = mqttPort,
                onValueChange = onMqttPortChange,
                placeholder = stringResource(R.string.home_connection_placeholder_mqtt_port),
                keyboardType = KeyboardType.Number
            )
            SettingsDivider()
            SettingsToggle(
                label = stringResource(R.string.home_connection_toggle_mqtt_auth),
                checked = mqttAuthEnabled,
                onCheckedChange = { enabled ->
                    mqttAuthEnabled = enabled
                    if (!enabled) {
                        onMqttUsernameChange("")
                        onMqttPasswordChange("")
                    }
                },
            )
            if (mqttAuthEnabled) {
                SettingsDivider()
                SettingsTextField(
                    label = stringResource(R.string.home_connection_label_mqtt_username),
                    value = mqttUsername,
                    onValueChange = onMqttUsernameChange,
                    placeholder = stringResource(R.string.home_connection_placeholder_username)
                )
                SettingsDivider()
                SettingsTextField(
                    label = stringResource(R.string.home_connection_label_mqtt_password),
                    value = mqttPassword,
                    onValueChange = onMqttPasswordChange,
                    placeholder = stringResource(R.string.home_connection_placeholder_password),
                    isPassword = true
                )
            }
            SettingsDivider()
            SettingsTextField(
                label = stringResource(R.string.home_connection_label_device_name),
                value = deviceName,
                onValueChange = onDeviceNameChange,
                placeholder = stringResource(R.string.home_connection_placeholder_device_name)
            )
            SettingsDivider()
            SettingsStatusRow(
                label = stringResource(R.string.home_connection_label_mqtt_connection),
                status = uiState.mqttTest,
                detail = uiState.mqttTestMessage,
                onClick = onTestMqtt,
            )
        }
    }
}
