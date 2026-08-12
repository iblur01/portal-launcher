package com.iblu01.portallauncher

import com.iblu01.portallauncher.ui.components.backgroundModes
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientBackgroundModesTest {
    @Test fun only_android_neutral_and_immich_are_offered() {
        assertEquals(listOf("system", "neutral", "immich"), backgroundModes.map { it.first })
    }
}
