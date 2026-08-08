package com.iblu01.portallauncher.ui.components

import androidx.compose.ui.graphics.Color
import com.iblu01.portallauncher.ui.theme.AppleColors
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedChipAccentTest {

    @Test
    fun `neutral icon becomes dark on selected white chip`() {
        assertEquals(Color(0xFF1C1C1E), selectedChipAccent(AppleColors.inactive, selected = true))
        assertEquals(Color(0xFF1C1C1E), selectedChipAccent(AppleColors.secondary, selected = true))
    }

    @Test
    fun `colored icon keeps its accent when selected`() {
        assertEquals(AppleColors.active, selectedChipAccent(AppleColors.active, selected = true))
    }

    @Test
    fun `unselected icon keeps its original tint`() {
        assertEquals(AppleColors.inactive, selectedChipAccent(AppleColors.inactive, selected = false))
    }
}
