package com.iblu01.portallauncher

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Applies a `{entity_id -> enabled}` selection onto [existing], mirroring what the Settings screen's
 * `onSetPillEnabled` does: a known entity keeps its rule and only flips `enabled`, an unknown one is
 * added from its discovered candidate, and an unknown entity being *disabled* is a no-op. Rules for
 * entities absent from [selection] are left untouched.
 */
internal fun mergePillSelection(
    existing: List<PillRule>,
    selection: List<Pair<String, Boolean>>,
    candidates: List<PillCandidate>,
): List<PillRule> {
    val rules = existing.toMutableList()
    selection.forEach { (entityId, enabled) ->
        val index = rules.indexOfFirst { it.entityId == entityId }
        if (index >= 0) {
            rules[index] = rules[index].copy(enabled = enabled)
        } else if (enabled) {
            candidates.firstOrNull { it.primary.entityId == entityId }?.let {
                rules += PillSupport.defaultRule(it)
            }
        }
    }
    return rules
}

/**
 * Short-lived HTTP server that lets a phone on the same LAN fill in this panel's configuration.
 *
 * It hands out Home Assistant and MQTT credentials, so every route requires the [token] minted at
 * construction and carried in the QR code's URL (`?t=`). That token is the only thing standing
 * between the LAN and the secrets, hence: fresh per server instance, compared in constant time, and
 * never logged — nor is any value it protects.
 */
