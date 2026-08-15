package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraPreferencesCodecTest {
    @Test fun `round trip preserves visibility order main camera and mode`() {
        val preferences = CameraPreferences(
            hidden = setOf("camera.garage"),
            order = listOf("camera.garden", "camera.hall"),
            mainCameraId = "camera.garden",
            defaultMode = CameraCenterMode.GRID,
        )

        val decoded = CameraPreferencesCodec.decode(CameraPreferencesCodec.encode(preferences))

        assertEquals(preferences, decoded)
    }

    @Test fun `an absent or corrupt payload falls back to defaults without throwing`() {
        assertNull(CameraPreferencesCodec.decode("{ not json"))
        assertEquals(CameraPreferences(), CameraPreferencesCodec.decodeOrDefault("{ not json"))
    }

    @Test fun `a newer schema version is refused rather than misread`() {
        val future = """{"schema_version":99,"hidden":[],"order":[]}"""

        assertNull(CameraPreferencesCodec.decode(future))
        assertEquals(CameraPreferences(), CameraPreferencesCodec.decodeOrDefault(future))
    }

    @Test fun `unknown cameras are visible and sort after the configured ones`() {
        val preferences = CameraPreferences(order = listOf("camera.hall", "camera.garden"))

        val visible = preferences.visibleCameras(
            listOf("camera.attic", "camera.garden", "camera.hall"),
        )

        assertEquals(listOf("camera.hall", "camera.garden", "camera.attic"), visible)
    }

    @Test fun `hidden cameras leave the centre without affecting the others`() {
        val preferences = CameraPreferences(hidden = setOf("camera.garden"))

        assertEquals(
            listOf("camera.attic", "camera.hall"),
            preferences.visibleCameras(listOf("camera.hall", "camera.garden", "camera.attic")),
        )
    }

    @Test fun `a main camera that disappeared falls back to the first visible one`() {
        val preferences = CameraPreferences(
            order = listOf("camera.hall"),
            mainCameraId = "camera.removed",
        )

        assertEquals(
            "camera.hall",
            preferences.resolveMainCamera(listOf("camera.attic", "camera.hall")),
        )
    }

    @Test fun `a main camera the user hid is not reopened by the general pill`() {
        val preferences = CameraPreferences(
            hidden = setOf("camera.hall"),
            mainCameraId = "camera.hall",
        )

        assertEquals("camera.attic", preferences.resolveMainCamera(listOf("camera.attic", "camera.hall")))
    }

    @Test fun `no camera at all resolves to no main camera`() {
        assertNull(CameraPreferences().resolveMainCamera(emptyList()))
    }
}
