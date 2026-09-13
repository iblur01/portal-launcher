package com.iblu01.portallauncher

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class HaApiClient(private val baseUrl: String, private val token: String) {

    companion object {
        private const val TAG = "HaApiClient"

        @Volatile
        private var cachedTtsEntity: String? = null
    }

    fun testConnection(): HaApiResult {
        return get("/api/")
    }

    fun getStates(): HaApiResult {
        return get("/api/states")
    }

    fun getState(entityId: String): HaApiResult {
        return get("/api/states/$entityId")
    }

    fun callService(domain: String, service: String, entityId: String? = null, data: Map<String, Any>? = null): HaApiResult {
        val path = "/api/services/$domain/$service"
        val body = JSONObject()
        entityId?.let { id ->
            body.put("entity_id", if (id.contains(",")) JSONArray(id.split(",").map { it.trim() }) else id)
        }
        data?.forEach { (k, v) -> body.put(k, v) }
        val payload = if (body.length() > 0) body.toString() else null
        return post(path, payload)
    }

    /**
     * Hands one intent to Home Assistant's own intent engine, the way its Assist pipeline does.
     *
     * This is what makes voice control worth having: HA resolves "the kitchen light" against its
     * entity names, aliases, areas and floors, so the assistant never has to guess an entity id.
     * Slots are the intent's own (`name`, `area`, `brightness`, …) and are passed through as
     * given — validating them here would only duplicate, badly, what HA answers with anyway.
     */
    fun handleIntent(intent: String, slots: Map<String, Any>): HaApiResult {
        val data = JSONObject()
        slots.forEach { (k, v) -> data.put(k, v) }
        val body = JSONObject().put("name", intent).put("data", data)
        return post("/api/intent/handle", body.toString())
    }

    /**
     * The URL of the clip Home Assistant renders for [message], or null when it will not render
     * one. Home Assistant does the synthesis; the panel only ever plays the file it names.
     *
     * [engine] is a `tts.*` entity; left null, the first one this Home Assistant exposes is used,
     * which is what a payload that does not care about the voice should get.
     */
    fun ttsUrl(message: String, engine: String? = null, language: String? = null): String? {
        val entity = engine?.takeIf { it.isNotBlank() } ?: firstTtsEntity() ?: return null
        val wanted = language?.takeIf { it.isNotBlank() }
        return renderTts(entity, message, wanted)
            // Engines disagree on what a language looks like — Google Translate wants "fr" where
            // the Gemini voices want "fr-FR", and the wrong shape is a 500. The words matter more
            // than the accent, so a refused language is retried as no language at all.
            ?: wanted?.let { renderTts(entity, message, null) }
    }

    private fun renderTts(entity: String, message: String, language: String?): String? {
        val body = JSONObject()
            .put("engine_id", entity)
            .put("message", message)
            // Home Assistant keeps the rendered clip keyed by (text, engine, options): the same
            // sentence is synthesized once and served from disk afterwards — measured at 3.0s then
            // 10ms. It matters twice over: a metered engine bills the first render only, and an
            // alarm announcement does not wait on the network to start speaking.
            .put("cache", true)
        language?.let { body.put("language", it) }
        val result = post("/api/tts_get_url", body.toString())
        if (!result.ok) {
            Log.w(TAG, "tts_get_url refused for $entity/${language ?: "default"} " +
                "(${result.statusCode}): ${result.body?.take(200)}")
            return null
        }
        return runCatching { JSONObject(result.body.orEmpty()).optString("url") }
            .getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** Cached: the panel asks for speech far more often than Home Assistant grows a new engine. */
    private fun firstTtsEntity(): String? {
        cachedTtsEntity?.let { return it }
        val result = get("/api/states")
        if (!result.ok) return null
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull() ?: return null
        for (i in 0 until states.length()) {
            val id = states.optJSONObject(i)?.optString("entity_id").orEmpty()
            if (id.startsWith("tts.")) {
                cachedTtsEntity = id
                return id
            }
        }
        Log.w(TAG, "no tts.* entity on this Home Assistant")
        return null
    }

    private fun get(path: String): HaApiResult {
        return try {
            val conn = openConnection("$baseUrl$path")
            conn.requestMethod = "GET"
            readResponse(conn)
        } catch (e: Exception) {
            Log.w(TAG, "GET $path failed: ${e.message}")
            HaApiResult(ok = false, body = e.message ?: "unknown error")
        }
    }

    private fun post(path: String, body: String?): HaApiResult {
        return try {
            val conn = openConnection("$baseUrl$path")
            conn.requestMethod = "POST"
            conn.doOutput = body != null
            conn.setRequestProperty("Content-Type", "application/json")
            if (body != null) {
                OutputStreamWriter(conn.outputStream).use { it.write(body) }
            }
            readResponse(conn)
        } catch (e: Exception) {
            Log.w(TAG, "POST $path failed: ${e.message}")
            HaApiResult(ok = false, body = e.message ?: "unknown error")
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        return conn
    }

    private fun readResponse(conn: HttpURLConnection): HaApiResult {
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        }
        return HaApiResult(ok = code in 200..299, body = body, statusCode = code)
    }
}

data class HaApiResult(
    val ok: Boolean,
    val body: String?,
    val statusCode: Int = -1,
)
