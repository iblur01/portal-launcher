package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.ui.settings.HomeSettingsCatalogBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * End-to-end discovery guard: a camera and a scene present in Home Assistant must reach the
 * settings list, and must not be greyed out for holding a state that means nothing for them.
 */
@RunWith(RobolectricTestRunner::class)
class SceneCameraDiscoveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entity(id: String, state: String) = HaEntity(id, state, JSONObject())

    /** A scene that has never run, and a camera that is not streaming: both perfectly usable. */
    private val entities = listOf(
        entity("scene.evening", "unknown"),
        entity("camera.hall", "idle"),
        entity("camera.garden", "streaming"),
        entity("camera.attic", "unavailable"),
    )

    @Test fun `cameras and scenes are discovered as pill candidates`() {
        val candidates = PillSupport.candidates(entities)

        assertEquals(
            setOf("scene.evening", "camera.hall", "camera.garden", "camera.attic"),
            candidates.map { it.primary.entityId }.toSet(),
        )
    }

    @Test fun `a never-activated scene and an idle camera are available, not greyed out`() {
        assertTrue(PillSupport.isIndividuallyAvailable(entity("scene.evening", "unknown")))
        assertTrue(PillSupport.isIndividuallyAvailable(entity("camera.hall", "idle")))
        assertTrue(PillSupport.isIndividuallyAvailable(entity("camera.hall", "unknown")))

        // Only Home Assistant's explicit `unavailable` means unavailable.
        assertTrue(!PillSupport.isIndividuallyAvailable(entity("camera.attic", "unavailable")))
        // The generic rule is untouched for everything else.
        assertTrue(!PillSupport.isIndividuallyAvailable(entity("sensor.x", "unknown")))
    }

    @Test fun `the settings catalog lists them with the right availability`() {
        val candidates = PillSupport.candidates(entities)
        val catalog = HomeSettingsCatalogBuilder.build(
            context = context,
            candidates = candidates,
            rules = candidates.map { PillSupport.defaultRule(it) },
        )
        val byId = catalog.devices.associateBy { (it.ref as com.iblu01.portallauncher.domain.home.PillRef.Device).entityId }

        assertTrue("scene missing from the settings catalog", "scene.evening" in byId)
        assertTrue("camera missing from the settings catalog", "camera.hall" in byId)
        assertTrue(byId.getValue("scene.evening").available)
        assertTrue(byId.getValue("camera.hall").available)
        // An unavailable camera stays listed so it can still be configured; it is only marked.
        assertTrue("camera.attic" in byId)
        assertTrue(!byId.getValue("camera.attic").available)
    }

    @Test fun `they land in their own settings families`() {
        val candidates = PillSupport.candidates(entities)
        val families = candidates.associate { it.primary.entityId to PillFamily.of(it.kind) }

        assertEquals(PillFamily.SCENES, families["scene.evening"])
        assertEquals(PillFamily.CAMERAS, families["camera.hall"])
    }
}
