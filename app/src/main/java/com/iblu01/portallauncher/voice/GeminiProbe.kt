package com.iblu01.portallauncher.voice

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks a Gemini key and model before the panel is left believing it has a working assistant.
 *
 * Why the model list rather than a real session: the two failures that actually happen are a
 * mistyped key and a model name Google has retired (these Live previews are renamed every few
 * months). `GET /v1beta/models` answers both in one blocking call, without opening a socket the
 * user would then be billed for. It does not prove the audio path works — nothing short of a
 * session does, and that is what the settings page's own connection test is for.
 */
object GeminiProbe {
    private const val TAG = "GeminiProbe"
    private const val TIMEOUT_MS = 8_000
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models"

    /** The method a Live speech-to-speech session needs; a text-only model does not list it. */
    private const val BIDI_METHOD = "bidiGenerateContent"

    sealed interface Result {
        /** [live] holds the models that can hold a Live session, short names, no `models/` prefix. */
        data class Ok(val all: Set<String>, val live: Set<String>) : Result
        object Unauthorized : Result
        data class Unreachable(val detail: String) : Result
    }

    fun listModels(apiKey: String): Result {
        if (apiKey.isBlank()) return Result.Unauthorized
        return try {
            val connection = (URL("$ENDPOINT?key=$apiKey&pageSize=200").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            // 400 is what a malformed key returns, 403 a revoked one: both are the user's key
            // being wrong, which is a different message from "the panel has no internet".
            if (code == 400 || code == 401 || code == 403) {
                connection.disconnect()
                return Result.Unauthorized
            }
            if (code !in 200..299) {
                connection.disconnect()
                return Result.Unreachable("http $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseModels(body)
        } catch (e: Exception) {
            Log.w(TAG, "model list failed: ${e.message}")
            Result.Unreachable(e.message ?: "unreachable")
        }
    }

    internal fun parseModels(body: String): Result {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.Unreachable("unreadable answer")
        val models = json.optJSONArray("models") ?: return Result.Unreachable("no models in answer")
        val all = mutableSetOf<String>()
        val live = mutableSetOf<String>()
        for (i in 0 until models.length()) {
            val model = models.optJSONObject(i) ?: continue
            val name = model.optString("name").removePrefix("models/").takeIf { it.isNotEmpty() } ?: continue
            all += name
            val methods = model.optJSONArray("supportedGenerationMethods")
            val supportsBidi = (0 until (methods?.length() ?: 0))
                .any { methods?.optString(it) == BIDI_METHOD }
            if (supportsBidi) live += name
        }
        return Result.Ok(all, live)
    }

    /** Accepts the name with or without its `models/` prefix, as the settings field allows both. */
    fun supportsLive(result: Result.Ok, model: String): Boolean =
        model.removePrefix("models/").let { it in result.live }

    fun exists(result: Result.Ok, model: String): Boolean =
        model.removePrefix("models/").let { it in result.all }
}
