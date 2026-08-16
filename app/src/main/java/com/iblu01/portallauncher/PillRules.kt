package com.iblu01.portallauncher

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

enum class PillKind(@StringRes val labelRes: Int, val icon: String, val basePriority: Int) {
    SAFETY(R.string.pill_kind_safety, "shield", 90),
    LOCK(R.string.pill_kind_lock, "lock", 35),
    OPENING(R.string.pill_kind_opening, "door", 25),
    MOTION(R.string.pill_kind_motion, "motion", 30),
    APPLIANCE(R.string.pill_kind_appliance, "washer", 58),
    VACUUM(R.string.pill_kind_vacuum, "vacuum", 58),
    BATTERY(R.string.pill_kind_battery, "battery", 20),
    AIR(R.string.pill_kind_air, "air", 35),
    CLIMATE(R.string.pill_kind_climate, "temperature", 22),
    THERMOSTAT(R.string.pill_kind_thermostat, "temperature", 24),
    COVER(R.string.pill_kind_cover, "cover", 26),
    SWITCH(R.string.pill_kind_switch, "switch", 17),
    FAN(R.string.pill_kind_fan, "fan", 15),
    SCENE(R.string.pill_kind_scene, "scene", 12),
    PRESENCE(R.string.pill_kind_presence, "presence", 20),
    ENERGY(R.string.pill_kind_energy, "energy", 18),
    LIGHTS(R.string.pill_kind_lights, "light", 16),
    MEDIA(R.string.pill_kind_media, "media", 13),
    PURIFIER(R.string.pill_kind_purifier, "air", 15),
    TIMER(R.string.pill_kind_timer, "timer", 50),
    HUMIDIFIER(R.string.pill_kind_humidifier, "humidity", 20),
    WATER_HEATER(R.string.pill_kind_water_heater, "temperature", 24),
    VALVE(R.string.pill_kind_valve, "valve", 30),
    SIREN(R.string.pill_kind_siren, "shield", 45),
    LAWN_MOWER(R.string.pill_kind_lawn_mower, "mower", 28),
    CAMERA(R.string.pill_kind_camera, "camera", 14),
    GENERIC(R.string.pill_kind_generic, "sensor", 15),
}

fun PillKind.localizedLabel(context: Context): String = context.getString(labelRes)

/**
 * A Home Assistant entity snapshot. [attributes] is a `JSONObject` (no structural `equals`), so
 * this class provides explicit value equality keyed on the entity id, state, and a cached string
 * form of the attributes. Without this, every HA push produced a fresh, unequal instance — the
 * enclosing `latestStates` map was never equal frame-to-frame, defeating `StateFlow` conflation
 * and making the UI state emit on every push. [attrKey] is computed once (lazy) and reused.
 */
class HaEntity(
    val entityId: String,
    val state: String,
    val attributes: JSONObject,
    val lastChanged: String = "",
) {
    val name: String get() = attributes.optString("friendly_name").ifBlank { entityId.substringAfter('.') }
    val domain: String get() = entityId.substringBefore('.')
    val deviceClass: String get() = attributes.optString("device_class").lowercase()

    private val attrKey: String by lazy(LazyThreadSafetyMode.PUBLICATION) { attributes.toString() }

    fun copy(
        entityId: String = this.entityId,
        state: String = this.state,
        attributes: JSONObject = this.attributes,
        lastChanged: String = this.lastChanged,
    ) = HaEntity(entityId, state, attributes, lastChanged)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HaEntity) return false
        return entityId == other.entityId && state == other.state && attrKey == other.attrKey
    }

    override fun hashCode(): Int = (entityId.hashCode() * 31 + state.hashCode()) * 31 + attrKey.hashCode()

    override fun toString(): String = "HaEntity($entityId, $state)"
}

data class PillRule(
    val entityId: String,
    val kind: PillKind,
    val label: String,
    val enabled: Boolean = true,
    val priorityBoost: Int = 0,
    val relatedEntityIds: List<String> = emptyList(),
)

/**
 * Plain-language buckets grouping the ~20 technical [PillKind]s into a handful of
 * families, so the settings page doesn't expose HA jargon to the user.
 */
