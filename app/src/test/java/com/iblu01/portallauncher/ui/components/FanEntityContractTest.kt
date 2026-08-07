package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.HaEntity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FanEntityContractTest {
    @Test fun `plain fan maps to on off control`() {
        val contract = HaEntity("fan.basic", "on", JSONObject()).toFanContract()
        assertEquals(FanPrimaryControl.OnOff, contract.primaryControl)
        assertTrue(contract.isOn)
    }

    @Test fun `percentage fan maps to vertical slider`() {
        val entity = HaEntity("fan.variable", "on", JSONObject()
            .put("supported_features", FanFeature.SET_SPEED)
            .put("percentage", 40)
            .put("percentage_step", 5))
        assertEquals(FanPrimaryControl.Percentage(40, 5), entity.toFanContract().primaryControl)
    }

    @Test fun `preset modes take precedence and preserve exact Home Assistant values`() {
        val entity = HaEntity("fan.levels", "on", JSONObject()
            .put("percentage", 66)
            .put("preset_mode", "level_2")
            .put("preset_modes", JSONArray(listOf("level_1", "level_2", "level_3"))))
        assertEquals(
            FanPrimaryControl.Presets(listOf("level_1", "level_2", "level_3"), "level_2"),
            entity.toFanContract().primaryControl,
        )
    }
}
