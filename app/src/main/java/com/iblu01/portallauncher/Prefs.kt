package com.iblu01.portallauncher

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.session.SessionAllowlist
import com.iblu01.portallauncher.session.SessionAllowlistCodec
import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.ui.components.systemWallpaperSupported
import com.iblu01.portallauncher.ui.components.wallpaperFile
import org.json.JSONArray
import org.json.JSONObject

private val backgroundModeKeys = setOf("system", "neutral", "custom", "immich")

/** Bundled openWakeWord model used until the user picks another one. */
const val DEFAULT_WAKE_WORD = "wakeword/hey_jarvis_v0.1.onnx"

class Prefs(private val context: Context) {
    private val sp = plainPrefs(context)

    /** Encrypted store for secrets (HA token, MQTT password). Falls back to [sp] if the keystore is unavailable. */
    private val secure: SharedPreferences = securePrefs(context)

    init {
        if (secure !== sp && !migrationDone) {
            migrationDone = true
            if (secure !== sp) {
                migrateSecret("ha_token")
                migrateSecret("password")
                migrateSecret("immich_api_key")
            }
        }
    }

    /** Move a legacy plaintext secret into the encrypted store, once, then scrub the plaintext copy. */
    private fun migrateSecret(key: String) {
        if (!secure.contains(key) && sp.contains(key)) {
            secure.edit().putString(key, sp.getString(key, "")).apply()
        }
        if (sp.contains(key)) sp.edit().remove(key).apply()
    }

    /**
     * App opened by a tap on the empty part of the home screen. Blank by default and blank is a
     * valid choice: the gesture is opt-in, so a fresh install does nothing on tap rather than
     * pointing at an app that may not be installed.
     */
    var homeAssistantPackage: String
        get() = sp.getString("ha_package", "") ?: ""
        set(value) = sp.edit().putString("ha_package", value.trim()).apply()

    var brokerHost: String
        get() = sp.getString("broker_host", "homeassistant.local") ?: "homeassistant.local"
        set(value) = sp.edit().putString("broker_host", value.trim().ifEmpty { "homeassistant.local" }).apply()

    var brokerPort: Int
        get() = sp.getInt("broker_port", 1883)
        set(value) = sp.edit().putInt("broker_port", value.coerceIn(1, 65535)).apply()

    var username: String
        get() = sp.getString("username", "") ?: ""
        set(value) = sp.edit().putString("username", value.trim()).apply()

    var password: String
        get() = secure.getString("password", "") ?: ""
        set(value) = secure.edit().putString("password", value).apply()

    var deviceName: String
        get() = sp.getString("device_name", null)
            ?.takeIf { it.isNotBlank() }
            ?: androidDeviceName(context)
        set(value) = sp.edit().putString("device_name", value.trim().ifEmpty { androidDeviceName(context) }).apply()

    /** Resolves the OS-level device name (Settings.Global.DEVICE_NAME, else Build.MODEL). */
    private fun androidDeviceName(context: Context): String =
        runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: Build.MODEL?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Android"

    val deviceId: String
        get() {
            val existing = sp.getString("device_id", null)
            if (existing != null) return existing
            val generated = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: java.util.UUID.randomUUID().toString().replace("-", "")
            sp.edit().putString("device_id", generated).apply()
            return generated
        }

    var powerMode: PowerMode
        get() = PowerMode.from(sp.getString("power_mode", PowerMode.FOLLOW_PRESENCE.name))
        set(value) = sp.edit().putString("power_mode", value.name).apply()

    var screenTimeoutEnabled: Boolean
        get() = sp.getBoolean("screen_timeout_enabled", true)
        set(value) = sp.edit().putBoolean("screen_timeout_enabled", value).apply()

    var devKeepScreenOn: Boolean
        get() = sp.getBoolean("dev_keep_screen_on", false)
        set(value) = sp.edit().putBoolean("dev_keep_screen_on", value).apply()

    var screenTimeoutMinutes: Int
        get() = sp.getInt("screen_timeout_minutes", 10)
        set(value) = sp.edit().putInt("screen_timeout_minutes", value.coerceIn(1, 240)).apply()

    /** Queried once: whether the OS has a wallpaper service at all cannot change while we run. */
    private val wallpaperSupported: Boolean by lazy { systemWallpaperSupported(context) }

    /**
     * The background source. On devices whose OS draws no wallpaper (Portal), "system" would mean a
     * permanently black screen, so it resolves to the launcher-drawn photo — or the neutral
     * gradient while no photo has been chosen — whatever the stored value says.
     */
    var backgroundMode: String
        get() {
            val stored = sp.getString("background_mode", "system")
                ?.takeIf { it in backgroundModeKeys } ?: "system"
            if (stored != "system" || wallpaperSupported) return stored
            return if (wallpaperFile(context).exists()) "custom" else "neutral"
        }
        set(value) = sp.edit().putString(
            "background_mode",
            value.takeIf { it in backgroundModeKeys } ?: "system",
        ).apply()

