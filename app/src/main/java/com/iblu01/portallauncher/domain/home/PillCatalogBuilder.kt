package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillPriorityEngine
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.PillSupport
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.friendlyEntityState
import com.iblu01.portallauncher.localizedLabel
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Builds the non-truncated device/group catalog from one atomic HA snapshot. */
class PillCatalogBuilder(private val priorityEngine: PillPriorityEngine) {
    private class Discovery(
        val keys: Set<String>,
        val deviceIdByEntity: Map<String, String>,
        val entityCategoryByEntity: Map<String, String>,
        val rules: List<PillRule>,
    )

    /**
     * Memoized discovery (see [discoverDefaults]). Threading contract: [build] is called
     * sequentially by a single collector — the `scan` of PillRepository.transformSnapshots, on
     * Dispatchers.Default — so a plain field is enough here: no lock, no atomic, no volatile.
     */
    private var discovery: Discovery? = null

    /** Number of actual discovery passes; a probe for the tests asserting the memoization holds. */
    internal var discoveryPasses = 0
        private set

    fun build(
        rules: List<PillRule>,
        states: Map<String, HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
        entityCategoryByEntity: Map<String, String> = emptyMap(),
        areaIdByEntity: Map<String, String> = emptyMap(),
        areaNameById: Map<String, String> = emptyMap(),
        manualGroups: List<ManualPillGroup> = emptyList(),
        cameraPreferences: CameraPreferences = CameraPreferences(),
        connected: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): PillCatalogSnapshot {
        // The live catalog is discovery-driven, not preference-driven: disabled rules and newly
        // discovered compatible devices must still be organizable from Maison/Settings. Only the
        // dynamic home ranking below is gated by an explicitly enabled persisted rule.
        val persistedRules = rules.distinctBy { it.entityId }
        val persistedIds = persistedRules.mapTo(hashSetOf()) { it.entityId }
        val discoveredDefaults = discoverDefaults(states, deviceIdByEntity, entityCategoryByEntity)
            .filter { it.entityId !in persistedIds }
        val catalogRules = persistedRules + discoveredDefaults
        val allDisabledDeviceRefs = catalogRules.asSequence()
            .filterNot(PillRule::enabled)
            .mapTo(linkedSetOf()) { PillRef.Device(it.entityId) }
        val enabledRules = persistedRules.filter { it.enabled }
        val availability = linkedMapOf<PillRef, Availability>()
        val resolvedDevices = linkedMapOf<PillRef.Device, ResolvedPill>()
        val ruleByRef = linkedMapOf<PillRef.Device, PillRule>()

        catalogRules.forEach { persistedRule ->
            val ref = PillRef.Device(persistedRule.entityId)
            val entity = states[persistedRule.entityId]
            if (entity == null) {
                availability[ref] = Availability.UNAVAILABLE
                return@forEach
            }
            if (!PillSupport.isAllowedAsPersistedChip(persistedRule, entity)) return@forEach
            val rule = persistedRule.copy(
                kind = if (persistedRule.kind == PillKind.APPLIANCE) PillKind.APPLIANCE
                else PillSupport.kind(entity),
            )
            ruleByRef[ref] = rule
            val deviceAvailability = availabilityOf(entity, connected)
            availability[ref] = deviceAvailability
            if (!deviceAvailability.isRenderable) return@forEach

            val chip = priorityEngine.toChip(rule, entity, states, nowMs)
                ?: calmDeviceChip(rule, entity, states)
            val base = ResolvedPill(ref, chip, deviceAvailability, setOf(entity.entityId))
            val occurredAt = parseInstant(entity.lastChanged)?.toEpochMilli() ?: 0L
            resolvedDevices[ref] = base.copy(alert = PillAlertPolicy.evaluate(base, occurredAt))
        }

        val groups = linkedMapOf<PillRef, PillGroupSnapshot>()
        val allKnownRefs = ruleByRef.keys.toList()

        allKnownRefs.groupBy { areaIdByEntity[it.entityId]?.takeIf(String::isNotBlank) }
            .filterKeys { it != null }
            .forEach { (nullableAreaId, members) ->
                val areaId = requireNotNull(nullableAreaId)
                val ref = PillRef.AreaGroup(areaId)
                groups[ref] = buildGroup(
                    ref = ref,
                    label = areaNameById[areaId].orEmpty().ifBlank { areaId },
                    icon = "home",
                    members = members,
                    resolvedDevices = resolvedDevices,
                    ruleByRef = ruleByRef,
                )
            }

        allKnownRefs.groupBy { requireNotNull(ruleByRef[it]).kind }.forEach { (kind, members) ->
            val ref = PillRef.KindGroup(kind)
            groups[ref] = buildGroup(
                ref = ref,
                label = kind.localizedLabel(priorityEngine.context),
                icon = kind.icon,
                members = members,
                resolvedDevices = resolvedDevices,
                ruleByRef = ruleByRef,
                forcedKind = kind,
            )
        }

        manualGroups.distinctBy { it.id }.forEach { manual ->
            val ref = PillRef.ManualGroup(manual.id)
            groups[ref] = buildGroup(
                ref = ref,
                label = manual.name.trim().ifBlank { manual.id },
                icon = manual.icon?.takeIf(String::isNotBlank) ?: "home",
                // Membership is persistent user intent. Missing/unavailable refs are retained in
                // [members] and only filtered from [resolvedMembers] for the current snapshot.
                members = manual.members.distinct(),
                resolvedDevices = resolvedDevices,
                ruleByRef = ruleByRef,
            )
        }

        groups.forEach { (ref, group) -> availability[ref] = group.availability }
        val specials = buildSpecials(states, cameraPreferences, connected)
        specials.forEach { (ref, pill) -> availability[ref] = pill.availability }
        val devices = resolvedDevices.mapValues { it.value.chip }
        val provisional = PillCatalogSnapshot(
            devices = devices,
            groups = groups,
            availability = availability,
            dynamicCandidates = emptyList(),
            resolvedDevices = resolvedDevices,
            disabledDeviceRefs = allDisabledDeviceRefs,
            specials = specials,
        )
        val dynamic = dynamicCandidates(enabledRules, states, provisional, nowMs)
        return provisional.copy(dynamicCandidates = dynamic)
    }

