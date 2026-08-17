package com.iblu01.portallauncher

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherDataTest {
    @Test fun `weather home wins and fallback is lexical`() {
        assertEquals("weather.home", selectWeatherEntityId(listOf("weather.z", "weather.home", "weather.a")))
        assertEquals("weather.a", selectWeatherEntityId(listOf("sensor.x", "weather.z", "weather.a")))
        assertNull(selectWeatherEntityId(listOf("sensor.x")))
    }

    @Test fun `forecast without temperature is omitted instead of becoming zero`() {
        val input = JSONArray()
            .put(JSONObject().put("datetime", "2026-08-17T10:00:00Z").put("condition", "sunny"))
            .put(JSONObject().put("datetime", "2026-08-17T11:00:00Z").put("temperature", 21.5))

        val result = parseForecastPoints(input)

        assertEquals(1, result.size)
        assertEquals(21.5, result.single().temp, 0.0)
    }

    @Test fun `temperature conversion handles both directions`() {
        assertEquals(0.0, convertTemperature(32.0, TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS), 0.0001)
        assertEquals(68.0, convertTemperature(20.0, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT), 0.0001)
    }
}
