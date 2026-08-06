package com.iblu01.portallauncher

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iblu01.portallauncher.session.SessionAllowlist
import com.iblu01.portallauncher.session.SessionAllowlistCodec
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint
import com.iblu01.portallauncher.photo.TransportPolicy

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

    var homeAssistantPackage: String
        get() = sp.getString("ha_package", DEFAULT_HA_PACKAGE) ?: DEFAULT_HA_PACKAGE
        set(value) = sp.edit().putString("ha_package", value.trim().ifEmpty { DEFAULT_HA_PACKAGE }).apply()

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
        get() = sp.getString("device_name", "Portal") ?: "Portal"
        set(value) = sp.edit().putString("device_name", value.trim().ifEmpty { "Portal" }).apply()

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

    var backgroundMode: String
        get() = sp.getString("background_mode", "neutral") ?: "neutral"
        set(value) = sp.edit().putString("background_mode", value).apply()

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
            elementSpacing = clockElementSpacing,
        )
        set(value) {
            clockFont = value.font.key
            clockWeight = value.weight
            clockSize = value.size
            clockLetterSpacing = value.letterSpacing
            clockTint = value.tint.key
            clockFormat24h = value.format24h
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

    var haUrl: String
        get() = sp.getString("ha_url", "http://homeassistant.local:8123") ?: "http://homeassistant.local:8123"
        set(value) = sp.edit().putString("ha_url", value.trim().trimEnd('/').ifEmpty { "http://homeassistant.local:8123" }).apply()

    var haToken: String
        get() = secure.getString("ha_token", "") ?: ""
        set(value) = secure.edit().putString("ha_token", value.trim()).apply()

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

    var pillAutoGroupsInitialized: Boolean
        get() = sp.getBoolean("pill_auto_groups_initialized", false)
        set(value) = sp.edit().putBoolean("pill_auto_groups_initialized", value).apply()

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

        // Building EncryptedSharedPreferences spins up a Keystore MasterKey + Tink (heavy crypto,
        // reflection via sun.misc.Unsafe) — ~hundreds of ms. Prefs() is constructed all over,
        // including on every touch (SleepScheduler.onInteraction) and every HA state callback, so
        // doing this per instance froze the main thread. Cache both stores process-wide (keyed by
        // the application context) so constructing a Prefs is effectively free after the first.
        @Volatile private var cachedPlain: SharedPreferences? = null
        @Volatile private var cachedSecure: SharedPreferences? = null
        @Volatile private var migrationDone = false

        private fun plainPrefs(context: Context): SharedPreferences =
            cachedPlain ?: synchronized(this) {
                cachedPlain ?: context.applicationContext
                    .getSharedPreferences("portal_launcher", Context.MODE_PRIVATE)
                    .also { cachedPlain = it }
            }

        private fun securePrefs(context: Context): SharedPreferences =
            cachedSecure ?: synchronized(this) {
                cachedSecure ?: run {
                    val app = context.applicationContext
                    runCatching {
                        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                        EncryptedSharedPreferences.create(
                            app, "portal_launcher_secure", key,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                        )
                    }.getOrElse {
                        Log.e("Prefs", "encrypted prefs unavailable; storing secrets in plain prefs", it)
                        plainPrefs(app)
                    }.also { cachedSecure = it }
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
