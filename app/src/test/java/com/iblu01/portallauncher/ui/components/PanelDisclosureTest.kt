package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelDisclosureTest {
    @Test fun `echo show sized weather panel shows only essential forecasts`() {
        val policy = weatherDisclosureFor(widthDp = 788f, heightDp = 394f)
        assertEquals(6, policy.hourlyCount)
        assertEquals(3, policy.dailyCount)
        assertFalse(policy.showCondition)
    }

    @Test fun `large weather panel keeps complete forecast`() {
        val policy = weatherDisclosureFor(widthDp = 1200f, heightDp = 800f)
        assertEquals(12, policy.hourlyCount)
        assertEquals(7, policy.dailyCount)
        assertTrue(policy.showCondition)
    }

    @Test fun `small media panel preserves controls and removes secondary detail`() {
        val policy = mediaDisclosureFor(widthDp = 788f, heightDp = 394f)
        assertFalse(policy.showAlbum)
        assertEquals(1, policy.secondaryPlayerCount)
    }
}
