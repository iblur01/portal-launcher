package com.iblu01.portallauncher.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The onboarding's branching, exercised without Android. */
class OnboardingNavigationTest {

    private val everything = OnboardingFlags()

    @Test
    fun `full flow visits every step in order`() {
        val visited = generateSequence(OnboardingStep.WELCOME) { nextStep(it, everything) }.toList()
        assertEquals(OnboardingStep.values().toList(), visited)
    }

    @Test
    fun `complete is the last step`() {
        assertNull(nextStep(OnboardingStep.COMPLETE, everything))
    }

    @Test
    fun `welcome has nothing before it`() {
        assertNull(previousStep(OnboardingStep.WELCOME, everything))
    }

    @Test
    fun `back mirrors forward on every step`() {
        OnboardingStep.values().forEach { step ->
            val next = nextStep(step, everything) ?: return@forEach
            assertEquals("back from $next", step, previousStep(next, everything))
        }
    }

    @Test
    fun `the home assistant offer is still shown, and skipping it lands on the app cleanup`() {
        val flags = OnboardingFlags(homeAssistantSkipped = true)
        // The offer itself is never skipped — it is where the user says no.
        assertEquals(OnboardingStep.HOME_ASSISTANT_INTRO, nextStep(OnboardingStep.BACKGROUND, flags))
        assertEquals(OnboardingStep.HIDDEN_APPS, nextStep(OnboardingStep.HOME_ASSISTANT_INTRO, flags))
    }

    @Test
    fun `skipping home assistant removes token pills and mqtt steps`() {
        val flags = OnboardingFlags(homeAssistantSkipped = true)
        val visited = generateSequence(OnboardingStep.WELCOME) { nextStep(it, flags) }.toList()
        listOf(
            OnboardingStep.HOME_ASSISTANT_CREDENTIALS,
            OnboardingStep.HOME_ASSISTANT_TEST,
            OnboardingStep.PILLS_INTRO,
            OnboardingStep.REMOTE_CONTROL,
            OnboardingStep.MQTT_CONFIGURATION,
            OnboardingStep.MQTT_TEST,
        ).forEach { assertFalse("$it should be skipped", it in visited) }
        assertTrue(OnboardingStep.HOME_ASSISTANT_INTRO in visited)
        assertTrue(OnboardingStep.COMPLETE in visited)
    }

    @Test
    fun `back from hidden apps returns to the intro when home assistant was skipped`() {
        val flags = OnboardingFlags(homeAssistantSkipped = true)
        assertEquals(OnboardingStep.HOME_ASSISTANT_INTRO, previousStep(OnboardingStep.HIDDEN_APPS, flags))
    }

    @Test
    fun `home assistant success leads to the pills introduction`() {
        assertEquals(OnboardingStep.PILLS_INTRO, nextStep(OnboardingStep.HOME_ASSISTANT_TEST, everything))
    }

    @Test
    fun `skipping mqtt keeps the home assistant steps and lands on hidden apps`() {
        val flags = OnboardingFlags(mqttSkipped = true)
        val visited = generateSequence(OnboardingStep.WELCOME) { nextStep(it, flags) }.toList()
        assertTrue(OnboardingStep.HOME_ASSISTANT_TEST in visited)
        assertTrue(OnboardingStep.PILLS_INTRO in visited)
        assertFalse(OnboardingStep.MQTT_CONFIGURATION in visited)
        assertFalse(OnboardingStep.MQTT_TEST in visited)
        assertEquals(OnboardingStep.HIDDEN_APPS, nextStep(OnboardingStep.REMOTE_CONTROL, flags))
    }

    @Test
    fun `a successful mqtt configuration leads to app cleanup`() {
        assertEquals(OnboardingStep.HIDDEN_APPS, nextStep(OnboardingStep.MQTT_TEST, everything))
    }

    @Test
    fun `skipping app cleanup still reaches the gestures and the summary`() {
        val flags = OnboardingFlags(appCleanupSkipped = true)
        assertEquals(OnboardingStep.GESTURES, nextStep(OnboardingStep.MQTT_TEST, flags))
        assertEquals(OnboardingStep.COMPLETE, nextStep(OnboardingStep.GESTURES, flags))
    }

    @Test
    fun `chapters group the steps as advertised`() {
        assertEquals(OnboardingChapter.LAUNCHER, OnboardingStep.BACKGROUND.chapter)
        assertEquals(OnboardingChapter.HOME, OnboardingStep.MQTT_TEST.chapter)
        assertEquals(OnboardingChapter.FINISH, OnboardingStep.COMPLETE.chapter)
    }

    @Test
    fun `chapter progress ignores skipped steps`() {
        val flags = OnboardingFlags(homeAssistantSkipped = true)
        assertEquals(listOf(OnboardingStep.HOME_ASSISTANT_INTRO), visibleSteps(OnboardingChapter.HOME, flags))
        assertEquals(0, indexInChapter(OnboardingStep.HOME_ASSISTANT_INTRO, flags))
        assertEquals(3, indexInChapter(OnboardingStep.BACKGROUND, flags))
    }

    @Test
    fun `an unknown persisted step restarts the flow`() {
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.from("SOMETHING_ELSE"))
        assertEquals(OnboardingStep.WELCOME, OnboardingStep.from(null))
        assertEquals(OnboardingStep.GRID, OnboardingStep.from("GRID"))
    }

    @Test
    fun `a persisted step is resumed after an interruption`() {
        assertEquals(OnboardingStep.BACKGROUND, resumeStep("BACKGROUND", completed = false))
    }

    @Test
    fun `a fresh install starts on welcome`() {
        assertEquals(OnboardingStep.WELCOME, resumeStep("", completed = false))
        assertTrue(shouldRunOnboarding(OnboardingStatus(completed = false, version = 0, legacyConfigured = false)))
    }

    @Test
    fun `a completed onboarding never runs again`() {
        assertFalse(shouldRunOnboarding(OnboardingStatus(completed = true, version = ONBOARDING_VERSION, legacyConfigured = false)))
    }

    @Test
    fun `a newer onboarding version does not reopen the flow over existing choices`() {
        assertFalse(
            shouldRunOnboarding(
                OnboardingStatus(completed = true, version = ONBOARDING_VERSION - 1, legacyConfigured = false)
            )
        )
    }

    @Test
    fun `a device configured before the assistant existed is left alone`() {
        assertFalse(shouldRunOnboarding(OnboardingStatus(completed = false, version = 0, legacyConfigured = true)))
    }
}
