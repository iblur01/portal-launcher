package com.iblu01.portallauncher.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingGridTest {

    // A 10" landscape tablet page, the reference device for the launcher.
    private val width = 1024f
    private val height = 600f

    @Test
    fun `balanced is the default preset`() {
        assertEquals(GridPreset.BALANCED, GridPreset.forScale(1f))
        assertEquals(1f, GridPreset.BALANCED.scale, 0.001f)
    }

    @Test
    fun `a hand-tuned scale matches no preset`() {
        assertNull(GridPreset.forScale(1.11f))
    }

    @Test
    fun `bigger icons mean fewer cells`() {
        val large = specForScale(width, height, GridPreset.LARGE_ICONS.scale)
        val balanced = specForScale(width, height, GridPreset.BALANCED.scale)
        val dense = specForScale(width, height, GridPreset.MORE_APPS.scale)

        assertTrue(large.columns <= balanced.columns)
        assertTrue(balanced.columns <= dense.columns)
        assertTrue(large.cellsPerPage < dense.cellsPerPage)
    }

    @Test
    fun `the scale is clamped to what Prefs accepts`() {
        assertEquals(specForScale(width, height, 0.7f), specForScale(width, height, 0.1f))
        assertEquals(specForScale(width, height, 1.3f), specForScale(width, height, 9f))
    }

    @Test
    fun `a tiny screen still yields a usable grid`() {
        val spec = specForScale(320f, 240f, GridPreset.LARGE_ICONS.scale)
        assertTrue(spec.columns >= 3)
        assertTrue(spec.rows >= 2)
    }
}
