package com.iblu01.portallauncher.ui

import com.iblu01.portallauncher.ui.components.pickerChoices
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPickerTest {

    @Test
    fun `a known value does not duplicate the list`() {
        assertEquals(listOf("a", "b"), pickerChoices("a", listOf("a", "b")))
    }

    /** A retired model name must stay selectable, not vanish from its own dropdown. */
    @Test
    fun `an unknown stored value is kept at the top`() {
        assertEquals(listOf("old-model", "a", "b"), pickerChoices("old-model", listOf("a", "b")))
    }

    @Test
    fun `an empty value adds nothing`() {
        assertEquals(listOf("a"), pickerChoices("", listOf("a")))
        assertEquals(emptyList<String>(), pickerChoices("", emptyList()))
    }
}
