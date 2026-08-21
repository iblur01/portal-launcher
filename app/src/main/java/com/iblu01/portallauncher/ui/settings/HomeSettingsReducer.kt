package com.iblu01.portallauncher.ui.settings

import android.content.Context
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.HomePillPreferencesCodec
import com.iblu01.portallauncher.PillCandidate
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.PillSupport
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.localizedLabel
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionIds
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import com.iblu01.portallauncher.domain.home.PillSpecials
import com.iblu01.portallauncher.domain.home.PillRef
import java.util.UUID

enum class MoveDirection { FIRST, PREVIOUS, NEXT, LAST }

sealed interface HomeSettingsAction {
    data class SetHomePageEnabled(val enabled: Boolean) : HomeSettingsAction
    data class TogglePin(val ref: PillRef, val canPin: Boolean) : HomeSettingsAction
    data class MovePin(val ref: PillRef, val direction: MoveDirection) : HomeSettingsAction
    /**
     * Commits a complete pin order after a transient drag-and-drop interaction.
     *
     * Keeping this separate from [MovePin] makes the UI free to stage as many visual moves as
     * needed without writing preferences until the user drops the item.
     */
    data class SetPinnedOrder(val pinnedOrder: List<PillRef>) : HomeSettingsAction
    data class SetSectionVisible(val sectionId: String, val visible: Boolean) : HomeSettingsAction
    data class MoveSection(val sectionId: String, val direction: MoveDirection) : HomeSettingsAction
    data class SetSectionItemOrder(val sectionId: String, val itemOrder: List<PillRef>) : HomeSettingsAction
    data class MoveSectionItem(
        val sectionId: String,
        val ref: PillRef,
        val visibleOrder: List<PillRef>,
        val direction: MoveDirection,
    ) : HomeSettingsAction
    data class CreateManualGroup(val name: String) : HomeSettingsAction
    data class RenameManualGroup(val groupId: String, val name: String) : HomeSettingsAction
    data class DeleteManualGroup(val groupId: String) : HomeSettingsAction
    data class MoveManualGroup(val groupId: String, val direction: MoveDirection) : HomeSettingsAction
    data class SetManualGroupMember(val groupId: String, val device: PillRef.Device, val included: Boolean) : HomeSettingsAction
    data class MoveManualGroupMember(val groupId: String, val device: PillRef.Device, val direction: MoveDirection) : HomeSettingsAction
    data class MoveManualGroupMemberToGroup(
        val fromGroupId: String,
        val toGroupId: String,
        val device: PillRef.Device,
    ) : HomeSettingsAction
}

data class SettingsPillTarget(
    val ref: PillRef,
    val label: String,
    val stateLabel: String,
    val enabled: Boolean,
    val available: Boolean,
    val kind: PillKind? = null,
    /** Global HA disconnect keeps cached data visible but blocks new actions. */
    val stale: Boolean = false,
) {
    /** A disabled rule stays visible here for recovery, but is hidden from launcher surfaces. */
    val canPin: Boolean get() = enabled && available && !stale
}

data class SettingsPillCatalog(
    val devices: List<SettingsPillTarget>,
    val automaticGroups: List<SettingsPillTarget>,
) {
    val byRef: Map<PillRef, SettingsPillTarget> = (devices + automaticGroups).associateBy { it.ref }
}