enum class PillFamily(@StringRes val labelRes: Int, val kinds: Set<PillKind>) {
    SECURITY(R.string.pill_family_security, setOf(PillKind.SAFETY, PillKind.LOCK, PillKind.OPENING, PillKind.MOTION, PillKind.SIREN)),
    COMFORT(R.string.pill_family_comfort, setOf(PillKind.THERMOSTAT, PillKind.PURIFIER, PillKind.FAN, PillKind.COVER, PillKind.HUMIDIFIER, PillKind.WATER_HEATER)),
    APPLIANCES(R.string.pill_family_appliances, setOf(PillKind.APPLIANCE, PillKind.VACUUM, PillKind.SWITCH, PillKind.VALVE, PillKind.LAWN_MOWER)),
    LIGHTS(R.string.pill_family_lights, setOf(PillKind.LIGHTS)),
    MEDIA(R.string.pill_family_media, setOf(PillKind.MEDIA)),
    SCENES(R.string.pill_family_scenes, setOf(PillKind.SCENE)),
    CAMERAS(R.string.pill_family_cameras, setOf(PillKind.CAMERA)),
    HOME(R.string.pill_family_home, setOf(PillKind.TIMER, PillKind.GENERIC));

    companion object {
        /** Legacy codec kinds intentionally have no Settings family once their support is removed. */
        fun of(kind: PillKind): PillFamily? = values().firstOrNull { kind in it.kinds }
    }
}

fun PillFamily.localizedLabel(context: Context): String = context.getString(labelRes)

/** Plain-language rendering of an entity's current state, e.g. "Ouverte", "21°", "Allumé". */
fun friendlyEntityState(context: Context, e: HaEntity): String {
    val raw = e.state.trim()
    val s = raw.lowercase()
    val unit = e.attributes.optString("unit_of_measurement").trim()
    fun number(): String? = s.toFloatOrNull()?.let { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString() else raw
    }
    return when {
        s == "unavailable" -> context.getString(R.string.entity_state_unavailable)
        s in setOf("unknown", "none", "") && e.domain != "scene" -> "—"
        // A scene's state is the ISO timestamp of its last activation (or `unknown` when it has
        // never run): neither is a state the user acts on, so the pill advertises the action.
        e.domain == "scene" -> context.getString(R.string.pill_scene_ready)
        e.domain == "camera" -> when (s) {
            "streaming" -> context.getString(R.string.camera_state_streaming)
            "recording" -> context.getString(R.string.camera_state_recording)
            else -> context.getString(R.string.camera_state_idle)
        }
        e.domain in setOf("person", "device_tracker") -> when (s) { "home" -> context.getString(R.string.entity_state_home); "not_home" -> context.getString(R.string.entity_state_away); else -> raw.replaceFirstChar { it.uppercase() } }
        e.domain == "lock" -> when (s) { "locked" -> context.getString(R.string.pill_lock_locked); "unlocked" -> context.getString(R.string.pill_lock_unlocked); "locking" -> context.getString(R.string.pill_lock_locking); "unlocking" -> context.getString(R.string.pill_lock_unlocking); else -> raw.replaceFirstChar { it.uppercase() } }
        s == "on" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> context.getString(R.string.opening_state_open)
            "motion", "occupancy", "presence" -> context.getString(R.string.motion_state_detected)
            "smoke", "carbon_monoxide", "gas", "moisture", "problem", "battery" -> context.getString(R.string.pill_safety_alert)
            "connectivity" -> context.getString(R.string.entity_state_connected)
            "battery_charging" -> context.getString(R.string.entity_state_charging)
            "plug" -> context.getString(R.string.entity_state_plugged)
            "power", "running", "moving", "vibration", "sound", "heat", "cold", "light" -> context.getString(R.string.entity_state_active)
            else -> context.getString(R.string.light_state_on)
        }
        s == "off" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> context.getString(R.string.opening_state_closed)
            "motion", "occupancy", "presence" -> context.getString(R.string.motion_state_clear)
            "smoke", "carbon_monoxide", "gas", "moisture", "problem", "battery" -> context.getString(R.string.entity_state_clear)
            "connectivity" -> context.getString(R.string.entity_state_disconnected)
            "battery_charging" -> context.getString(R.string.entity_state_not_charging)
            "plug" -> context.getString(R.string.entity_state_unplugged)
            "power", "running", "moving", "vibration", "sound", "heat", "cold", "light" -> context.getString(R.string.entity_state_inactive)
            else -> context.getString(R.string.light_state_off)
        }
        s == "open" || s == "opening" -> context.getString(R.string.opening_state_open)
        s == "closed" -> context.getString(R.string.opening_state_closed)
        s == "playing" -> context.getString(R.string.entity_state_playing)
        s == "paused" -> context.getString(R.string.entity_state_paused)
        s == "idle" || s == "standby" -> context.getString(R.string.entity_state_inactive)
        s == "locked" -> context.getString(R.string.pill_lock_locked)
        s == "unlocked" -> context.getString(R.string.pill_lock_unlocked)
        number() != null -> if (unit.isNotEmpty()) "${number()} $unit" else raw
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}

