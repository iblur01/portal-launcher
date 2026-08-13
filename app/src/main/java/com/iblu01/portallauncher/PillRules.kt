package com.iblu01.portallauncher

import org.json.JSONArray
import org.json.JSONObject

enum class PillKind(val label: String, val icon: String, val basePriority: Int) {
    SAFETY("Sécurité", "shield", 90),
    LOCK("Serrure", "lock", 35),
    OPENING("Porte / fenêtre", "door", 25),
    MOTION("Mouvement", "motion", 30),
    APPLIANCE("Appareil", "washer", 58),
    VACUUM("Aspirateur", "vacuum", 58),
    BATTERY("Batterie", "battery", 20),
    AIR("Qualité de l'air", "air", 35),
    CLIMATE("Confort", "temperature", 22),
    THERMOSTAT("Thermostat", "temperature", 24),
    COVER("Volet", "cover", 26),
    SWITCH("Prise", "switch", 17),
    FAN("Ventilateur", "fan", 15),
    SCENE("Scènes", "scene", 12),
    PRESENCE("Présence", "presence", 20),
    ENERGY("Énergie", "energy", 18),
    LIGHTS("Lumières", "light", 16),
    MEDIA("Médias", "media", 13),
    PURIFIER("Purificateur", "air", 15),
    TIMER("Minuteur", "timer", 50),
    HUMIDIFIER("Humidificateur", "humidity", 20),
    WATER_HEATER("Chauffe-eau", "temperature", 24),
    VALVE("Vanne", "valve", 30),
    SIREN("Sirène", "shield", 45),
    LAWN_MOWER("Robot tondeuse", "mower", 28),
    GENERIC("Information", "sensor", 15),
}

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
enum class PillFamily(val label: String, val kinds: Set<PillKind>) {
    SECURITY("Sécurité & accès", setOf(PillKind.SAFETY, PillKind.LOCK, PillKind.OPENING, PillKind.MOTION, PillKind.SIREN)),
    COMFORT("Confort", setOf(PillKind.THERMOSTAT, PillKind.PURIFIER, PillKind.FAN, PillKind.COVER, PillKind.HUMIDIFIER, PillKind.WATER_HEATER)),
    APPLIANCES("Appareils", setOf(PillKind.APPLIANCE, PillKind.VACUUM, PillKind.SWITCH, PillKind.VALVE, PillKind.LAWN_MOWER)),
    LIGHTS("Lumières", setOf(PillKind.LIGHTS)),
    MEDIA("Médias", setOf(PillKind.MEDIA)),
    HOME("Maison", setOf(PillKind.TIMER, PillKind.GENERIC));

    companion object {
        /** Legacy codec kinds intentionally have no Settings family once their support is removed. */
        fun of(kind: PillKind): PillFamily? = values().firstOrNull { kind in it.kinds }
    }
}

/** Plain-language rendering of an entity's current state, e.g. "Ouverte", "21°", "Allumé". */
fun friendlyEntityState(e: HaEntity): String {
    val raw = e.state.trim()
    val s = raw.lowercase()
    val unit = e.attributes.optString("unit_of_measurement").trim()
    fun number(): String? = s.toFloatOrNull()?.let { v ->
        if (v == v.toInt().toFloat()) v.toInt().toString() else raw
    }
    return when {
        s == "unavailable" -> "Indisponible"
        s in setOf("unknown", "none", "") -> "—"
        e.domain in setOf("person", "device_tracker") -> when (s) { "home" -> "À la maison"; "not_home" -> "Absente"; else -> raw.replaceFirstChar { it.uppercase() } }
        e.domain == "lock" -> if (s == "locked") "Verrouillée" else "Déverrouillée"
        s == "on" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> "Ouverte"
            "motion", "occupancy", "presence" -> "Mouvement"
            "smoke", "carbon_monoxide", "gas", "moisture" -> "Alerte"
            "problem", "battery" -> "Alerte"
            "connectivity" -> "Connecté"
            "battery_charging" -> "En charge"
            "plug" -> "Branché"
            "power", "running", "moving", "vibration", "sound", "heat", "cold", "light" -> "Actif"
            else -> "Allumé"
        }
        s == "off" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> "Fermée"
            "motion", "occupancy", "presence" -> "Aucun mouvement"
            "smoke", "carbon_monoxide", "gas", "moisture" -> "Rien à signaler"
            "problem", "battery" -> "Rien à signaler"
            "connectivity" -> "Déconnecté"
            "battery_charging" -> "Pas en charge"
            "plug" -> "Débranché"
            "power", "running", "moving", "vibration", "sound", "heat", "cold", "light" -> "Inactif"
            else -> "Éteint"
        }
        s == "open" || s == "opening" -> "Ouverte"
        s == "closed" -> "Fermée"
        s == "playing" -> "En lecture"
        s == "paused" -> "En pause"
        s == "idle" || s == "standby" -> "Inactif"
        s == "locked" -> "Verrouillée"
        s == "unlocked" -> "Déverrouillée"
        number() != null -> if (unit.isNotEmpty()) "${number()} $unit" else raw
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}