object HomeSettingsReducer {
    fun reduce(
        preferences: HomePillPreferences,
        action: HomeSettingsAction,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): HomePillPreferences = when (action) {
        is HomeSettingsAction.SetHomePageEnabled -> preferences.copy(homePageEnabled = action.enabled)
        is HomeSettingsAction.TogglePin -> togglePin(preferences, action.ref, action.canPin)
        is HomeSettingsAction.MovePin -> preferences.copy(
            pinnedOrder = move(preferences.pinnedOrder, action.ref, action.direction),
        )
        is HomeSettingsAction.SetPinnedOrder -> preferences.copy(
            pinnedOrder = action.pinnedOrder.distinct(),
        )
        is HomeSettingsAction.SetSectionVisible -> preferences.copy(
            homeSections = preferences.homeSections.map {
                if (it.sectionId == action.sectionId) it.copy(visible = action.visible) else it
            },
        )
        is HomeSettingsAction.MoveSection -> {
            val ordered = preferences.homeSections.sortedWith(compareBy({ it.order }, { it.sectionId }))
            val moved = move(ordered, ordered.firstOrNull { it.sectionId == action.sectionId }, action.direction)
            preferences.copy(homeSections = moved.mapIndexed { index, section -> section.copy(order = index) })
        }
        is HomeSettingsAction.SetSectionItemOrder -> preferences.copy(
            homeSections = preferences.homeSections.map { section ->
                if (section.sectionId == action.sectionId) {
                    section.copy(itemOrder = action.itemOrder.distinct())
                } else section
            },
        )
        is HomeSettingsAction.MoveSectionItem -> preferences.copy(
            homeSections = preferences.homeSections.map { section ->
                if (section.sectionId != action.sectionId) section else {
                    val visible = action.visibleOrder.distinct()
                    val moved = move(visible, action.ref, action.direction)
                    section.copy(itemOrder = mergeVisibleOrder(section.itemOrder.distinct(), moved))
                }
            },
        )
        is HomeSettingsAction.CreateManualGroup -> {
            val cleanName = action.name.trim()
            if (cleanName.isBlank()) preferences else {
                val id = generateUniqueId(preferences.manualGroups, idFactory)
                preferences.copy(manualGroups = preferences.manualGroups + ManualPillGroup(id, cleanName, null, emptyList()))
            }
        }
        is HomeSettingsAction.RenameManualGroup -> {
            val cleanName = action.name.trim()
            if (cleanName.isBlank()) preferences else preferences.copy(
                manualGroups = preferences.manualGroups.map {
                    if (it.id == action.groupId) it.copy(name = cleanName) else it
                },
            )
        }
        is HomeSettingsAction.DeleteManualGroup -> deleteManualGroup(preferences, action.groupId)
        is HomeSettingsAction.MoveManualGroup -> preferences.copy(
            manualGroups = move(
                preferences.manualGroups,
                preferences.manualGroups.firstOrNull { it.id == action.groupId },
                action.direction,
            ),
        )
        is HomeSettingsAction.SetManualGroupMember -> preferences.copy(
            manualGroups = preferences.manualGroups.map { group ->
                if (group.id != action.groupId) group else group.copy(
                    members = if (action.included) {
                        (group.members + action.device).distinct()
                    } else {
                        group.members - action.device
                    },
                )
            },
        )
        is HomeSettingsAction.MoveManualGroupMember -> preferences.copy(
            manualGroups = preferences.manualGroups.map { group ->
                if (group.id == action.groupId) {
                    group.copy(members = move(group.members, action.device, action.direction))
                } else group
            },
        )
        is HomeSettingsAction.MoveManualGroupMemberToGroup -> {
            if (action.fromGroupId == action.toGroupId || preferences.manualGroups.none { it.id == action.toGroupId }) {
                preferences
            } else preferences.copy(
                manualGroups = preferences.manualGroups.map { group -> when (group.id) {
                    action.fromGroupId -> group.copy(members = group.members - action.device)
                    action.toGroupId -> group.copy(members = (group.members + action.device).distinct())
                    else -> group
                } },
            )
        }
    }

    private fun togglePin(preferences: HomePillPreferences, ref: PillRef, canPin: Boolean): HomePillPreferences {
        val pinned = ref in preferences.pinnedOrder
        if (!pinned && !canPin) return preferences
        return preferences.copy(
            pinnedOrder = if (pinned) preferences.pinnedOrder - ref else preferences.pinnedOrder + ref,
        )
    }

