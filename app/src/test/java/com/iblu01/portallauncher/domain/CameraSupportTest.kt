package com.iblu01.portallauncher.domain

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.domain.home.CameraStreamFormat
import com.iblu01.portallauncher.domain.home.CameraSupport
import com.iblu01.portallauncher.domain.home.PtzAction
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraSupportTest {
    private fun camera(
        id: String = "camera.hall",
        state: String = "idle",
        features: Int? = null,
    ) = HaEntity(
        id,
        state,
        JSONObject().apply { features?.let { put("supported_features", it) } },
    )

    private fun button(id: String) = HaEntity(id, "unknown", JSONObject())

    @Test fun `a camera advertising the stream feature prefers HLS and keeps MJPEG as a fallback`() {
        assertEquals(
            listOf(CameraStreamFormat.HLS, CameraStreamFormat.MJPEG),
            CameraSupport.formatsFor(camera(features = CameraSupport.FEATURE_STREAM)),
        )
    }

    @Test fun `a camera without the stream feature only offers MJPEG`() {
        assertEquals(listOf(CameraStreamFormat.MJPEG), CameraSupport.formatsFor(camera(features = 1)))
        assertEquals(listOf(CameraStreamFormat.MJPEG), CameraSupport.formatsFor(camera()))
    }

    @Test fun `a fixed camera exposes no PTZ control at all`() {
        val capabilities = CameraSupport.capabilitiesOf(
            entity = camera(),
            states = mapOf("camera.hall" to camera()),
            deviceIdByEntity = mapOf("camera.hall" to "device-1"),
            entityPlatformByEntity = mapOf("camera.hall" to "generic"),
            services = mapOf("onvif" to setOf("ptz")),
        )

        assertFalse(capabilities.supportsPtz)
        assertTrue(capabilities.ptz.isEmpty())
    }

    @Test fun `companion entities expose exactly the movements the camera really has`() {
        val states = mapOf(
            "camera.hall" to camera(),
            "button.hall_ptz_left" to button("button.hall_ptz_left"),
            "button.hall_ptz_right" to button("button.hall_ptz_right"),
            // No tilt and no zoom companion: this camera only pans.
        )
        val devices = states.keys.associateWith { "device-1" }

        val capabilities = CameraSupport.capabilitiesOf(
            entity = camera(),
            states = states,
            deviceIdByEntity = devices,
            entityPlatformByEntity = emptyMap(),
            services = emptyMap(),
        )

        assertEquals(setOf(PtzAction.PAN_LEFT, PtzAction.PAN_RIGHT), capabilities.ptz)
        assertEquals("button.hall_ptz_left", capabilities.ptzEntityIds[PtzAction.PAN_LEFT])
    }

    @Test fun `companion entities of another device are never borrowed`() {
        val states = mapOf(
            "camera.hall" to camera(),
            "button.garden_ptz_left" to button("button.garden_ptz_left"),
        )
        val devices = mapOf("camera.hall" to "device-1", "button.garden_ptz_left" to "device-2")

        val capabilities = CameraSupport.capabilitiesOf(
            camera(), states, devices, emptyMap(), emptyMap(),
        )

        assertFalse(capabilities.supportsPtz)
    }

    @Test fun `an ONVIF camera gets PTZ only when the ONVIF service actually exists`() {
        val states = mapOf("camera.hall" to camera())
        val devices = mapOf("camera.hall" to "device-1")
        val platforms = mapOf("camera.hall" to "onvif")

        assertTrue(
            CameraSupport.capabilitiesOf(
                camera(), states, devices, platforms, mapOf("onvif" to setOf("ptz")),
            ).supportsPtz,
        )
        assertFalse(
            CameraSupport.capabilitiesOf(
                camera(), states, devices, platforms, mapOf("onvif" to setOf("reboot")),
            ).supportsPtz,
        )
        assertFalse(
            CameraSupport.capabilitiesOf(camera(), states, devices, platforms, emptyMap()).supportsPtz,
        )
    }

    @Test fun `companion entities win over the ONVIF service so a partial camera stays honest`() {
        val states = mapOf(
            "camera.hall" to camera(),
            "button.hall_ptz_up" to button("button.hall_ptz_up"),
        )
        val devices = states.keys.associateWith { "device-1" }

        val capabilities = CameraSupport.capabilitiesOf(
            camera(), states, devices, mapOf("camera.hall" to "onvif"), mapOf("onvif" to setOf("ptz")),
        )

        assertEquals(setOf(PtzAction.TILT_UP), capabilities.ptz)
    }

    @Test fun `an unavailable camera is reported as such`() {
        assertFalse(CameraSupport.isAvailable(camera(state = "unavailable")))
        assertFalse(CameraSupport.isAvailable(null))
        assertTrue(CameraSupport.isAvailable(camera(state = "idle")))
    }
}
