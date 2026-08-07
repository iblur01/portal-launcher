package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateEntityContractTest {
    @Test
    fun `maps a full Home Assistant range thermostat contract`() {
        val entity = HaEntity("climate.living_room", "heat_cool", JSONObject()
            .put("supported_features", ClimateFeature.TARGET_TEMPERATURE_RANGE)
            .put("hvac_modes", JSONArray(listOf("off", "heat", "heat_cool")))
            .put("hvac_action", "heating")
            .put("current_temperature", 20.4)
            .put("target_temp_low", 19.5)
            .put("target_temp_high", 22.5)
            .put("min_temp", 7)
            .put("max_temp", 30)
            .put("target_temp_step", 0.5)
            .put("temperature_unit", "°C"))

        val contract = entity.toClimateContract()

        assertEquals("heat_cool", contract.hvacMode)
        assertEquals("heating", contract.hvacAction)
        assertEquals(listOf("off", "heat", "heat_cool"), contract.availableModes)
        assertEquals(19.5f, contract.targetLow)
        assertEquals(22.5f, contract.targetHigh)
        assertEquals("°C", contract.temperatureUnit)
        assertTrue(contract.supportsTargetRange)
        assertFalse(contract.supportsSingleTarget)
    }

    @Test
    fun `infers legacy integration capabilities from exposed attributes`() {
        val entity = HaEntity("climate.bedroom", "heat", JSONObject()
            .put("temperature", 21.0)
            .put("current_temperature", 20.0))

        val contract = entity.toClimateContract()

        assertTrue(contract.supportsSingleTarget)
        assertFalse(contract.supportsTargetRange)
        assertEquals(0.5f, contract.temperatureStep)
        assertEquals("°", contract.temperatureUnit)
    }

    @Test
    fun `keeps an integration without setpoints read only`() {
        val entity = HaEntity("climate.ventilation", "fan_only", JSONObject()
            .put("hvac_modes", JSONArray(listOf("off", "fan_only")))
            .put("current_temperature", 23.0))

        val contract = entity.toClimateContract()

        assertFalse(contract.hasTemperatureControl)
        assertEquals(listOf("off", "fan_only"), contract.availableModes)
    }
}