    private fun deleteManualGroup(preferences: HomePillPreferences, groupId: String): HomePillPreferences {
        val ref = PillRef.ManualGroup(groupId)
        return preferences.copy(
            manualGroups = preferences.manualGroups.filterNot { it.id == groupId },
            pinnedOrder = preferences.pinnedOrder.filterNot { it == ref },
            homeSections = preferences.homeSections.map { section ->
                section.copy(itemOrder = section.itemOrder.filterNot { it == ref })
            },
        )
    }

    private fun generateUniqueId(groups: List<ManualPillGroup>, idFactory: () -> String): String {
        val existing = groups.mapTo(hashSetOf()) { it.id }
        repeat(16) {
            idFactory().trim().takeIf { candidate -> candidate.isNotBlank() && candidate !in existing }?.let { return it }
        }
        return "group-${System.currentTimeMillis()}-${existing.size}"
    }

    /** Reorders rendered items while retaining temporarily absent refs in persisted preferences. */
    private fun mergeVisibleOrder(persisted: List<PillRef>, movedVisible: List<PillRef>): List<PillRef> {
        if (movedVisible.isEmpty()) return persisted
        val visibleSet = movedVisible.toHashSet()
        val moved = movedVisible.iterator()
        return buildList {
            persisted.forEach { ref ->
                add(if (ref in visibleSet && moved.hasNext()) moved.next() else ref)
            }
            while (moved.hasNext()) add(moved.next())
        }.distinct()
    }

