package com.iblu01.portallauncher

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject

enum class PillKind(val label: String, val icon: String, val basePriority: Int) {
    SAFETY("Sécurité", "shield", 90),
    LOCK("Serrure", "lock", 35),
    OPENING("Porte / fenêtre", "door", 25),
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
enum class PillFamily(@StringRes val labelRes: Int, val kinds: Set<PillKind>) {
    SECURITY(R.string.pill_family_security, setOf(PillKind.SAFETY, PillKind.LOCK, PillKind.OPENING)),
    COMFORT(R.string.pill_family_comfort, setOf(PillKind.CLIMATE, PillKind.THERMOSTAT, PillKind.AIR, PillKind.PURIFIER, PillKind.FAN, PillKind.COVER)),
    APPLIANCES(R.string.pill_family_appliances, setOf(PillKind.APPLIANCE, PillKind.VACUUM, PillKind.SWITCH, PillKind.ENERGY, PillKind.BATTERY)),
    LIGHTS_SCENES(R.string.pill_family_lights_scenes, setOf(PillKind.LIGHTS, PillKind.SCENE)),
    MEDIA(R.string.pill_family_media, setOf(PillKind.MEDIA)),
    HOME(R.string.pill_family_home, setOf(PillKind.PRESENCE, PillKind.TIMER, PillKind.GENERIC));

    companion object {
        fun of(kind: PillKind): PillFamily = values().first { kind in it.kinds }
    }
}

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
        s in setOf("unknown", "none", "") -> "—"
        e.domain == "person" || s in setOf("home", "not_home") -> if (s == "home") context.getString(R.string.entity_state_home) else context.getString(R.string.entity_state_away)
        e.domain == "lock" -> if (s == "locked") context.getString(R.string.entity_state_locked) else context.getString(R.string.entity_state_unlocked)
        s == "on" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> context.getString(R.string.entity_state_open)
            "motion", "occupancy", "presence" -> context.getString(R.string.entity_state_motion)
            "smoke", "carbon_monoxide", "gas", "moisture" -> context.getString(R.string.entity_state_alert)
            else -> context.getString(R.string.entity_state_on)
        }
        s == "off" -> when (e.deviceClass) {
            "door", "window", "opening", "garage_door" -> context.getString(R.string.entity_state_closed)
            "motion", "occupancy", "presence" -> context.getString(R.string.entity_state_no_motion)
            "smoke", "carbon_monoxide", "gas", "moisture" -> context.getString(R.string.entity_state_clear)
            else -> context.getString(R.string.entity_state_off)
        }
        s == "open" || s == "opening" -> context.getString(R.string.entity_state_open)
        s == "closed" -> context.getString(R.string.entity_state_closed)
        s == "playing" -> context.getString(R.string.entity_state_playing)
        s == "paused" -> context.getString(R.string.entity_state_paused)
        s == "idle" || s == "standby" -> context.getString(R.string.entity_state_idle)
        s == "locked" -> context.getString(R.string.entity_state_locked)
        s == "unlocked" -> context.getString(R.string.entity_state_unlocked)
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
    private val safetyClasses = setOf("smoke", "carbon_monoxide", "gas", "moisture", "safety", "tamper")
    private val openingClasses = setOf("door", "window", "opening", "garage_door")
    private val applianceTokens = setOf("washer", "washing", "machine_a_laver", "dryer", "seche_linge", "dishwasher", "lave_vaisselle", "p2s", "printer", "imprimante")
    fun isSupported(e: HaEntity) = isPrimary(e)
    private fun isPrimary(e: HaEntity): Boolean = when {
        e.domain in setOf("light", "media_player", "fan", "switch", "cover") -> true
        e.domain in setOf("lock", "vacuum", "timer", "climate", "alarm_control_panel") -> true
        e.domain in setOf("scene", "script") -> true
        e.domain == "person" -> true
        e.domain == "binary_sensor" && (e.deviceClass in openingClasses || e.deviceClass in safetyClasses) -> true
        e.domain == "sensor" && e.deviceClass in setOf("aqi", "carbon_dioxide", "volatile_organic_compounds", "volatile_organic_compounds_parts", "pm25", "pm10", "temperature", "humidity") -> true
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
        e.domain == "switch" -> PillKind.SWITCH
        e.domain == "cover" -> PillKind.COVER
        e.domain == "scene" || e.domain == "script" -> PillKind.SCENE
        e.domain == "person" -> PillKind.PRESENCE
        e.domain == "alarm_control_panel" || e.deviceClass in safetyClasses -> PillKind.SAFETY
        e.domain == "lock" || e.deviceClass == "lock" -> PillKind.LOCK
        e.deviceClass in openingClasses -> PillKind.OPENING
        e.domain == "vacuum" -> PillKind.VACUUM
        e.domain == "timer" || e.deviceClass == "duration" -> PillKind.TIMER
        e.deviceClass == "battery" -> PillKind.BATTERY
        e.deviceClass in setOf("aqi", "carbon_dioxide", "volatile_organic_compounds", "volatile_organic_compounds_parts", "pm25", "pm10") -> PillKind.AIR
        e.domain == "climate" -> PillKind.THERMOSTAT
        e.deviceClass in setOf("temperature", "humidity") -> PillKind.CLIMATE
        e.deviceClass in setOf("energy", "power", "current", "voltage") -> PillKind.ENERGY
        applianceTokens.any { e.entityId.contains(it) } -> PillKind.APPLIANCE
        else -> PillKind.GENERIC
    }
    private fun logicalKey(e: HaEntity): String {
        var slug = e.entityId.substringAfter('.')
        listOf("_machine_state", "_etat_de_la_machine", "_state", "_contact", "_temperature", "_humidity", "_humidite", "_etat_de_l_impression").forEach { if (slug.endsWith(it)) slug = slug.removeSuffix(it) }
        return slug
    }
    fun candidates(entities: List<HaEntity>): List<PillCandidate> {
        val supported = entities.filter(::isPrimary)
        val temperatureKeys = supported.filter { it.deviceClass == "temperature" }.map(::logicalKey).toSet()
        val primaries = supported.filterNot { it.deviceClass == "humidity" && logicalKey(it) in temperatureKeys }
        return primaries.map { primary ->
            val key = logicalKey(primary)
            val related = entities.filter { e -> e.entityId != primary.entityId && (e.entityId.substringAfter('.').startsWith("${key}_") || key.startsWith(e.entityId.substringAfter('.') + "_")) }
                .filter { it.domain in setOf("sensor", "binary_sensor") }
            val label = primary.name
                .replace(Regex(" (État de la machine|État de la tâche|Porte)$", RegexOption.IGNORE_CASE), "")
                .replace(Regex("^Capteur ", RegexOption.IGNORE_CASE), "")
                .replaceFirstChar { it.uppercase() }
            PillCandidate(primary, kind(primary), label, related)
        }.distinctBy { it.primary.entityId }
    }
    fun defaultRule(c: PillCandidate) = PillRule(c.primary.entityId, c.kind, c.label, relatedEntityIds = c.related.map { it.entityId })
}
