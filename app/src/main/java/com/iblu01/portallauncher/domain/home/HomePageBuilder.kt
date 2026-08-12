package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.PillKind
import java.util.Locale

/** Pure projection of preferences + catalog into ordered, non-empty Maison rails. */
object HomePageBuilder {
    fun build(catalog: PillCatalogSnapshot, preferences: HomePillPreferences): HomePageModel {
        val preferenceById = preferences.homeSections.associateBy { it.sectionId }
        val candidates = mutableListOf<OrderedSection>()

        val favoriteItems = preferences.pinnedOrder.distinct().mapNotNull(catalog::resolve)
            .filter { catalog.isVisible(it.ref) && it.availability.isRenderable }
        addSection(
            target = candidates,
            preference = preferenceById[HomeSectionIds.FAVORITES],
            defaultOrder = 0,
            sectionId = HomeSectionIds.FAVORITES,
            type = HomeSectionType.FAVORITES,
            title = "Favoris",
            items = favoriteItems,
        )

        val areaItems = catalog.groups.keys.filterIsInstance<PillRef.AreaGroup>()
            .mapNotNull(catalog::resolve)
            .filter { it.availability.isRenderable }
            .stableOrder()
        addSection(
            target = candidates,
            preference = preferenceById[HomeSectionIds.AREAS],
            defaultOrder = 100,
            sectionId = HomeSectionIds.AREAS,
            type = HomeSectionType.AREAS,
            title = "Pièces",
            items = areaItems,
        )

        PillKind.values().forEachIndexed { index, kind ->
            val sectionId = HomeSectionIds.kind(kind)
            val devices = catalog.resolvedDevices.values
                .filter {
                    catalog.isVisible(it.ref) &&
                        it.chip.kind == kind &&
                        it.availability.isRenderable
                }
                .stableOrder()
            addSection(
                target = candidates,
                preference = preferenceById[sectionId],
                defaultOrder = 200 + index,
                sectionId = sectionId,
                type = HomeSectionType.KIND,
                title = kind.label,
                // The rail title already expresses the type. Repeating an aggregate "all" pill
                // before the individual devices adds no information and opens the wrong level.
                items = devices,
            )
        }

        val manualItems = preferences.manualGroups.mapNotNull { manual ->
            catalog.resolve(PillRef.ManualGroup(manual.id))
        }.filter { it.availability.isRenderable }
        addSection(
            target = candidates,
            preference = preferenceById[HomeSectionIds.MANUAL_GROUPS],
            defaultOrder = 1_000,
            sectionId = HomeSectionIds.MANUAL_GROUPS,
            type = HomeSectionType.MANUAL_GROUPS,
            title = "Mes groupes",
            items = manualItems,
        )

        return HomePageModel(
            sections = candidates.sortedWith(compareBy<OrderedSection> { it.order }.thenBy { it.model.sectionId })
                .map { it.model },
            hasCompatibleDevices = catalog.devices.isNotEmpty(),
        )
    }

    private fun addSection(
        target: MutableList<OrderedSection>,
        preference: HomeSectionPreference?,
        defaultOrder: Int,
        sectionId: String,
        type: HomeSectionType,
        title: String,
        items: List<ResolvedPill>,
    ) {
        if (preference?.visible == false || items.isEmpty()) return
        val ordered = applyItemOrder(items, preference?.itemOrder.orEmpty())
        target += OrderedSection(
            order = preference?.order ?: defaultOrder,
            model = HomeSectionModel(sectionId, type, title, ordered),
        )
    }

    private fun applyItemOrder(items: List<ResolvedPill>, preferred: List<PillRef>): List<ResolvedPill> {
        val byRef = items.associateBy { it.ref }
        val explicit = preferred.distinct().mapNotNull(byRef::get)
        val explicitRefs = explicit.mapTo(hashSetOf()) { it.ref }
        return explicit + items.filter { it.ref !in explicitRefs }
    }

    private fun List<ResolvedPill>.stableOrder(): List<ResolvedPill> = sortedWith(
        compareBy<ResolvedPill> { it.chip.label.trim().lowercase(Locale.ROOT) }
            .thenBy { it.ref.stableKey },
    )

    private data class OrderedSection(val order: Int, val model: HomeSectionModel)
}
