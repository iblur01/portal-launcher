package com.iblu01.portallauncher.ui.onboarding

/**
 * What the stored state says about a device, as far as "should the assistant open?" is concerned.
 */
data class OnboardingStatus(
    val completed: Boolean,
    val version: Int,
    /** True when a build older than the assistant already configured a home. */
    val legacyConfigured: Boolean,
)

/**
 * Whether the first-run assistant should open by itself.
 *
 * Two rules, both deliberate:
 * - a device that already had a working Home Assistant setup before this flow existed is treated as
 *   configured, so an update never drops a wall panel into a wizard;
 * - a bump of [ONBOARDING_VERSION] does *not* re-open the flow. New steps are offered from the
 *   settings ("relancer l'assistant"), never forced on top of choices the user already made.
 */
fun shouldRunOnboarding(status: OnboardingStatus): Boolean =
    !status.completed && !status.legacyConfigured

/** The step to resume on: the stored one, or the start of the flow. */
fun resumeStep(storedStep: String?, completed: Boolean): OnboardingStep =
    if (completed) OnboardingStep.COMPLETE else OnboardingStep.from(storedStep)
