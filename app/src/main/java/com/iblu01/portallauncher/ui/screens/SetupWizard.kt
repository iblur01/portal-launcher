package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaMdnsDiscovery
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsInfoDialog
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsStatusRow
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsTile
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * First-run wizard shown when no access key is configured yet.
 * Step 0: find Home Assistant on the network (or type the address).
 * Step 1: paste the access key, verify.
 * Step 2: done — saves and lands on the main settings page.
 */
@Composable
fun SetupWizard(
    uiState: SettingsUiState,
    haUrl: String,
    haToken: String,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSelectInstance: (HaInstance) -> Unit,
    onTest: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var showKeyHelp by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val discovered = remember { mutableStateListOf<HaInstance>() }
    DisposableEffect(Unit) {
        val discovery = HaMdnsDiscovery(context)
        discovery.start { instances ->
            discovered.clear()
            discovered.addAll(instances)
        }
        onDispose { discovery.stop() }
    }

    // A successful test on step 1 ends the connection part.
    LaunchedEffect(uiState.haTest) {
        if (uiState.haTest == ConnStatus.OK && step == 1) step = 2
    }

    if (showKeyHelp) {
        SettingsInfoDialog(
            title = stringResource(R.string.setup_label_where_to_find_key),
            lines = accessKeyHelpLines,
            onDismiss = { showKeyHelp = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        when (step) {
            0 -> {
                Text(stringResource(R.string.setup_welcome_title), style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text(
                    stringResource(R.string.setup_welcome_subtitle),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary
                )

                discovered.forEach { instance ->
                    SettingsTile(
                        icon = Icons.Outlined.Cloud,
                        title = instance.name,
                        subtitle = instance.url,
                        onClick = {
                            onSelectInstance(instance)
                            step = 1
                        },
                    )
                }
                if (discovered.isEmpty()) {
                    Text(
                        stringResource(R.string.setup_scanning_network),
                        style = AppleTypography.bodyMedium,
                        color = AppleColors.tertiary
                    )
                }

                SettingsSection(title = stringResource(R.string.setup_section_manual_address)) {
                    SettingsTextField(
                        label = stringResource(R.string.setup_label_address),
                        value = haUrl,
                        onValueChange = onUrlChange,
                        placeholder = stringResource(R.string.setup_placeholder_address)
                    )
                }

                PillButton(label = stringResource(R.string.setup_button_continue), primary = true, onClick = { step = 1 })
                PillButton(label = stringResource(R.string.setup_button_configure_later), onClick = onSkip)
            }

            1 -> {
                Text(stringResource(R.string.setup_access_key_title), style = AppleTypography.headlineLarge, color = AppleColors.primary)
                Text(
                    stringResource(R.string.setup_access_key_subtitle),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary
                )

                SettingsSection(title = stringResource(R.string.setup_section_access_key)) {
                    SettingsTextField(
                        label = stringResource(R.string.setup_label_access_key),
                        value = haToken,
                        onValueChange = onTokenChange,
                        placeholder = stringResource(R.string.setup_placeholder_paste_key),
                        isPassword = true
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.setup_label_where_to_find_key),
                        onClick = { showKeyHelp = true }
                    )
                    SettingsDivider()
                    SettingsStatusRow(
                        label = stringResource(R.string.setup_label_connection),
                        status = uiState.haTest,
                        detail = uiState.haTestMessage,
                        onClick = onTest,
                    )
                }

                PillButton(label = stringResource(R.string.setup_button_test_connection), primary = true, onClick = onTest)
                PillButton(label = stringResource(R.string.setup_button_back), onClick = { step = 0 })
            }

            else -> {
                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(stringResource(R.string.setup_done_title), style = AppleTypography.headlineLarge, color = AppleColors.primary)
                    Text(
                        stringResource(R.string.setup_done_subtitle),
                        style = AppleTypography.bodyLarge,
                        color = AppleColors.secondary
                    )
                    PillButton(label = stringResource(R.string.setup_button_start), primary = true, onClick = onFinish)
                }
            }
        }
    }
}
