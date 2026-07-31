package com.iblu01.portallauncher

import com.iblu01.portallauncher.ui.components.backgroundModes
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBackgroundModesTest {
    @Test fun custom_mode_is_offered() {
        assertTrue(backgroundModes.any { it.first == "custom" })
    }

    @Test fun immich_mode_is_offered() {
        assertTrue(backgroundModes.any { it.first == "immich" })
    }
}
