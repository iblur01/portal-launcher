package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockScreenSizePolicyTest {
    @Test
    fun `five inch screen uses compact temperature header`() {
        assertTrue(isCompactClockScreen(widthPixels = 1080, heightPixels = 1920, xdpi = 441f, ydpi = 441f))
    }

    @Test
    fun `echo show five uses compact clock layout`() {
        assertTrue(isCompactClockScreen(widthPixels = 960, heightPixels = 480, xdpi = 195f, ydpi = 195f))
    }

    @Test
    fun `screen above six inches keeps regular layout`() {
        assertFalse(isCompactClockScreen(widthPixels = 1080, heightPixels = 2400, xdpi = 380f, ydpi = 380f))
    }

    @Test
    fun `invalid physical metrics safely keep regular layout`() {
        assertFalse(isCompactClockScreen(widthPixels = 1080, heightPixels = 1920, xdpi = 0f, ydpi = 0f))
    }

    @Test
    fun `indoor range keeps a single degree symbol`() {
        assertEquals("18–22°", compactIndoorTemperature("18°", "22°"))
        assertEquals("21°", compactIndoorTemperature("21°", "21°"))
        assertEquals(null, compactIndoorTemperature("—", "—"))
        assertEquals("21°", compactIndoorTemperature("—", "21°"))
    }

    @Test
    fun `missing compact temperatures are omitted instead of showing dashes`() {
        assertEquals(null, compactOutdoorTemperature("—", "--°"))
        assertEquals("14°", compactOutdoorTemperature("—", "14°"))
        assertEquals("12°", compactOutdoorTemperature("12°", "14°"))
    }

    @Test
    fun `compact tray uses at most two rows`() {
        assertEquals(3, compactTrayItemLimit(compactScreen = true, expanded = false))
        assertEquals(6, compactTrayItemLimit(compactScreen = true, expanded = true))
        assertEquals(9, compactTrayItemLimit(compactScreen = false, expanded = true))
    }
}