data class PillCandidate(val primary: HaEntity, val kind: PillKind, val label: String, val related: List<HaEntity>)

/** Decodes a `/api/states` payload; malformed JSON yields an empty list rather than throwing. */
fun parseHaEntities(raw: String): List<HaEntity> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { i ->
        val o = array.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("entity_id")
        if (id.isBlank()) null
        else HaEntity(
            id,
            o.optString("state"),
            o.optJSONObject("attributes") ?: JSONObject(),
            o.optString("last_changed"),
        )
    }
}.getOrDefault(emptyList())

object PillRuleCodec {
    fun encode(rules: List<PillRule>): String = JSONArray().apply {
        rules.forEach { r -> put(JSONObject().apply {
            put("entity_id", r.entityId); put("kind", r.kind.name); put("label", r.label)
            put("enabled", r.enabled); put("priority_boost", r.priorityBoost)
            put("related_entity_ids", JSONArray(r.relatedEntityIds))
        }) }
    }.toString()

    fun decode(raw: String): List<PillRule> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("entity_id").trim()
            if (id.isBlank()) return@mapNotNull null
            PillRule(id, runCatching { PillKind.valueOf(o.optString("kind")) }.getOrDefault(PillKind.GENERIC),
                o.optString("label").ifBlank { id.substringAfter('.') }, o.optBoolean("enabled", true),
                o.optInt("priority_boost", 0).coerceIn(-20, 20),
                (o.optJSONArray("related_entity_ids") ?: JSONArray()).let { a -> (0 until a.length()).map { a.optString(it) }.filter(String::isNotBlank) })
        }
    }.getOrDefault(emptyList())
}

object PillSupport {
    /**
     * Whether an entity is usable right now.
     *
     * Domain-aware on purpose: a scene's state is the timestamp of its last activation, so one
     * that has never run reports `unknown`, and a camera reports `idle`/`unknown` until it
     * streams. Neither is unavailable — only Home Assistant's explicit `unavailable` is. Reading
     * the generic rule for them would make a brand-new scene impossible to pin.
     */
    fun isIndividuallyAvailable(e: HaEntity): Boolean {
        val state = e.state.trim().lowercase()
        return if (e.domain in statelessActionDomains) state != "unavailable"
        else state !in setOf("unavailable", "unknown", "none", "")
    }

    /** Domains whose entities act instead of holding a state worth reading back. */
    internal val statelessActionDomains = setOf("scene", "camera")

