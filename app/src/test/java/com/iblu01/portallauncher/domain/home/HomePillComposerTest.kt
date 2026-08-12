package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePillComposerTest {
    private fun ref(id: String) = PillRef.Device("switch.$id")
    private fun pill(
        id: String,
        score: Int = 20,
        relevant: Boolean = true,
        availability: Availability = Availability.AVAILABLE,
        alert: PillAlert? = null,
    ): ScoredPill {
        val ref = ref(id)
        val resolved = ResolvedPill(
            ref,
            LauncherChip(ref.entityId, "switch", id, id, priority = score, entityId = ref.entityId, kind = PillKind.SWITCH),
            availability,
            setOf(ref.entityId),
            alert,
        )
        return ScoredPill(resolved, score, relevant)
    }

    private fun catalog(vararg pills: ScoredPill): PillCatalogSnapshot {
        val resolved = pills.associate { it.ref as PillRef.Device to it.pill }
        return PillCatalogSnapshot(
            devices = resolved.mapValues { it.value.chip },
            groups = emptyMap(),
            availability = resolved.mapValues { it.value.availability },
            dynamicCandidates = pills.toList(),
            resolvedDevices = resolved,
        )
    }

    private fun ids(items: List<ResolvedPill>) = items.map { (it.ref as PillRef.Device).entityId.substringAfter('.') }

    @Test fun `zero pins fills the three resting slots with calm devices after relevant dynamics`() {
        val result = HomePillComposer.compose(catalog(pill("a", 30), pill("calm", 100, relevant = false), pill("b", 20)), emptyList())
        assertEquals(listOf("a", "b", "calm"), ids(result.primary))
        assertTrue(result.secondary.isEmpty())
    }

    @Test fun `nine enabled devices fill primary and secondary even when none is pinned`() {
        val calm = (1..9).map { pill("calm$it", score = 100 - it, relevant = false) }

        val result = HomePillComposer.compose(catalog(*calm.toTypedArray()), emptyList())

        assertEquals(3, result.primary.size)
        assertEquals(6, result.secondary.size)
        assertEquals((1..9).map { "calm$it" }, ids(result.primary + result.secondary))
    }

    @Test fun `explicitly disabled device remains catalogued but cannot fill the tray`() {
        val visible = pill("visible", relevant = false)
        val hidden = pill("hidden", score = 999, relevant = false)
        val base = catalog(visible, hidden)
        val result = HomePillComposer.compose(
            base.copy(disabledDeviceRefs = setOf(hidden.ref as PillRef.Device)),
            listOf(hidden.ref),
        )

        assertEquals(listOf("visible"), ids(result.primary))
        assertTrue(result.secondary.isEmpty())
    }

    @Test fun `three pins preserve persistent order even when calm`() {
        val c = catalog(pill("a", relevant = false), pill("b", relevant = false), pill("c", relevant = false))
        val result = HomePillComposer.compose(c, listOf(ref("c"), ref("a"), ref("b")))
        assertEquals(listOf("c", "a", "b"), ids(result.primary))
    }

    @Test fun `nine pins fill primary and secondary`() {
        val pills = (1..9).map { pill("p$it", relevant = false) }
        val result = HomePillComposer.compose(catalog(*pills.toTypedArray()), pills.map { it.ref })
        assertEquals(listOf("p1", "p2", "p3"), ids(result.primary))
        assertEquals((4..9).map { "p$it" }, ids(result.secondary))
        assertTrue(result.favoriteOverflow.isEmpty())
    }

    @Test fun `pins beyond nine remain in favorite overflow and promote when unpinned`() {
        val pills = (1..11).map { pill("p$it", relevant = false) }
        val c = catalog(*pills.toTypedArray())
        val order = pills.map { it.ref }
        assertEquals(listOf("p10", "p11"), ids(HomePillComposer.compose(c, order).favoriteOverflow))
        val after = HomePillComposer.compose(c, order.drop(1))
        assertEquals("p10", ids(after.secondary).last())
        assertEquals(listOf("p11"), ids(after.favoriteOverflow))
    }

    @Test fun `pins and dynamic candidates never duplicate a ref`() {
        val c = catalog(pill("pinned", 1), pill("dynamic", 50))
        val result = HomePillComposer.compose(c, listOf(ref("pinned")))
        assertEquals(listOf("pinned", "dynamic"), ids(result.primary))
    }

    @Test fun `alert displaces visually without changing pin order`() {
        val alert = pill(
            "alarm",
            100,
            alert = PillAlert(AlertSeverity.CRITICAL, 10, setOf("switch.alarm")),
        )
        val pins = (1..9).map { pill("p$it", relevant = false) }
        val order = pins.map { it.ref }
        val c = catalog(alert, *pins.toTypedArray())
        val constrained = HomePillComposer.compose(c, order)
        assertEquals(listOf("alarm", "p1", "p2"), ids(constrained.primary))
        assertEquals(listOf("p9"), ids(constrained.favoriteOverflow))

        val wide = HomePillComposer.compose(c, order, HomeCapacity(extraCriticalPrimarySlots = 1))
        assertEquals(listOf("alarm", "p1", "p2", "p3"), ids(wide.primary))
        assertTrue(wide.favoriteOverflow.isEmpty())

        val noAlertCatalog = catalog(*pins.toTypedArray())
        val restored = HomePillComposer.compose(noAlertCatalog, order)
        assertEquals((1..9).map { "p$it" }, ids(restored.primary + restored.secondary))
    }

    @Test fun `multiple alerts sort by severity then recency then stable key`() {
        val criticalOld = pill(
            "critical-old",
            alert = PillAlert(AlertSeverity.CRITICAL, 100, setOf("switch.critical-old")),
        )
        val criticalRecentB = pill(
            "critical-recent-b",
            alert = PillAlert(AlertSeverity.CRITICAL, 200, setOf("switch.critical-recent-b")),
        )
        val criticalRecentA = pill(
            "critical-recent-a",
            alert = PillAlert(AlertSeverity.CRITICAL, 200, setOf("switch.critical-recent-a")),
        )

        val result = HomePillComposer.compose(
            catalog(criticalOld, criticalRecentB, criticalRecentA),
            emptyList(),
        )

        assertEquals(
            listOf("critical-recent-a", "critical-recent-b", "critical-old"),
            ids(result.primary + result.secondary),
        )
    }

    @Test fun `high warning never overtakes pinned favorites`() {
        val unlockedLock = pill(
            "unlocked-lock",
            score = 100,
            alert = PillAlert(AlertSeverity.HIGH, 999, setOf("lock.front")),
        )
        val pins = (1..9).map { pill("p$it", relevant = false) }

        val result = HomePillComposer.compose(
            catalog(unlockedLock, *pins.toTypedArray()),
            pins.map { it.ref },
        )

        assertEquals((1..9).map { "p$it" }, ids(result.primary + result.secondary))
    }

    @Test fun `unavailable pin is replaced then restored at its logical rank`() {
        val unavailable = pill("p2", relevant = false, availability = Availability.UNAVAILABLE)
        val unavailableCatalog = catalog(pill("p1", relevant = false), unavailable, pill("p3", relevant = false), pill("p4", 40))
        val order = listOf(ref("p1"), ref("p2"), ref("p3"))
        assertEquals(listOf("p1", "p3", "p4"), ids(HomePillComposer.compose(unavailableCatalog, order).primary))

        val restored = catalog(pill("p1", relevant = false), pill("p2", relevant = false), pill("p3", relevant = false), pill("p4", 40))
        assertEquals(listOf("p1", "p2", "p3"), ids(HomePillComposer.compose(restored, order).primary))
    }

    @Test fun `unavailable favorite promotes overflow then restoration is exact`() {
        val nominalPills = (1..11).map { pill("p$it", relevant = false) }
        val order = nominalPills.map { it.ref }
        val unavailablePills = nominalPills.map { candidate ->
            if (candidate.ref == ref("p2")) candidate.copy(
                pill = candidate.pill.copy(availability = Availability.UNAVAILABLE),
            ) else candidate
        }

        val temporarilyUnavailable = HomePillComposer.compose(
            catalog(*unavailablePills.toTypedArray()),
            order,
        )
        assertEquals(
            listOf("p1") + (3..10).map { "p$it" },
            ids(temporarilyUnavailable.primary + temporarilyUnavailable.secondary),
        )
        assertEquals(listOf("p11"), ids(temporarilyUnavailable.favoriteOverflow))

        val restored = HomePillComposer.compose(catalog(*nominalPills.toTypedArray()), order)
        assertEquals((1..9).map { "p$it" }, ids(restored.primary + restored.secondary))
        assertEquals(listOf("p10", "p11"), ids(restored.favoriteOverflow))
    }

    @Test fun `equal dynamic scores use normalized label then stable key`() {
        fun named(id: String, label: String): ScoredPill {
            val base = pill(id, 42)
            return base.copy(pill = base.pill.copy(chip = base.chip.copy(label = label)))
        }
        val c = catalog(named("z", "beta"), named("b", "Alpha"), named("a", "alpha"))
        assertEquals(listOf("a", "b", "z"), ids(HomePillComposer.compose(c, emptyList()).primary))
    }

    @Test fun `individual alert wins over group representation of same incident`() {
        val incident = "switch.alarm"
        val individual = pill("alarm", 100, alert = PillAlert(AlertSeverity.CRITICAL, 2, setOf(incident)))
        val groupRef = PillRef.ManualGroup("security")
        val groupChip = LauncherChip(groupRef.stableKey, "shield", "Sécurité", "Alerte", kind = PillKind.SAFETY)
        val groupMember = individual.pill
        val group = PillGroupSnapshot(groupRef, groupChip, listOf(ref("alarm")), listOf(groupMember))
        val base = catalog(individual)
        val c = base.copy(
            groups = mapOf(groupRef to group),
            availability = base.availability + (groupRef to Availability.AVAILABLE),
        )
        val result = HomePillComposer.compose(c, emptyList())
        assertEquals(listOf(ref("alarm")), result.primary.map { it.ref })
    }
}
