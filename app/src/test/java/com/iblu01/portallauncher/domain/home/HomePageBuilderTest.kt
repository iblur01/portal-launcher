package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePageBuilderTest {
    private fun device(id: String, kind: PillKind): ResolvedPill {
        val ref = PillRef.Device(id)
        return ResolvedPill(ref, LauncherChip(id, kind.icon, id.substringAfter('.'), "ok", entityId = id, kind = kind), sourceEntityIds = setOf(id))
    }

    private fun preferences(
        pins: List<PillRef> = emptyList(),
        sections: List<HomeSectionPreference> = emptyList(),
        manualGroups: List<ManualPillGroup> = emptyList(),
    ) = HomePillPreferences(1, true, pins, sections, manualGroups)

    private fun catalog(vararg devices: ResolvedPill): PillCatalogSnapshot {
        val byRef = devices.associateBy { it.ref as PillRef.Device }
        val kindGroups: Map<PillRef, PillGroupSnapshot> = devices.groupBy { it.chip.kind }.map { (kind, members) ->
            val ref = PillRef.KindGroup(kind)
            ref to PillGroupSnapshot(ref, LauncherChip(ref.stableKey, kind.icon, kind.label, "${members.size}", kind = kind), members.map { it.ref as PillRef.Device }, members)
        }.toMap()
        return PillCatalogSnapshot(byRef.mapValues { it.value.chip }, kindGroups, byRef.mapValues { Availability.AVAILABLE } + kindGroups.mapValues { Availability.AVAILABLE }, emptyList(), byRef)
    }

    @Test fun `sections follow configured order and item order`() {
        val lightA = device("light.a", PillKind.LIGHTS)
        val lightB = device("light.b", PillKind.LIGHTS)
        val prefs = preferences(
            pins = listOf(lightA.ref),
            sections = listOf(
                HomeSectionPreference(HomeSectionIds.kind(PillKind.LIGHTS), true, 0, listOf(lightB.ref, lightA.ref)),
                HomeSectionPreference(HomeSectionIds.FAVORITES, true, 10, emptyList()),
            ),
        )
        val page = HomePageBuilder.build(catalog(lightA, lightB), prefs)
        assertEquals(HomeSectionIds.kind(PillKind.LIGHTS), page.sections.first().sectionId)
        assertEquals(listOf(lightB.ref, lightA.ref), page.sections.first().items.map { it.ref })
        assertTrue(page.sections.first().items.none { it.ref is PillRef.KindGroup })
        assertEquals(HomeSectionIds.FAVORITES, page.sections.last().sectionId)
    }

    @Test fun `hidden and empty sections are omitted without losing compatible state`() {
        val light = device("light.a", PillKind.LIGHTS)
        val prefs = preferences(sections = listOf(HomeSectionPreference(HomeSectionIds.kind(PillKind.LIGHTS), false, 0, emptyList())))
        val page = HomePageBuilder.build(catalog(light), prefs)
        assertTrue(page.sections.isEmpty())
        assertTrue(page.hasCompatibleDevices)
    }

    @Test fun `empty catalog exposes one page level empty state rather than empty rails`() {
        val page = HomePageBuilder.build(catalog(), preferences())
        assertFalse(page.hasCompatibleDevices)
        assertTrue(page.sections.isEmpty())
    }

    @Test fun `rail uses deterministic one or two row column major placement`() {
        val one = HomeRailLayoutPolicy.calculate(3, 800f, 200f, 1f)
        assertEquals(1, one.rowCount)
        val two = HomeRailLayoutPolicy.calculate(8, 400f, 140f, 1f)
        assertEquals(2, two.rowCount)
        assertEquals(
            listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            two.placements.take(4).map { it.row to it.column },
        )
    }

    @Test fun `large font avoids a second row when two accessible rows do not fit`() {
        val layout = HomeRailLayoutPolicy.calculate(20, 400f, 110f, 1.5f)
        assertEquals(1, layout.rowCount)
    }
}