    /**
     * Language code ("en", "fr", …) or "" to follow the device's system language.
     * Written with [SharedPreferences.Editor.commit], not `apply()`: the caller kills the
     * process right after setting this to restart with the new locale, and `apply()`'s async
     * disk write can lose the race against that — the setting silently reverted otherwise.
     */
    var appLanguage: String
        get() = sp.getString("app_language", "") ?: ""
        set(value) { sp.edit().putString("app_language", value).commit() }

    /** Distinguishes the valid "system language" choice from a language never chosen yet. */
    var onboardingLanguageSelected: Boolean
        get() = sp.getBoolean("onboarding_language_selected", false)
        set(value) { sp.edit().putBoolean("onboarding_language_selected", value).commit() }

    var updateLastCheckAt: Long
        get() = sp.getLong("update_last_check_at", 0L)
        set(value) = sp.edit().putLong("update_last_check_at", value).apply()

    var updateRemindAfter: Long
        get() = sp.getLong("update_remind_after", 0L)
        set(value) = sp.edit().putLong("update_remind_after", value).apply()

    var ignoredUpdateVersion: String
        get() = sp.getString("ignored_update_version", "").orEmpty()
        set(value) = sp.edit().putString("ignored_update_version", value).apply()

    var bgOverlayOpacity: Float
        get() = sp.getFloat("bg_overlay_opacity", 0.25f)
        set(value) = sp.edit().putFloat("bg_overlay_opacity", value.coerceIn(0f, 0.6f)).apply()

    // --- Clock theme (see ui.theme.ClockTheme) ------------------------------------------------
    var clockFont: String
        get() = sp.getString("clock_font", ClockFont.SPACE_GROTESK.key) ?: ClockFont.SPACE_GROTESK.key
        set(value) = sp.edit().putString("clock_font", value).apply()

    var clockWeight: Int
        get() = sp.getInt("clock_weight", 900)
        set(value) = sp.edit().putInt("clock_weight", value.coerceIn(100, 900)).apply()

    var clockSize: Float
        get() = sp.getFloat("clock_size", 138f)
        set(value) = sp.edit().putFloat("clock_size", value.coerceIn(60f, 200f)).apply()

    var clockLetterSpacing: Float
        get() = sp.getFloat("clock_letter_spacing", 0f)
        set(value) = sp.edit().putFloat("clock_letter_spacing", value.coerceIn(-5f, 15f)).apply()

    var clockTint: String
        get() = sp.getString("clock_tint", ClockTint.WHITE.key) ?: ClockTint.WHITE.key
        set(value) = sp.edit().putString("clock_tint", value).apply()

    var clockFormat24h: Boolean
        get() = sp.getBoolean("clock_format_24h", true)
        set(value) = sp.edit().putBoolean("clock_format_24h", value).apply()

    var clockDateFormat: String
        get() = sp.getString("clock_date_format", ClockDateFormat.LONG.key) ?: ClockDateFormat.LONG.key
        set(value) = sp.edit().putString("clock_date_format", value).apply()

    var clockElementSpacing: Float
        get() = sp.getFloat("clock_element_spacing", 1f)
        set(value) = sp.edit().putFloat("clock_element_spacing", value.coerceIn(0.4f, 2f)).apply()

    /** Whole clock styling as one value object, mapping to/from the individual keys above. */
    var clockTheme: ClockTheme
        get() = ClockTheme(
            font = ClockFont.fromKey(clockFont),
            weight = clockWeight,
            size = clockSize,
            letterSpacing = clockLetterSpacing,
            tint = ClockTint.fromKey(clockTint),
            format24h = clockFormat24h,
            dateFormat = ClockDateFormat.fromKey(clockDateFormat),
            elementSpacing = clockElementSpacing,
        )
        set(value) {
            clockFont = value.font.key
            clockWeight = value.weight
            clockSize = value.size
            clockLetterSpacing = value.letterSpacing
            clockTint = value.tint.key
            clockFormat24h = value.format24h
            clockDateFormat = value.dateFormat.key
            clockElementSpacing = value.elementSpacing
        }

    /** Multiplier on the app grid's cell size (icon size), so a smaller device can shrink it. */
    var gridScale: Float
        get() = sp.getFloat("grid_scale", 1f)
        set(value) = sp.edit().putFloat("grid_scale", value.coerceIn(0.7f, 1.3f)).apply()


    // --- First-run onboarding (see ui.onboarding) ----------------------------------------------
    /**
     * Content version of the onboarding the user last completed, 0 when they never did.
     *
     * Deliberately independent of [haToken]: Portal is a launcher first, so a user who never
     * connects a home has still finished setting it up. A newer [ONBOARDING_VERSION] never
     * overwrites existing choices — it only makes the flow offerable again.
     */
    var onboardingVersion: Int
        get() = sp.getInt("onboarding_version", 0)
        set(value) = sp.edit().putInt("onboarding_version", value).apply()

    /** Name of the [com.iblu01.portallauncher.ui.onboarding.OnboardingStep] to resume on. */
    var onboardingStep: String
        get() = sp.getString("onboarding_step", "") ?: ""
        set(value) = sp.edit().putString("onboarding_step", value).apply()

