package com.iblu01.portallauncher.ui.components

import com.iblu01.portallauncher.LauncherChip
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards which chips hand their glyph over to Home Assistant. The two exceptions are easy to delete
 * by accident while tidying `ChipGlyph`, and both regress silently: a group chip would start showing
 * one arbitrary member's icon, and the washer would lose its animated phases for a static one.
 */
class ChipHaIconTest {

    private fun chip(icon: String, entityId: String = "") =
        LauncherChip(id = "c", icon = icon, label = "l", value = "v", entityId = entityId)

    @Test
    fun `a chip backed by one entity defers to Home Assistant`() {
        assertTrue(chip("air", "fan.purifier").defersToHaIcon())
        assertTrue(chip("lock", "lock.front").defersToHaIcon())
        assertTrue(chip("cover", "cover.bedroom").defersToHaIcon())
        assertTrue(chip("security", "alarm_control_panel.home").defersToHaIcon())
    }

    @Test
    fun `a group chip keeps the launcher glyph because it has no single entity`() {
        assertFalse(chip("light").defersToHaIcon())
        assertFalse(chip("media").defersToHaIcon())
        assertFalse(chip("temperature").defersToHaIcon())
        assertFalse(chip("air", entityId = "  ").defersToHaIcon())
    }

    @Test
    fun `the washer keeps its animated glyph even when it has an entity`() {
        assertFalse(chip("washer", "sensor.washer_phase").defersToHaIcon())
    }
}
