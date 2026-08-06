package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.backgroundModes
import com.iblu01.portallauncher.ui.onboarding.CapabilityStatus
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.SelectedCheck
import com.iblu01.portallauncher.ui.onboarding.specForScale
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * What the flow ended up configuring, in one glance, then the way out.
 *
 * Every line is read back from the state rather than from what the user was offered, so a step that
 * was skipped shows what Portal will actually do — not what it suggested.
 */
@Composable
fun CompletionStep(
    state: OnboardingUiState,
    onDiscover: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val spec = specForScale(
        configuration.screenWidthDp.toFloat(),
        configuration.screenHeightDp.toFloat(),
        state.gridScale,
    )
    val enabled = stringResource(R.string.onb_complete_value_enabled)
    val disabled = stringResource(R.string.onb_complete_value_disabled)

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_complete_title),
        description = stringResource(R.string.onb_complete_body),
        modifier = modifier,
        aside = { SelectedCheck(visible = true) },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_complete_action_discover),
                onPrimary = onDiscover,
                secondaryLabel = stringResource(R.string.onb_complete_action_open_settings),
                onSecondary = onOpenSettings,
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.card)
                .background(AppleColors.frostedFill, AppleShapes.card)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                .padding(vertical = 6.dp),
        ) {
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_default_launcher),
                value = if (state.systemCapabilities.defaultLauncher == CapabilityStatus.GRANTED) enabled
                else disabled,
            )
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_grid),
                value = stringResource(
                    R.string.onb_complete_value_grid_format,
                    spec.columns,
                    spec.rows,
                ),
            )
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_background),
                value = backgroundModes.firstOrNull { it.first == state.backgroundMode }
                    ?.let { stringResource(it.second) }
                    ?: state.backgroundMode,
            )
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_home_assistant),
                value = if (state.haConnected) stringResource(R.string.onb_complete_value_connected)
                else stringResource(R.string.onb_complete_value_not_connected),
            )
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_remote_control),
                value = if (state.mqttConnected) enabled else disabled,
            )
            SummaryRow(
                label = stringResource(R.string.onb_complete_summary_hidden_apps),
                value = state.hiddenPackages.size.toString(),
            )
        }
        Spacer(Modifier.fillMaxWidth())
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Text(value, style = AppleTypography.titleMedium, color = AppleColors.secondary)
    }
}
