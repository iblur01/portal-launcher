package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmPanelStateTest {
    @Test fun `every Home Assistant alarm state routes to its own phase`() {
        assertEquals(AlarmPanelPhase.DISARMED, alarmPanelPhase("disarmed"))
        assertEquals(AlarmPanelPhase.ARMING, alarmPanelPhase("arming"))
        assertEquals(AlarmPanelPhase.PENDING, alarmPanelPhase("pending"))
        assertEquals(AlarmPanelPhase.TRIGGERED, alarmPanelPhase("triggered"))
        assertEquals(AlarmPanelPhase.DISARMING, alarmPanelPhase("disarming"))
    }

    @Test fun `all supported armed modes route to the keypad phase`() {
        listOf(
            "armed_away",
            "armed_home",
            "armed_night",
            "armed_vacation",
            "armed_custom_bypass",
        ).forEach { state ->
            assertEquals(state, AlarmPanelPhase.ARMED, alarmPanelPhase(state))
        }
    }

    @Test fun `unknown integration states never masquerade as disabled`() {
        assertEquals(AlarmPanelPhase.OTHER, alarmPanelPhase("some_vendor_transition"))
    }
}
