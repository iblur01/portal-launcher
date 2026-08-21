package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.PillSpecials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Upgrade guard: a launcher configured on 1.0.1 must keep its layout on this release. The payload
 * below is a 1.0.1 preference file — no camera section, no scene section, no special reference.
 */
class PreferencesUpgradeTest {
    private val savedOn101 = """
        {
          "schema_version": 1,
          "home_page_enabled": true,
          "grouping_mode": "type",
          "pinned_order": ["device:light.living_room", "kind:LIGHTS", "manual:evening"],
          "home_sections": [
            {"section_id": "favorites", "visible": true, "order": 0, "item_order": []},
            {"section_id": "kind:LIGHTS", "visible": false, "order": 7, "item_order": []}
          ],
          "manual_groups": [
            {"id": "evening", "name": "Evening", "icon": null, "members": ["device:light.living_room"]}
          ]
        }
    """.trimIndent()

    @Test fun `a layout saved before this release is read back unchanged`() {
        val decoded = requireNotNull(HomePillPreferencesCodec.decode(savedOn101))

        assertEquals(
            listOf(
                PillRef.Device("light.living_room"),
                PillRef.KindGroup(PillKind.LIGHTS),
                PillRef.ManualGroup("evening"),
            ),
            decoded.pinnedOrder,
        )
        assertEquals(2, decoded.homeSections.size)
        assertEquals("Evening", decoded.manualGroups.single().name)
    }

    @Test fun `the new pill kinds need no section migration to appear`() {
        val decoded = requireNotNull(HomePillPreferencesCodec.decode(savedOn101))
        val sectionIds = decoded.homeSections.map { it.sectionId }

        // Absent on purpose: HomePageBuilder treats a missing section preference as visible, so
        // the new Scenes and Cameras rails show up without rewriting anyone's saved file.
        assertTrue(HomePillPreferencesCodec.kindSectionId(PillKind.CAMERA) !in sectionIds)
        assertTrue(HomePillPreferencesCodec.kindSectionId(PillKind.SCENE) !in sectionIds)
    }

    @Test fun `the general Cameras pill survives a save and reload like any other pin`() {
        val decoded = requireNotNull(HomePillPreferencesCodec.decode(savedOn101))
        val pinned = decoded.copy(pinnedOrder = decoded.pinnedOrder + PillSpecials.cameras)

        val reloaded = requireNotNull(
            HomePillPreferencesCodec.decode(HomePillPreferencesCodec.encode(pinned)),
        )

        assertEquals(pinned.pinnedOrder, reloaded.pinnedOrder)
        assertEquals(PillSpecials.cameras, reloaded.pinnedOrder.last())
    }

    @Test fun `an unknown reference type is dropped rather than corrupting the order`() {
        val withFutureRef = savedOn101.replace(
            """"pinned_order": [""",
            """"pinned_order": ["quantum:thing", """,
        )

        val decoded = requireNotNull(HomePillPreferencesCodec.decode(withFutureRef))

        assertEquals(3, decoded.pinnedOrder.size)
    }

    @Test fun `a launcher that never opened the camera centre reads default camera preferences`() {
        // Nothing stored: defaults, and no camera hidden or reordered behind the user's back.
        val defaults = CameraPreferencesCodec.decodeOrDefault("")

        assertTrue(defaults.hidden.isEmpty())
        assertTrue(defaults.order.isEmpty())
        assertEquals(null, defaults.mainCameraId)
    }
}
