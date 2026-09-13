package com.iblu01.portallauncher.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOrderTest {
    private val ids = listOf("camera.a", "camera.b", "camera.c")

    @Test fun `moving a camera up swaps it with the one above`() {
        assertEquals(listOf("camera.b", "camera.a", "camera.c"), moved(ids, 1, -1))
    }

    @Test fun `moving a camera down swaps it with the one below`() {
        assertEquals(listOf("camera.a", "camera.c", "camera.b"), moved(ids, 1, 1))
    }

    @Test fun `moving past either end changes nothing`() {
        assertEquals(ids, moved(ids, 0, -1))
        assertEquals(ids, moved(ids, 2, 1))
    }

    @Test fun `an out-of-range index is ignored rather than throwing`() {
        assertEquals(ids, moved(ids, 9, -1))
    }

    @Test fun `the result is the complete explicit order, not a partial one`() {
        assertEquals(ids.size, moved(ids, 2, -1).size)
        assertEquals(ids.toSet(), moved(ids, 2, -1).toSet())
    }
}
