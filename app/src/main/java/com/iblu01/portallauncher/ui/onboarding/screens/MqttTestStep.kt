package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.MqttFeature
import com.iblu01.portallauncher.ui.onboarding.OnboardingError
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.TestPhase
import com.iblu01.portallauncher.ui.onboarding.TestState
import com.iblu01.portallauncher.ui.onboarding.components.Badge
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.SelectedCheck
import com.iblu01.portallauncher.ui.onboarding.components.TestPhaseList
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * The broker check. Unlike a plain connection attempt this one publishes to a device-scoped topic
 * and waits for the broker to hand the message back, so an ACL that would silently break the Home
 * Assistant device shows up here instead of days later.
 */
@Composable
fun MqttTestStep(
    state: OnboardingUiState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onLater: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val test = state.mqttTest) {
        is TestState.Success -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(R.string.onb_mqtt_test_success_title),
            description = stringResource(R.string.onb_mqtt_test_success_body),
            modifier = modifier,
            navigation = {
                OnboardingNavigationBar(
                    onBack = null,
                    primaryLabel = stringResource(R.string.onb_common_nav_continue),
                    onPrimary = onContinue,
                )
            },
        ) {
            SelectedCheck(visible = true)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.onb_mqtt_test_features_header),
                style = AppleTypography.labelSmall,
                color = AppleColors.tertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                test.summary.features.forEach { feature ->
                    Badge(stringResource(mqttFeatureLabel(feature)))
                }
            }
        }

        is TestState.Failure -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(mqttErrorMessage(test.error)),
            modifier = modifier,
            navigation = {
                OnboardingNavigationBar(
                    onBack = null,
                    primaryLabel = stringResource(R.string.onb_common_nav_retry),
                    onPrimary = onRetry,
                    skipLabel = stringResource(R.string.onb_common_nav_set_up_later),
                    onSkip = onLater,
                    secondaryLabel = stringResource(R.string.onb_common_nav_edit_details),
                    onSecondary = onEdit,
                )
            },
        ) {
            // Address only: the broker password never leaves the form.
            Text(
                "${state.mqttHost}:${state.mqttPort}",
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
            )
        }

        else -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(R.string.onb_mqtt_test_running_title),
            modifier = modifier,
        ) {
            TestPhaseList(
                phases = listOf(
                    stringResource(R.string.onb_mqtt_test_phase_connect),
                    stringResource(R.string.onb_mqtt_test_phase_publish),
                    stringResource(R.string.onb_mqtt_test_phase_verify),
                ),
                currentIndex = mqttPhaseIndex(test),
            )
        }
    }
}

private fun mqttPhaseIndex(test: TestState): Int = when ((test as? TestState.Running)?.phase) {
    TestPhase.CONNECTING_BROKER -> 0
    TestPhase.PUBLISHING_DEVICE -> 1
    TestPhase.VERIFYING_ROUNDTRIP -> 2
    else -> 0
}

private fun mqttFeatureLabel(feature: MqttFeature): Int = when (feature) {
    MqttFeature.SCREEN -> R.string.onb_remote_feature_screen
    MqttFeature.BRIGHTNESS -> R.string.onb_remote_feature_brightness
    MqttFeature.VOLUME -> R.string.onb_remote_feature_volume
    MqttFeature.PRESENCE -> R.string.onb_remote_feature_presence
    MqttFeature.NOTIFICATIONS -> R.string.onb_remote_feature_notification
}

private fun mqttErrorMessage(error: OnboardingError): Int = when (error) {
    OnboardingError.BROKER_UNREACHABLE -> R.string.onb_mqtt_test_error_unreachable
    OnboardingError.BROKER_BAD_CREDENTIALS -> R.string.onb_mqtt_test_error_bad_credentials
    OnboardingError.BROKER_REFUSED -> R.string.onb_mqtt_test_error_connection_refused
    OnboardingError.TIMEOUT -> R.string.onb_mqtt_test_error_timeout
    OnboardingError.PUBLISH_FORBIDDEN -> R.string.onb_mqtt_test_error_publish_refused
    OnboardingError.SUBSCRIBE_FORBIDDEN -> R.string.onb_mqtt_test_error_subscribe_refused
    else -> R.string.onb_mqtt_test_error_generic
}
