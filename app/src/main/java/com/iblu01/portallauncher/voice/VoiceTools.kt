package com.iblu01.portallauncher.voice

import android.util.Log
import com.iblu01.portallauncher.HaApiClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * Executes the tool calls Gemini makes, against this panel's own Home Assistant.
 *
 * The Pipecat add-on used to own this: it proxied Home Assistant's own MCP server, which is just
 * HA's intent API wrapped for an LLM. Speaking to Gemini directly means the model knows nothing
 * about the home, so the same reach is handed to it here: [GeminiLive.INTENT_TOOL] is HA's intent
 * engine (and with it HA's own name, alias, area and floor resolution), the three others are the
 * escape hatch for what intents cannot express.
 *
 * Every call is blocking HTTP ([HaApiClient] is synchronous), so callers must be on an IO
 * dispatcher. [GeminiLive.END_CONVERSATION_TOOL] is not handled here: hanging up is the
 * controller's business, not Home Assistant's.
 */
class VoiceTools(private val client: HaApiClient) {

    private companion object {
        const val TAG = "VoiceTools"
        const val MAX_MATCHES = 20

        /** Entities that need a physical confirmation. Rebuilt on every process start. */
        @Volatile
        private var cachedGuardedNames: Set<String>? = null
    }

    /**
     * Names and ids of this home's guarded entities (locks, alarms, garage doors), so an intent
     * can be recognised as guarded from the name alone. One states read, cached for the process:
     * a house grows a new lock far less often than the assistant is spoken to.
     */
    fun guardedNames(): Set<String> {
        cachedGuardedNames?.let { return it }
        val result = client.getStates()
        if (!result.ok) return emptySet()
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull() ?: return emptySet()
        val names = mutableSetOf<String>()
        for (i in 0 until states.length()) {
            val entity = states.optJSONObject(i) ?: continue
            val id = entity.optString("entity_id")
            val attributes = entity.optJSONObject("attributes")
            if (!VoiceGuard.isGuardedEntity(id, attributes?.optString("device_class"))) continue
            names += id
            attributes?.optString("friendly_name")?.takeIf { it.isNotBlank() }?.let { names += it }
        }
        cachedGuardedNames = names
        return names
    }

    fun execute(name: String, args: JSONObject): Map<String, Any?> = when (name) {
        GeminiLive.INTENT_TOOL -> handleIntent(args)
        GeminiLive.FIND_ENTITIES_TOOL -> findEntities(
            query = args.optString("query"),
            domain = args.optString("domain").takeIf { it.isNotBlank() },
        )
        GeminiLive.GET_STATE_TOOL -> getState(args.optString("entity_id"))
        GeminiLive.VACUUM_ROOM_TOOL -> cleanVacuumRoom(args)
        GeminiLive.CALL_SERVICE_TOOL -> callService(args)
        else -> mapOf("error" to "unknown tool $name")
    }

    /**
     * Name search over `/api/states`. Matching is done on the friendly name *and* the entity id,
     * lowercased, all query words required: "cuisine plafond" must not return every light in the
     * house just because one word hit.
     */
    private fun findEntities(query: String, domain: String?): Map<String, Any?> {
        if (searchWords(query).isEmpty()) return mapOf("error" to "empty query")

        val result = client.getStates()
        if (!result.ok) return mapOf("error" to "home assistant unreachable")
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull()
            ?: return mapOf("error" to "unreadable state list")

        val matches = mutableListOf<Map<String, Any?>>()
        for (i in 0 until states.length()) {
            val entity = states.optJSONObject(i) ?: continue
            val id = entity.optString("entity_id")
            if (id.isEmpty()) continue
            if (domain != null && !id.startsWith("$domain.")) continue
            val attributes = entity.optJSONObject("attributes")
            val friendly = attributes?.optString("friendly_name").orEmpty()
            val deviceClass = attributes?.optString("device_class").orEmpty()
            // device_class is part of the haystack because a window sensor is routinely named
            // after its room alone ("Salon"), and "quelles fenetres sont ouvertes" has no other
            // word to hit.
            if (!matchesQuery("$id $friendly $deviceClass", query)) continue
            matches += mapOf(
                "entity_id" to id,
                "name" to friendly.ifEmpty { id },
                "state" to entity.optString("state"),
                "device_class" to deviceClass,
            )
            if (matches.size >= MAX_MATCHES) break
        }
        Log.i(TAG, "find \"$query\" domain=$domain -> ${matches.size} matches")
        return mapOf("count" to matches.size, "entities" to JSONArray(matches))
    }

    private fun handleIntent(args: JSONObject, retried: Boolean = false): Map<String, Any?> {
        val intent = args.optString("intent")
        if (intent.isBlank()) return mapOf("error" to "missing intent")
        val slots = intentSlots(args)
        val result = client.handleIntent(intent, slots)
        if (!result.ok) {
            return mapOf("success" to false, "error" to result.body?.take(200).orEmpty())
        }
        // HA answers with the sentence its own assistant would have spoken. Handing that back
        // verbatim is the whole point: it already knows what it turned on and in which room.
        val speech = runCatching {
            JSONObject(result.body.orEmpty())
                .optJSONObject("response")
                ?.optJSONObject("speech")
                ?.optJSONObject("plain")
                ?.optString("speech")
        }.getOrNull()
        // Home Assistant answers 200 with an error *body* when an intent cannot be handled
        // ("Failed to call clean_area for areas: ['cuisine']"). Reading only the HTTP status and
        // a non-empty speech string reported that as a success, so the assistant announced
        // actions that had failed. response_type is the authority.
        val body = runCatching { JSONObject(result.body.orEmpty()) }.getOrNull()
        val response = body?.optJSONObject("response")
        val isError = response?.optString("response_type") == "error"
        val data = response?.optJSONObject("data")
        val targets = data?.optJSONArray("success")?.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("name") }
        }.orEmpty()
        val failed = data?.optJSONArray("failed")?.length() ?: 0

        Log.i(
            TAG,
            "$intent $slots -> ${if (isError) "ERROR" else "ok"} acted_on=$targets failed=$failed " +
                "speech=\"${speech?.take(80).orEmpty()}\"",
        )
        if (isError) {
            val raw = speech.orEmpty()
            return mapOf(
                "success" to false,
                // Home Assistant's own words unless they are an internal error repr, which no
                // assistant can act on and none should read out loud.
                "error" to (explainMatchFailure(raw) ?: raw.ifEmpty { data?.optString("code").orEmpty() }),
            )
        }

        // Home Assistant matches an entity name exactly or not at all: "Nicky" reached nothing
        // while the vacuum is called "Nicky ménage", and the answer to that is an empty success
        // list with no error at all. Resolving the name once and retrying is what a person would
        // do, and it costs one state read only on the calls that already failed.
        val spokenName = slots["name"] as? String
        if (!retried && targets.isEmpty() && failed == 0 && !spokenName.isNullOrBlank()) {
            val exact = resolveEntityName(spokenName, slots["domain"] as? String)
            if (exact != null && !exact.equals(spokenName, ignoreCase = true)) {
                Log.i(TAG, "intent matched nothing for \"$spokenName\"; retrying as \"$exact\"")
                val retryArgs = JSONObject(args.toString()).put("name", exact)
                return handleIntent(retryArgs, retried = true)
            }
        }
        return mapOf(
            "success" to (failed == 0 && (targets.isNotEmpty() || speech?.isNotEmpty() == true)),
            "response" to speech.orEmpty(),
            "acted_on" to JSONArray(targets),
            "failed_targets" to failed,
        )
    }

    /**
     * Sends the robot vacuum to one room.
     *
     * Home Assistant's own `HassVacuumCleanArea` cannot do this: a vacuum's rooms are segments it
     * carries in its own attributes, not Home Assistant areas, and the intent answers
     * "Failed to call clean_area for areas: ['cuisine']". The mapping from a spoken room name to
     * a segment id exists nowhere else, which is why this is a tool of its own rather than
     * something the model could express with a plain service call.
     */
    private fun cleanVacuumRoom(args: JSONObject): Map<String, Any?> {
        val wanted = args.optString("room")
        if (wanted.isBlank()) return mapOf("error" to "missing room")

        val result = client.getStates()
        if (!result.ok) return mapOf("error" to "home assistant unreachable")
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull()
            ?: return mapOf("error" to "unreadable state list")

        val requested = args.optString("vacuum").takeIf { it.isNotBlank() }
        var entityId: String? = null
        var rooms = emptyMap<String, Int>()
        for (i in 0 until states.length()) {
            val entity = states.optJSONObject(i) ?: continue
            val id = entity.optString("entity_id")
            if (!id.startsWith("vacuum.")) continue
            val attributes = entity.optJSONObject("attributes")
            val name = attributes?.optString("friendly_name").orEmpty()
            if (requested != null && !id.contains(requested, true) && !name.contains(requested, true)) continue
            val candidate = vacuumRooms(attributes)
            // The first vacuum that actually exposes rooms wins: a home with a second, dumber
            // vacuum must not answer "no rooms" just because it was listed first.
            if (candidate.isNotEmpty() || entityId == null) {
                entityId = id
                rooms = candidate
            }
            if (candidate.isNotEmpty()) break
        }

        val vacuum = entityId ?: return mapOf("error" to "no vacuum in this home")
        if (rooms.isEmpty()) {
            return mapOf("error" to "$vacuum exposes no room map; it can only clean everywhere")
        }
        val match = matchRoom(rooms, wanted)
            ?: return mapOf("error" to "unknown room", "known_rooms" to JSONArray(rooms.keys.toList()))

        // Dreame's own service takes the segment list; the Xiaomi/Roborock family goes through
        // vacuum.send_command. Both are tried in that order because only the first has a schema
        // we can be sure of on this panel.
        val call = client.callService(
            domain = "dreame_vacuum",
            service = "vacuum_clean_segment",
            entityId = vacuum,
            data = mapOf("segments" to match.second),
        )
        val used = if (call.ok) "dreame_vacuum.vacuum_clean_segment" else "vacuum.send_command"
        val fallback = if (call.ok) null else client.callService(
            domain = "vacuum",
            service = "send_command",
            entityId = vacuum,
            data = mapOf("command" to "app_segment_clean", "params" to JSONArray(listOf(match.second))),
        )
        val ok = call.ok || fallback?.ok == true
        Log.i(TAG, "vacuum room \"$wanted\" -> ${match.first} (segment ${match.second}) via $used ok=$ok")
        return if (ok) {
            mapOf("success" to true, "room" to match.first, "segment" to match.second, "vacuum" to vacuum)
        } else {
            mapOf("success" to false, "error" to (fallback?.body ?: call.body)?.take(200).orEmpty())
        }
    }

    /**
     * The real name of the entity the user meant, or null when nothing is close.
     *
     * Guarded entities are deliberately excluded: the confirmation guard judged the *spoken*
     * words, so silently resolving them to a lock would walk a door open behind that check.
     */
    private fun resolveEntityName(spoken: String, domain: String?): String? {
        val result = client.getStates()
        if (!result.ok) return null
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull() ?: return null
        val names = mutableListOf<String>()
        for (i in 0 until states.length()) {
            val entity = states.optJSONObject(i) ?: continue
            val id = entity.optString("entity_id")
            if (id.isEmpty()) continue
            if (domain != null && !id.startsWith("$domain.")) continue
            val attributes = entity.optJSONObject("attributes")
            if (VoiceGuard.isGuardedEntity(id, attributes?.optString("device_class"))) continue
            attributes?.optString("friendly_name")?.takeIf { it.isNotBlank() }?.let { names += it }
        }
        return matchName(names, spoken)
    }

    private fun getState(entityId: String): Map<String, Any?> {
        if (entityId.isBlank()) return mapOf("error" to "missing entity_id")
        val result = client.getState(entityId)
        if (!result.ok) return mapOf("error" to "unknown entity $entityId")
        val entity = runCatching { JSONObject(result.body.orEmpty()) }.getOrNull()
            ?: return mapOf("error" to "unreadable state")
        return mapOf(
            "entity_id" to entityId,
            "state" to entity.optString("state"),
            "attributes" to (entity.optJSONObject("attributes") ?: JSONObject()),
        )
    }

    private fun callService(args: JSONObject): Map<String, Any?> {
        val domain = args.optString("domain")
        val service = args.optString("service")
        if (domain.isBlank() || service.isBlank()) {
            return mapOf("error" to "missing domain or service")
        }
        val result = client.callService(
            domain = domain,
            service = service,
            entityId = args.optString("entity_id").takeIf { it.isNotBlank() },
            data = flatObjectOf(args.opt("data")),
        )
        if (!result.ok) {
            return mapOf("success" to false, "error" to result.body?.take(200).orEmpty())
        }
        // A 200 means Home Assistant accepted the call, not that the device did anything: an
        // unreachable bulb answers exactly the same. Reading the state back is the difference
        // between "it is on" and "I asked for it to be on".
        val entityId = args.optString("entity_id").takeIf { it.isNotBlank() }
        val states = entityId?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)
            ?.associateWith { id ->
                runCatching {
                    JSONObject(client.getState(id).body.orEmpty()).optString("state")
                }.getOrNull().orEmpty()
            }
            .orEmpty()
        Log.i(TAG, "$domain.$service ${args.optString("entity_id")} -> states_after=$states")
        return mapOf(
            "success" to true,
            "states_after" to JSONObject(states),
            "unavailable" to JSONArray(
                states.filterValues { it == "unavailable" || it == "unknown" }.keys.toList(),
            ),
        )
    }

    /**
     * The model is told to pass extra slots as a JSON object, and sends it either as one (when
     * the schema is honoured) or as a string containing one (which it does often enough that
     * rejecting it would break `brightness` in practice).
     */
    private fun flatObjectOf(raw: Any?): Map<String, Any>? {
        val json = when (raw) {
            null, JSONObject.NULL -> return null
            is JSONObject -> raw
            is String -> raw.takeIf { it.isNotBlank() }
                ?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: return null
            else -> return null
        }
        return json.keys().asSequence().mapNotNull { key ->
            json.opt(key)?.takeIf { it != JSONObject.NULL }?.let { key to it }
        }.toMap().takeIf { it.isNotEmpty() }
    }
}

