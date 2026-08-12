package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockScreenSizePolicyTest {
    @Test
    fun `five inch screen uses compact temperature header`() {
        assertTrue(isVerySmallScreen(widthPixels = 1080, heightPixels = 1920, xdpi = 441f, ydpi = 441f))
    }

    @Test
    fun `screen above five inches keeps temperature pill`() {
        assertFalse(isVerySmallScreen(widthPixels = 1080, heightPixels = 2400, xdpi = 420f, ydpi = 420f))
    }

    @Test
    fun `invalid physical metrics safely keep regular layout`() {
        assertFalse(isVerySmallScreen(widthPixels = 1080, heightPixels = 1920, xdpi = 0f, ydpi = 0f))
    }
}
