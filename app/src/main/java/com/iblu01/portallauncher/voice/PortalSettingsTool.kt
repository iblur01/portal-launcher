package com.iblu01.portallauncher.voice

import android.content.Context
import android.content.Intent
import com.iblu01.portallauncher.DeviceStateHub
import com.iblu01.portallauncher.PillRepository
import com.iblu01.portallauncher.PillSupport
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.SettingsChangeBus
import com.iblu01.portallauncher.SleepScheduler
import com.iblu01.portallauncher.domain.home.HomeGroupingMode
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.ui.apps.LauncherAppsFacade
import org.json.JSONObject

/** Local settings and launcher actions exposed to the voice assistant. */
class PortalSettingsTool(
    private val context: Context,
    private val prefs: Prefs,
    private val pills: PillRepository,
) {
    fun execute(args: JSONObject): Map<String, Any?> = when (args.optString("action")) {
        ACTION_PIN_DEVICE -> pinDevice(args.optString("device"))
        ACTION_SET_GROUPING -> setGrouping(args.optString("grouping"))
        ACTION_OPEN_APP -> openApp(args.optString("app"))
        ACTION_SET_HOME_PAGE -> setHomePage(args)
        ACTION_SET_SCREEN_TIMEOUT -> setScreenTimeout(args)
        ACTION_SET_AUTO_RETURN -> setAutoReturn(args)
        ACTION_SET_CLOCK_FORMAT -> setClockFormat(args.optString("clock_format"))
        ACTION_SET_GRID_SCALE -> setGridScale(args)
        ACTION_SET_BACKGROUND -> setBackground(args.optString("background"))
        ACTION_SET_VOICE_BEHAVIOR -> setVoiceBehavior(args)
        else -> mapOf("error" to "unknown Portal action")
    }

    private fun pinDevice(spokenName: String): Map<String, Any?> {
        if (spokenName.isBlank()) return mapOf("error" to "missing device name")
        val candidates = PillSupport.candidates(
            pills.latestStates.values.toList(),
            pills.latestDeviceIds,
        )
        val candidate = candidates.firstOrNull { it.primary.entityId.equals(spokenName, ignoreCase = true) }
            ?: matchName(candidates.map { it.label }, spokenName)?.let { matched ->
                candidates.firstOrNull { it.label == matched }
            }
            ?: return mapOf("error" to "no pill device matches $spokenName")

        // A device hidden from pills must be enabled before pinning it. This mirrors Settings'
        // onSetPillEnabled path, including the related state entities discovered for that device.
        val rules = prefs.pillRules
        val index = rules.indexOfFirst { it.entityId == candidate.primary.entityId }
        val enabledRules = if (index >= 0) {
            rules.mapIndexed { ruleIndex, rule ->
                if (ruleIndex == index) rule.copy(enabled = true) else rule
            }
        } else {
            rules + PillSupport.defaultRule(candidate)
        }
        if (enabledRules != rules) {
            prefs.pillRules = enabledRules
            SettingsChangeBus.get().emit("pillRules")
        }

        val ref = PillRef.Device(candidate.primary.entityId)
        prefs.updateHomePillPreferences { current ->
            current.copy(pinnedOrder = pinFirst(current.pinnedOrder, ref))
        }
        return mapOf(
            "success" to true,
            "device" to candidate.label,
            "entity_id" to candidate.primary.entityId,
            "position" to "first",
        )
    }

    private fun setGrouping(raw: String): Map<String, Any?> {
        val mode = when (raw.lowercase()) {
            "room" -> HomeGroupingMode.BY_ROOM
            "type" -> HomeGroupingMode.BY_TYPE
            else -> return mapOf("error" to "grouping must be room or type")
        }
        prefs.updateHomePillPreferences { it.copy(groupingMode = mode) }
        return mapOf("success" to true, "grouping" to raw.lowercase())
    }

    private fun setHomePage(args: JSONObject): Map<String, Any?> {
        val enabled = args.requiredBoolean("enabled")
            ?: return mapOf("error" to "enabled is required")
        prefs.updateHomePillPreferences { it.copy(homePageEnabled = enabled) }
        return mapOf("success" to true, "home_page_enabled" to enabled)
    }

    private fun setScreenTimeout(args: JSONObject): Map<String, Any?> {
        if (!args.has("enabled") && !args.has("minutes")) {
            return mapOf("error" to "provide enabled or minutes")
        }
        val enabled = if (args.has("enabled")) args.requiredBoolean("enabled")
            ?: return mapOf("error" to "enabled must be a boolean") else null
        val minutes = if (args.has("minutes")) args.optInt("minutes", -1) else null
        if (minutes != null && minutes !in 1..240) {
            return mapOf("error" to "minutes must be between 1 and 240")
        }
        enabled?.let { prefs.screenTimeoutEnabled = it }
        minutes?.let { prefs.screenTimeoutMinutes = it }
        SleepScheduler.apply(context)
        return mapOf(
            "success" to true,
            "enabled" to prefs.screenTimeoutEnabled,
            "minutes" to prefs.screenTimeoutMinutes,
        )
    }

    private fun setAutoReturn(args: JSONObject): Map<String, Any?> {
        if (!args.has("enabled") && !args.has("seconds")) {
            return mapOf("error" to "provide enabled or seconds")
        }
        val enabled = if (args.has("enabled")) args.requiredBoolean("enabled")
            ?: return mapOf("error" to "enabled must be a boolean") else null
        val seconds = if (args.has("seconds")) args.optInt("seconds", -1) else null
        if (seconds != null && seconds !in 5..60) {
            return mapOf("error" to "seconds must be between 5 and 60")
        }
        enabled?.let { prefs.autoReturnEnabled = it }
        seconds?.let { prefs.autoReturnDelaySeconds = it }
        SettingsChangeBus.get().emit("autoReturn")
        return mapOf(
            "success" to true,
            "enabled" to prefs.autoReturnEnabled,
            "seconds" to prefs.autoReturnDelaySeconds,
        )
    }

    private fun setClockFormat(raw: String): Map<String, Any?> {
        val format24h = when (raw.lowercase()) {
            "24h" -> true
            "12h" -> false
            else -> return mapOf("error" to "clock_format must be 12h or 24h")
        }
        prefs.clockFormat24h = format24h
        SettingsChangeBus.get().emit("clockTheme")
        return mapOf("success" to true, "clock_format" to raw.lowercase())
    }

    private fun setGridScale(args: JSONObject): Map<String, Any?> {
        if (!args.has("grid_scale")) return mapOf("error" to "grid_scale is required")
        val scale = args.optDouble("grid_scale", Double.NaN)
        if (!scale.isFinite() || scale !in 0.7..1.3) {
            return mapOf("error" to "grid_scale must be between 0.7 and 1.3")
        }
        prefs.gridScale = scale.toFloat()
        SettingsChangeBus.get().emit("gridScale")
        return mapOf("success" to true, "grid_scale" to prefs.gridScale)
    }

    private fun setBackground(raw: String): Map<String, Any?> {
        if (raw !in setOf("system", "neutral", "custom", "immich")) {
            return mapOf("error" to "background must be system, neutral, custom or immich")
        }
        prefs.backgroundMode = raw
        SettingsChangeBus.get().emit("backgroundMode")
        return mapOf("success" to true, "background" to prefs.backgroundMode)
    }

    private fun setVoiceBehavior(args: JSONObject): Map<String, Any?> {
        if (!args.has("barge_in") && !args.has("idle_seconds") && !args.has("wake_sensitivity")) {
            return mapOf("error" to "provide barge_in, idle_seconds or wake_sensitivity")
        }
        val bargeIn = if (args.has("barge_in")) args.requiredBoolean("barge_in")
            ?: return mapOf("error" to "barge_in must be a boolean") else null
        val seconds = if (args.has("idle_seconds")) args.optInt("idle_seconds", -1) else null
        if (seconds != null && seconds !in 5..300) {
            return mapOf("error" to "idle_seconds must be between 5 and 300")
        }
        val sensitivity = if (args.has("wake_sensitivity")) args.optInt("wake_sensitivity", -1) else null
        if (sensitivity != null && sensitivity !in 1..95) {
            return mapOf("error" to "wake_sensitivity must be between 1 and 95")
        }
        bargeIn?.let { prefs.voiceBargeIn = it }
        seconds?.let { prefs.voiceAssistantIdleSeconds = it }
        sensitivity?.let { prefs.voiceAssistantThreshold = it }
        return mapOf(
            "success" to true,
            "barge_in" to prefs.voiceBargeIn,
            "idle_seconds" to prefs.voiceAssistantIdleSeconds,
            "wake_sensitivity" to prefs.voiceAssistantThreshold,
            "applies_next_session" to true,
        )
    }

    private fun openApp(spokenName: String): Map<String, Any?> {
        if (spokenName.isBlank()) return mapOf("error" to "missing app name")
        val pm = context.packageManager
        val launchable = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0,
        ).mapNotNull { resolved ->
            val activity = resolved.activityInfo ?: return@mapNotNull null
            PortalLaunchableApp(
                label = resolved.loadLabel(pm).toString().ifBlank { activity.packageName },
                packageName = activity.packageName,
                activityName = activity.name,
            )
        }.filterNot { it.packageName == context.packageName }

        val app = selectPortalApp(launchable, spokenName)
            ?: return mapOf("error" to "no installed app matches $spokenName")
        val intent = LauncherAppsFacade.launchIntent(context, app.packageName, app.activityName)
            ?: return mapOf("error" to "${app.label} cannot be launched")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching {
            DeviceStateHub.noteLaunchingApp(app.packageName, context)
            context.startActivity(intent)
        }.isSuccess
        return if (opened) {
            mapOf("success" to true, "app" to app.label, "package" to app.packageName)
        } else {
            mapOf("success" to false, "error" to "failed to open ${app.label}")
        }
    }

    companion object {
        const val ACTION_PIN_DEVICE = "pin_device"
        const val ACTION_SET_GROUPING = "set_device_grouping"
        const val ACTION_OPEN_APP = "open_app"
        const val ACTION_SET_HOME_PAGE = "set_home_page"
        const val ACTION_SET_SCREEN_TIMEOUT = "set_screen_timeout"
        const val ACTION_SET_AUTO_RETURN = "set_auto_return"
        const val ACTION_SET_CLOCK_FORMAT = "set_clock_format"
        const val ACTION_SET_GRID_SCALE = "set_grid_scale"
        const val ACTION_SET_BACKGROUND = "set_background"
        const val ACTION_SET_VOICE_BEHAVIOR = "set_voice_behavior"
        val ACTIONS = listOf(
            ACTION_PIN_DEVICE,
            ACTION_SET_GROUPING,
            ACTION_OPEN_APP,
            ACTION_SET_HOME_PAGE,
            ACTION_SET_SCREEN_TIMEOUT,
            ACTION_SET_AUTO_RETURN,
            ACTION_SET_CLOCK_FORMAT,
            ACTION_SET_GRID_SCALE,
            ACTION_SET_BACKGROUND,
            ACTION_SET_VOICE_BEHAVIOR,
        )
    }
}

private fun JSONObject.requiredBoolean(key: String): Boolean? =
    if (has(key) && opt(key) is Boolean) getBoolean(key) else null

internal data class PortalLaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
)

internal fun selectPortalApp(apps: List<PortalLaunchableApp>, query: String): PortalLaunchableApp? {
    apps.firstOrNull { it.packageName.equals(query.trim(), ignoreCase = true) }?.let { return it }
    val label = matchName(apps.map { it.label }, query) ?: return null
    return apps.firstOrNull { it.label == label }
}

internal fun pinFirst(current: List<PillRef>, target: PillRef): List<PillRef> =
    listOf(target) + current.filterNot { it == target }
