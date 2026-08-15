package com.iblu01.portallauncher.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.HomePillPreferencesCodec
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillCandidate
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import com.iblu01.portallauncher.domain.home.PillRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeSettingsReducerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val deviceA = PillRef.Device("light.a")
    private val deviceB = PillRef.Device("light.b")

    @Test fun unavailable_target_can_be_unpinned_but_not_newly_pinned() {
        val defaults = HomePillPreferencesCodec.defaults()
        val refused = HomeSettingsReducer.reduce(defaults, HomeSettingsAction.TogglePin(deviceA, canPin = false))
        assertTrue(refused.pinnedOrder.isEmpty())

        val pinned = defaults.copy(pinnedOrder = listOf(deviceA))
        val removed = HomeSettingsReducer.reduce(pinned, HomeSettingsAction.TogglePin(deviceA, canPin = false))
        assertTrue(removed.pinnedOrder.isEmpty())
    }

    @Test fun pins_and_manual_members_keep_accessible_reorder_order() {
        val initial = HomePillPreferencesCodec.defaults().copy(
            pinnedOrder = listOf(deviceA, deviceB),
            manualGroups = listOf(ManualPillGroup("g", "Groupe", null, listOf(deviceA, deviceB))),
        )
        val pins = HomeSettingsReducer.reduce(initial, HomeSettingsAction.MovePin(deviceB, MoveDirection.FIRST))
        assertEquals(listOf(deviceB, deviceA), pins.pinnedOrder)

        val members = HomeSettingsReducer.reduce(
            pins,
            HomeSettingsAction.MoveManualGroupMember("g", deviceA, MoveDirection.FIRST),
        )
        assertEquals(listOf(deviceA, deviceB), members.manualGroups.single().members)
    }

    @Test fun section_items_have_pure_accessible_reorder_and_persist_through_codec() {
        val deviceC = PillRef.Device("light.c")
        val defaults = HomePillPreferencesCodec.defaults()
        val sectionId = HomePillPreferencesCodec.kindSectionId(PillKind.LIGHTS)
        val initial = defaults.copy(
            homeSections = defaults.homeSections.map { section ->
                if (section.sectionId == sectionId) {
                    section.copy(itemOrder = listOf(deviceA, deviceB, deviceC))
                } else section
            },
        )

        val moved = HomeSettingsReducer.reduce(
            initial,
            HomeSettingsAction.MoveSectionItem(
                sectionId = sectionId,
                ref = deviceB,
                visibleOrder = listOf(deviceA, deviceB, deviceC),
                direction = MoveDirection.LAST,
            ),
        )
        assertEquals(
            listOf(deviceA, deviceC, deviceB),
            moved.homeSections.first { it.sectionId == sectionId }.itemOrder,
        )

        val restored = HomePillPreferencesCodec.decode(HomePillPreferencesCodec.encode(moved))!!
        assertEquals(
            listOf(deviceA, deviceC, deviceB),
            restored.homeSections.first { it.sectionId == sectionId }.itemOrder,
        )
    }

    @Test fun section_item_reorder_keeps_temporarily_hidden_references() {
        val hidden = PillRef.Device("light.hidden")
        val defaults = HomePillPreferencesCodec.defaults()
        val sectionId = HomePillPreferencesCodec.kindSectionId(PillKind.LIGHTS)
        val initial = defaults.copy(
            homeSections = defaults.homeSections.map { section ->
                if (section.sectionId == sectionId) {
                    section.copy(itemOrder = listOf(deviceA, hidden, deviceB))
                } else section
            },
        )

        val moved = HomeSettingsReducer.reduce(
            initial,
            HomeSettingsAction.MoveSectionItem(
                sectionId,
                deviceB,
                visibleOrder = listOf(deviceA, deviceB),
                direction = MoveDirection.FIRST,
            ),
        )

        val order = moved.homeSections.first { it.sectionId == sectionId }.itemOrder
        assertEquals(listOf(deviceB, hidden, deviceA), order)
    }

    @Test fun manual_group_lifecycle_preserves_devices_and_cleans_dangling_group_refs() {
        val created = HomeSettingsReducer.reduce(
            HomePillPreferencesCodec.defaults(),
            HomeSettingsAction.CreateManualGroup("  Soirée  "),
            idFactory = { "stable-id" },
        )
        assertEquals("Soirée", created.manualGroups.single().name)
        assertEquals("stable-id", created.manualGroups.single().id)

        val populated = HomeSettingsReducer.reduce(
            created,
            HomeSettingsAction.SetManualGroupMember("stable-id", deviceA, included = true),
        ).copy(pinnedOrder = listOf(PillRef.ManualGroup("stable-id"), deviceA))
        val deleted = HomeSettingsReducer.reduce(populated, HomeSettingsAction.DeleteManualGroup("stable-id"))
        assertTrue(deleted.manualGroups.isEmpty())
        assertEquals(listOf(deviceA), deleted.pinnedOrder)
    }

    @Test fun empty_manual_group_remains_editable_and_section_visibility_is_independent() {
        val initial = HomePillPreferencesCodec.defaults().copy(
            manualGroups = listOf(ManualPillGroup("empty", "Vide", null, emptyList())),
        )
        val section = initial.homeSections.first()
        val updated = HomeSettingsReducer.reduce(
            initial,
            HomeSettingsAction.SetSectionVisible(section.sectionId, !section.visible),
        )
        assertEquals(1, updated.manualGroups.size)
        assertEquals(!section.visible, updated.homeSections.first { it.sectionId == section.sectionId }.visible)
    }

    @Test fun deleting_group_does_not_disable_or_remove_member_devices() {
        val initial = HomePillPreferencesCodec.defaults().copy(
            pinnedOrder = listOf(deviceA),
            manualGroups = listOf(ManualPillGroup("g", "G", null, listOf(deviceA))),
        )
        val deleted = HomeSettingsReducer.reduce(initial, HomeSettingsAction.DeleteManualGroup("g"))
        assertEquals(listOf(deviceA), deleted.pinnedOrder)
        assertFalse(deleted.manualGroups.any { it.id == "g" })
    }

    @Test fun accessible_move_to_another_manual_group_is_atomic() {
        val initial = HomePillPreferencesCodec.defaults().copy(
            manualGroups = listOf(
                ManualPillGroup("from", "Source", null, listOf(deviceA, deviceB)),
                ManualPillGroup("to", "Destination", null, emptyList()),
            ),
        )
        val moved = HomeSettingsReducer.reduce(
            initial,
            HomeSettingsAction.MoveManualGroupMemberToGroup("from", "to", deviceA),
        )
        assertEquals(listOf(deviceB), moved.manualGroups.first().members)
        assertEquals(listOf(deviceA), moved.manualGroups.last().members)
    }

    @Test fun settings_catalog_is_not_truncated_and_uses_stable_area_id() {
        val candidates = (1..12).map { index ->
            val entity = HaEntity("light.device_$index", "off", JSONObject().put("friendly_name", "Lampe $index"))
            PillCandidate(entity, PillKind.LIGHTS, entity.name, emptyList())
        }
        val rules = candidates.map { PillRule(it.primary.entityId, it.kind, it.label) }
        val catalog = HomeSettingsCatalogBuilder.build(
            context = context,
            candidates = candidates,
            rules = rules,
            areaIdByEntity = candidates.associate { it.primary.entityId to "area-stable-42" },
            areaNameById = mapOf("area-stable-42" to "Cuisine"),
        )
        assertEquals(12, catalog.devices.size)
        val area = catalog.automaticGroups.single { it.ref is PillRef.AreaGroup }
        assertEquals(PillRef.AreaGroup("area-stable-42"), area.ref)
        assertEquals("Cuisine", area.label)
    }

    @Test fun unavailable_or_disabled_device_cannot_be_pinned_until_enabled() {
        val unavailable = HaEntity("light.offline", "unavailable", JSONObject())
        val disabled = HaEntity("light.disabled", "off", JSONObject())
        val candidates = listOf(
            PillCandidate(unavailable, PillKind.LIGHTS, "Offline", emptyList()),
            PillCandidate(disabled, PillKind.LIGHTS, "Disabled", emptyList()),
        )
        val catalog = HomeSettingsCatalogBuilder.build(
            context,
            candidates,
            rules = listOf(PillRule(unavailable.entityId, PillKind.LIGHTS, "Offline")),
        )
        assertFalse(catalog.devices.first { it.ref == PillRef.Device(unavailable.entityId) }.canPin)
        assertFalse(catalog.devices.first { it.ref == PillRef.Device(disabled.entityId) }.canPin)
        assertTrue(catalog.automaticGroups.isEmpty())
    }

    @Test fun global_disconnect_keeps_last_snapshot_visible_but_blocks_new_pins() {
        val entity = HaEntity("light.cached", "on", JSONObject().put("friendly_name", "Cached"))
        val candidate = PillCandidate(entity, PillKind.LIGHTS, "Cached", emptyList())
        val ref = PillRef.Device(entity.entityId)
        val catalog = HomeSettingsCatalogBuilder.build(
            context = context,
            candidates = listOf(candidate),
            rules = listOf(PillRule(entity.entityId, PillKind.LIGHTS, "Cached")),
            connected = false,
        )

        val cachedTarget = catalog.devices.single()
        assertEquals(ref, cachedTarget.ref)
        assertTrue(cachedTarget.available)
        assertTrue(cachedTarget.stale)
        assertFalse(cachedTarget.canPin)
        assertTrue(catalog.automaticGroups.isNotEmpty())
        assertTrue(catalog.automaticGroups.all { it.stale && !it.canPin })

        val pinned = HomePillPreferencesCodec.defaults().copy(pinnedOrder = listOf(ref))
        val unpinned = HomeSettingsReducer.reduce(
            pinned,
            HomeSettingsAction.TogglePin(ref, canPin = cachedTarget.canPin),
        )
        assertTrue(unpinned.pinnedOrder.isEmpty())
    }

    @Test fun settings_pin_preview_promotes_available_overflow_without_losing_unavailable_pin() {
        val refs = (1..11).map { PillRef.Device("light.$it") }
        val preferences = HomePillPreferencesCodec.defaults().copy(pinnedOrder = refs)
        val catalog = SettingsPillCatalog(
            devices = refs.mapIndexed { index, ref ->
                SettingsPillTarget(
                    ref = ref,
                    label = ref.entityId,
                    stateLabel = "off",
                    enabled = true,
                    available = index != 1,
                    kind = PillKind.LIGHTS,
                )
            },
            automaticGroups = emptyList(),
        )

        val preview = HomeSettingsPinPreview.build(preferences, catalog)

        assertEquals(listOf(refs[0]) + refs.subList(2, 10), preview.visible)
        assertEquals(listOf(refs[10]), preview.overflow)
        assertEquals(listOf(refs[1]), preview.unavailable)
    }

    @Test fun section_order_matches_rendered_items_and_keeps_disabled_compatible_device() {
        val unavailable = PillRef.Device("light.unavailable")
        val catalog = SettingsPillCatalog(
            devices = listOf(
                SettingsPillTarget(deviceA, "A", "off", enabled = false, available = true, kind = PillKind.LIGHTS),
                SettingsPillTarget(deviceB, "B", "off", enabled = true, available = true, kind = PillKind.LIGHTS),
                SettingsPillTarget(unavailable, "Offline", "unavailable", enabled = true, available = false, kind = PillKind.LIGHTS),
            ),
            automaticGroups = listOf(
                SettingsPillTarget(
                    PillRef.KindGroup(PillKind.LIGHTS),
                    "Lights",
                    "2",
                    enabled = true,
                    available = true,
                    kind = PillKind.LIGHTS,
                ),
            ),
        )
        val defaults = HomePillPreferencesCodec.defaults()
        val sectionId = HomePillPreferencesCodec.kindSectionId(PillKind.LIGHTS)
        val preferences = defaults.copy(
            homeSections = defaults.homeSections.map { section ->
                if (section.sectionId == sectionId) {
                    section.copy(itemOrder = listOf(deviceB, unavailable, deviceA))
                } else section
            },
        )

        assertEquals(
            listOf(deviceB, deviceA, PillRef.KindGroup(PillKind.LIGHTS)),
            HomeSettingsSectionOrder.items(sectionId, preferences, catalog),
        )
    }
}
