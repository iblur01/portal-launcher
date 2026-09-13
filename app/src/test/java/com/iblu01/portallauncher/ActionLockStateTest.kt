package com.iblu01.portallauncher

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLockStateTest {

    @After
    fun reset() = ActionLockState.set(false)

    @Test
    fun `unlocked lets everything through`() {
        ActionLockState.set(false)
        assertFalse(ActionLockState.blocks("light"))
        assertFalse(ActionLockState.blocks("alarm_control_panel"))
    }

    @Test
    fun `locked stops the house but never the alarm`() {
        ActionLockState.set(true)
        assertTrue(ActionLockState.blocks("light"))
        assertTrue(ActionLockState.blocks("lock"))
        assertTrue(ActionLockState.blocks("scene"))
        assertTrue(ActionLockState.blocks("media_player"))
        // Disarming has to work: a locked panel that cannot disarm traps whoever comes home, and
        // the code is the authentication anyway.
        assertFalse(ActionLockState.blocks("alarm_control_panel"))
    }

    @Test
    fun `a blank reason falls back to the built-in wording`() {
        ActionLockState.set(true, "Absent jusqu'à 18 h")
        assertEquals("Absent jusqu'à 18 h", ActionLockState.reason)
        ActionLockState.set(true, "   ")
        assertNull(ActionLockState.reason)
    }
}
