package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.MqttFeature
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Offers the remote-control branch.
 *
 * Deliberately sells the result rather than the transport: the broker, the topics and the word
 * "MQTT" belong on the configuration screen, not on the screen that asks whether the user wants
 * their panel to show up in Home Assistant at all. The five controls are shown as calm chips so the
 * promise is concrete without turning into a feature list.
 */
@Composable
fun RemoteControlStep(
    state: OnboardingUiState,
    onConfigure: () -> Unit,
    onLater: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_remote_title),
        modifier = modifier,
        description = stringResource(R.string.onb_remote_body),
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_remote_action_primary),
                onPrimary = onConfigure,
                secondaryLabel = stringResource(R.string.onb_common_nav_later),
                onSecondary = onLater,
            )
        },
    ) {
        RemoteFeatureChips(features = MqttFeature.values().toList())
    }
}

/** The controls Portal exposes, laid out as a wrapping row of quiet chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemoteFeatureChips(
    features: List<MqttFeature>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        features.forEach { feature ->
            RemoteFeatureChip(
                icon = featureIcon(feature),
                label = stringResource(featureLabel(feature)),
            )
        }
    }
}

@Composable
private fun RemoteFeatureChip(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AppleColors.secondary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
    }
}

/** Vertical variant used by the test screen, where the chips are a checked-off result list. */
@Composable
internal fun RemoteFeatureList(
    features: List<MqttFeature>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    featureIcon(feature),
                    contentDescription = null,
                    tint = AppleColors.active,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(featureLabel(feature)),
                    style = AppleTypography.titleMedium,
                    color = AppleColors.primary,
                )
            }
        }
    }
}

internal fun featureLabel(feature: MqttFeature): Int = when (feature) {
    MqttFeature.SCREEN -> R.string.onb_remote_feature_screen
    MqttFeature.BRIGHTNESS -> R.string.onb_remote_feature_brightness
    MqttFeature.VOLUME -> R.string.onb_remote_feature_volume
    MqttFeature.PRESENCE -> R.string.onb_remote_feature_presence
    MqttFeature.NOTIFICATIONS -> R.string.onb_remote_feature_notification
}

private fun featureIcon(feature: MqttFeature): ImageVector = when (feature) {
    MqttFeature.SCREEN -> Icons.Filled.Tv
    MqttFeature.BRIGHTNESS -> Icons.Filled.BrightnessMedium
    MqttFeature.VOLUME -> Icons.AutoMirrored.Filled.VolumeUp
    MqttFeature.PRESENCE -> Icons.Filled.Sensors
    MqttFeature.NOTIFICATIONS -> Icons.Filled.Notifications
}
