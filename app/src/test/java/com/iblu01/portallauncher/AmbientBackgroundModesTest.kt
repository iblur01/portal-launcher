package com.iblu01.portallauncher

import com.iblu01.portallauncher.ui.components.backgroundModes
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientBackgroundModesTest {
    @Test fun all_supported_wallpaper_sources_are_offered() {
        assertEquals(listOf("system", "neutral", "custom", "immich"), backgroundModes.map { it.first })
    }
}
