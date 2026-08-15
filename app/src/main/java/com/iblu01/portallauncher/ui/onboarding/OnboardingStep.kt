package com.iblu01.portallauncher.ui.onboarding

/**
 * One screen of the first-run flow.
 *
 * The name is what gets persisted ([com.iblu01.portallauncher.Prefs.onboardingStep]), so entries
 * must not be renamed without bumping [ONBOARDING_VERSION] — a stored name that no longer resolves
 * falls back to [WELCOME].
 */
enum class OnboardingStep {
    WELCOME,
    SYSTEM_SETUP,
    GRID,
    BACKGROUND,
    HOME_ASSISTANT_INTRO,
    HOME_ASSISTANT_CREDENTIALS,
    HOME_ASSISTANT_TEST,
    PILLS_INTRO,
    REMOTE_CONTROL,
    MQTT_CONFIGURATION,
    MQTT_TEST,
    HIDDEN_APPS,
    TAP_APP,
    GESTURES,
    COMPLETE;

    val chapter: OnboardingChapter
        get() = when (this) {
            WELCOME, SYSTEM_SETUP, GRID, BACKGROUND -> OnboardingChapter.LAUNCHER
            HOME_ASSISTANT_INTRO, HOME_ASSISTANT_CREDENTIALS, HOME_ASSISTANT_TEST,
            PILLS_INTRO, REMOTE_CONTROL, MQTT_CONFIGURATION, MQTT_TEST -> OnboardingChapter.HOME
            HIDDEN_APPS, TAP_APP, GESTURES, COMPLETE -> OnboardingChapter.FINISH
        }

    companion object {
        /** Parses a persisted step name. Anything unknown (older/newer build) restarts the flow. */
        fun from(value: String?): OnboardingStep =
            values().firstOrNull { it.name == value } ?: WELCOME
    }
}

/** The three phases shown by the progress indicator, instead of a "step 4 of 13" counter. */
enum class OnboardingChapter { LAUNCHER, HOME, FINISH }

/**
 * Version of the onboarding *content*. Bumped only when the flow changes enough that a user who
 * already finished should be offered it again; a bump never silently overwrites existing settings
 * (see [com.iblu01.portallauncher.Prefs.onboardingVersion]).
 */
const val ONBOARDING_VERSION = 1

/**
 * Which optional branches the user opted out of. Drives every transition, so navigation stays a
 * pure function of (step, flags) and is testable without Android.
 */
data class OnboardingFlags(
    val homeAssistantSkipped: Boolean = false,
    val mqttSkipped: Boolean = false,
    val appCleanupSkipped: Boolean = false,
)

/** Steps [flags] takes out of the flow entirely. */
private fun OnboardingFlags.isSkipped(step: OnboardingStep): Boolean = when (step) {
    OnboardingStep.HOME_ASSISTANT_CREDENTIALS,
    OnboardingStep.HOME_ASSISTANT_TEST,
    OnboardingStep.PILLS_INTRO,
    OnboardingStep.REMOTE_CONTROL -> homeAssistantSkipped

    // MQTT also disappears with Home Assistant: without a home there is nothing to control from.
    OnboardingStep.MQTT_CONFIGURATION,
    OnboardingStep.MQTT_TEST -> homeAssistantSkipped || mqttSkipped

    OnboardingStep.HIDDEN_APPS -> appCleanupSkipped

    else -> false
}

private val ORDER = OnboardingStep.values().toList()

/** The next visible step, or null when [current] is the last one. */
fun nextStep(current: OnboardingStep, flags: OnboardingFlags = OnboardingFlags()): OnboardingStep? =
    ORDER.drop(ORDER.indexOf(current) + 1).firstOrNull { !flags.isSkipped(it) }

/** The previous visible step, or null when [current] is the first one. */
fun previousStep(current: OnboardingStep, flags: OnboardingFlags = OnboardingFlags()): OnboardingStep? =
    ORDER.take(ORDER.indexOf(current)).lastOrNull { !flags.isSkipped(it) }

/** Steps of [chapter] that are still part of the flow, for the per-chapter progress dots. */
fun visibleSteps(chapter: OnboardingChapter, flags: OnboardingFlags = OnboardingFlags()): List<OnboardingStep> =
    ORDER.filter { it.chapter == chapter && !flags.isSkipped(it) }

/** 0-based position of [step] inside its chapter, for the progress dots. */
fun indexInChapter(step: OnboardingStep, flags: OnboardingFlags = OnboardingFlags()): Int =
    visibleSteps(step.chapter, flags).indexOf(step).coerceAtLeast(0)
