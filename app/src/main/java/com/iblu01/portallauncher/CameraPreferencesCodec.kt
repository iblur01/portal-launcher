package com.iblu01.portallauncher

import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Versioned, Android-independent persistence for the camera center configuration. Same contract as
 * [HomePillPreferencesCodec]: malformed or newer data decodes to `null` so the caller can fall back
 * on defaults without overwriting a file it does not understand.
 */
object CameraPreferencesCodec {
    const val CURRENT_SCHEMA_VERSION = CameraPreferences.CURRENT_SCHEMA_VERSION

    fun defaults(): CameraPreferences = CameraPreferences()

    fun encode(preferences: CameraPreferences): String = JSONObject().apply {
        put("schema_version", CURRENT_SCHEMA_VERSION)
        put("hidden", JSONArray(preferences.hidden.sorted()))
        put("order", JSONArray(preferences.order))
        preferences.mainCameraId?.let { put("main_camera", it) }
        put("default_mode", encodeMode(preferences.defaultMode))
    }.toString()

    fun decode(raw: String): CameraPreferences? = runCatching {
        val root = JSONObject(raw)
        val version = root.optInt("schema_version", CURRENT_SCHEMA_VERSION)
        if (version !in 1..CURRENT_SCHEMA_VERSION) return null
        CameraPreferences(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            hidden = root.optJSONArray("hidden").entityIds().toSet(),
            order = root.optJSONArray("order").entityIds(),
            mainCameraId = root.optString("main_camera").takeIf { it.isNotBlank() },
            defaultMode = decodeMode(root.optString("default_mode", "")),
        )
    }.getOrNull()

    fun decodeOrDefault(raw: String): CameraPreferences = decode(raw) ?: defaults()

    fun encodeMode(mode: CameraCenterMode): String = when (mode) {
        CameraCenterMode.MAIN -> "main"
        CameraCenterMode.GRID -> "grid"
    }

    fun decodeMode(raw: String): CameraCenterMode = when (raw) {
        "grid" -> CameraCenterMode.GRID
        else -> CameraCenterMode.MAIN
    }

    private fun JSONArray?.entityIds(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it).trim() }.filter(String::isNotEmpty).distinct()
    }
}
