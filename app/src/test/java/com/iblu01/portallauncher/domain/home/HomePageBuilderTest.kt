package com.iblu01.portallauncher.domain.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.localizedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomePageBuilderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
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
            ref to PillGroupSnapshot(ref, LauncherChip(ref.stableKey, kind.icon, kind.localizedLabel(context), "${members.size}", kind = kind), members.map { it.ref as PillRef.Device }, members)
        }.toMap()
        return PillCatalogSnapshot(byRef.mapValues { it.value.chip }, kindGroups, byRef.mapValues { Availability.AVAILABLE } + kindGroups.mapValues { Availability.AVAILABLE }, emptyList(), byRef)
    }

    private fun catalogByRoom(vararg rooms: Pair<ResolvedPill, String>): PillCatalogSnapshot {
        val byRef = rooms.map { it.first }.associateBy { it.ref as PillRef.Device }
        val areaGroups: Map<PillRef, PillGroupSnapshot> = rooms.groupBy { it.second }.map { (areaId, members) ->
            val ref = PillRef.AreaGroup(areaId)
            val pills = members.map { it.first }
            ref to PillGroupSnapshot(
                ref,
                LauncherChip(ref.stableKey, "home", areaId, "${members.size}", kind = PillKind.GENERIC),
                pills.map { it.ref as PillRef.Device },
                pills,
            )
        }.toMap()
        return PillCatalogSnapshot(byRef.mapValues { it.value.chip }, areaGroups, byRef.mapValues { Availability.AVAILABLE } + areaGroups.mapValues { Availability.AVAILABLE }, emptyList(), byRef)
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
        val page = HomePageBuilder.build(context, catalog(lightA, lightB), prefs)
        assertEquals(HomeSectionIds.kind(PillKind.LIGHTS), page.sections.first().sectionId)
        assertEquals(listOf(lightB.ref, lightA.ref), page.sections.first().items.map { it.ref })
        assertTrue(page.sections.first().items.none { it.ref is PillRef.KindGroup })
        assertEquals(HomeSectionIds.FAVORITES, page.sections.last().sectionId)
    }

    @Test fun `hidden and empty sections are omitted without losing compatible state`() {
        val light = device("light.a", PillKind.LIGHTS)
        val prefs = preferences(sections = listOf(HomeSectionPreference(HomeSectionIds.kind(PillKind.LIGHTS), false, 0, emptyList())))
        val page = HomePageBuilder.build(context, catalog(light), prefs)
        assertTrue(page.sections.isEmpty())
        assertTrue(page.hasCompatibleDevices)
    }

    @Test fun `empty catalog exposes one page level empty state rather than empty rails`() {
        val page = HomePageBuilder.build(context, catalog(), preferences())
        assertFalse(page.hasCompatibleDevices)
        assertTrue(page.sections.isEmpty())
    }

    @Test fun `by room groups devices into one section per room instead of type rails`() {
        val salonLight = device("light.salon", PillKind.LIGHTS)
        val salonSwitch = device("switch.salon", PillKind.SWITCH)
        val cuisineLight = device("light.cuisine", PillKind.LIGHTS)
        val prefs = preferences().copy(groupingMode = HomeGroupingMode.BY_ROOM)
        val page = HomePageBuilder.build(
            context,
            catalogByRoom(
                salonLight to "salon",
                salonSwitch to "salon",
                cuisineLight to "cuisine",
            ),
            prefs,
        )

        assertEquals(listOf("area:cuisine", "area:salon"), page.sections.filter { it.type == HomeSectionType.AREA }.map { it.sectionId })
        assertTrue(page.sections.none { it.type == HomeSectionType.KIND || it.type == HomeSectionType.AREAS })
        val salon = page.sections.first { it.sectionId == "area:salon" }
        assertEquals(setOf(salonLight.ref, salonSwitch.ref), salon.items.map { it.ref }.toSet())
    }

    @Test fun `by type shows only per-kind rails`() {
        val light = device("light.salon", PillKind.LIGHTS)
        val page = HomePageBuilder.build(context, catalogByRoom(light to "salon"), preferences())
        assertTrue(page.sections.none { it.type == HomeSectionType.AREAS })
        assertTrue(page.sections.any { it.type == HomeSectionType.KIND })
        assertTrue(page.sections.none { it.type == HomeSectionType.AREA })
    }

    @Test fun `column count follows the available width and never exceeds four`() {
        assertEquals(4, HomeGridLayoutPolicy.columns(availableWidthDp = 1200f, fontScale = 1f))
        assertEquals(3, HomeGridLayoutPolicy.columns(availableWidthDp = 800f, fontScale = 1f))
        assertEquals(1, HomeGridLayoutPolicy.columns(availableWidthDp = 400f, fontScale = 1f))
    }

    @Test fun `large font yields fewer columns rather than narrower pills`() {
        assertEquals(2, HomeGridLayoutPolicy.columns(availableWidthDp = 1200f, fontScale = 2f))
        assertEquals(1, HomeGridLayoutPolicy.columns(availableWidthDp = 800f, fontScale = 2f))
    }
}
