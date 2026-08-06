package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.onboarding.OnboardingError
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.SummaryCategory
import com.iblu01.portallauncher.ui.onboarding.TestPhase
import com.iblu01.portallauncher.ui.onboarding.TestState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.onboarding.components.SelectedCheck
import com.iblu01.portallauncher.ui.onboarding.components.TestPhaseList
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * The connection attempt, as its own screen: what is being tried, what was found, or what failed
 * and what can be done about it.
 *
 * No back button — going back happens through "edit details", which is what keeps the address and
 * token the user typed.
 */
@Composable
fun HomeAssistantTestStep(
    state: OnboardingUiState,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val test = state.haTest) {
        is TestState.Success -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(R.string.onb_ha_test_success_title),
            description = stringResource(
                R.string.onb_ha_test_success_body_format,
                test.summary.entityCount,
            ),
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                test.summary.breakdown.forEach { (category, count) ->
                    val label = categoryPlural(category)
                    if (label != null) {
                        Text(
                            pluralStringResource(label, count, count),
                            style = AppleTypography.titleMedium,
                            color = AppleColors.secondary,
                        )
                    }
                }
            }
        }

        is TestState.Failure -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(errorTitle(test.error)),
            description = stringResource(errorBody(test.error)),
            modifier = modifier,
            navigation = {
                OnboardingNavigationBar(
                    onBack = null,
                    primaryLabel = stringResource(R.string.onb_common_nav_retry),
                    onPrimary = onRetry,
                    skipLabel = stringResource(R.string.onb_common_nav_set_up_later),
                    onSkip = onSkip,
                    secondaryLabel = stringResource(R.string.onb_common_nav_edit_details),
                    onSecondary = onEdit,
                )
            },
        ) {
            // The address is echoed back so the user can see what was actually tried; the token
            // never appears, here or anywhere else.
            Text(state.haUrl, style = AppleTypography.bodySmall, color = AppleColors.tertiary)
        }

        else -> OnboardingScaffold(
            step = state.step,
            flags = state.flags,
            title = stringResource(R.string.onb_ha_test_running_title),
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                TestPhaseList(
                    phases = listOf(
                        stringResource(R.string.onb_ha_test_phase_address),
                        stringResource(R.string.onb_ha_test_phase_auth),
                        stringResource(R.string.onb_ha_test_phase_devices),
                    ),
                    currentIndex = phaseIndex(test),
                )
            }
        }
    }
}

private fun phaseIndex(test: TestState): Int = when ((test as? TestState.Running)?.phase) {
    TestPhase.CHECKING_ADDRESS -> 0
    TestPhase.AUTHENTICATING -> 1
    TestPhase.FETCHING_ENTITIES -> 2
    else -> 0
}

private fun categoryPlural(category: SummaryCategory): Int? = when (category) {
    SummaryCategory.LIGHTS -> R.plurals.onb_ha_test_category_lights
    SummaryCategory.OPENINGS -> R.plurals.onb_ha_test_category_openings
    SummaryCategory.MEDIA -> R.plurals.onb_ha_test_category_media_players
    SummaryCategory.ALARM -> R.plurals.onb_ha_test_category_alarms
    SummaryCategory.WEATHER -> R.plurals.onb_ha_test_category_weather
    SummaryCategory.CLIMATE -> R.plurals.onb_ha_test_category_thermostats
    SummaryCategory.OTHER -> null
}

private fun errorTitle(error: OnboardingError): Int = when (error) {
    OnboardingError.UNAUTHORIZED -> R.string.onb_ha_test_error_token_title
    OnboardingError.TIMEOUT -> R.string.onb_ha_test_error_timeout_title
    OnboardingError.INVALID_CERTIFICATE -> R.string.onb_ha_test_error_certificate_title
    OnboardingError.INVALID_RESPONSE -> R.string.onb_ha_test_error_invalid_response_title
    OnboardingError.HOST_UNREACHABLE -> R.string.onb_ha_test_error_unreachable_title
    else -> R.string.onb_ha_test_error_generic_title
}

private fun errorBody(error: OnboardingError): Int = when (error) {
    OnboardingError.UNAUTHORIZED -> R.string.onb_ha_test_error_token_body
    OnboardingError.TIMEOUT -> R.string.onb_ha_test_error_timeout_body
    OnboardingError.INVALID_CERTIFICATE -> R.string.onb_ha_test_error_certificate_body
    OnboardingError.INVALID_RESPONSE -> R.string.onb_ha_test_error_invalid_response_body
    OnboardingError.HOST_UNREACHABLE -> R.string.onb_ha_test_error_unreachable_body
    else -> R.string.onb_ha_test_error_generic_body
}
