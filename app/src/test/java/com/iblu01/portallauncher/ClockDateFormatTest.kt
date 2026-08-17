package com.iblu01.portallauncher

import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockDateFormatTest {
    @Test fun `stored date formats round trip by key`() {
        ClockDateFormat.entries.forEach { format ->
            assertEquals(format, ClockDateFormat.fromKey(format.key))
        }
    }

    @Test fun `unknown stored date format keeps the historical long layout`() {
        assertEquals(ClockDateFormat.LONG, ClockDateFormat.fromKey("future_format"))
        assertEquals("EEEE d MMMM", ClockDateFormat.LONG.fullPattern)
        assertEquals("EEE d MMM", ClockDateFormat.LONG.compactPattern)
    }

    @Test fun `textual and numeric layouts expose stable unique preference keys`() {
        assertTrue(ClockDateFormat.entries.size >= 9)
        assertEquals(ClockDateFormat.entries.size, ClockDateFormat.entries.map { it.key }.distinct().size)
        assertTrue(ClockDateFormat.entries.any { "EEEE" in it.fullPattern })
        assertTrue(ClockDateFormat.entries.any { it.fullPattern == "MMMM d" })
        assertTrue(ClockDateFormat.entries.any { it.fullPattern == "dd/MM/yyyy" })
    }
}
