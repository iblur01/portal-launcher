package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCityTest {
    @Test fun `explicit city wins over location and friendly name`() {
        val entity = weatherEntity(
            JSONObject()
                .put("city", "Nantes")
                .put("location", "Loire-Atlantique")
                .put("friendly_name", "Forecast Home"),
        )

        assertEquals("Nantes", explicitWeatherCity(entity))
    }

    @Test fun `location is accepted but friendly name is never presented as a city`() {
        assertEquals("Paris", explicitWeatherCity(weatherEntity(JSONObject().put("location", "Paris"))))
        assertEquals("", explicitWeatherCity(weatherEntity(JSONObject().put("friendly_name", "Forecast Maison"))))
        assertEquals("", explicitWeatherCity(weatherEntity(JSONObject())))
    }

    private fun weatherEntity(attributes: JSONObject) =
        HaEntity("weather.home", "sunny", attributes)
}