data class PillCandidate(val primary: HaEntity, val kind: PillKind, val label: String, val related: List<HaEntity>)

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
        if (e.domain in setOf("button", "input_button", "number", "input_number", "select", "input_select", "camera")) return false
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
        e.domain == "binary_sensor" && e.deviceClass in (openingClasses + motionClasses) -> true
        // Only the appliance's MAIN state sensor is a primary pill; sub-sensors like
        // "_etat_du_cycle" get absorbed as related (see candidates()), not shown separately.
        e.domain == "sensor" && e.deviceClass == "enum" && applianceTokens.any { e.entityId.contains(it) } &&
            (e.entityId.contains("machine_state") || e.entityId.contains("etat_de_la_machine") || e.entityId.endsWith("_state")) -> true
        else -> false
    }
    fun kind(e: HaEntity): PillKind = when {
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
    fun candidates(
        entities: List<HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
    ): List<PillCandidate> {
        val supported = entities.filter(::isPrimary)
        val relatedOnlyClasses = setOf("humidity", "battery", "timestamp", "duration")
        val primaries = supported.filterNot { entity ->
            entity.deviceClass in relatedOnlyClasses && supported.any { owner ->
                    if (owner.entityId == entity.entityId || owner.deviceClass in relatedOnlyClasses) return@any false
                    val entityDevice = deviceIdByEntity[entity.entityId]
                    val ownerDevice = deviceIdByEntity[owner.entityId]
                    if (entityDevice != null) entityDevice == ownerDevice
                    else ownerDevice == null &&
                        (entity.entityId.substringAfter('.').startsWith("${logicalKey(owner)}_") ||
                            logicalKey(owner).startsWith(entity.entityId.substringAfter('.') + "_"))
                }
        }
        return primaries.map { primary ->
            val key = logicalKey(primary)
            val primaryDevice = deviceIdByEntity[primary.entityId]
            val related = entities.filter { e ->
                if (e.entityId == primary.entityId) return@filter false
                if (primaryDevice != null) deviceIdByEntity[e.entityId] == primaryDevice
                else deviceIdByEntity[e.entityId] == null &&
                    (e.entityId.substringAfter('.').startsWith("${key}_") || key.startsWith(e.entityId.substringAfter('.') + "_"))
            }
                .filter { it.domain in setOf("sensor", "binary_sensor") }
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
     */
    fun isAutomaticallyEnabled(
        candidate: PillCandidate,
        entities: List<HaEntity>,
        deviceIdByEntity: Map<String, String> = emptyMap(),
        entityCategoryByEntity: Map<String, String> = emptyMap(),
    ): Boolean {
        val primary = candidate.primary
        if (entityCategoryByEntity[primary.entityId] in setOf("config", "diagnostic")) return false
        if (primary.domain !in setOf("switch", "input_boolean")) return true

        val slug = primary.entityId.substringAfter('.').lowercase()
        if (auxiliarySwitchTokens.any(slug::contains)) return false

        val deviceId = deviceIdByEntity[primary.entityId] ?: return true
        val siblings = entities.filter { it.entityId != primary.entityId && deviceIdByEntity[it.entityId] == deviceId }
        val hasPrincipalOwner = siblings.any { sibling ->
            sibling.domain in setOf(
                "media_player", "vacuum", "camera", "fan", "humidifier", "climate", "cover",
                "lock", "lawn_mower", "water_heater",
            ) || (sibling.domain == "sensor" && sibling.deviceClass == "enum" && isPrimary(sibling))
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
