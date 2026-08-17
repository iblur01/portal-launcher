package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class WeatherForecastLabelTest {
    @Test fun `hour label follows 24 hour preference`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("14:05", forecastPointLabel("2026-08-17T14:05:00Z", true, true, Locale.ENGLISH))
    }

    @Test fun `hour label follows 12 hour preference`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("2 PM", forecastPointLabel("2026-08-17T14:05:00Z", true, false, Locale.ENGLISH))
    }

    @Test fun `day label follows locale`() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        assertEquals("Lun.", forecastPointLabel("2026-08-17T14:05:00Z", false, true, Locale.FRENCH))
    }
}
