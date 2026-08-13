package com.iblu01.portallauncher.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.iblu01.portallauncher.ui.onboarding.screens.BackgroundStep
import com.iblu01.portallauncher.ui.onboarding.screens.CompletionStep
import com.iblu01.portallauncher.ui.onboarding.screens.GesturesStep
import com.iblu01.portallauncher.ui.onboarding.screens.GridStep
import com.iblu01.portallauncher.ui.onboarding.screens.HiddenAppsStep
import com.iblu01.portallauncher.ui.onboarding.screens.HomeAssistantCredentialsStep
import com.iblu01.portallauncher.ui.onboarding.screens.HomeAssistantIntroStep
import com.iblu01.portallauncher.ui.onboarding.screens.HomeAssistantTestStep
import com.iblu01.portallauncher.ui.onboarding.screens.MqttConfigurationStep
import com.iblu01.portallauncher.ui.onboarding.screens.MqttTestStep
import com.iblu01.portallauncher.ui.onboarding.screens.PillsIntroStep
import com.iblu01.portallauncher.ui.onboarding.screens.RemoteControlStep
import com.iblu01.portallauncher.ui.onboarding.screens.SystemSetupStep
import com.iblu01.portallauncher.ui.onboarding.screens.WelcomeStep
import com.iblu01.portallauncher.ui.theme.AppleMotion

/**
 * Root of the first-run assistant: one step on screen at a time, chosen by
 * [OnboardingUiState.step], with a soft lateral transition between them.
 *
 * Steps never touch `Prefs` or the ViewModel directly — every one of them receives the state it
 * renders and the intents it can emit, so the whole flow's behaviour lives in
 * [OnboardingViewModel] and stays testable.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
    onOpenSystemSetting: (Capability) -> Unit,
    onFinish: (openSettings: Boolean) -> Unit,
    onConfigureWithPhone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = state.step,
        transitionSpec = {
            val forward = targetState.ordinal >= initialState.ordinal
            val distance = if (forward) 1 else -1
            (
                slideInHorizontally(tween(AppleMotion.SLIDE_DURATION)) { width -> distance * width / 6 } +
                    fadeIn(tween(AppleMotion.FADE_DURATION))
                ) togetherWith (
                slideOutHorizontally(tween(AppleMotion.SLIDE_DURATION)) { width -> -distance * width / 6 } +
                    fadeOut(tween(AppleMotion.FADE_DURATION))
                ) using SizeTransform(clip = false)
        },
        modifier = modifier.fillMaxSize(),
        label = "onboarding-step",
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                state = state,
                onContinue = viewModel::continueFromWelcome,
                onSkipEverything = {
                    viewModel.skipOnboarding()
                    onFinish(false)
                },
            )

            OnboardingStep.SYSTEM_SETUP -> SystemSetupStep(
                state = state,
                onOpenSetting = onOpenSystemSetting,
                onProvisionWithRoot = viewModel::provisionWithRoot,
                onAcknowledgeGrant = viewModel::acknowledgeGrant,
                adbCommand = viewModel.adbSetHomeCommand(),
                onBack = viewModel::goBack,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.GRID -> GridStep(
                state = state,
                onSelectPreset = viewModel::selectGridPreset,
                onSelectScale = viewModel::selectGridScale,
                onSetManual = viewModel::setGridManual,
                onBack = viewModel::goBack,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.BACKGROUND -> BackgroundStep(
                state = state,
                onSelectBackground = viewModel::selectBackground,
                onSetOpacity = viewModel::setBackgroundOpacity,
                onBack = viewModel::goBack,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.HOME_ASSISTANT_INTRO -> HomeAssistantIntroStep(
                state = state,
                onStartDiscovery = viewModel::startHomeDiscovery,
                onStopDiscovery = viewModel::stopHomeDiscovery,
                onSelectHome = viewModel::selectDiscoveredHome,
                onManual = viewModel::chooseManualHome,
                onConfigureWithPhone = onConfigureWithPhone,
                onBack = viewModel::goBack,
                onSkip = viewModel::skipHomeAssistant,
            )

            OnboardingStep.HOME_ASSISTANT_CREDENTIALS -> HomeAssistantCredentialsStep(
                state = state,
                onUrlChange = viewModel::setHaUrl,
                onTokenChange = viewModel::setHaToken,
                onTest = viewModel::testHomeAssistant,
                onBack = viewModel::goBack,
                onAbandon = viewModel::abandonHomeAssistantCredentials,
            )

            OnboardingStep.HOME_ASSISTANT_TEST -> HomeAssistantTestStep(
                state = state,
                onRetry = viewModel::testHomeAssistant,
                onEdit = viewModel::editHomeAssistantCredentials,
                onSkip = viewModel::skipHomeAssistant,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.PILLS_INTRO -> PillsIntroStep(
                state = state,
                onAcceptRecommended = viewModel::acceptRecommendedPills,
                onLoadPillOptions = viewModel::loadPillCandidates,
                onSetPillEnabled = viewModel::setPillEnabled,
                onSetAllPillsEnabled = viewModel::setAllPillsEnabled,
                onBack = viewModel::goBack,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.REMOTE_CONTROL -> RemoteControlStep(
                state = state,
                onConfigure = viewModel::configureMqtt,
                onLater = viewModel::skipMqtt,
                onBack = viewModel::goBack,
            )

            OnboardingStep.MQTT_CONFIGURATION -> MqttConfigurationStep(
                state = state,
                onStartDiscovery = viewModel::startBrokerDiscovery,
                onStopDiscovery = viewModel::stopBrokerDiscovery,
                onSelectBroker = viewModel::selectDiscoveredBroker,
                onHostChange = viewModel::setMqttHost,
                onPortChange = viewModel::setMqttPort,
                onAuthEnabledChange = viewModel::setMqttAuthEnabled,
                onUsernameChange = viewModel::setMqttUsername,
                onPasswordChange = viewModel::setMqttPassword,
                onDeviceNameChange = viewModel::setMqttDeviceName,
                onTest = viewModel::testMqtt,
                onBack = viewModel::goBack,
                onLater = viewModel::skipMqtt,
            )

            OnboardingStep.MQTT_TEST -> MqttTestStep(
                state = state,
                onRetry = viewModel::testMqtt,
                onEdit = viewModel::editMqttConfiguration,
                onLater = viewModel::skipMqtt,
                onContinue = viewModel::goNext,
            )

            OnboardingStep.HIDDEN_APPS -> HiddenAppsStep(
                state = state,
                onLoadApps = viewModel::loadApps,
                onApply = viewModel::applyHiddenApps,
                onHideNothing = viewModel::skipHiddenApps,
                onBack = viewModel::goBack,
            )

            OnboardingStep.GESTURES -> GesturesStep(
                state = state,
                onUnderstood = viewModel::acknowledgeGestures,
                onBack = viewModel::goBack,
            )

            OnboardingStep.COMPLETE -> CompletionStep(
                state = state,
                onDiscover = {
                    viewModel.completeOnboarding()
                    onFinish(false)
                },
                onOpenSettings = {
                    viewModel.completeOnboarding()
                    onFinish(true)
                },
                onBack = viewModel::goBack,
            )
        }
    }
}
