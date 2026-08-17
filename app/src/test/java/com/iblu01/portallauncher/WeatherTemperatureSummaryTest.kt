package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WeatherTemperatureSummaryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `sensor readings are converted to weather entity unit`() {
        val weather = HaEntity(
            "weather.home",
            "sunny",
            JSONObject().put("temperature_unit", "°C").put("temperature", 20),
        )
        val indoorF = HaEntity(
            "sensor.living_temperature",
            "68",
            JSONObject().put("device_class", "temperature").put("unit_of_measurement", "°F"),
        )
        val outdoorC = HaEntity(
            "sensor.outdoor_temperature",
            "10",
            JSONObject().put("device_class", "temperature").put("unit_of_measurement", "°C"),
        )
        val rules = listOf(
            PillRule(indoorF.entityId, PillKind.CLIMATE, "Living room"),
            PillRule(outdoorC.entityId, PillKind.CLIMATE, "Outdoor"),
        )

        val result = PillRepository(context).temperatureSummary(
            rules,
            listOf(weather, indoorF, outdoorC).associateBy(HaEntity::entityId),
        )

        assertEquals("20°C", result.indoorMin)
        assertEquals("20°C", result.indoorMax)
        assertEquals("10°C", result.outdoor)
    }
}