    var onboardingCompleted: Boolean
        get() = sp.getBoolean("onboarding_completed", false)
        set(value) = sp.edit().putBoolean("onboarding_completed", value).apply()

    /** Set when the user declined the Home Assistant branch; scopes only that branch. */
    var homeAssistantOnboardingSkipped: Boolean
        get() = sp.getBoolean("onboarding_skipped_ha", false)
        set(value) = sp.edit().putBoolean("onboarding_skipped_ha", value).apply()

    var mqttOnboardingSkipped: Boolean
        get() = sp.getBoolean("onboarding_skipped_mqtt", false)
        set(value) = sp.edit().putBoolean("onboarding_skipped_mqtt", value).apply()

    var appCleanupOnboardingSkipped: Boolean
        get() = sp.getBoolean("onboarding_skipped_app_cleanup", false)
        set(value) = sp.edit().putBoolean("onboarding_skipped_app_cleanup", value).apply()

    /** True once the home-screen gesture hints have been dismissed for good. */
    var gestureHintsSeen: Boolean
        get() = sp.getBoolean("onboarding_gestures_seen", false)
        set(value) = sp.edit().putBoolean("onboarding_gestures_seen", value).apply()

    /** Wipes the flow's progress so the assistant can be offered again, keeping every setting. */
    fun resetOnboarding() {
        sp.edit()
            .remove("onboarding_version")
            .remove("onboarding_step")
            .remove("onboarding_completed")
            .remove("onboarding_skipped_ha")
            .remove("onboarding_skipped_mqtt")
            .remove("onboarding_skipped_app_cleanup")
            .remove("onboarding_gestures_seen")
            .apply()
    }