    private val openingClasses = setOf("door", "window", "opening", "garage_door")
    /** Only short-lived movement signals remain eligible; location/presence is intentionally out. */
    private val motionClasses = setOf("motion", "occupancy", "moving")
    private val applianceTokens = setOf("washer", "washing", "machine_a_laver", "dryer", "seche_linge", "dishwasher", "lave_vaisselle", "p2s", "printer", "imprimante")
    private val auxiliarySwitchTokens = setOf(
        "crossfade", "loudness", "autoplay", "auto_play", "tv_autoplay", "night_sound",
        "surround", "dialog", "speech_enhancement", "mic_mute", "screen_timeout",
        "volume_mute", "led", "indicator", "child_lock", "motor_reversal", "chirp",
        "schedule", "bubble_soak", "carpet_boost", "dust_collect", "multi_floor",
        "resume_clean", "tight_mop", "customized_clean", "permit_join",
    )
    fun isSupported(e: HaEntity) = isPrimary(e)
    fun isAllowedAsPersistedChip(rule: PillRule, e: HaEntity): Boolean {
        if (e.domain in setOf("button", "input_button", "number", "input_number", "select", "input_select")) return false
        if (e.domain == "sensor" && e.deviceClass in setOf(
                "illuminance", "atmospheric_pressure", "pressure", "absolute_humidity", "moisture",
                "signal_strength", "water", "volume", "volume_flow_rate", "volume_storage",
                "duration", "timestamp", "uptime",
            )) return false
        if (e.domain == "binary_sensor" && e.deviceClass in setOf("plug", "power", "battery_charging", "heat", "cold", "light")) return false
        // Existing appliance rules predate strict discovery and may use an integration-specific
        // enum without a standard device class. Keep those quick controls working.
        return isSupported(e) || rule.kind == PillKind.APPLIANCE
    }
    private fun isPrimary(e: HaEntity): Boolean = when {
        e.domain in setOf("light", "media_player", "fan", "switch", "cover") -> true
        e.domain in setOf("lock", "vacuum", "timer", "climate", "alarm_control_panel") -> true
        e.domain in setOf("humidifier", "water_heater", "valve", "siren", "lawn_mower", "input_boolean") -> true
        // Scenes are stateless one-shot actions and cameras open the camera center; both are
        // discoverable pills, both stay opt-in (see [isAutomaticallyEnabled]).
        e.domain in setOf("scene", "camera") -> true
        e.domain == "binary_sensor" && e.deviceClass in (openingClasses + motionClasses) -> true
        // Only the appliance's MAIN state sensor is a primary pill; sub-sensors like
        // "_etat_du_cycle" get absorbed as related (see candidates()), not shown separately.
        e.domain == "sensor" && e.deviceClass == "enum" && applianceTokens.any { e.entityId.contains(it) } &&
            (e.entityId.contains("machine_state") || e.entityId.contains("etat_de_la_machine") || e.entityId.endsWith("_state")) -> true
        else -> false
    }
    fun kind(e: HaEntity): PillKind = when {
        e.domain == "scene" -> PillKind.SCENE
        e.domain == "camera" -> PillKind.CAMERA
        e.domain == "light" -> PillKind.LIGHTS
        e.domain == "media_player" -> PillKind.MEDIA
        e.domain == "fan" && (e.entityId.contains("purif") || e.name.contains("purif", ignoreCase = true)) -> PillKind.PURIFIER
        e.domain == "fan" -> PillKind.FAN
        e.domain == "humidifier" -> PillKind.HUMIDIFIER
        e.domain == "water_heater" -> PillKind.WATER_HEATER
        e.domain == "valve" -> PillKind.VALVE
        e.domain == "siren" -> PillKind.SIREN
        e.domain == "lawn_mower" -> PillKind.LAWN_MOWER
        e.domain in setOf("switch", "input_boolean") -> PillKind.SWITCH
        e.domain == "cover" -> PillKind.COVER
        e.domain == "binary_sensor" && e.deviceClass in motionClasses -> PillKind.MOTION
        e.domain == "alarm_control_panel" -> PillKind.SAFETY
        e.domain == "lock" || e.deviceClass == "lock" -> PillKind.LOCK
        e.deviceClass in openingClasses -> PillKind.OPENING
        e.domain == "vacuum" -> PillKind.VACUUM
        e.domain == "timer" -> PillKind.TIMER
        e.domain == "climate" -> PillKind.THERMOSTAT
        applianceTokens.any { e.entityId.contains(it) } -> PillKind.APPLIANCE
        else -> PillKind.GENERIC
    }
    private fun logicalKey(e: HaEntity): String {
        var slug = e.entityId.substringAfter('.')
        listOf("_machine_state", "_etat_de_la_machine", "_state", "_contact", "_temperature", "_humidity", "_humidite", "_etat_de_l_impression").forEach { if (slug.endsWith(it)) slug = slug.removeSuffix(it) }
        return slug
    }

    /** True when [child] is [parent] plus an `_` suffix — same as startsWith on an interpolated prefix, without the allocation. */
    private fun isSuffixed(child: String, parent: String): Boolean =
        child.length > parent.length && child[parent.length] == '_' && child.startsWith(parent)

    private val relatedOnlyClasses = setOf("humidity", "battery", "timestamp", "duration")
    private val relatedDomains = setOf("sensor", "binary_sensor")
    private val principalOwnerDomains = setOf(
        "media_player", "vacuum", "camera", "fan", "humidifier", "climate", "cover",
        "lock", "lawn_mower", "water_heater",
    )

    /**
     * One pass over an entity snapshot, so [candidates] and [isAutomaticallyEnabled] stop rescanning
     * the whole list once per candidate (765 entities × ~60 candidates, on every HA push).
     *
     * Both views keep the snapshot iteration order, which the full scans they replace also produced.
     * Build it from the very list and registry handed to those functions, and share the same
     * instance across the candidates of one snapshot.
     */
    class EntityIndex(entities: List<HaEntity>, deviceIdByEntity: Map<String, String>) {
        val byDevice: Map<String, List<HaEntity>>
        val withoutDevice: List<HaEntity>

        init {
            val grouped = HashMap<String, MutableList<HaEntity>>()
            val orphans = ArrayList<HaEntity>()
            entities.forEach { e ->
                val device = deviceIdByEntity[e.entityId]
                if (device == null) orphans += e else grouped.getOrPut(device) { ArrayList() } += e
            }
            byDevice = grouped
            withoutDevice = orphans
        }
    }

