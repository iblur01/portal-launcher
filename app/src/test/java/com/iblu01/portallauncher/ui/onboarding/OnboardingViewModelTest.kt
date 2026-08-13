package com.iblu01.portallauncher.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The flow as the user drives it: what gets persisted, what survives a restart, and what each
 * "skip" is allowed to take with it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    private lateinit var context: Context
    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = Prefs(context)
        prefs.resetOnboarding()
        prefs.hiddenApps = emptySet()
    }

    private fun viewModel() = OnboardingViewModel(
        context = context,
        prefs = prefs,
        capabilities = OnboardingCapabilities(context),
        haTester = HaOnboardingTester(),
        mqttTester = MqttOnboardingTester(),
    )

    @Test
    fun `a fresh install starts on the welcome screen`() {
        assertEquals(OnboardingStep.WELCOME, viewModel().state.value.step)
    }

    @Test
    fun `every transition is persisted so the flow can resume where it stopped`() {
        val model = viewModel()
        model.continueFromWelcome()
        model.goNext() // GRID

        assertEquals(OnboardingStep.GRID.name, prefs.onboardingStep)
        // A process death here: a brand new ViewModel picks the step back up.
        assertEquals(OnboardingStep.GRID, viewModel().state.value.step)
    }

    @Test
    fun `a language change keeps the progress because it lives in prefs, not in the composition`() {
        val model = viewModel()
        model.continueFromWelcome()
        model.goNext()
        model.goNext() // BACKGROUND

        prefs.appLanguage = "en" // what LanguagePage does before restarting the process

        assertEquals(OnboardingStep.BACKGROUND, viewModel().state.value.step)
    }

    @Test
    fun `skipping the whole setup completes the onboarding and keeps the defaults`() {
        val model = viewModel()
        val defaultScale = prefs.gridScale
        val defaultBackground = prefs.backgroundMode

        model.skipOnboarding()

        assertTrue(prefs.onboardingCompleted)
        assertEquals(ONBOARDING_VERSION, prefs.onboardingVersion)
        assertEquals("", prefs.onboardingStep)
        assertEquals(defaultScale, prefs.gridScale, 0.001f)
        assertEquals(defaultBackground, prefs.backgroundMode)
        assertFalse(
            shouldRunOnboarding(
                OnboardingStatus(prefs.onboardingCompleted, prefs.onboardingVersion, legacyConfigured = false)
            )
        )
    }

    @Test
    fun `choices are written as they are made, not at the end`() {
        val model = viewModel()
        model.selectGridPreset(GridPreset.MORE_APPS)
        model.selectBackground("system", configured = true)

        assertEquals(GridPreset.MORE_APPS.scale, prefs.gridScale, 0.001f)
        assertEquals("system", prefs.backgroundMode)
        assertFalse(prefs.onboardingCompleted)
    }

    @Test
    fun `skipping home assistant scopes to home assistant, pills and mqtt only`() {
        val model = viewModel()
        model.skipHomeAssistant()

        assertTrue(prefs.homeAssistantOnboardingSkipped)
        assertFalse(prefs.mqttOnboardingSkipped)
        assertFalse(prefs.appCleanupOnboardingSkipped)
        assertEquals(OnboardingStep.HIDDEN_APPS, model.state.value.step)
        assertTrue(model.state.value.flags.homeAssistantSkipped)
    }

    @Test
    fun `skipping mqtt leaves the home assistant connection alone`() {
        val model = viewModel()
        prefs.haUrl = "http://192.168.1.20:8123"
        prefs.haToken = "token-value"

        model.skipMqtt()

        assertTrue(prefs.mqttOnboardingSkipped)
        assertFalse(prefs.homeAssistantOnboardingSkipped)
        assertEquals("http://192.168.1.20:8123", prefs.haUrl)
        assertEquals("token-value", prefs.haToken)
        assertEquals(OnboardingStep.HIDDEN_APPS, model.state.value.step)
    }

    @Test
    fun `a failed connection keeps what the user typed`() {
        val model = viewModel()
        model.setHaUrl("http://192.168.1.99:8123")
        model.setHaToken("  typed-token  ")

        model.editHomeAssistantCredentials()

        assertEquals("http://192.168.1.99:8123", model.state.value.haUrl)
        assertEquals("typed-token", model.state.value.haToken)
        assertEquals(OnboardingStep.HOME_ASSISTANT_CREDENTIALS, model.state.value.step)
    }

    @Test
    fun `returning from phone setup reloads externally saved connection values`() {
        val model = viewModel()
        prefs.haUrl = "http://192.168.1.44:8123"
        prefs.haToken = "phone-token"
        prefs.brokerHost = "192.168.1.45"
        prefs.brokerPort = 1884

        model.refreshExternalConfiguration()

        assertEquals("http://192.168.1.44:8123", model.state.value.haUrl)
        assertEquals("phone-token", model.state.value.haToken)
        assertEquals("192.168.1.45", model.state.value.mqttHost)
        assertEquals(1884, model.state.value.mqttPort)
    }

    @Test
    fun `an unfinished flow never claims to be complete`() {
        val model = viewModel()
        model.continueFromWelcome()

        assertFalse(prefs.onboardingCompleted)
        assertTrue(
            shouldRunOnboarding(
                OnboardingStatus(prefs.onboardingCompleted, prefs.onboardingVersion, legacyConfigured = false)
            )
        )
    }

    @Test
    fun `protected apps cannot be hidden even when the screen asks for it`() {
        val model = viewModel()
        model.loadApps()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()

        val everything = model.state.value.apps.map { it.packageName }.toSet() + context.packageName
        model.applyHiddenApps(everything)

        assertFalse(context.packageName in prefs.hiddenApps)
        assertFalse(context.packageName in model.state.value.hiddenPackages)
    }

    @Test
    fun `hiding nothing is recorded as a skip of that step only`() {
        val model = viewModel()
        model.skipHiddenApps()

        assertTrue(prefs.appCleanupOnboardingSkipped)
        assertTrue(prefs.hiddenApps.isEmpty())
        assertEquals(OnboardingStep.GESTURES, model.state.value.step)
    }

    @Test
    fun `finishing marks the version so a later bump never reopens the flow by itself`() {
        val model = viewModel()
        model.completeOnboarding()

        assertEquals(ONBOARDING_VERSION, prefs.onboardingVersion)
        assertTrue(prefs.gestureHintsSeen)
        assertFalse(
            shouldRunOnboarding(
                // A newer content version than the one the user completed.
                OnboardingStatus(prefs.onboardingCompleted, prefs.onboardingVersion, legacyConfigured = false)
            )
        )
    }

    @Test
    fun `relaunching from the settings clears the progress but keeps every setting`() {
        val model = viewModel()
        model.selectGridPreset(GridPreset.LARGE_ICONS)
        model.selectBackground("system", configured = true)
        model.completeOnboarding()

        prefs.resetOnboarding() // what the settings row does, through OnboardingActivity

        assertFalse(prefs.onboardingCompleted)
        assertEquals(0, prefs.onboardingVersion)
        assertEquals(GridPreset.LARGE_ICONS.scale, prefs.gridScale, 0.001f)
        assertEquals("system", prefs.backgroundMode)
        assertEquals(OnboardingStep.WELCOME, viewModel().state.value.step)
    }

    @Test
    fun `the mqtt broker is suggested from the home assistant host, never discovered`() {
        val model = viewModel()
        model.setHaUrl("http://192.168.1.20:8123")
        model.configureMqtt()

        assertEquals("192.168.1.20", model.state.value.mqttHost)
        assertEquals(1883, model.state.value.mqttPort)
        // Nothing about credentials can be derived from Home Assistant.
        assertEquals("", model.state.value.mqttUsername)
        assertEquals("", model.state.value.mqttPassword)
    }

    @Test
    fun `the state never prints a secret`() {
        val model = viewModel()
        model.setHaToken("super-secret-token")
        model.setMqttPassword("broker-password")

        val printed = model.state.value.toString()

        assertFalse(printed.contains("super-secret-token"))
        assertFalse(printed.contains("broker-password"))
    }
}
