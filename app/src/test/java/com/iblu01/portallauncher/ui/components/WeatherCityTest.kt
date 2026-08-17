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

        assertEquals("Nantes", weatherCity(entity))
    }

    @Test fun `location then friendly name provide fallbacks`() {
        assertEquals("Paris", weatherCity(weatherEntity(JSONObject().put("location", "Paris"))))
        assertEquals("Maison", weatherCity(weatherEntity(JSONObject().put("friendly_name", "Maison"))))
        assertEquals("", weatherCity(weatherEntity(JSONObject())))
    }

    private fun weatherEntity(attributes: JSONObject) =
        HaEntity("weather.home", "sunny", attributes)
}
