package com.iblu01.portallauncher.ui.camera

import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraCenterStateTest {
    private val available = listOf("camera.hall", "camera.garden", "camera.attic")

    @Test fun `the general pill opens the configured main camera in the configured mode`() {
        val preferences = CameraPreferences(
            mainCameraId = "camera.garden",
            defaultMode = CameraCenterMode.GRID,
        )

        val open = requireNotNull(CameraCenterState().opened(null, available, preferences).open)

        assertEquals("camera.garden", open.selected)
        assertEquals(CameraCenterMode.GRID, open.mode)
    }

    @Test fun `an individual pill opens its own camera even when another one is main`() {
        val preferences = CameraPreferences(
            mainCameraId = "camera.garden",
            defaultMode = CameraCenterMode.GRID,
        )

        val open = requireNotNull(
            CameraCenterState().opened("camera.attic", available, preferences).open,
        )

        assertEquals("camera.attic", open.selected)
        assertEquals(CameraCenterMode.MAIN, open.mode)
    }

    @Test fun `an individual pill opens a camera the user hid from the centre`() {
        val preferences = CameraPreferences(hidden = setOf("camera.attic"))

        val open = requireNotNull(
            CameraCenterState().opened("camera.attic", available, preferences).open,
        )

        assertEquals("camera.attic", open.selected)
        assertTrue("camera.attic" in open.cameras)
    }

    @Test fun `no available camera never opens an empty centre`() {
        assertFalse(CameraCenterState().opened(null, emptyList(), CameraPreferences()).isOpen)
    }

    @Test fun `the grid only shows the cameras made visible in the settings`() {
        val preferences = CameraPreferences(hidden = setOf("camera.garden"))

        val open = requireNotNull(CameraCenterState().opened(null, available, preferences).open)

        assertEquals(listOf("camera.attic", "camera.hall"), open.cameras)
    }

    @Test fun `picking a grid thumbnail promotes it to the main stream`() {
        val opened = CameraCenterState()
            .opened(null, available, CameraPreferences(defaultMode = CameraCenterMode.GRID))

        val open = requireNotNull(opened.selected("camera.garden").open)

        assertEquals("camera.garden", open.selected)
        assertEquals(CameraCenterMode.MAIN, open.mode)
    }

    @Test fun `closing leaves no open state at all so no player can survive it`() {
        val opened = CameraCenterState().opened(null, available, CameraPreferences())

        assertNull(opened.closed().open)
    }

    @Test fun `a deleted camera falls back to another instead of closing the centre`() {
        val opened = CameraCenterState().opened("camera.attic", available, CameraPreferences())

        val open = requireNotNull(
            opened.reconciled(listOf("camera.hall", "camera.garden"), CameraPreferences()).open,
        )

        assertEquals("camera.garden", open.selected)
    }

    @Test fun `losing every camera closes the centre`() {
        val opened = CameraCenterState().opened(null, available, CameraPreferences())

        assertFalse(opened.reconciled(emptyList(), CameraPreferences()).isOpen)
    }

    @Test fun `hiding the last visible camera in the settings closes the centre`() {
        val opened = CameraCenterState().opened(null, listOf("camera.hall"), CameraPreferences())

        assertFalse(
            opened.reconciled(listOf("camera.hall"), CameraPreferences(hidden = setOf("camera.hall"))).isOpen,
        )
    }

    @Test fun `mode switches while open and is ignored while closed`() {
        val opened = CameraCenterState().opened(null, available, CameraPreferences())

        assertEquals(CameraCenterMode.GRID, opened.withMode(CameraCenterMode.GRID).open?.mode)
        assertFalse(CameraCenterState().withMode(CameraCenterMode.GRID).isOpen)
    }
}
