package com.iblu01.portallauncher.ui.onboarding

import kotlin.math.sqrt

enum class OnboardingChannel { DEVICE, WEB;

    companion object {
        fun from(value: String?): OnboardingChannel? = entries.firstOrNull { it.name == value }
    }
}

enum class OnboardingChannelAvailability { WEB_REQUIRED, DEVICE_OR_WEB }

data class PhysicalScreenMetrics(
    val widthPixels: Int,
    val heightPixels: Int,
    val xdpi: Float,
    val ydpi: Float,
)

private const val WEB_REQUIRED_BELOW_INCHES = 6f

/**
 * Selects the first-run channels from the panel's physical diagonal.
 *
 * Bad DPI values are common on inexpensive wall panels. They deliberately fail closed to the Web
 * flow: squeezing the full assistant onto an unknown display is the less recoverable outcome.
 */
fun onboardingChannelAvailability(metrics: PhysicalScreenMetrics): OnboardingChannelAvailability {
    if (
        metrics.widthPixels <= 0 || metrics.heightPixels <= 0 ||
        !metrics.xdpi.isFinite() || !metrics.ydpi.isFinite() ||
        metrics.xdpi <= 0f || metrics.ydpi <= 0f
    ) return OnboardingChannelAvailability.WEB_REQUIRED

    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonal = sqrt(widthInches * widthInches + heightInches * heightInches)
    return if (diagonal < WEB_REQUIRED_BELOW_INCHES) {
        OnboardingChannelAvailability.WEB_REQUIRED
    } else {
        OnboardingChannelAvailability.DEVICE_OR_WEB
    }
}