class WebConfigServer private constructor(
    port: Int,
    private val prefs: Prefs,
    private val deviceIds: () -> Map<String, String>,
    private val onMqttConfigChanged: () -> Unit,
) : NanoHTTPD(BIND_ADDRESS, port) {

    val token: String = mintToken()

    /** Candidates from the last `/api/entities` call, so saving pills needs no second HA round-trip. */
    @Volatile private var cachedCandidates: List<PillCandidate> = emptyList()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.ifEmpty { "/" }
        when (uri) {
            "/webconfig.css" -> return css(WebConfigPage.asset("webconfig.css"))
            "/access.js" -> return javascript(WebConfigPage.asset("access.js"))
            "/config.js" -> return javascript(WebConfigPage.asset("config.js"))
        }
        val providedToken = session.parameters["t"]?.firstOrNull()
        if (!tokenMatches(providedToken)) {
            return if (uri == "/" && session.method == Method.GET) {
                html(WebConfigPage.renderAccess(invalidCode = !providedToken.isNullOrBlank()))
            } else {
                errorJson(Response.Status.FORBIDDEN, "forbidden")
            }
        }
        return runCatching {
            when {
                uri == "/" -> html(WebConfigPage.render(token))
                uri == "/api/config" && session.method == Method.GET -> json(configJson())
                uri == "/api/config" && session.method == Method.POST -> applyConfig(readBody(session))
                uri == "/api/entities" && session.method == Method.GET -> json(entitiesJson())
                uri == "/api/pills" && session.method == Method.POST -> applyPills(readBody(session))
                else -> text(Response.Status.NOT_FOUND, "not found")
            }
        }.getOrElse { failure ->
            // Message only: an exception from a config route can carry a URL or a credential.
            Log.w(TAG, "request to $uri failed: ${failure.javaClass.simpleName}")
            errorJson(Response.Status.INTERNAL_ERROR, "server_error")
        }
    }

    private fun configJson(): String = JSONObject()
        .put("ha_url", prefs.haUrl)
        .put("ha_token", prefs.haToken)
        .put("broker_host", prefs.brokerHost)
        .put("broker_port", prefs.brokerPort)
        .put("username", prefs.username)
        .put("password", prefs.password)
        .put("device_name", prefs.deviceName)
        .toString()

    private fun applyConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")

        val haUrl = payload.optString("ha_url", prefs.haUrl).trim().trimEnd('/')
        if (!haUrl.startsWith("http://") && !haUrl.startsWith("https://")) {
            return errorJson(Response.Status.BAD_REQUEST, "invalid_ha_url")
        }
        val host = payload.optString("broker_host", prefs.brokerHost).trim()
        if (host.isEmpty()) return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_host")
        val port = payload.optInt("broker_port", prefs.brokerPort)
        if (port !in 1..65535) return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_port")

        val before = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)

        prefs.haUrl = haUrl
        prefs.haToken = payload.optString("ha_token", prefs.haToken)
        prefs.brokerHost = host
        prefs.brokerPort = port
        prefs.username = payload.optString("username", prefs.username)
        prefs.password = payload.optString("password", prefs.password)
        prefs.deviceName = payload.optString("device_name", prefs.deviceName)

        val after = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)
        if (after != before) onMqttConfigChanged()

        return json("""{"ok":true}""")
    }

    private fun entitiesJson(): String {
        val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
        if (!result.ok) {
            return JSONObject().put("ok", false).put("status", result.statusCode).toString()
        }
        val candidates = sortedCandidates(result.body.orEmpty()).also { cachedCandidates = it }

        val enabledIds = prefs.pillRules.filter { it.enabled }.map { it.entityId }.toSet()
        val items = JSONArray()
        candidates.forEach { candidate ->
            items.put(
                JSONObject()
                    .put("entity_id", candidate.primary.entityId)
                    .put("label", candidate.label)
                    .put("domain", candidate.primary.domain)
                    .put("enabled", candidate.primary.entityId in enabledIds)
            )
        }
        return JSONObject().put("ok", true).put("items", items).toString()
    }

    private fun applyPills(body: String): Response {
        val requested = runCatching { JSONArray(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")

        // Normally already warm from the page's own /api/entities call; refetch only if it is not.
        val candidates = cachedCandidates.ifEmpty {
            val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
            if (!result.ok) return errorJson(Response.Status.SERVICE_UNAVAILABLE, "home_unreachable")
            sortedCandidates(result.body.orEmpty()).also { cachedCandidates = it }
        }

        val selection = (0 until requested.length()).mapNotNull { i ->
            val item = requested.optJSONObject(i) ?: return@mapNotNull null
            val entityId = item.optString("entity_id").trim()
            if (entityId.isBlank()) null else entityId to item.optBoolean("enabled", false)
        }

        prefs.pillRules = mergePillSelection(prefs.pillRules, selection, candidates)
        SettingsChangeBus.get().emit("pillRules")
        return json("""{"ok":true}""")
    }

    private fun sortedCandidates(statesBody: String): List<PillCandidate> =
        PillSupport.candidates(parseHaEntities(statesBody), deviceIds())
            .sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))

    private fun readBody(session: IHTTPSession): String {
        val files = HashMap<String, String>()
        return try {
            session.parseBody(files)
            files["postData"].orEmpty()
        } catch (_: IOException) {
            ""
        } catch (_: ResponseException) {
            ""
        }
    }

    /** Accepts the code however the user typed it: any case, with or without the dash. */
    private fun tokenMatches(provided: String?): Boolean {
        val normalized = provided?.filter(Char::isLetterOrDigit)?.uppercase() ?: return false
        val expected = token.filter(Char::isLetterOrDigit)
        if (normalized.length != expected.length) return false
        return MessageDigest.isEqual(normalized.toByteArray(), expected.toByteArray())
    }

    private fun html(body: String) = noStore(
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    )

    private fun json(body: String) = noStore(
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
    )

    private fun css(body: String) = noStore(
        newFixedLengthResponse(Response.Status.OK, "text/css; charset=utf-8", body)
    )

    private fun javascript(body: String) = noStore(
        newFixedLengthResponse(Response.Status.OK, "text/javascript; charset=utf-8", body)
    )

    private fun errorJson(status: Response.Status, code: String) = noStore(
        newFixedLengthResponse(status, "application/json; charset=utf-8", """{"ok":false,"error":"$code"}""")
    )

    private fun text(status: Response.Status, body: String) = noStore(
        newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
    )

    /** Credentials travel through these responses; no browser or proxy should keep a copy. */
    private fun noStore(response: Response): Response = response.apply {
        addHeader("Cache-Control", "no-store")
        addHeader("X-Content-Type-Options", "nosniff")
    }

    companion object {
        private const val TAG = "WebConfigServer"
        private const val BIND_ADDRESS = "0.0.0.0"
        private const val PREFERRED_PORT = 8080
        private const val SOCKET_READ_TIMEOUT_MS = 15_000

        /** No O/0 or I/1: the code is read off a screen and typed by hand. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val GROUP = 4

        /**
         * Binds [PREFERRED_PORT], falling back to a kernel-assigned free port when it is taken.
         * Returns a listening server, or null when neither attempt could bind.
         */
        fun launch(
            prefs: Prefs,
            deviceIds: () -> Map<String, String>,
            onMqttConfigChanged: () -> Unit,
        ): WebConfigServer? {
            intArrayOf(PREFERRED_PORT, 0).forEach { port ->
                val server = WebConfigServer(port, prefs, deviceIds, onMqttConfigChanged)
                val started = runCatching { server.start(SOCKET_READ_TIMEOUT_MS, true) }
                if (started.isSuccess) return server
                server.stop()
                Log.w(TAG, "could not bind port $port", started.exceptionOrNull())
            }
            return null
        }

        /** `XXXX-XXXX` over a 32-symbol alphabet — 40 bits, short enough to type. */
        private fun mintToken(): String {
            val random = SecureRandom()
            val chars = CharArray(GROUP * 2) { ALPHABET[random.nextInt(ALPHABET.length)] }
            return String(chars, 0, GROUP) + "-" + String(chars, GROUP, GROUP)
        }
    }
}