    fun candidates(
        entities: List<HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
        index: EntityIndex = EntityIndex(entities, deviceIdByEntity),
    ): List<PillCandidate> {
        val supported = entities.filter(::isPrimary)
        // Slugs and logical keys are compared in the nested scans below; each is derived at most
        // once instead of allocating a fresh string per comparison.
        val slugs = HashMap<String, String>()
        fun slugOf(e: HaEntity) = slugs.getOrPut(e.entityId) { e.entityId.substringAfter('.') }
        val keys = HashMap<String, String>()
        fun keyOf(e: HaEntity) = keys.getOrPut(e.entityId) { logicalKey(e) }

        // A related-only entity (humidity, battery…) is demoted when another supported entity owns
        // it; only those possible owners are indexed, so related-only ones are skipped up front.
        val ownersByDevice = HashMap<String, MutableList<HaEntity>>()
        val ownersWithoutDevice = ArrayList<HaEntity>()
        supported.forEach { owner ->
            if (owner.deviceClass in relatedOnlyClasses) return@forEach
            val device = deviceIdByEntity[owner.entityId]
            if (device == null) ownersWithoutDevice += owner
            else ownersByDevice.getOrPut(device) { ArrayList() } += owner
        }

        val primaries = supported.filterNot { entity ->
            if (entity.deviceClass !in relatedOnlyClasses) return@filterNot false
            val entityDevice = deviceIdByEntity[entity.entityId]
            if (entityDevice != null) {
                ownersByDevice[entityDevice]?.any { it.entityId != entity.entityId } == true
            } else {
                val slug = slugOf(entity)
                ownersWithoutDevice.any { owner ->
                    if (owner.entityId == entity.entityId) return@any false
                    val ownerKey = keyOf(owner)
                    isSuffixed(slug, ownerKey) || isSuffixed(ownerKey, slug)
                }
            }
        }
        return primaries.map { primary ->
            val key = keyOf(primary)
            val primaryDevice = deviceIdByEntity[primary.entityId]
            val pool = if (primaryDevice != null) index.byDevice[primaryDevice].orEmpty() else index.withoutDevice
            val related = pool.filter { e ->
                if (e.entityId == primary.entityId) return@filter false
                if (primaryDevice == null) {
                    val slug = slugOf(e)
                    if (!isSuffixed(slug, key) && !isSuffixed(key, slug)) return@filter false
                }
                e.domain in relatedDomains
            }
            val label = primary.name
                .replace(Regex(" (État de la machine|État de la tâche|Porte)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^Capteur ", RegexOption.IGNORE_CASE), "")
                .replaceFirstChar { it.uppercase() }
            PillCandidate(primary, kind(primary), label, related)
        }.distinctBy { it.primary.entityId }
    }

    /**
     * Conservative launcher default: expose the appliance, not every configuration toggle owned
     * by it. Users can still enable any compatible entity explicitly from Settings.
     *
     * [index] is optional: pass the one shared with [candidates] when walking every candidate of a
     * snapshot, leave it null for a one-off call (a single scan is cheaper than building an index).
     */
    fun isAutomaticallyEnabled(
        candidate: PillCandidate,
        entities: List<HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
        entityCategoryByEntity: Map<String, String> = emptyMap(),
        index: EntityIndex? = null,
    ): Boolean {
        val primary = candidate.primary
        if (entityCategoryByEntity[primary.entityId] in setOf("config", "diagnostic")) return false
        if (primary.domain !in setOf("switch", "input_boolean")) return true

        val slug = primary.entityId.substringAfter('.').lowercase()
        if (auxiliarySwitchTokens.any(slug::contains)) return false

        val deviceId = deviceIdByEntity[primary.entityId] ?: return true
        val siblings = index?.byDevice?.get(deviceId)
            ?: entities.filter { deviceIdByEntity[it.entityId] == deviceId }
        val hasPrincipalOwner = siblings.any { sibling ->
            if (sibling.entityId == primary.entityId) return@any false
            sibling.domain in principalOwnerDomains ||
                (sibling.domain == "sensor" && sibling.deviceClass == "enum" && isPrimary(sibling))
        }
        return !hasPrincipalOwner
    }

    fun defaultRule(c: PillCandidate, enabled: Boolean = true) = PillRule(
        c.primary.entityId,
        c.kind,
        c.label,
        enabled = enabled,
        relatedEntityIds = c.related.map { it.entityId },
    )
}
