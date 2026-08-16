package com.iblu01.portallauncher.domain.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.HomePillPreferencesCodec
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillPriorityEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cameras and scenes must reach the Maison page as ordinary pills, in both grouping modes. They
 * only get there when their discovered rule is enabled: a disabled rule is excluded from every
 * section *and* from pinning, so this is also what makes them pinnable at all.
 */
@RunWith(RobolectricTestRunner::class)
class CameraHomePageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun entity(id: String, state: String) = HaEntity(id, state, JSONObject())

    private val states = listOf(
        entity("camera.hall", "idle"),
        entity("scene.evening", "unknown"),
        entity("light.lamp", "on"),
    ).associateBy { it.entityId }

    private fun catalog() = PillCatalogBuilder(PillPriorityEngine(context)).build(
        rules = emptyList(),
        states = states,
        areaIdByEntity = states.keys.associateWith { "living-room" },
        areaNameById = mapOf("living-room" to "Salon"),
    )

    private fun sectionItems(page: HomePageModel, sectionId: String): List<String> =
        page.sections.firstOrNull { it.sectionId == sectionId }
            ?.items.orEmpty()
            .map { it.ref.stableKey }

    @Test fun `a discovered camera is not hidden behind a disabled rule`() {
        val catalog = catalog()

        assertTrue(
            "camera should not be disabled by default",
            PillRef.Device("camera.hall") !in catalog.disabledDeviceRefs,
        )
        assertTrue(catalog.isVisible(PillRef.Device("camera.hall")))
    }

    @Test fun `grouping by type puts cameras and scenes in their own sections`() {
        val page = HomePageBuilder.build(context, catalog(), HomePillPreferencesCodec.defaults())

        assertEquals(
            listOf("device:camera.hall"),
            sectionItems(page, HomeSectionIds.kind(PillKind.CAMERA)),
        )
        assertEquals(
            listOf("device:scene.evening"),
            sectionItems(page, HomeSectionIds.kind(PillKind.SCENE)),
        )
    }

    @Test fun `grouping by room lists the camera with the rest of its room`() {
        val preferences = HomePillPreferencesCodec.defaults()
            .copy(groupingMode = HomeGroupingMode.BY_ROOM)

        val page = HomePageBuilder.build(context, catalog(), preferences)
        val room = sectionItems(page, HomeSectionIds.area("living-room"))

        assertTrue("camera missing from its room: $room", "device:camera.hall" in room)
        assertTrue("scene missing from its room: $room", "device:scene.evening" in room)
    }

    @Test fun `a camera can be pinned to the tray like any other pill`() {
        val catalog = catalog()
        val pinned = HomePillPreferencesCodec.defaults()
            .copy(pinnedOrder = listOf(PillRef.Device("camera.hall")))

        val composition = HomePillComposer.compose(catalog, pinned)

        assertEquals("device:camera.hall", composition.primary.first().ref.stableKey)
    }

    @Test fun `the general Cameras pill can be pinned too`() {
        val catalog = catalog()
        val pinned = HomePillPreferencesCodec.defaults()
            .copy(pinnedOrder = listOf(PillSpecials.cameras))

        val composition = HomePillComposer.compose(catalog, pinned)

        assertEquals("special:cameras", composition.primary.first().ref.stableKey)
    }

    @Test fun `cameras and scenes never outrank a real device for a tray slot`() {
        val catalog = catalog()

        val composition = HomePillComposer.compose(catalog, HomePillPreferencesCodec.defaults())
        val order = composition.primary.map { it.ref.stableKey }

        // They fill the tray only once nothing more informative is left: what keeps them out of
        // the way is their low base priority, not being hidden.
        assertEquals("device:light.lamp", order.first())
    }
}