    private fun <T> move(list: List<T>, item: T?, direction: MoveDirection): List<T> {
        if (item == null) return list
        val from = list.indexOf(item)
        if (from < 0 || list.size < 2) return list
        val to = when (direction) {
            MoveDirection.FIRST -> 0
            MoveDirection.PREVIOUS -> (from - 1).coerceAtLeast(0)
            MoveDirection.NEXT -> (from + 1).coerceAtMost(list.lastIndex)
            MoveDirection.LAST -> list.lastIndex
        }
        if (from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}

object HomeSettingsCatalogBuilder {
    fun build(
        context: Context,
        candidates: List<PillCandidate>,
        rules: List<PillRule>,
        areaIdByEntity: Map<String, String> = emptyMap(),
        areaNameById: Map<String, String> = emptyMap(),
        connected: Boolean = true,
    ): SettingsPillCatalog {
        val enabledIds = rules.filter(PillRule::enabled).mapTo(hashSetOf(), PillRule::entityId)
        val devices = candidates.distinctBy { it.primary.entityId }.map { candidate ->
            SettingsPillTarget(
                ref = PillRef.Device(candidate.primary.entityId),
                label = candidate.label,
                stateLabel = com.iblu01.portallauncher.friendlyEntityState(context, candidate.primary),
                enabled = candidate.primary.entityId in enabledIds,
                available = candidate.primary.isIndividuallyAvailable(),
                kind = candidate.kind,
                stale = !connected,
            )
        }.sortedWith(compareBy({ it.label.lowercase() }, { it.ref.stableKey }))

        // Disabled devices remain listed above so they can be re-enabled, but they must not make
        // an automatic group look pinnable or reappear indirectly through that group.
        val compatibleAvailable = candidates.filter {
            it.primary.isIndividuallyAvailable() && it.primary.entityId in enabledIds
        }
        val kindGroups = compatibleAvailable.groupBy(PillCandidate::kind).map { (kind, members) ->
            SettingsPillTarget(
                ref = PillRef.KindGroup(kind),
                label = kind.localizedLabel(context),
                stateLabel = "${members.size}",
                enabled = true,
                available = members.isNotEmpty(),
                kind = kind,
                stale = !connected,
            )
        }
        val areaGroups = compatibleAvailable.groupBy { areaIdByEntity[it.primary.entityId]?.takeIf(String::isNotBlank) }
            .filterKeys { it != null }
            .map { (nullableAreaId, members) ->
                val areaId = requireNotNull(nullableAreaId)
                SettingsPillTarget(
                    ref = PillRef.AreaGroup(areaId),
                    label = areaNameById[areaId].orEmpty().ifBlank { areaId },
                    stateLabel = "${members.size}",
                    enabled = true,
                    available = members.isNotEmpty(),
                    stale = !connected,
                )
            }
        return SettingsPillCatalog(
            devices = devices,
            automaticGroups = (areaGroups + kindGroups).sortedWith(
                compareBy({ if (it.ref is PillRef.AreaGroup) 0 else 1 }, { it.label.lowercase() }, { it.ref.stableKey }),
            ),
        )
    }

    /** Shared with the live catalog so a scene or camera is never wrongly greyed out here. */
    private fun HaEntity.isIndividuallyAvailable(): Boolean =
        PillSupport.isIndividuallyAvailable(this)
}

/** Pure projection used by Settings UI and accessible reorder actions. */
object HomeSettingsSectionOrder {
    fun items(
        sectionId: String,
        preferences: HomePillPreferences,
        catalog: SettingsPillCatalog,
    ): List<PillRef> {
        val candidates = when (sectionId) {
            HomeSectionIds.FAVORITES -> preferences.pinnedOrder.filter {
                isSettingsRenderable(it, preferences, catalog)
            }
            HomeSectionIds.AREAS -> catalog.automaticGroups.mapNotNull { target ->
                target.ref.takeIf { it is PillRef.AreaGroup }
            }
            HomeSectionIds.MANUAL_GROUPS -> preferences.manualGroups.map { PillRef.ManualGroup(it.id) }
                .filter { isSettingsRenderable(it, preferences, catalog) }
            else -> PillKind.entries.firstOrNull { HomeSectionIds.kind(it) == sectionId }?.let { kind ->
                val group = PillRef.KindGroup(kind).takeIf(catalog.byRef::containsKey)
                listOfNotNull(group) + catalog.devices.filter { it.kind == kind && it.available }.map { it.ref }
            }.orEmpty()
        }.distinct()
        val preference = preferences.homeSections.firstOrNull { it.sectionId == sectionId }
        val byRef = candidates.toHashSet()
        val explicit = preference?.itemOrder.orEmpty().filter { it in byRef }.distinct()
        return explicit + candidates.filter { it !in explicit }
    }
}

data class HomePinPreview(
    val visible: List<PillRef>,
    val overflow: List<PillRef>,
    val unavailable: List<PillRef>,
)

/** Pure settings preview mirroring the composer's available-pin promotion semantics. */
object HomeSettingsPinPreview {
    fun build(
        preferences: HomePillPreferences,
        catalog: SettingsPillCatalog,
        capacity: Int = 9,
    ): HomePinPreview {
        require(capacity >= 0)
        val distinctPins = preferences.pinnedOrder.distinct()
        val renderable = distinctPins.filter { isSettingsRenderable(it, preferences, catalog) }
        return HomePinPreview(
            visible = renderable.take(capacity),
            overflow = renderable.drop(capacity),
            unavailable = distinctPins.filterNot(renderable::contains),
        )
    }

}

private fun isSettingsRenderable(
    ref: PillRef,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
): Boolean = when (ref) {
    is PillRef.ManualGroup -> preferences.manualGroups.firstOrNull { it.id == ref.groupId }
        ?.members
        ?.any { catalog.byRef[it]?.available == true }
        ?: false
    else -> catalog.byRef[ref]?.available == true
}

fun labelForSettingsRef(
    context: Context,
    ref: PillRef,
    catalog: SettingsPillCatalog,
    preferences: HomePillPreferences,
): String = catalog.byRef[ref]?.label ?: when (ref) {
    is PillRef.Device -> ref.entityId
    is PillRef.AreaGroup -> ref.areaId
    is PillRef.KindGroup -> ref.kind.localizedLabel(context)
    is PillRef.ManualGroup -> preferences.manualGroups.firstOrNull { it.id == ref.groupId }?.name ?: ref.groupId
    is PillRef.Special -> when (ref.id) {
        PillSpecials.CAMERAS -> context.getString(R.string.pill_cameras_label)
        else -> ref.id
    }
}

fun defaultHomePreferences(): HomePillPreferences = HomePillPreferencesCodec.defaults()
