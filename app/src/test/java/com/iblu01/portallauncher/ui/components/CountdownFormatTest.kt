package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownFormatTest {

    @Test
    fun `the counter rounds up so it never shows zero while time is left`() {
        assertEquals("1:30", formatRemaining(90_000L))
        assertEquals("0:01", formatRemaining(1L))
        assertEquals("0:00", formatRemaining(0L))
        assertEquals("0:10", formatRemaining(9_600L))
    }

    @Test
    fun `hours appear only once there are hours`() {
        assertEquals("59:59", formatRemaining(3_599_000L))
        assertEquals("1:00:00", formatRemaining(3_600_000L))
        assertEquals("2:05:03", formatRemaining(7_503_000L))
    }
}