    /**
     * Discovery depends only on the set of entity ids and on the two registries — never on state
     * values, which change several times per second. Recomputing it per push cost hundreds of
     * thousands of iterations for a result that only moves when a device is added or removed, so it
     * is memoized on that key; comparing 765 hashed keys is three orders of magnitude cheaper.
     *
     * Accepted limitation: attribute-derived data (a renamed `friendly_name`, a late `device_class`)
     * does not refresh the discovered label or kind until the key set or a registry changes. A wall
     * panel's entity set is otherwise stable for days, and persisted rules are unaffected.
     */
    private fun discoverDefaults(
        states: Map<String, HaEntity>,
        deviceIdByEntity: Map<String, String>,
        entityCategoryByEntity: Map<String, String>,
    ): List<PillRule> {
        val cached = discovery
        if (cached != null &&
            cached.keys == states.keys &&
            cached.deviceIdByEntity == deviceIdByEntity &&
            cached.entityCategoryByEntity == entityCategoryByEntity
        ) {
            return cached.rules
        }
        val allEntities = states.values.toList()
        val index = PillSupport.EntityIndex(allEntities, deviceIdByEntity)
        val rules = PillSupport.candidates(allEntities, deviceIdByEntity, index).map { candidate ->
            PillSupport.defaultRule(
                candidate,
                PillSupport.isAutomaticallyEnabled(
                    candidate,
                    allEntities,
                    deviceIdByEntity,
                    entityCategoryByEntity,
                    index,
                ),
            )
        }
        discovery = Discovery(states.keys.toSet(), deviceIdByEntity, entityCategoryByEntity, rules)
        discoveryPasses++
        return rules
    }

    /**
     * The launcher-provided entries. Today: the general "Cameras" pill, which exists as soon as
     * Home Assistant exposes at least one camera the user has not hidden. It is never
     * auto-promoted onto the home — only pinning puts it there — so it costs nothing when unused.
     */
    private fun buildSpecials(
        states: Map<String, HaEntity>,
        cameraPreferences: CameraPreferences,
        connected: Boolean,
    ): Map<PillRef.Special, ResolvedPill> {
        val cameras = states.values
            .filter { it.domain == "camera" }
            .sortedBy { it.entityId }
        if (cameras.isEmpty()) return emptyMap()
        val visibleIds = cameraPreferences.visibleCameras(cameras.map { it.entityId })
        if (visibleIds.isEmpty()) return emptyMap()
        val visible = visibleIds.mapNotNull(states::get)
        val reachable = visible.filter {
            it.state.trim().lowercase(Locale.ROOT) != "unavailable"
        }
        val ref = PillSpecials.cameras
        val context = priorityEngine.context
        val chip = LauncherChip(
            id = ref.stableKey,
            icon = "camera",
            label = context.getString(com.iblu01.portallauncher.R.string.pill_cameras_label),
            value = context.resources.getQuantityString(
                com.iblu01.portallauncher.R.plurals.pill_group_available_count,
                reachable.size,
                reachable.size,
            ),
            state = if (reachable.isEmpty()) "ok" else "active",
            // Deliberately blank: this pill targets no single entity, and a fake entity id here
            // would make the panel router try to resolve one.
            entityId = "",
            priority = PillKind.CAMERA.basePriority,
            details = visible.map {
                PillDetail(it.name, friendlyEntityState(context, it), it.entityId)
            },
            kind = PillKind.CAMERA,
        )
        val availability = when {
            reachable.isEmpty() -> Availability.UNAVAILABLE
            !connected -> Availability.STALE
            else -> Availability.AVAILABLE
        }
        return mapOf(
            ref to ResolvedPill(ref, chip, availability, visible.mapTo(linkedSetOf()) { it.entityId }),
        )
    }

