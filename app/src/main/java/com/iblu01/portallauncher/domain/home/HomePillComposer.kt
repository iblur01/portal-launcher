package com.iblu01.portallauncher.domain.home

import java.util.Locale

/** Pure, atomic projection of alert + pin + dynamic sources into the home tray. */
object HomePillComposer {
    fun compose(
        catalog: PillCatalogSnapshot,
        preferences: HomePillPreferences,
        capacity: HomeCapacity = HomeCapacity(),
    ): HomeComposition = compose(catalog, preferences.pinnedOrder, capacity)

    fun compose(
        catalog: PillCatalogSnapshot,
        pinnedOrder: List<PillRef>,
        capacity: HomeCapacity = HomeCapacity(),
    ): HomeComposition {
        val alerts = collapseDuplicateIncidents(
            catalog.allResolved().filter {
                catalog.isVisible(it.ref) &&
                    it.availability.isRenderable &&
                    it.alert?.severity == AlertSeverity.CRITICAL
            },
        ).sortedWith(PillAlertPolicy.comparator())

        val pins = pinnedOrder.distinct().mapNotNull(catalog::resolve)
            .filter { catalog.isVisible(it.ref) && it.availability.isRenderable }
        val dynamics = catalog.dynamicCandidates.asSequence()
            .filter { it.relevant && it.pill.availability.isRenderable }
            .sortedWith(
                compareByDescending<ScoredPill> { it.score }
                    .thenBy { it.chip.label.trim().lowercase(Locale.ROOT) }
                    .thenBy { it.ref.stableKey },
            )
            .map { it.pill }
            .toList()
        // The tray is a fixed 3/9 surface. Once alerts, pins and relevant dynamic entries have
        // taken their places, calm enabled devices fill the remaining slots instead of leaving
        // unexplained holes. Disabled devices remain in the catalog for Settings only.
        val calmFillers = catalog.resolvedDevices.values.asSequence()
            .filter { catalog.isVisible(it.ref) && it.availability.isRenderable }
            .sortedWith(
                compareByDescending<ResolvedPill> { it.chip.priority }
                    .thenBy { it.chip.label.trim().lowercase(Locale.ROOT) }
                    .thenBy { it.ref.stableKey },
            )
            .toList()

        val extraAlertSlots = minOf(capacity.extraCriticalPrimarySlots, alerts.size)
        val primaryCount = capacity.primarySlots + extraAlertSlots
        val totalCount = primaryCount + capacity.secondarySlots
        val selected = LinkedHashMap<PillRef, ResolvedPill>(totalCount)
        fun append(source: Iterable<ResolvedPill>) {
            source.forEach { pill ->
                if (selected.size < totalCount) selected.putIfAbsent(pill.ref, pill)
            }
        }
        append(alerts)
        append(pins)
        append(dynamics)
        append(calmFillers)

        val visible = selected.values.toList()
        val visibleRefs = selected.keys
        return HomeComposition(
            primary = visible.take(primaryCount),
            secondary = visible.drop(primaryCount).take(capacity.secondarySlots),
            favoriteOverflow = pins.filter { it.ref !in visibleRefs },
        )
    }

    private fun collapseDuplicateIncidents(alerts: List<ResolvedPill>): List<ResolvedPill> {
        val claimed = mutableSetOf<String>()
        val representations = alerts.sortedWith(
            compareBy<ResolvedPill> { if (it.ref is PillRef.Device) 0 else 1 }
                .thenByDescending { it.alert?.severity?.rank ?: Int.MIN_VALUE }
                .thenByDescending { it.alert?.occurredAtMs ?: 0L }
                .thenBy { it.ref.stableKey },
        )
        return representations.filter { pill ->
            val incidents = pill.alert?.incidentEntityIds.orEmpty().ifEmpty { setOf(pill.ref.stableKey) }
            if (incidents.any { it in claimed }) false else {
                claimed += incidents
                true
            }
        }
    }
}
