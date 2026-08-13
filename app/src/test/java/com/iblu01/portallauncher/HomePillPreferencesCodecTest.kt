package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionPreference
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import com.iblu01.portallauncher.domain.home.PillRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePillPreferencesCodecTest {
    @Test fun `round trip preserves every stable reference and configured order`() {
        val light = PillRef.Device("light.living_room")
        val cover = PillRef.Device("cover.kitchen")
        val preferences = HomePillPreferences(
            schemaVersion = HomePillPreferencesCodec.CURRENT_SCHEMA_VERSION,
            homePageEnabled = false,
            pinnedOrder = listOf(
                PillRef.ManualGroup("evening"),
                PillRef.AreaGroup("living-room-area-id"),
                PillRef.KindGroup(PillKind.LIGHTS),
                light,
            ),
            homeSections = listOf(
                HomeSectionPreference(
                    sectionId = HomePillPreferencesCodec.kindSectionId(PillKind.LIGHTS),
                    visible = false,
                    order = 7,
                    itemOrder = listOf(cover, light),
                ),
                HomeSectionPreference(
                    sectionId = HomePillPreferencesCodec.SECTION_AREAS,
                    visible = true,
                    order = 2,
                    itemOrder = listOf(PillRef.AreaGroup("kitchen")),
                ),
            ),
            manualGroups = listOf(
                ManualPillGroup(
                    id = "evening",
                    name = "Soirée",
                    icon = "weather-sunset",
                    members = listOf(cover, light),
                ),
            ),
        )

        assertEquals(preferences, HomePillPreferencesCodec.decode(HomePillPreferencesCodec.encode(preferences)))
    }

    @Test fun `stable ids absent from the current catalog survive decoding`() {
        val unknownDevice = PillRef.Device("light.device_removed_for_now")
        val unknownArea = PillRef.AreaGroup("area-no-longer-in-snapshot")
        val unknownManualGroup = PillRef.ManualGroup("group-not-currently-rendered")
        val encoded = HomePillPreferencesCodec.encode(
            HomePillPreferencesCodec.defaults().copy(
                pinnedOrder = listOf(unknownDevice, unknownArea, unknownManualGroup),
            ),
        )

        assertEquals(
            listOf(unknownDevice, unknownArea, unknownManualGroup),
            HomePillPreferencesCodec.decode(encoded)?.pinnedOrder,
        )
    }

    @Test fun `typed reference keys are locale independent`() {
        assertEquals("device:light.salon", HomePillPreferencesCodec.encodeRef(PillRef.Device("light.salon")))
        assertEquals("area:kitchen", HomePillPreferencesCodec.encodeRef(PillRef.AreaGroup("kitchen")))
        assertEquals("kind:LIGHTS", HomePillPreferencesCodec.encodeRef(PillRef.KindGroup(PillKind.LIGHTS)))
        assertEquals("manual:uuid", HomePillPreferencesCodec.encodeRef(PillRef.ManualGroup("uuid")))
    }

    @Test fun `grouping mode round trips and defaults to by type`() {
        val byRoom = HomePillPreferencesCodec.defaults().copy(
            groupingMode = com.iblu01.portallauncher.domain.home.HomeGroupingMode.BY_ROOM,
        )
        assertEquals(
            com.iblu01.portallauncher.domain.home.HomeGroupingMode.BY_ROOM,
            HomePillPreferencesCodec.decode(HomePillPreferencesCodec.encode(byRoom))?.groupingMode,
        )
        assertEquals(
            com.iblu01.portallauncher.domain.home.HomeGroupingMode.BY_TYPE,
            HomePillPreferencesCodec.decode("{\"schema_version\":1}")?.groupingMode,
        )
    }

    @Test fun `corrupt and unsupported future JSON do not decode`() {
        assertNull(HomePillPreferencesCodec.decode("not-json"))
        assertNull(HomePillPreferencesCodec.decode("{\"schema_version\":999}"))
    }

    @Test fun `migration defaults enable Home and every automatic section`() {
        val defaults = HomePillPreferencesCodec.defaults()

        assertEquals(HomePillPreferencesCodec.CURRENT_SCHEMA_VERSION, defaults.schemaVersion)
        assertTrue(defaults.homePageEnabled)
        assertTrue(defaults.pinnedOrder.isEmpty())
        assertTrue(defaults.manualGroups.isEmpty())
        assertTrue(defaults.homeSections.first { it.sectionId == "areas" }.visible)
        PillKind.values().forEach { kind ->
            assertTrue(defaults.homeSections.first { it.sectionId == "kind:${kind.name}" }.visible)
        }
        assertFalse(defaults.homeSections.zipWithNext().any { (left, right) -> left.order > right.order })
    }
}