/**
 * The five named slots every target-shaped intent shares, plus whatever the intent itself needs
 * in `extras`. Empty strings are dropped rather than forwarded: Home Assistant matches `name=""`
 * against nothing and answers "I could not find any device", which reads as a broken assistant
 * rather than as the malformed call it is. Kept top-level so it can be tested without a
 * Home Assistant to talk to.
 */
internal fun intentSlots(args: JSONObject): Map<String, Any> {
    val slots = mutableMapOf<String, Any>()
    for (slot in listOf("name", "area", "floor", "domain", "device_class")) {
        args.optString(slot).takeIf { it.isNotBlank() }?.let { slots[slot] = it }
    }
    val extras = when (val raw = args.opt("extras")) {
        is JSONObject -> raw
        is String -> raw.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() }
        else -> null
    }
    extras?.keys()?.forEach { key ->
        extras.opt(key)?.takeIf { it != JSONObject.NULL }?.let { slots[key] = it }
    }
    return slots
}

/**
 * Flattens a vacuum's room map to name -> segment id. Dreame publishes it per saved map
 * (`rooms: {"Le QG": [{id, name}, …]}`), and a home can have several maps; the names are unique
 * enough in practice that flattening them all is what a spoken room name needs.
 */
internal fun vacuumRooms(attributes: JSONObject?): Map<String, Int> {
    val rooms = attributes?.optJSONObject("rooms") ?: return emptyMap()
    val flat = mutableMapOf<String, Int>()
    rooms.keys().forEach { map ->
        val list = rooms.optJSONArray(map) ?: return@forEach
        for (i in 0 until list.length()) {
            val room = list.optJSONObject(i) ?: continue
            val name = room.optString("name").takeIf { it.isNotBlank() } ?: continue
            flat.putIfAbsent(name, room.optInt("id"))
        }
    }
    return flat
}