    private fun buildGroup(
        ref: PillRef,
        label: String,
        icon: String,
        members: List<PillRef.Device>,
        resolvedDevices: Map<PillRef.Device, ResolvedPill>,
        ruleByRef: Map<PillRef.Device, PillRule>,
        forcedKind: PillKind? = null,
    ): PillGroupSnapshot {
        val distinctMembers = members.distinct()
        val disabledRefs = disabledDeviceRefsFor(ruleByRef)
        val visibleMembers = distinctMembers.filterNot { it in disabledRefs }
        val resolved = visibleMembers
            .mapNotNull(resolvedDevices::get)
        val memberKinds = visibleMembers.mapNotNull { ruleByRef[it]?.kind }.toSet()
        val kind = forcedKind ?: memberKinds.singleOrNull() ?: PillKind.GENERIC
        val active = resolved.count(::isActive)
        val important = resolved.maxWithOrNull(
            compareBy<ResolvedPill> { it.alert?.severity?.rank ?: Int.MIN_VALUE }
                .thenBy { it.chip.priority },
        )
        val value = when {
            resolved.isEmpty() -> priorityEngine.context.getString(com.iblu01.portallauncher.R.string.entity_state_unavailable)
            active > 0 -> priorityEngine.context.resources.getQuantityString(com.iblu01.portallauncher.R.plurals.pill_group_active_count, active, active, resolved.size)
            else -> priorityEngine.context.resources.getQuantityString(com.iblu01.portallauncher.R.plurals.pill_group_available_count, resolved.size, resolved.size)
        }
        val chip = LauncherChip(
            id = ref.stableKey,
            icon = icon,
            label = label,
            value = value,
            state = when {
                important?.alert != null -> "critical"
                active > 0 -> "active"
                else -> "ok"
            },
            entityId = resolved.joinToString(",") { it.sourceEntityIds.firstOrNull().orEmpty() },
            priority = important?.chip?.priority ?: 0,
            stale = resolved.isNotEmpty() && resolved.all { it.availability == Availability.STALE },
            details = resolved.map { PillDetail(it.chip.label, it.chip.value, it.chip.entityId, isActive(it)) },
            kind = kind,
            deviceState = important?.chip?.deviceState,
        )
        return PillGroupSnapshot(
            ref = ref,
            chip = chip,
            members = distinctMembers,
            resolvedMembers = resolved,
            collectiveAction = collectiveAction(memberKinds),
        )
    }

    private fun disabledDeviceRefsFor(ruleByRef: Map<PillRef.Device, PillRule>): Set<PillRef.Device> =
        ruleByRef.asSequence()
            .filterNot { (_, rule) -> rule.enabled }
            .mapTo(hashSetOf()) { (ref, _) -> ref }

    private fun dynamicCandidates(
        rules: List<PillRule>,
        states: Map<String, HaEntity>,
        catalog: PillCatalogSnapshot,
        nowMs: Long,
    ): List<ScoredPill> {
        val byId = catalog.resolvedDevices.keys.associateBy { it.entityId }
        return priorityEngine.rankDynamic(rules, states, nowMs).mapNotNull { rankedChip ->
            val ref = byId[rankedChip.entityId] ?: historicalGroupRef(rankedChip.id)
                ?: return@mapNotNull null
            val resolved = catalog.resolve(ref) ?: return@mapNotNull null
            if (!resolved.availability.isRenderable) return@mapNotNull null
            val sourceIds = resolved.sourceEntityIds
            val occurrence = sourceIds.mapNotNull { states[it]?.lastChanged?.let(::parseInstant) }
                .maxOfOrNull { it.toEpochMilli() } ?: 0L
            val ranked = resolved.copy(chip = rankedChip, alert = null)
            val withAlert = ranked.copy(alert = PillAlertPolicy.evaluate(ranked, occurrence))
            ScoredPill(withAlert, rankedChip.priority, isDynamicallyRelevant(rankedChip, withAlert.alert))
        }.distinctBy { it.ref }
            .sortedWith(
                compareByDescending<ScoredPill> { it.score }
                    .thenBy { it.chip.label.trim().lowercase(Locale.ROOT) }
                    .thenBy { it.ref.stableKey },
            )
    }

