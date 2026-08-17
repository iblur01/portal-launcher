package com.iblu01.portallauncher

import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import org.junit.Assert.assertEquals
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
}