/**
 * Accents, case and the article dropped: a spoken room never matches a stored one on those. The
 * article matters because people say "la cuisine" while the vacuum's app holds "Cusine", and the
 * three extra characters push a one-letter typo out of edit-distance range.
 */
internal fun normalizeRoom(value: String): String = java.text.Normalizer
    .normalize(value.trim().lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .replace(Regex("^(la |le |les |l'|du |de la |des |the )"), "")
    .trim()

/**
 * Matches a spoken room against the vacuum's own names, tolerating a typo.
 *
 * The exact-match version would have failed on this home: the kitchen segment is stored as
 * "Cusine", so "cuisine" never matched and the room simply did not exist as far as the assistant
 * was concerned. Names are typed once into a vacuum app and never corrected, so tolerating one or
 * two characters is the difference between working and not.
 */
internal fun matchRoom(rooms: Map<String, Int>, query: String): Pair<String, Int>? =
    matchName(rooms.keys, query)?.let { it to rooms.getValue(it) }

/**
 * The closest of [names] to what the user said, or null when nothing is close enough.
 *
 * Used for both a vacuum's room names and Home Assistant's entity names, because both fail the
 * same way: Home Assistant matches an entity name exactly or not at all, so "Nicky" found nothing
 * while "Nicky ménage" worked, and a vacuum's kitchen was stored as "Cusine".
 */
internal fun matchName(names: Collection<String>, query: String): String? {
    val wanted = normalizeRoom(query)
    if (wanted.isEmpty()) return null
    names.firstOrNull { normalizeRoom(it) == wanted }?.let { return it }
    names.firstOrNull {
        val name = normalizeRoom(it)
        name.contains(wanted) || wanted.contains(name)
    }?.let { return it }
    // Last resort, and only for a short distance: "cusine" is one insertion from "cuisine",
    // while "salon" and "sdb" must never collapse into each other.
    return names
        .map { it to editDistance(normalizeRoom(it), wanted) }
        .filter { (_, distance) -> distance <= 2 }
        .minByOrNull { (_, distance) -> distance }
        ?.first
}

/**
 * Words of a spoken search query, accent- and plural-free.
 *
 * The raw lowercase substring test this replaces failed on the two things every French query
 * has: "fenetres" is not a substring of "Fenetre cuisine", and neither is "fenêtre".
 */
internal fun searchWords(query: String): List<String> = normalizeRoom(query)
    .split(' ', ',', '\'')
    .filter { it.isNotBlank() }
    .map { if (it.length > 3) it.trimEnd('s') else it }

/** True when every word of [query] appears in [haystack], both normalised. */
internal fun matchesQuery(haystack: String, query: String): Boolean {
    val words = searchWords(query)
    if (words.isEmpty()) return false
    val hay = normalizeRoom(haystack)
    return words.all { word ->
        when (word) {
            "fenetre", "window" -> "fenetre" in hay || "window" in hay
            else -> word in hay
        }
    }
}

/**
 * Turns Home Assistant's internal match failures into something the model can act on. Left raw,
 * the panel reads a Python repr out loud ("MatchFailedError result=MatchTargetsResult(...)") and
 * the model has no idea that adding a room would have fixed it.
 */
internal fun explainMatchFailure(speech: String): String? = when {
    "DUPLICATE_NAME" in speech ->
        "several entities share that name; say which room it is in, or pass area and domain"
    "MatchFailedReason.NAME" in speech || "no_match_name" in speech ->
        "no entity of this home is called that; look the exact name up first"
    "MatchFailedReason.AREA" in speech -> "this home has no such area"
    "MatchFailedError" in speech -> "Home Assistant could not match that target"
    else -> null
}

private fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length]
}