    private fun historicalGroupRef(id: String): PillRef? = when (id) {
        "openings_group" -> PillRef.KindGroup(PillKind.OPENING)
        "lights_group" -> PillRef.KindGroup(PillKind.LIGHTS)
        "media_group" -> PillRef.KindGroup(PillKind.MEDIA)
        "purifier_group" -> PillRef.KindGroup(PillKind.PURIFIER)
        else -> null
    }

    private fun calmDeviceChip(rule: PillRule, entity: HaEntity, states: Map<String, HaEntity>): LauncherChip {
        val related = rule.relatedEntityIds.mapNotNull(states::get)
        val state = entity.state.lowercase(Locale.ROOT)
        val active = state in setOf(
            "on", "open", "opening", "unlocked", "playing", "buffering", "running", "cleaning",
            "washing", "drying", "mowing", "heat", "cool", "heat_cool", "auto",
        )
        return LauncherChip(
            id = entity.entityId,
            icon = rule.kind.icon,
            label = rule.label,
            value = friendlyEntityState(priorityEngine.context, entity),
            state = if (active) "active" else "ok",
            entityId = entity.entityId,
            priority = rule.kind.basePriority + rule.priorityBoost,
            details = related.map { PillDetail(it.name, friendlyEntityState(priorityEngine.context, it), it.entityId) },
            kind = rule.kind,
            deviceState = state,
        )
    }

    private fun availabilityOf(entity: HaEntity, connected: Boolean): Availability = when {
        !PillSupport.isIndividuallyAvailable(entity) -> Availability.UNAVAILABLE
        !connected -> Availability.STALE
        else -> Availability.AVAILABLE
    }

    private fun isDynamicallyRelevant(chip: LauncherChip, alert: PillAlert?): Boolean {
        if (alert != null || chip.state in setOf("critical", "warning")) return true
        val state = chip.deviceState?.lowercase(Locale.ROOT).orEmpty()
        return when (chip.kind) {
            PillKind.SAFETY -> state in setOf("triggered", "pending", "arming", "on", "detected")
            PillKind.LOCK -> state in setOf("unlocked", "jammed", "open")
            PillKind.OPENING -> state in setOf("on", "open", "opening")
            PillKind.MOTION -> state == "on"
            PillKind.APPLIANCE, PillKind.VACUUM -> state in setOf(
                "on", "run", "running", "cleaning", "washing", "drying", "printing", "active",
                "returning", "paused", "prepare", "slicing", "init", "done", "finished", "complete",
                "completed", "finish",
            )
            PillKind.TIMER -> state !in setOf("off", "idle", "paused", "cancelled", "")
            PillKind.COVER -> state in setOf("open", "opening", "closing")
            PillKind.THERMOSTAT -> state in setOf("heat", "cool", "heat_cool", "auto", "dry", "fan_only")
            PillKind.SWITCH, PillKind.FAN, PillKind.LIGHTS, PillKind.PURIFIER,
            PillKind.HUMIDIFIER, PillKind.WATER_HEATER, PillKind.SIREN -> state == "on"
            PillKind.MEDIA -> state in setOf("playing", "buffering", "paused")
            PillKind.VALVE -> state !in setOf("closed", "off", "")
            PillKind.LAWN_MOWER -> state in setOf("mowing", "returning", "error")
            PillKind.GENERIC -> state == "on"
            PillKind.AIR, PillKind.CLIMATE, PillKind.BATTERY, PillKind.ENERGY,
            // Stateless actions: never promoted by the dynamic ranking, only pinned or calm-filled.
            PillKind.SCENE, PillKind.CAMERA, PillKind.PRESENCE -> false
        }
    }

    private fun isActive(pill: ResolvedPill): Boolean =
        pill.chip.state in setOf("active", "warning", "critical") &&
            pill.chip.deviceState?.lowercase(Locale.ROOT) !in setOf("off", "closed", "locked", "idle")

    private fun collectiveAction(kinds: Set<PillKind>): GroupCollectiveAction? = when {
        kinds.isEmpty() -> null
        kinds.all { it in setOf(PillKind.LIGHTS, PillKind.SWITCH, PillKind.FAN, PillKind.PURIFIER) } ->
            GroupCollectiveAction.TURN_OFF
        kinds.singleOrNull() == PillKind.COVER -> GroupCollectiveAction.CLOSE
        kinds.singleOrNull() == PillKind.LOCK -> GroupCollectiveAction.LOCK
        else -> null
    }

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrElse {
            runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
        }
}
