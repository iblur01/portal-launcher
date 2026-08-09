package com.iblu01.portallauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRailRowsTest {
    @Test
    fun `two rows keep column-major reading order without shared columns`() {
        assertEquals(
            listOf(listOf("A", "C", "E"), listOf("B", "D")),
            distributeHomeRailRows(listOf("A", "B", "C", "D", "E"), rowCount = 2),
        )
    }

    @Test
    fun `one row remains a simple packed sequence`() {
        assertEquals(
            listOf(listOf("A", "B", "C")),
            distributeHomeRailRows(listOf("A", "B", "C"), rowCount = 1),
        )
    }
}
