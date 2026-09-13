package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind

/** Stable identity used by preferences and domain reducers. Display text is never an identity. */
sealed interface PillRef {
    val stableKey: String

    data class Device(val entityId: String) : PillRef {
        override val stableKey: String = "device:$entityId"
    }

    data class AreaGroup(val areaId: String) : PillRef {
        override val stableKey: String = "area:$areaId"
    }

    data class KindGroup(val kind: PillKind) : PillRef {
        override val stableKey: String = "kind:${kind.name}"
    }

    data class ManualGroup(val groupId: String) : PillRef {
        override val stableKey: String = "manual:$groupId"
    }

    /**
     * A launcher-provided entry that no single HA entity backs — currently only the general
     * "Cameras" pill. Kept as a first-class [PillRef] so pinning, ordering and persistence reuse
     * the existing machinery instead of growing a parallel one.
     */
    data class Special(val id: String) : PillRef {
        override val stableKey: String = "special:$id"
    }
}

/** Ids of the launcher-provided [PillRef.Special] entries. */
object PillSpecials {
    const val CAMERAS = "cameras"

    val cameras = PillRef.Special(CAMERAS)
}

/** How the Maison catalog is organized into sections: by device type or by room. */
enum class HomeGroupingMode { BY_TYPE, BY_ROOM }

data class HomePillPreferences(
    val schemaVersion: Int,
    val homePageEnabled: Boolean,
    val pinnedOrder: List<PillRef>,
    val homeSections: List<HomeSectionPreference>,
    val manualGroups: List<ManualPillGroup>,
    val groupingMode: HomeGroupingMode = HomeGroupingMode.BY_TYPE,
)

data class HomeSectionPreference(
    val sectionId: String,
    val visible: Boolean,
    val order: Int,
    val itemOrder: List<PillRef>,
)

data class ManualPillGroup(
    val id: String,
    val name: String,
    val icon: String?,
    val members: List<PillRef.Device>,
)

/** STALE is renderable but blocks new risky actions; it is not individual unavailability. */
enum class Availability {
    AVAILABLE,
    STALE,
    UNAVAILABLE;

    val isRenderable: Boolean get() = this != UNAVAILABLE
    val isPinnable: Boolean get() = this == AVAILABLE
}

enum class AlertSeverity(val rank: Int) {
    HIGH(200),
    CRITICAL(300),
}

data class PillAlert(
    val severity: AlertSeverity,
    val occurredAtMs: Long = 0L,
    /** Stable HA entity ids used to collapse the same incident across individual/group pills. */
    val incidentEntityIds: Set<String>,
)

data class ResolvedPill(
    val ref: PillRef,
    val chip: LauncherChip,
    val availability: Availability = Availability.AVAILABLE,
    val sourceEntityIds: Set<String> = emptySet(),
    val alert: PillAlert? = null,
)

data class ScoredPill(
    val pill: ResolvedPill,
    val score: Int,
    /** Calm, non-pinned entries remain in the catalog but never fill an empty home slot. */
    val relevant: Boolean,
) {
    val ref: PillRef get() = pill.ref
    val chip: LauncherChip get() = pill.chip
}

enum class GroupCollectiveAction {
    TURN_OFF,
    CLOSE,
    LOCK,
}

data class PillGroupSnapshot(
    val ref: PillRef,
    val chip: LauncherChip,
    /** Persisted membership, including temporarily unavailable members. */
    val members: List<PillRef.Device>,
    /** Currently actionable/renderable members, in deterministic group order. */
    val resolvedMembers: List<ResolvedPill>,
    val collectiveAction: GroupCollectiveAction? = null,
) {
    val availability: Availability
        get() = when {
            resolvedMembers.any { it.availability == Availability.AVAILABLE } -> Availability.AVAILABLE
            resolvedMembers.any { it.availability == Availability.STALE } -> Availability.STALE
            else -> Availability.UNAVAILABLE
        }
}

data class PillCatalogSnapshot(
    val devices: Map<PillRef.Device, LauncherChip>,
    val groups: Map<PillRef, PillGroupSnapshot>,
    val availability: Map<PillRef, Availability>,
    val dynamicCandidates: List<ScoredPill>,
    /** Rich device models, kept separately so LauncherChip stays a render model. */
    val resolvedDevices: Map<PillRef.Device, ResolvedPill> = emptyMap(),
    /** Explicitly disabled rules stay discoverable in Settings but are hidden everywhere else. */
    val disabledDeviceRefs: Set<PillRef.Device> = emptySet(),
    /** Launcher-provided entries (the general "Cameras" pill); absent when nothing backs them. */
    val specials: Map<PillRef.Special, ResolvedPill> = emptyMap(),
) {
    fun isVisible(ref: PillRef): Boolean = when (ref) {
        is PillRef.Device -> ref !in disabledDeviceRefs
        else -> true
    }

    fun resolve(ref: PillRef): ResolvedPill? = when (ref) {
        is PillRef.Device -> resolvedDevices[ref] ?: devices[ref]?.let {
            ResolvedPill(ref, it, availability[ref] ?: Availability.UNAVAILABLE, setOf(ref.entityId))
        }
        is PillRef.Special -> specials[ref]
        else -> groups[ref]?.let { group ->
            val alert = group.resolvedMembers.mapNotNull { it.alert }.maxByOrNull { it.severity.rank }
            ResolvedPill(
                ref = ref,
                chip = group.chip,
                availability = availability[ref] ?: group.availability,
                sourceEntityIds = group.resolvedMembers.flatMapTo(linkedSetOf()) { it.sourceEntityIds },
                alert = alert?.copy(
                    incidentEntityIds = group.resolvedMembers.flatMapTo(linkedSetOf()) {
                        it.alert?.incidentEntityIds.orEmpty()
                    },
                ),
            )
        }
    }

    fun allResolved(): List<ResolvedPill> =
        resolvedDevices.values + groups.keys.mapNotNull(::resolve)
}

data class HomeCapacity(
    val primarySlots: Int = 3,
    val secondarySlots: Int = 6,
    /** Extra primary slots are consumed by alerts only, never by pins or dynamic entries. */
    val extraCriticalPrimarySlots: Int = 0,
) {
    init {
        require(primarySlots >= 0)
        require(secondarySlots >= 0)
        require(extraCriticalPrimarySlots >= 0)
    }
}

data class HomeComposition(
    val primary: List<ResolvedPill>,
    val secondary: List<ResolvedPill>,
    val favoriteOverflow: List<ResolvedPill>,
)

enum class HomeSectionType { FAVORITES, AREAS, AREA, KIND, MANUAL_GROUPS }

data class HomeSectionModel(
    val sectionId: String,
    val type: HomeSectionType,
    val title: String,
    val items: List<ResolvedPill>,
)

data class HomePageModel(
    val sections: List<HomeSectionModel>,
    val hasCompatibleDevices: Boolean,
)

object HomeSectionIds {
    const val FAVORITES = "favorites"
    const val AREAS = "areas"
    const val MANUAL_GROUPS = "manual_groups"
    fun kind(kind: PillKind): String = "kind:${kind.name}"
    fun area(areaId: String): String = "area:$areaId"
}
