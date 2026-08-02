package com.iblu01.portallauncher.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSessionVisibilityTest {
    @Test fun `sessions are hidden without a configured broker`() {
        assertFalse(shouldShowAppSessions(""))
        assertFalse(shouldShowAppSessions("   "))
    }

    @Test fun `sessions are visible with a configured broker`() {
        assertTrue(shouldShowAppSessions("homeassistant.local"))
    }
}
