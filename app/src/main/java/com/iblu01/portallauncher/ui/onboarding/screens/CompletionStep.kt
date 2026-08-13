package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
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
        val layout = LocalOnboardingLayout.current
        val items = listOf(
            stringResource(R.string.onb_complete_summary_default_launcher) to
                if (state.systemCapabilities.defaultLauncher == CapabilityStatus.GRANTED) enabled else disabled,
            stringResource(R.string.onb_complete_summary_grid) to stringResource(
                R.string.onb_complete_value_grid_format, spec.columns, spec.rows,
            ),
            stringResource(R.string.onb_complete_summary_background) to
                (backgroundModes.firstOrNull { it.first == state.backgroundMode }
                    ?.let { stringResource(it.second) } ?: state.backgroundMode),
            stringResource(R.string.onb_complete_summary_home_assistant) to
                if (state.haConnected) stringResource(R.string.onb_complete_value_connected)
                else stringResource(R.string.onb_complete_value_not_connected),
            stringResource(R.string.onb_complete_summary_remote_control) to
                if (state.mqttConnected) enabled else disabled,
            stringResource(R.string.onb_complete_summary_hidden_apps) to state.hiddenPackages.size.toString(),
        )

        // A functional bento: stable groups that can be scanned and reopened, never ornamental
        // blocks of arbitrary sizes. Three columns fit the Portal; two fit the short Echo screen.
        val columns = if (layout.short) 3 else 2
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.spacing),
            ) {
                rowItems.forEach { (label, value) ->
                    SummaryTile(label, value, onOpenSettings, Modifier.weight(1f))
                }
                repeat(columns - rowItems.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = Modifier
            .then(modifier)
            .heightIn(min = 78.dp)
            .clip(AppleShapes.card)
            .background(AppleColors.elevated, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppleTypography.bodySmall, color = AppleColors.secondary)
        Text(value, style = AppleTypography.titleLarge, color = AppleColors.primary)
    }
}
