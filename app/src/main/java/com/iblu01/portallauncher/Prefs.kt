package com.iblu01.portallauncher

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint

class Prefs(private val context: Context) {
    private val sp = plainPrefs(context)

    /** Encrypted store for secrets (HA token, MQTT password). Falls back to [sp] if the keystore is unavailable. */
    private val secure: SharedPreferences = securePrefs(context)

    init {
        if (secure !== sp && !migrationDone) {
            migrationDone = true
            if (secure !== sp) { migrateSecret("ha_token"); migrateSecret("password") }
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

    var tapThreshold: Float
        get() = sp.getFloat("tap_threshold", 4.0f)
        set(value) = sp.edit().putFloat("tap_threshold", value.coerceIn(2f, 15f)).apply()

    var tempOffset: Float
        get() = sp.getFloat("temp_offset", 0f)
        set(value) = sp.edit().putFloat("temp_offset", value.coerceIn(-20f, 20f)).apply()

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

    var adbEnabled: Boolean
        get() = sp.getBoolean("adb_enabled", false)
        set(value) = sp.edit().putBoolean("adb_enabled", value).apply()

    var adbPort: Int
        get() = sp.getInt("adb_port", 5555)
        set(value) = sp.edit().putInt("adb_port", value).apply()

    var webConfigEnabled: Boolean
        get() = sp.getBoolean("web_config_enabled", false)
        set(value) = sp.edit().putBoolean("web_config_enabled", value).apply()

    var webConfigPort: Int
        get() = sp.getInt("web_config_port", 8080)
        set(value) = sp.edit().putInt("web_config_port", value.coerceIn(1024, 65535)).apply()

    /** Secret bearer token for the web config server. Auto-generated on first read if empty. */
    val webConfigToken: String
        get() {
            val existing = secure.getString("web_config_token", "") ?: ""
            if (existing.isNotEmpty()) return existing
            return regenerateWebConfigToken()
        }

    fun regenerateWebConfigToken(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        val token = android.util.Base64.encodeToString(
            bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
        secure.edit().putString("web_config_token", token).apply()
        return token
    }

    var haUrl: String
        get() = sp.getString("ha_url", "http://homeassistant.local:8123") ?: "http://homeassistant.local:8123"
        set(value) = sp.edit().putString("ha_url", value.trim().trimEnd('/').ifEmpty { "http://homeassistant.local:8123" }).apply()

    var haToken: String
        get() = secure.getString("ha_token", "") ?: ""
        set(value) = secure.edit().putString("ha_token", value.trim()).apply()

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

    /** Trigger-sensor -> camera pairs: when the sensor turns "on", the camera pops up. */
    var cameraPairs: List<CameraPair>
        get() = runCatching {
            val arr = org.json.JSONArray(sp.getString("camera_pairs", "[]") ?: "[]")
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val t = o.optString("trigger"); val c = o.optString("camera")
                if (t.isBlank() || c.isBlank()) null else CameraPair(t, c)
            }
        }.getOrDefault(emptyList())
        set(value) {
            val arr = org.json.JSONArray()
            value.forEach { arr.put(org.json.JSONObject().put("trigger", it.trigger).put("camera", it.camera)) }
            sp.edit().putString("camera_pairs", arr.toString()).apply()
        }

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

data class CameraPair(val trigger: String, val camera: String)

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
