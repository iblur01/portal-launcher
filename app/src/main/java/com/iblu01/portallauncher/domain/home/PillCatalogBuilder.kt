package com.iblu01.portallauncher.domain.home

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillPriorityEngine
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.PillSupport
import com.iblu01.portallauncher.domain.model.PillDetail
import com.iblu01.portallauncher.friendlyEntityState
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Builds the non-truncated device/group catalog from one atomic HA snapshot. */
class PillCatalogBuilder(private val priorityEngine: PillPriorityEngine) {
    fun build(
        rules: List<PillRule>,
        states: Map<String, HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
        areaIdByEntity: Map<String, String> = emptyMap(),
        areaNameById: Map<String, String> = emptyMap(),
        manualGroups: List<ManualPillGroup> = emptyList(),
        connected: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
    ): PillCatalogSnapshot {
        // The live catalog is discovery-driven, not preference-driven: disabled rules and newly
        // discovered compatible devices must still be organizable from Maison/Settings. Only the
        // dynamic home ranking below is gated by an explicitly enabled persisted rule.
        val persistedRules = rules.distinctBy { it.entityId }
        val disabledDeviceRefs = persistedRules.asSequence()
            .filterNot(PillRule::enabled)
            .mapTo(linkedSetOf()) { PillRef.Device(it.entityId) }
        val persistedIds = persistedRules.mapTo(hashSetOf()) { it.entityId }
        val discoveredDefaults = PillSupport.candidates(states.values.toList(), deviceIdByEntity)
            .map(PillSupport::defaultRule)
            .filter { it.entityId !in persistedIds }
        val catalogRules = persistedRules + discoveredDefaults
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
                label = kind.label,
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
        val devices = resolvedDevices.mapValues { it.value.chip }
        val provisional = PillCatalogSnapshot(
            devices = devices,
            groups = groups,
            availability = availability,
            dynamicCandidates = emptyList(),
            resolvedDevices = resolvedDevices,
            disabledDeviceRefs = disabledDeviceRefs,
        )
        val dynamic = dynamicCandidates(enabledRules, states, provisional, nowMs)
        return provisional.copy(dynamicCandidates = dynamic)
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
            resolved.isEmpty() -> "Indisponible"
            active > 0 -> "$active actif${if (active > 1) "s" else ""} sur ${resolved.size}"
            else -> "${resolved.size} disponible${if (resolved.size > 1) "s" else ""}"
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
            value = friendlyEntityState(entity),
            state = if (active) "active" else "ok",
            entityId = entity.entityId,
            priority = rule.kind.basePriority + rule.priorityBoost,
            details = related.map { PillDetail(it.name, friendlyEntityState(it), it.entityId) },
            kind = rule.kind,
            deviceState = state,
        )
    }

    private fun availabilityOf(entity: HaEntity, connected: Boolean): Availability = when {
        entity.state.trim().lowercase(Locale.ROOT) in setOf("unavailable", "unknown", "none", "") -> Availability.UNAVAILABLE
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
            PillKind.SCENE, PillKind.PRESENCE -> false
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
