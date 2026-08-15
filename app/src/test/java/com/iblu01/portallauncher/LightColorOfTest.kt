package com.iblu01.portallauncher

import androidx.compose.ui.graphics.Color
import com.iblu01.portallauncher.ui.components.lightColorOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Resolving the current visual colour of a light from its HA attributes, so a light pill's icon
 * circle can paint the bulb's real colour (with contrast handled separately by the caller).
 */
class LightColorOfTest {

    private fun light(state: String, attrs: JSONObject = JSONObject()) =
        HaEntity("light.salon", state, attrs)

    @Test fun `off light has no colour even with a colour attribute`() {
        val attrs = JSONObject().put("rgb_color", JSONArray(listOf(255, 0, 0)))
        assertNull(lightColorOf(light("off", attrs)))
    }

    @Test fun `rgb bulb reports its rgb colour`() {
        val attrs = JSONObject().put("rgb_color", JSONArray(listOf(255, 0, 0)))
        assertEquals(Color(255, 0, 0), lightColorOf(light("on", attrs)))
    }

    @Test fun `tunable-white light derives colour from kelvin`() {
        val warm = lightColorOf(light("on", JSONObject(mapOf("color_temp_kelvin" to 2700))))
        val cold = lightColorOf(light("on", JSONObject(mapOf("color_temp_kelvin" to 10000))))
        assertNotNull(warm)
        assertNotNull(cold)
        assert(warm!!.red > warm.blue)      // 2700 K reads warm (red-dominant)
        assert(cold!!.blue > cold.red)      // 10000 K reads cold (blue-dominant)
    }

    @Test fun `on light without colour attributes has no colour`() {
        assertNull(lightColorOf(light("on", JSONObject(mapOf("brightness" to 255)))))
    }
}