    /**
     * Versioned, in-memory snapshot used by nearby configuration transfer. Secrets stay in their
     * own section so the receiver writes them back through its encrypted store. Device identity,
     * onboarding progress and OS-owned widget ids are deliberately local and never exported.
     */
    fun exportTransferPayload(): ByteArray {
        val plain = JSONObject()
        sp.all.forEach { (key, value) ->
            if (key !in TRANSFER_LOCAL_KEYS && key !in TRANSFER_SECRET_KEYS && !key.startsWith("onboarding_")) {
                putTransferValue(plain, key, sanitizeTransferValue(key, value))
            }
        }
        val secrets = JSONObject()
        TRANSFER_SECRET_KEYS.forEach { key ->
            if (secure.contains(key)) putTransferValue(secrets, key, secure.all[key])
        }
        return JSONObject()
            .put("version", TRANSFER_PAYLOAD_VERSION)
            .put("plain", plain)
            .put("secure", secrets)
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    /**
     * Validates the complete snapshot before writing. Secrets commit first; completion is part of
     * the final plain-store commit, so an interrupted import returns to onboarding rather than
     * launching with a partially configured profile.
     */
    fun importTransferPayload(payload: ByteArray): Boolean = synchronized(transferLock) {
        if (payload.isEmpty() || payload.size > TRANSFER_MAX_BYTES) return false
        val root = runCatching { JSONObject(String(payload, Charsets.UTF_8)) }.getOrNull()
            ?: return false
        if (root.optInt("version", -1) != TRANSFER_PAYLOAD_VERSION) return false
        val plain = root.optJSONObject("plain") ?: return false
        val secrets = root.optJSONObject("secure") ?: return false
        val plainValues = decodeTransferObject(plain, allowSecrets = false) ?: return false
        val secretValues = decodeTransferObject(secrets, allowSecrets = true) ?: return false

        val secureEditor = secure.edit()
        TRANSFER_SECRET_KEYS.forEach(secureEditor::remove)
        secretValues.forEach { (key, value) -> putEditorValue(secureEditor, key, value) }
        if (!secureEditor.commit()) return false

        val plainEditor = sp.edit()
        sp.all.keys.filter {
            it !in TRANSFER_LOCAL_KEYS && it !in TRANSFER_SECRET_KEYS && !it.startsWith("onboarding_")
        }
            .forEach(plainEditor::remove)
        plainValues.forEach { (key, value) -> putEditorValue(plainEditor, key, value) }
        plainEditor
            .putBoolean("onboarding_completed", true)
            .putInt("onboarding_version", com.iblu01.portallauncher.ui.onboarding.ONBOARDING_VERSION)
            .remove("onboarding_step")
        plainEditor.commit()
    }

    private fun sanitizeTransferValue(key: String, value: Any?): Any? {
        if (key != "app_placements" || value !is String) return value
        return runCatching {
            val source = JSONArray(value)
            val clean = JSONArray()
            for (index in 0 until source.length()) {
                source.optJSONObject(index)?.takeIf { !it.optString("k").startsWith("wg:") }
                    ?.let(clean::put)
            }
            clean.toString()
        }.getOrDefault("[]")
    }

    private fun putTransferValue(target: JSONObject, key: String, value: Any?) {
        val encoded = when (value) {
            null -> JSONObject.NULL
            is Set<*> -> JSONArray(value.filterIsInstance<String>())
            is String, is Boolean, is Int, is Long, is Float, is Double -> value
            else -> return
        }
        target.put(key, JSONObject().put("type", transferType(value)).put("value", encoded))
    }

    private fun transferType(value: Any?): String = when (value) {
        is Boolean -> "boolean"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        is Double -> "float"
        is Set<*> -> "strings"
        else -> "string"
    }

    private fun decodeTransferObject(source: JSONObject, allowSecrets: Boolean): Map<String, Any>? {
        val result = LinkedHashMap<String, Any>()
        val keys = source.keys().asSequence().toList()
        if (keys.size > TRANSFER_MAX_KEYS) return null
        for (key in keys) {
            if (key.length > 128 || key in TRANSFER_LOCAL_KEYS || key.startsWith("onboarding_")) return null
            if (allowSecrets != (key in TRANSFER_SECRET_KEYS)) return null
            val entry = source.optJSONObject(key) ?: return null
            val value: Any = when (entry.optString("type")) {
                "string" -> entry.optString("value").also {
                    if (it.length > TRANSFER_MAX_STRING) return null
                }
                "boolean" -> entry.optBoolean("value")
                "int" -> entry.optInt("value")
                "long" -> entry.optLong("value")
                "float" -> entry.optDouble("value").toFloat()
                "strings" -> {
                    val array = entry.optJSONArray("value") ?: return null
                    if (array.length() > TRANSFER_MAX_SET) return null
                    (0 until array.length()).map {
                        array.optString(it).also { value ->
                            if (value.length > TRANSFER_MAX_STRING) return null
                        }
                    }.toSet()
                }
                else -> return null
            }
            result[key] = value
        }
        return result
    }

    private fun putEditorValue(editor: SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }

    var haUrl: String
        get() = sp.getString("ha_url", "http://homeassistant.local:8123") ?: "http://homeassistant.local:8123"
        set(value) = sp.edit().putString("ha_url", value.trim().trimEnd('/').ifEmpty { "http://homeassistant.local:8123" }).apply()

    var haToken: String
        get() = secure.getString("ha_token", "") ?: ""
        set(value) = secure.edit().putString("ha_token", value.trim()).apply()

    // --- Voice assistant (Pipecat Assist add-on satellite, see voice.VoiceAssistantController) --
    var voiceAssistantEnabled: Boolean
        get() = sp.getBoolean("voice_enabled", false)
        set(value) = sp.edit().putBoolean("voice_enabled", value).apply()

    /**
     * Whatever the user pasted from the add-on page: the `/api/offer` URL, the ESPHome satellite
     * `ws://` URL, or a bare `host:port`. Normalised at read time by `parseVoiceEndpoint`, so the
     * raw string is stored as typed and stays recognisable in settings.
     */
    var voiceAssistantUrl: String
        get() = sp.getString("voice_url", "") ?: ""
        set(value) = sp.edit().putString("voice_url", value.trim().take(2048)).apply()

    /** Add-on "satellite shared secret". Sent as a bearer token, never in the URL. */
    var voiceAssistantToken: String
        get() = secure.getString("voice_token", "") ?: ""
        set(value) = secure.edit().putString("voice_token", value.trim().take(512)).apply()

    val hasVoiceAssistantToken: Boolean
        get() = secure.getString("voice_token", "").orEmpty().isNotBlank()

    /** openWakeWord model, as an assets-relative path. See app/src/main/assets/wakeword. */
    var voiceAssistantWakeWord: String
        get() = sp.getString("voice_wake_word", DEFAULT_WAKE_WORD) ?: DEFAULT_WAKE_WORD
        set(value) = sp.edit().putString("voice_wake_word", value).apply()

    /**
     * Detection threshold, in percent. Lower is more sensitive; noisy rooms want higher.
     * False wakes are handled by the engine's consecutive-frame patience rather than by a tighter
     * threshold, so this stays where detection range is best.
     */
    var voiceAssistantThreshold: Int
        get() = sp.getInt("voice_threshold", 50)
        set(value) = sp.edit().putInt("voice_threshold", value.coerceIn(1, 95)).apply()

    /**
     * Speaker-loop calibration measured by voice.MicCalibrator; null until the user has run it.
     * Raw measurements are stored (not the derived engine parameters) so the derivation can
     * change across app updates without a re-calibration.
     */
    var voiceMicCalibration: com.iblu01.portallauncher.voice.MicCalibration?
        get() {
            val floor = sp.getFloat("voice_mic_noise_floor", -1f)
            val playback = sp.getFloat("voice_mic_playback_rms", -1f)
            if (floor < 0f || playback <= 0f) return null
            return com.iblu01.portallauncher.voice.MicCalibration(floor, playback)
        }
        set(value) = sp.edit()
            .putFloat("voice_mic_noise_floor", value?.noiseFloor ?: -1f)
            .putFloat("voice_mic_playback_rms", value?.playbackRms ?: -1f)
            .apply()

    /**
     * In-turn silence ceiling while an exchange is under way. The windows right after connect and
     * after a bot answer are shorter and fixed (see VoiceAssistantController).
     */
    var voiceAssistantIdleSeconds: Int
        get() = sp.getInt("voice_idle_seconds", 20)
        set(value) = sp.edit().putInt("voice_idle_seconds", value.coerceIn(5, 300)).apply()

    // --- Immich photo source (see photo.immich) ------------------------------------------------
    var immichUrl: String
        get() = sp.getString("immich_url", "") ?: ""
        set(value) = sp.edit().putString("immich_url", value.trim().trimEnd('/').take(2048)).apply()

    var immichApiKey: String
        get() = secure.getString("immich_api_key", "") ?: ""
        set(value) = secure.edit().putString("immich_api_key", value.trim().take(512)).apply()

    val hasImmichApiKey: Boolean
        get() = secure.getString("immich_api_key", "").orEmpty().isNotBlank()

    var immichAlbumIds: List<String>
        get() = decodeStringList(sp.getString("immich_album_ids", "[]"))
        set(value) = sp.edit().putString(
            "immich_album_ids",
            encodeStringList(value.map { it.trim().take(128) }.filter { it.isNotBlank() }.distinct().take(20)),
        ).apply()

    var immichAllowInsecure: Boolean
        get() = sp.getBoolean("immich_allow_insecure", false)
        set(value) = sp.edit().putBoolean("immich_allow_insecure", value).apply()

    var immichShuffle: Boolean
        get() = sp.getBoolean("immich_shuffle", true)
        set(value) = sp.edit().putBoolean("immich_shuffle", value).apply()

    var immichRefreshMinutes: Int
        get() = sp.getInt("immich_refresh_minutes", 60)
        set(value) = sp.edit().putInt("immich_refresh_minutes", value.coerceIn(5, 24 * 60)).apply()

    var immichCadenceSeconds: Int
        get() = sp.getInt("immich_cadence_seconds", 30)
        set(value) = sp.edit().putInt("immich_cadence_seconds", value.coerceIn(5, 3600)).apply()

    val immichTransportPolicy: TransportPolicy
        get() = if (immichAllowInsecure) TransportPolicy.ALLOW_INSECURE else TransportPolicy.REQUIRE_SECURE

    fun clearImmichConfiguration() {
        secure.edit().remove("immich_api_key").apply()
        sp.edit()
            .remove("immich_url")
            .remove("immich_album_ids")
            .remove("immich_allow_insecure")
            .remove("immich_shuffle")
            .remove("immich_refresh_minutes")
            .remove("immich_cadence_seconds")
            .apply()
    }

    var pillRules: List<PillRule>
        get() = PillRuleCodec.decode(sp.getString("pill_rules", "[]") ?: "[]")
        set(value) = sp.edit().putString("pill_rules", PillRuleCodec.encode(value)).apply()

    /** Integration domains hidden only inside Portal. Home Assistant is never modified. */
    var disabledHaIntegrations: Set<String>
        get() = sp.getStringSet(DISABLED_HA_INTEGRATIONS_KEY, emptySet())
            ?.mapTo(sortedSetOf()) { it.trim().lowercase() }
            .orEmpty()
        set(value) {
            sp.edit().putStringSet(
                DISABLED_HA_INTEGRATIONS_KEY,
                value.mapNotNull { it.trim().lowercase().takeIf(String::isNotBlank) }.toSet(),
            ).apply()
            SettingsChangeBus.get().emit(DISABLED_HA_INTEGRATIONS_KEY)
        }

    /**
     * Persistent Home-page layout, kept separate from live [LauncherChip] rendering models.
     *
     * Reading this property performs the one-time migration only when the key is genuinely
     * absent. Invalid JSON falls back safely in memory and is deliberately left untouched so it
     * can be inspected or recovered instead of being silently destroyed.
     */
    var homePillPreferences: HomePillPreferences
        get() = synchronized(homePillPreferencesLock) { readHomePillPreferencesLocked() }
        set(value) {
            writeHomePillPreferences(value)
        }

    /** Atomically stores [value], returning false when its canonical JSON is already persisted. */
    fun writeHomePillPreferences(value: HomePillPreferences): Boolean =
        synchronized(homePillPreferencesLock) { writeHomePillPreferencesLocked(value) }

    /**
     * Serializes read-modify-write calls within the process and publishes one change at most.
     * The transformed value (with the current schema version) is returned to simplify reducers.
     */
    fun updateHomePillPreferences(
        transform: (HomePillPreferences) -> HomePillPreferences,
    ): HomePillPreferences = synchronized(homePillPreferencesLock) {
        val updated = transform(readHomePillPreferencesLocked()).copy(
            schemaVersion = HomePillPreferencesCodec.CURRENT_SCHEMA_VERSION,
        )
        writeHomePillPreferencesLocked(updated)
        updated
    }

    private fun readHomePillPreferencesLocked(): HomePillPreferences {
        val raw = runCatching {
            if (sp.contains(HOME_PILL_PREFERENCES_KEY)) {
                sp.getString(HOME_PILL_PREFERENCES_KEY, null)
            } else {
                null
            }
        }.getOrElse {
            Log.e("Prefs", "home pill preferences are not stored as JSON", it)
            return HomePillPreferencesCodec.defaults()
        }

        if (raw == null) {
            val migrated = HomePillPreferencesCodec.defaults()
            // This editor changes only the new key: legacy pill_rules remain byte-for-byte intact.
            sp.edit()
                .putString(HOME_PILL_PREFERENCES_KEY, HomePillPreferencesCodec.encode(migrated))
                .apply()
            return migrated
        }

        return HomePillPreferencesCodec.decode(raw) ?: run {
            Log.e("Prefs", "invalid home pill preferences; using defaults without overwriting source")
            HomePillPreferencesCodec.defaults()
        }
    }

    private fun writeHomePillPreferencesLocked(value: HomePillPreferences): Boolean {
        val currentVersion = value.copy(
            schemaVersion = HomePillPreferencesCodec.CURRENT_SCHEMA_VERSION,
        )
        val encoded = HomePillPreferencesCodec.encode(currentVersion)
        val stored = runCatching { sp.getString(HOME_PILL_PREFERENCES_KEY, null) }.getOrNull()
        if (stored == encoded) return false

        sp.edit().putString(HOME_PILL_PREFERENCES_KEY, encoded).apply()
        SettingsChangeBus.get().emit(HOME_PILL_PREFERENCES_CHANGE_KEY)
        return true
    }

    /**
     * Camera center configuration. Stored under its own key, so a launcher that never opened the
     * camera center keeps a preference file byte-for-byte identical to the previous release.
     *
     * Absent or invalid JSON falls back to defaults **in memory** and the raw value is left
     * untouched, exactly like [homePillPreferences].
     */
    var cameraPreferences: CameraPreferences
        get() = synchronized(cameraPreferencesLock) { readCameraPreferencesLocked() }
        set(value) {
            synchronized(cameraPreferencesLock) { writeCameraPreferencesLocked(value) }
        }

    /** Serializes read-modify-write calls and publishes at most one change event. */
    fun updateCameraPreferences(
        transform: (CameraPreferences) -> CameraPreferences,
    ): CameraPreferences = synchronized(cameraPreferencesLock) {
        val updated = transform(readCameraPreferencesLocked()).copy(
            schemaVersion = CameraPreferencesCodec.CURRENT_SCHEMA_VERSION,
        )
        writeCameraPreferencesLocked(updated)
        updated
    }

    private fun readCameraPreferencesLocked(): CameraPreferences {
        val raw = runCatching { sp.getString(CAMERA_PREFERENCES_KEY, null) }.getOrElse {
            Log.e("Prefs", "camera preferences are not stored as JSON", it)
            return CameraPreferencesCodec.defaults()
        } ?: return CameraPreferencesCodec.defaults()
        return CameraPreferencesCodec.decode(raw) ?: run {
            Log.e("Prefs", "invalid camera preferences; using defaults without overwriting source")
            CameraPreferencesCodec.defaults()
        }
    }

    private fun writeCameraPreferencesLocked(value: CameraPreferences): Boolean {
        val encoded = CameraPreferencesCodec.encode(
            value.copy(schemaVersion = CameraPreferencesCodec.CURRENT_SCHEMA_VERSION),
        )
        if (runCatching { sp.getString(CAMERA_PREFERENCES_KEY, null) }.getOrNull() == encoded) return false
        sp.edit().putString(CAMERA_PREFERENCES_KEY, encoded).apply()
        SettingsChangeBus.get().emit(CAMERA_PREFERENCES_CHANGE_KEY)
        return true
    }

    var pillAutoGroupsInitialized: Boolean
        get() = sp.getBoolean("pill_auto_groups_initialized", false)
        set(value) = sp.edit().putBoolean("pill_auto_groups_initialized", value).apply()

    var pillNoisePolicyVersion: Int
        get() = sp.getInt("pill_noise_policy_version", 0)
        set(value) = sp.edit().putInt("pill_noise_policy_version", value).apply()

    var autoReturnEnabled: Boolean
        get() = sp.getBoolean("auto_return_enabled", true)
        set(value) = sp.edit().putBoolean("auto_return_enabled", value).apply()

    var autoReturnDelaySeconds: Int
        get() = sp.getInt("auto_return_delay_seconds", 10)
        set(value) = sp.edit().putInt("auto_return_delay_seconds", value.coerceIn(5, 60)).apply()

    /** Local kill switch for the bounded external-app session feature. Defaults off (fail-closed). */
    var appSessionsEnabled: Boolean
        get() = sp.getBoolean("app_sessions_enabled", false)
        set(value) = sp.edit().putBoolean("app_sessions_enabled", value).apply()

    /** Classified package allowlist, configured only from local app settings. Empty by default. */
    var appSessionAllowlist: SessionAllowlist
        get() = SessionAllowlistCodec.decode(sp.getStringSet("app_session_allowlist", emptySet()))
        set(value) = sp.edit()
            .putStringSet("app_session_allowlist", SessionAllowlistCodec.encode(value))
            .apply()

    // --- Launcher grid (see ui.apps.LauncherLayoutStore) --------------------------------------
    /**
     * The app grid's order, as item keys. Dense and ordered (iOS semantics): a drag inserts at an
     * index and everything after cascades, so there are no holes to persist. Keys absent from the
     * device are ignored on read; newly installed apps are appended alphabetically.
     */
    var appOrder: List<String>
        get() = decodeStringList(sp.getString("app_order", "[]"))
        set(value) = sp.edit().putString("app_order", encodeStringList(value)).apply()

    /**
     * Where each item sits: page + cell. Free placement, so holes are meaningful and nothing is
     * inferred from an index. [appOrder] is only read once, to seed these from a pre-pages
     * arrangement.
     */
    var appPlacements: List<AppPlacement>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("app_placements", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = o.optString("k")
                if (key.isBlank()) null
                else AppPlacement(
                    key = key,
                    page = o.optInt("p"),
                    col = o.optInt("c"),
                    row = o.optInt("r"),
                    // Absent for arrangements written before widgets existed: icons are 1x1.
                    spanX = o.optInt("w", 1).coerceAtLeast(1),
                    spanY = o.optInt("h", 1).coerceAtLeast(1),
                )
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach {
                arr.put(
                    org.json.JSONObject()
                        .put("k", it.key).put("p", it.page).put("c", it.col).put("r", it.row)
                        .put("w", it.spanX).put("h", it.spanY)
                )
            }
            sp.edit().putString("app_placements", arr.toString()).apply()
        }

    /** True once [appOrder] has been converted into [appPlacements], so it is never replayed. */
    var appPlacementsSeeded: Boolean
        get() = sp.getBoolean("app_placements_seeded", false)
        set(value) = sp.edit().putBoolean("app_placements_seeded", value).apply()

    /**
     * Folders on the app grid: id -> member item keys. Labels are *not* stored here — a folder is
     * renamed through [appLabels] like any other item, keyed by its `fd:<id>` grid key.
     */
    var appFolders: List<FolderRecord>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("app_folders", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val members = o.optJSONArray("m") ?: org.json.JSONArray()
                val keys = (0 until members.length()).mapNotNull {
                    members.optString(it).takeIf(String::isNotBlank)
                }
                if (id.isBlank() || keys.isEmpty()) null else FolderRecord(id, keys)
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach { folder ->
                val members = org.json.JSONArray()
                folder.members.forEach { members.put(it) }
                arr.put(org.json.JSONObject().put("id", folder.id).put("m", members))
            }
            sp.edit().putString("app_folders", arr.toString()).apply()
        }

    /**
     * Package name of the installed icon pack applied to app icons, or "" for the system icons.
     * See `ui.apps.IconPack`.
     */
    var iconPack: String
        get() = sp.getString("icon_pack", "") ?: ""
        set(value) = sp.edit().putString("icon_pack", value.trim()).apply()

    /**
     * Whether the grid draws a dot on apps with a pending notification. Off by default: it needs
     * notification-listener access, which the user has to grant in the system settings.
     */
    var notificationDots: Boolean
        get() = sp.getBoolean("notification_dots", false)
        set(value) = sp.edit().putBoolean("notification_dots", value).apply()

    /**
     * Set once a root shell granted every system capability. It also means the app was exempted
     * from background restrictions, so the MQTT bridge runs without a foreground-service
     * notification — no permanent entry in the notification shade.
     */
    var rootProvisioned: Boolean
        get() = sp.getBoolean("root_provisioned", false)
        set(value) = sp.edit().putBoolean("root_provisioned", value).apply()

    /** Item keys hidden from the grid. */
    var hiddenApps: Set<String>
        get() = decodeStringList(sp.getString("hidden_apps", "[]")).toSet()
        set(value) = sp.edit().putString("hidden_apps", encodeStringList(value.toList())).apply()

    /** Item key -> user-chosen label, overriding the one the app declares. */
    var appLabels: Map<String, String>
        get() = runCatching {
            val obj = org.json.JSONObject(sp.getString("app_labels", "{}") ?: "{}")
            obj.keys().asSequence().mapNotNull { key ->
                obj.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
            }.toMap()
        }.getOrDefault(emptyMap())
        set(value) {
            val obj = org.json.JSONObject()
            value.forEach { (k, v) -> obj.put(k, v) }
            sp.edit().putString("app_labels", obj.toString()).apply()
        }

    /** Widget ids allocated from our `AppWidgetHost`, in no particular order. */
    var widgetIds: List<Int>
        get() = decodeStringList(sp.getString("widget_ids", "[]")).mapNotNull { it.toIntOrNull() }
        set(value) = sp.edit().putString("widget_ids", encodeStringList(value.map(Int::toString))).apply()

    /** Shortcuts pinned by apps through `ACTION_CONFIRM_PIN_SHORTCUT`. */
    var pinnedShortcuts: List<PinnedShortcut>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("pinned_shortcuts", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val pkg = o.optString("pkg"); val id = o.optString("id")
                if (pkg.isBlank() || id.isBlank()) null
                else PinnedShortcut(pkg, id, o.optString("label"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach {
                arr.put(
                    org.json.JSONObject()
                        .put("pkg", it.packageName)
                        .put("id", it.shortcutId)
                        .put("label", it.label)
                )
            }
            sp.edit().putString("pinned_shortcuts", arr.toString()).apply()
        }

    private fun decodeStringList(raw: String?): List<String> = runCatching {
        val arr = org.json.JSONArray(raw ?: "[]")
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    private fun encodeStringList(values: List<String>): String {
        val arr = org.json.JSONArray()
        values.forEach { arr.put(it) }
        return arr.toString()
    }

    val brokerUri: String get() = "tcp://$brokerHost:$brokerPort"

    companion object {
        const val DEFAULT_HA_PACKAGE = "io.homeassistant.companion.android"
        const val HOME_PILL_PREFERENCES_CHANGE_KEY = "homePillPreferences"
        const val DISABLED_HA_INTEGRATIONS_KEY = "disabled_ha_integrations"
        private const val HOME_PILL_PREFERENCES_KEY = "home_pill_preferences"
        private val homePillPreferencesLock = Any()
        const val CAMERA_PREFERENCES_CHANGE_KEY = "cameraPreferences"
        private const val CAMERA_PREFERENCES_KEY = "camera_preferences"
        private val cameraPreferencesLock = Any()
        private val transferLock = Any()
        private const val TRANSFER_PAYLOAD_VERSION = 1
        private const val TRANSFER_MAX_BYTES = 1024 * 1024
        private const val TRANSFER_MAX_KEYS = 512
        private const val TRANSFER_MAX_STRING = 256 * 1024
        private const val TRANSFER_MAX_SET = 4096
        private val TRANSFER_SECRET_KEYS = setOf("ha_token", "password", "immich_api_key")
        private val TRANSFER_LOCAL_KEYS = setOf(
            "device_id", "device_name", "widget_ids", "root_provisioned",
            "update_last_check_at", "update_remind_after", "ignored_update_version",
        )

        // Building EncryptedSharedPreferences spins up a Keystore MasterKey + Tink (heavy crypto,
        // reflection via sun.misc.Unsafe) — ~hundreds of ms. Prefs() is constructed all over,
        // including on every touch (SleepScheduler.onInteraction) and every HA state callback, so
        // doing this per instance froze the main thread. Cache both stores process-wide (keyed by
        // the application context) so constructing a Prefs is effectively free after the first.
        @Volatile private var cachedPlain: SharedPreferences? = null
        @Volatile private var cachedPlainContext: Context? = null
        @Volatile private var cachedSecure: SharedPreferences? = null
        @Volatile private var cachedSecureContext: Context? = null
        @Volatile private var migrationDone = false

        private fun plainPrefs(context: Context): SharedPreferences {
            val app = context.applicationContext
            if (cachedPlainContext === app) cachedPlain?.let { return it }
            return synchronized(this) {
                if (cachedPlainContext === app) cachedPlain?.let { return@synchronized it }
                app.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE).also {
                    cachedPlainContext = app
                    cachedPlain = it
                }
            }
        }

        private fun securePrefs(context: Context): SharedPreferences {
            val app = context.applicationContext
            if (cachedSecureContext === app) cachedSecure?.let { return it }
            return synchronized(this) {
                if (cachedSecureContext === app) cachedSecure?.let { return@synchronized it }
                run {
                    val result = runCatching {
                        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                        EncryptedSharedPreferences.create(
                            app, "portal_launcher_secure", key,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    }.getOrElse {
                        Log.e("Prefs", "encrypted prefs unavailable; storing secrets in plain prefs", it)
                        plainPrefs(app)
                    }
                    cachedSecureContext = app
                    cachedSecure = result
                    migrationDone = false
                    result
                }
            }
        }
    }
}

/** One item's position and size on the launcher grid. Icons are 1x1; widgets span more. */
data class AppPlacement(
    val key: String,
    val page: Int,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

/** A folder as persisted: its id and the item keys it holds. See `ui.apps.Folder`. */
data class FolderRecord(val id: String, val members: List<String>)

/** A shortcut an app asked the launcher to pin. Its icon lives in `ShortcutIconStore`. */
data class PinnedShortcut(val packageName: String, val shortcutId: String, val label: String)

enum class PowerMode {
    FOLLOW_PRESENCE,
    ALWAYS_ON;

    companion object {
        fun from(value: String?): PowerMode =
            values().firstOrNull { it.name == value } ?: FOLLOW_PRESENCE
    }
}

/** App display language. [code] is stored in [Prefs.appLanguage]; "" means "follow the system". */
enum class AppLanguage(val code: String, val flag: String, val nameRes: Int) {
    SYSTEM("", "🌐", R.string.language_system),
    ENGLISH("en", "🇬🇧", R.string.language_english),
    FRENCH("fr", "🇫🇷", R.string.language_french);

    companion object {
        fun from(code: String): AppLanguage = values().firstOrNull { it.code == code } ?: SYSTEM
    }
}
