package com.iblu01.portallauncher.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingChannelPolicyTest {
    private fun squareAtDiagonal(diagonal: Float): PhysicalScreenMetrics {
        val side = diagonal / kotlin.math.sqrt(2f)
        return PhysicalScreenMetrics(1000, 1000, 1000f / side, 1000f / side)
    }

    @Test fun `screen below six inches requires web`() = assertEquals(
        OnboardingChannelAvailability.WEB_REQUIRED,
        onboardingChannelAvailability(squareAtDiagonal(5.99f)),
    )

    @Test fun `exactly six inches allows either channel`() = assertEquals(
        OnboardingChannelAvailability.DEVICE_OR_WEB,
        onboardingChannelAvailability(squareAtDiagonal(6f)),
    )

    @Test fun `screen above six inches allows either channel`() = assertEquals(
        OnboardingChannelAvailability.DEVICE_OR_WEB,
        onboardingChannelAvailability(squareAtDiagonal(6.01f)),
    )

    @Test fun `invalid dpi fails closed to web`() = assertEquals(
        OnboardingChannelAvailability.WEB_REQUIRED,
        onboardingChannelAvailability(PhysicalScreenMetrics(1920, 1080, 0f, 0f)),
    )
}
