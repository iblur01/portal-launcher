package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericHaEntityContractTest {
    private fun entity(domain: String, state: String, attrs: String) =
        HaEntity("$domain.test", state, JSONObject(attrs))

    @Test fun `number uses advertised bounds step and unit`() {
        val c = entity("number", "3", "{\"min\":1,\"max\":9,\"step\":2,\"unit_of_measurement\":\"s\"}").toGenericControlContract()
        assertEquals(1f, c.min); assertEquals(9f, c.max); assertEquals(2f, c.step)
        assertEquals(5f, c.normalized(4.6f)); assertEquals("s", c.unit)
    }

    @Test fun `valve actions strictly follow supported feature bits`() {
        val c = entity("valve", "open", "{\"current_valve_position\":42,\"supported_features\":5}").toGenericControlContract()
        assertTrue("open_valve" in c.actions); assertTrue("set_valve_position" in c.actions)
        assertFalse("close_valve" in c.actions); assertFalse("stop_valve" in c.actions)
    }

    @Test fun `water heater actions strictly follow supported feature bits`() {
        val c = entity("water_heater", "eco", "{\"temperature\":55,\"supported_features\":3,\"operation_list\":[\"eco\",\"off\"]}").toGenericControlContract()
        assertEquals(listOf("set_temperature", "set_operation_mode"), c.actions)
        assertEquals(listOf("eco", "off"), c.options)
    }

    @Test fun `lawn mower exposes only supported commands`() {
        assertEquals(listOf("start_mowing", "dock"), entity("lawn_mower", "docked", "{\"supported_features\":5}").toGenericControlContract().actions)
    }
}
