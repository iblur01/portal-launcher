package com.iblu01.portallauncher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import com.iblu01.portallauncher.ui.onboarding.OnboardingChannel
import com.iblu01.portallauncher.ui.onboarding.OnboardingCommand
import com.iblu01.portallauncher.ui.onboarding.OnboardingCommandError
import com.iblu01.portallauncher.ui.onboarding.OnboardingCommandResult
import com.iblu01.portallauncher.ui.onboarding.OnboardingCoordinator
import com.iblu01.portallauncher.ui.onboarding.OnboardingSnapshot
import com.iblu01.portallauncher.ui.onboarding.OnboardingStep
import com.iblu01.portallauncher.ui.onboarding.OnboardingLauncherPreview
import com.iblu01.portallauncher.ui.onboarding.OnboardingBehaviorConfig
import com.iblu01.portallauncher.ui.onboarding.OnboardingCapabilities
import com.iblu01.portallauncher.ui.onboarding.OnboardingClockConfig
import com.iblu01.portallauncher.ui.onboarding.Capability
import com.iblu01.portallauncher.ui.apps.installedIconPacks
import com.iblu01.portallauncher.ui.components.wallpaperFile
import com.iblu01.portallauncher.photo.OkHttpTransport
import com.iblu01.portallauncher.photo.DisplaySize
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.photo.immich.ImmichApiClient
import com.iblu01.portallauncher.voice.GeminiProbe
import com.iblu01.portallauncher.voice.WakeWordCatalog
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

enum class WebConfigSection { HOME_ASSISTANT, MQTT, VOICE, ALL }

/** Ephemeral visual values: they are rendered on both surfaces but never written to [Prefs]. */
typealias WebLauncherPreview = OnboardingLauncherPreview

internal fun webLanguage(acceptLanguage: String?, appLanguage: String, requestedLanguage: String? = null): String {
    requestedLanguage?.lowercase()?.takeIf { it == "en" || it == "fr" }?.let { return it }
    val browserLanguages = acceptLanguage.orEmpty().split(',').map { it.substringBefore(';').trim().substringBefore('-').lowercase() }
    return browserLanguages.firstOrNull { it == "en" || it == "fr" }
        ?: appLanguage.substringBefore('-').lowercase().takeIf { it == "en" || it == "fr" }
        ?: "en"
}

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

/** The configuration read model is intentionally incapable of serializing stored secrets. */
internal fun webConfigJson(prefs: Prefs): String = JSONObject()
    .put("ha_url", prefs.haUrl)
    .put("ha_token_configured", prefs.haToken.isNotBlank())
    .put("broker_host", prefs.brokerHost)
    .put("broker_port", prefs.brokerPort)
    .put("username", prefs.username)
    .put("mqtt_password_configured", prefs.password.isNotBlank())
    .put("device_name", prefs.deviceName)
    .put("voice_enabled", prefs.voiceAssistantEnabled)
    .put("voice_gemini_key_configured", prefs.hasVoiceGeminiApiKey)
    .put("voice_gemini_model", prefs.voiceGeminiModel)
    .put("voice_gemini_voice", prefs.voiceGeminiVoice)
    .put("voice_gemini_prompt", prefs.voiceGeminiPrompt)
    .put("voice_barge_in", prefs.voiceBargeIn)
    .put("voice_wake_word", prefs.voiceAssistantWakeWord)
    .put("voice_threshold", prefs.voiceAssistantThreshold)
    .put("voice_idle_seconds", prefs.voiceAssistantIdleSeconds)
    .put("voice_daily_limit", prefs.voiceDailySessionLimit)
    .toString()

/** Strict mutation envelope. Reads never need it; every write must provide both values. */
internal fun webCommandEnvelope(payload: JSONObject): Pair<String, Long>? {
    if (!payload.has("session_id") || !payload.has("expected_revision")) return null
    val sessionId = payload.optString("session_id")
    val revisionValue = payload.opt("expected_revision")
    if (sessionId.length !in 8..128 || sessionId.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) {
        return null
    }
    val revision = when (revisionValue) {
        is Byte -> revisionValue.toLong()
        is Short -> revisionValue.toLong()
        is Int -> revisionValue.toLong()
        is Long -> revisionValue
        else -> return null
    }
    if (revision < 0L) return null
    return sessionId to revision
}

internal fun strictBoolean(payload: JSONObject, key: String): Boolean? =
    payload.opt(key).takeIf { it is Boolean } as? Boolean

internal fun strictString(payload: JSONObject, key: String): String? =
    payload.opt(key).takeIf { it is String } as? String

internal fun strictInt(payload: JSONObject, key: String): Int? = when (val value = payload.opt(key)) {
    is Byte -> value.toInt()
    is Short -> value.toInt()
    is Int -> value
    is Long -> value.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
    else -> null
}

internal fun strictFloat(payload: JSONObject, key: String): Float? = when (val value = payload.opt(key)) {
    is Number -> value.toDouble().takeIf(Double::isFinite)?.toFloat()
    else -> null
}

internal data class MqttTestCredentials(val username: String, val password: String)

internal data class WebInstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val protected: Boolean,
)

internal fun installedApps(prefs: Prefs): List<WebInstalledApp> {
    val context = prefs.context
    val pm = context.packageManager
    val settingsPackage = Intent(Settings.ACTION_SETTINGS).resolveActivity(pm)?.packageName
    val protectedPackages = setOfNotNull(
        context.packageName,
        settingsPackage,
        prefs.homeAssistantPackage.takeIf(String::isNotBlank),
        prefs.haCompanionPackage.takeIf(String::isNotBlank),
    )
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val application = runCatching { pm.getApplicationInfo(activity.packageName, 0) }.getOrNull()
            WebInstalledApp(
                packageName = activity.packageName,
                label = info.loadLabel(pm).toString().ifBlank { activity.packageName },
                system = application != null && application.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                protected = activity.packageName in protectedPackages,
            )
        }
        .distinctBy(WebInstalledApp::packageName)
        .sortedBy { it.label.lowercase() }
}

internal fun installedAppsJson(prefs: Prefs): JSONArray {
    val order = prefs.appOrder.withIndex().associate { it.value to it.index }
    return JSONArray().also { array ->
        installedApps(prefs).sortedWith(
            compareBy<WebInstalledApp> { order[it.packageName] ?: Int.MAX_VALUE }
                .thenBy { it.label.lowercase() }
        ).forEachIndexed { index, app ->
            array.put(JSONObject()
                .put("package", app.packageName)
                .put("label", app.label)
                .put("system", app.system)
                .put("protected", app.protected)
                .put("hidden", app.packageName in prefs.hiddenApps)
                .put("order", index))
        }
    }
}

/** Validates and atomically transcodes a browser upload into the launcher's existing wallpaper. */
internal fun writeWebWallpaper(context: android.content.Context, bytes: ByteArray): Boolean {
    if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return false
    if (!hasSupportedImageSignature(bytes)) return false
    val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 64..12_000 || bounds.outHeight !in 64..12_000) return false
    if (bounds.outWidth.toLong() * bounds.outHeight.toLong() > 36_000_000L) return false
    var sample = 1
    while (bounds.outWidth / sample > 3_840 || bounds.outHeight / sample > 3_840) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
    ) ?: return false
    val target = wallpaperFile(context)
    val staging = File(context.filesDir, "wallpaper.jpg.web.tmp")
    return runCatching {
        staging.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
        check(staging.length() in 1..8L * 1024 * 1024)
        check(staging.renameTo(target) || (target.delete() && staging.renameTo(target)))
    }.onFailure {
        bitmap.recycle()
        staging.delete()
    }.isSuccess
}

/** BitmapFactory accepts more formats than the Web contract; keep the upload allow-list exact. */
internal fun hasSupportedImageSignature(bytes: ByteArray): Boolean {
    val jpeg = bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte()
    val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    )
    val webp = bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())
    return jpeg || png || webp
}

internal fun detectedImageMime(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
        bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    ) -> "image/png"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
        bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> "image/webp"
    else -> null
}

/** A blank password only means "reuse" when the broker identity is byte-for-byte unchanged. */
internal fun mqttTestCredentials(
    requestedHost: String,
    requestedPort: Int,
    requestedUsername: String,
    requestedPassword: String,
    persistedHost: String,
    persistedPort: Int,
    persistedUsername: String,
    persistedPassword: String,
): MqttTestCredentials {
    val identityMatches = requestedHost == persistedHost &&
        requestedPort == persistedPort && requestedUsername == persistedUsername
    return MqttTestCredentials(
        username = requestedUsername,
        password = requestedPassword.takeIf(String::isNotBlank)
            ?: persistedPassword.takeIf { identityMatches }.orEmpty(),
    )
}

/**
 * Short-lived HTTP server that lets a phone on the same LAN fill in this panel's configuration.
 *
 * It accepts Home Assistant, MQTT and Gemini credentials but never returns their values. Every
 * route still requires the [token] minted at construction and carried in the QR code's URL (`?t=`):
 * fresh per server instance, compared in constant time, and never logged.
 */
class WebConfigServer private constructor(
    port: Int,
    private val prefs: Prefs,
    private val onMqttConfigChanged: () -> Unit,
    private val onConfigSaved: (WebConfigSection) -> Unit,
    private val onOnboardingComplete: () -> Unit,
    private val onBrowserConnected: (OnboardingSnapshot) -> Unit,
    private val onLauncherPreview: (WebLauncherPreview?) -> Boolean,
    private val onSystemAction: (String) -> Boolean,
    private val onScreenCapture: () -> ByteArray?,
) : NanoHTTPD(BIND_ADDRESS, port) {

    val token: String = mintToken()
    private val onboarding = OnboardingCoordinator(prefs, previewApplier = onLauncherPreview)

    /** Called with the activity lifecycle: a stopped host cannot own a browser editor lease. */
    fun releaseOnboardingEditor() {
        onboarding.releaseEditorLease()
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.ifEmpty { "/" }
        val language = webLanguage(
            acceptLanguage = session.headers["accept-language"],
            appLanguage = prefs.appLanguage,
            requestedLanguage = session.parameters["lang"]?.firstOrNull(),
        )
        when (uri) {
            "/webconfig.css" -> return css(WebConfigPage.asset("webconfig.css"))
            "/access.js" -> return javascript(WebConfigPage.asset("access.js", language))
            "/config.js" -> return javascript(WebConfigPage.asset("config.js", language))
            "/logo-home-assistant.svg" -> return svg(WebConfigPage.asset("logo-home-assistant.svg"))
            "/logo-mqtt.svg" -> return svg(WebConfigPage.asset("logo-mqtt.svg"))
            "/logo-gemini.svg" -> return svg(WebConfigPage.asset("logo-gemini.svg"))
        }
        val providedToken = session.parameters["t"]?.firstOrNull()
        if (!tokenMatches(providedToken)) {
            return if (uri == "/" && session.method == Method.GET) {
                html(WebConfigPage.renderAccess(invalidCode = !providedToken.isNullOrBlank(), language = language))
            } else {
                errorJson(Response.Status.FORBIDDEN, "forbidden")
            }
        }
        if (uri == "/api/onboarding/screen" && session.method == Method.GET) {
            val bytes = onScreenCapture()
                ?: return errorJson(Response.Status.SERVICE_UNAVAILABLE, "screen_capture_unavailable")
            return noStore(
                newFixedLengthResponse(
                    Response.Status.OK,
                    "image/png",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                )
            )
        }
        return runCatching {
            when {
                uri == "/" -> html(WebConfigPage.render(token, language))
                uri == "/api/config" && session.method == Method.GET -> json(configJson())
                uri == "/api/launcher" && session.method == Method.GET -> json(launcherJson().toString())
                uri == "/api/ha/catalog" && session.method == Method.GET -> homeAssistantCatalog()
                uri.startsWith("/api/ha/brand/") && session.method == Method.GET ->
                    homeAssistantBrand(uri.substringAfterLast('/'))
                uri == "/api/background/custom" && session.method == Method.GET -> customBackgroundResponse()
                uri == "/api/onboarding" && session.method == Method.GET -> onboardingSnapshotResponse(session)
                uri == "/api/onboarding/events" && session.method == Method.GET -> onboardingEventsResponse(session)
                uri == "/api/health" && session.method == Method.GET -> json("""{"ok":true}""")
                uri == "/api/config/ha" && session.method == Method.POST -> applyHomeAssistantConfig(readBody(session))
                uri == "/api/config/mqtt" && session.method == Method.POST -> applyMqttConfig(readBody(session))
                uri == "/api/config/voice" && session.method == Method.POST -> applyVoiceConfig(readBody(session))
                uri == "/api/config/background" && session.method == Method.POST -> applyBackgroundConfig(readBody(session))
                uri == "/api/config/clock" && session.method == Method.POST -> applyClockConfig(readBody(session))
                uri == "/api/config/apps" && session.method == Method.POST -> applyAppsConfig(readBody(session))
                uri == "/api/config/behavior" && session.method == Method.POST -> applyBehaviorConfig(readBody(session))
                uri == "/api/config/home" && session.method == Method.POST -> applyHomeConfig(readBody(session))
                uri == "/api/background/upload" && session.method == Method.POST -> uploadBackground(readBody(session))
                uri == "/api/test-immich" && session.method == Method.POST -> testImmich(readBody(session))
                uri == "/api/system/action" && session.method == Method.POST -> openSystemAction(readBody(session))
                uri == "/api/test-ha" && session.method == Method.POST -> testHomeAssistant(readBody(session))
                uri == "/api/test-mqtt" && session.method == Method.POST -> testMqtt(readBody(session))
                uri == "/api/test-voice" && session.method == Method.POST -> testVoice(readBody(session))
                uri == "/api/onboarding/command" && session.method == Method.POST -> onboardingCommand(readBody(session))
                uri == "/api/onboarding/complete" && session.method == Method.POST -> completeOnboarding(readBody(session))
                uri == "/api/onboarding/reset" && session.method == Method.POST -> resetOnboarding(readBody(session))
                else -> text(Response.Status.NOT_FOUND, "not found")
            }
        }.getOrElse { failure ->
            // Message only: an exception from a config route can carry a URL or a credential.
            Log.w(TAG, "request to $uri failed: ${failure.javaClass.simpleName}")
            errorJson(Response.Status.INTERNAL_ERROR, "server_error")
        }
    }

    private fun configJson(): String = webConfigJson(prefs)

    private fun onboardingSnapshotResponse(session: IHTTPSession): Response {
        val snapshot = onboarding.snapshot()
        onBrowserConnected(snapshot)
        return json(snapshotJson(snapshot, editorOwned(session)).put("ok", true).toString())
    }

    /**
     * A deliberately short SSE response. EventSource reconnects using the advertised retry while
     * clients without EventSource use the same snapshot through polling. Keeping responses short
     * avoids pinning a NanoHTTPD worker for the whole onboarding session.
     */
    private fun onboardingEventsResponse(session: IHTTPSession): Response {
        val snapshot = onboarding.snapshot()
        onBrowserConnected(snapshot)
        val event = "retry: 1000\ndata: ${snapshotJson(snapshot, editorOwned(session)).put("ok", true)}\n\n"
        return noStore(newFixedLengthResponse(Response.Status.OK, "text/event-stream; charset=utf-8", event)).apply {
            addHeader("Connection", "close")
        }
    }

    private fun onboardingCommand(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val commandName = strictString(payload, "command")
            ?: return errorJson(Response.Status.BAD_REQUEST, "command_required")
        val command = when (commandName) {
            "preview_launcher" -> return previewLauncher(payload)
            "revert_launcher" -> return revertLauncher(payload)
            "save_launcher" -> {
                val scale = strictFloat(payload, "grid_scale")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_grid_scale")
                val mode = strictString(payload, "background_mode")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_mode")
                val opacity = strictFloat(payload, "background_opacity")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_opacity")
                if (payload.has("preview_id") && strictString(payload, "preview_id") == null) {
                    return errorJson(Response.Status.BAD_REQUEST, "invalid_preview_id")
                }
                if (payload.has("preview_sequence") && strictLong(payload, "preview_sequence") == null) {
                    return errorJson(Response.Status.BAD_REQUEST, "invalid_preview_sequence")
                }
                OnboardingCommand.SaveLauncher(
                    gridScale = scale,
                    backgroundMode = mode,
                    backgroundOpacity = opacity,
                    previewId = strictString(payload, "preview_id")?.takeIf(String::isNotBlank),
                    previewSequence = strictLong(payload, "preview_sequence"),
                )
            }
            "select_providers" -> {
                val ha = strictBoolean(payload, "ha_selected")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_ha_selected")
                val mqtt = strictBoolean(payload, "mqtt_selected")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_selected")
                val gemini = strictBoolean(payload, "gemini_selected")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_gemini_selected")
                OnboardingCommand.SelectProviders(ha, mqtt, gemini)
            }
            "navigate" -> {
                val step = runCatching { OnboardingStep.valueOf(payload.optString("step")) }.getOrNull()
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_step")
                OnboardingCommand.Navigate(step)
            }
            "save_background" -> return applyBackgroundConfig(body)
            "save_clock" -> return applyClockConfig(body)
            "save_apps" -> return applyAppsConfig(body)
            "save_behavior" -> return applyBehaviorConfig(body)
            "skip_ha" -> OnboardingCommand.SkipHomeAssistant
            "skip_mqtt" -> OnboardingCommand.SkipMqtt
            "save_finish_options" -> OnboardingCommand.SaveFinishOptions(
                skipAppCleanup = strictBoolean(payload, "skip_app_cleanup")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_skip_app_cleanup"),
                gesturesSeen = strictBoolean(payload, "gestures_seen")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_gestures_seen"),
            )
            else -> return errorJson(Response.Status.BAD_REQUEST, "invalid_command")
        }
        return executeOnboarding(payload, command)
    }

    private fun completeOnboarding(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        if (strictBoolean(payload, "confirm_finish") != true) {
            return errorJson(Response.Status.BAD_REQUEST, "confirmation_required")
        }
        val response = executeOnboarding(
            payload,
            OnboardingCommand.Complete(
                defaultsWarningAccepted = strictBoolean(payload, "defaults_warning_accepted")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_defaults_warning_accepted"),
            ),
        )
        if (response.status == Response.Status.OK) onOnboardingComplete()
        return response
    }

    private fun resetOnboarding(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        if (payload.optString("confirmation") != "RESET") {
            return errorJson(Response.Status.BAD_REQUEST, "confirmation_required")
        }
        return executeOnboarding(
            payload,
            OnboardingCommand.ResetProgress(confirmed = true, channel = OnboardingChannel.WEB),
        )
    }

    private fun executeOnboarding(payload: JSONObject, command: OnboardingCommand): Response {
        val envelope = webCommandEnvelope(payload) ?: return errorJson(
            Response.Status.BAD_REQUEST,
            envelopeError(payload),
        )
        val (sessionId, expectedRevision) = envelope
        val takeOver = when {
            !payload.has("take_over") -> false
            else -> strictBoolean(payload, "take_over")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_take_over")
        }
        return when (val result = onboarding.execute(
            sessionId = sessionId,
            expectedRevision = expectedRevision,
            command = command,
            takeOver = takeOver,
        )) {
            is OnboardingCommandResult.Applied -> {
                onBrowserConnected(result.snapshot)
                json(snapshotJson(result.snapshot, editorOwnedBy = true).put("ok", true).also {
                    if (command is OnboardingCommand.PreviewLauncher ||
                        command is OnboardingCommand.SaveLauncher ||
                        command is OnboardingCommand.RevertLauncher
                    ) it.put("device_ack", true)
                }.toString())
            }
            is OnboardingCommandResult.Rejected -> {
                val status = if (
                    result.error == OnboardingCommandError.REVISION_CONFLICT ||
                    result.error == OnboardingCommandError.EDITOR_ALREADY_ACTIVE
                ) Response.Status.CONFLICT else Response.Status.BAD_REQUEST
                onboardingErrorJson(status, result.error, result.snapshot)
            }
        }
    }

    private fun previewLauncher(payload: JSONObject): Response {
        val previewId = strictString(payload, "preview_id")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_preview_id")
        val scale = strictFloat(payload, "grid_scale")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_grid_scale")
        val mode = strictString(payload, "background_mode")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_mode")
        val opacity = strictFloat(payload, "background_opacity")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_opacity")
        val clock = if (payload.has("clock")) {
            val clockPayload = payload.opt("clock") as? JSONObject
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_clock")
            parseClock(clockPayload) ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_clock")
        } else null
        return executeOnboarding(payload, OnboardingCommand.PreviewLauncher(previewId, scale, mode, opacity, clock))
    }

    private fun revertLauncher(payload: JSONObject): Response = executeOnboarding(
        payload,
        OnboardingCommand.RevertLauncher(
            strictString(payload, "preview_id").orEmpty(),
            strictLong(payload, "preview_sequence"),
        ),
    )

    private fun envelopeError(payload: JSONObject): String = when {
        !payload.has("session_id") -> "session_id_required"
        !payload.has("expected_revision") -> "expected_revision_required"
        strictLong(payload, "expected_revision")?.takeIf { it >= 0L } == null ->
            "invalid_expected_revision"
        else -> "invalid_session"
    }

    private fun snapshotJson(snapshot: OnboardingSnapshot, editorOwnedBy: Boolean = false): JSONObject = JSONObject()
        .put("version", snapshot.version)
        .put("revision", snapshot.revision)
        .put("step", snapshot.step.name)
        .put("channel", snapshot.channel?.name ?: JSONObject.NULL)
        .put("completed", snapshot.completed)
        .put("grid_scale", snapshot.gridScale)
        .put("background_mode", snapshot.backgroundMode)
        .put("background_opacity", snapshot.backgroundOpacity)
        .put("custom_background_configured", snapshot.customBackgroundConfigured)
        .put("immich", JSONObject()
            .put("url", snapshot.immich.url)
            .put("key_configured", snapshot.immich.keyConfigured)
            .put("album_ids", JSONArray(snapshot.immich.albumIds))
            .put("allow_insecure", snapshot.immich.allowInsecure)
            .put("shuffle", snapshot.immich.shuffle)
            .put("refresh_minutes", snapshot.immich.refreshMinutes)
            .put("cadence_seconds", snapshot.immich.cadenceSeconds))
        .put("clock", clockJson(snapshot.clock))
        .put("hidden_apps", JSONArray(snapshot.hiddenApps.toList()))
        .put("app_order", JSONArray(snapshot.appOrder))
        .put("icon_pack", snapshot.iconPack)
        .put("notification_dots", snapshot.notificationDots)
        .put("home_assistant_package", snapshot.homeAssistantPackage)
        .put("tap_app_package", snapshot.tapAppPackage)
        .put("behavior", JSONObject()
            .put("keep_screen_on", snapshot.behavior.keepScreenOn)
            .put("screen_timeout_enabled", snapshot.behavior.screenTimeoutEnabled)
            .put("screen_timeout_minutes", snapshot.behavior.screenTimeoutMinutes)
            .put("auto_return_enabled", snapshot.behavior.autoReturnEnabled)
            .put("auto_return_delay_seconds", snapshot.behavior.autoReturnDelaySeconds))
        .put("ha_configured", snapshot.homeAssistant.configured)
        .put("ha_skipped", snapshot.flags.homeAssistantSkipped)
        .put("mqtt_configured", snapshot.mqtt.configured)
        .put("mqtt_skipped", snapshot.flags.mqttSkipped)
        .put("gemini_configured", snapshot.gemini.configured)
        .put("ha_selected", snapshot.homeAssistantSelected)
        .put("mqtt_selected", snapshot.mqttSelected)
        .put("gemini_selected", snapshot.geminiSelected)
        .put("gemini_enabled", snapshot.geminiEnabled)
        .put("disabled_ha_integrations", JSONArray(snapshot.disabledHaIntegrations.sorted()))
        .put("pill_rules", JSONArray(PillRuleCodec.encode(snapshot.pillRules)))
        .put("home", JSONObject(HomePillPreferencesCodec.encode(snapshot.homePillPreferences)))
        .put("cameras", JSONObject(CameraPreferencesCodec.encode(snapshot.cameraPreferences)))
        .put("gemini_prompt", snapshot.geminiPrompt)
        .put("gemini_barge_in", snapshot.geminiBargeIn)
        .put("gemini_wake_word", snapshot.geminiWakeWord)
        .put("gemini_threshold", snapshot.geminiThreshold)
        .put("gemini_idle_seconds", snapshot.geminiIdleSeconds)
        .put("gemini_daily_limit", snapshot.geminiDailyLimit)
        .put("gemini_calibrated", snapshot.geminiCalibrated)
        .put("gemini_wake_words", JSONArray(WakeWordCatalog.available(prefs.context)))
        .put("app_cleanup_skipped", snapshot.flags.appCleanupSkipped)
        .put("gestures_seen", snapshot.gesturesSeen)
        .put("editor_active", snapshot.editorActive)
        .put("editor_owned", editorOwnedBy)
        .put("warnings", org.json.JSONArray(snapshot.warnings.map { it.name }))
        .put("preview", snapshot.launcherPreview?.let(::previewJson) ?: JSONObject.NULL)

    private fun editorOwned(session: IHTTPSession): Boolean =
        session.parameters["session_id"]?.firstOrNull()?.let(onboarding::editorOwnedBy) == true

    private fun strictLong(payload: JSONObject, key: String): Long? = when (val value = payload.opt(key)) {
        is Byte -> value.toLong()
        is Short -> value.toLong()
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }

    private fun previewJson(preview: WebLauncherPreview): JSONObject = JSONObject()
        .put("id", preview.id)
        .put("sequence", preview.sequence)
        .put("grid_scale", preview.gridScale)
        .put("background_mode", preview.backgroundMode)
        .put("background_opacity", preview.backgroundOpacity)
        .put("clock", preview.clock?.let(::clockJson) ?: JSONObject.NULL)

    private fun clockJson(clock: OnboardingClockConfig): JSONObject = JSONObject()
        .put("font", clock.font).put("weight", clock.weight).put("size", clock.size)
        .put("letter_spacing", clock.letterSpacing).put("tint", clock.tint)
        .put("format_24h", clock.format24h).put("date_format", clock.dateFormat)
        .put("element_spacing", clock.elementSpacing)

    private fun onboardingErrorJson(
        status: Response.Status,
        error: OnboardingCommandError,
        snapshot: OnboardingSnapshot,
    ) = noStore(
        newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            snapshotJson(snapshot)
                .put("ok", false)
                .put("error", error.name.lowercase())
                .toString(),
        )
    )

    private fun applyHomeAssistantConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val response = executeOnboarding(
            payload,
            OnboardingCommand.SaveHomeAssistant(
                url = payload.optString("ha_url"),
                token = payload.optString("ha_token").trim().takeIf { it.isNotBlank() }
                    ?.let(com.iblu01.portallauncher.ui.onboarding.SecretInput::of),
            ),
        )
        if (response.status == Response.Status.OK) onConfigSaved(WebConfigSection.HOME_ASSISTANT)
        return response
    }

    /**
     * Voice assistant credentials. The key is the only required field: the model and the voice
     * have working defaults, and blanking them here must not wipe them in prefs — a phone that
     * only came to paste a key should not silently reset the rest.
     */
    private fun applyVoiceConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val enabled = strictBoolean(payload, "voice_enabled")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_enabled")
        val bargeIn = strictBoolean(payload, "voice_barge_in")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_barge_in")
        val response = executeOnboarding(
            payload,
            OnboardingCommand.SaveGemini(
                enabled = enabled,
                apiKey = payload.optString("voice_gemini_key").trim().takeIf { it.isNotBlank() }
                    ?.let(com.iblu01.portallauncher.ui.onboarding.SecretInput::of),
                model = payload.optString("voice_gemini_model", prefs.voiceGeminiModel),
                voice = payload.optString("voice_gemini_voice", prefs.voiceGeminiVoice),
                bargeIn = bargeIn,
                prompt = strictString(payload, "voice_gemini_prompt")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_prompt"),
                wakeWord = strictString(payload, "voice_wake_word")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_wake_word"),
                threshold = strictInt(payload, "voice_threshold")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_threshold"),
                idleSeconds = strictInt(payload, "voice_idle_seconds")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_idle"),
                dailyLimit = strictInt(payload, "voice_daily_limit")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_voice_daily_limit"),
            ),
        )
        if (response.status == Response.Status.OK) onConfigSaved(WebConfigSection.VOICE)
        return response
    }

    private fun applyHomeConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val disabled = strictStringArray(payload, "disabled_integrations")?.toSet()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_disabled_integrations")
        val rules = (payload.opt("pill_rules") as? JSONArray)?.let { PillRuleCodec.decode(it.toString()) }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_pill_rules")
        val home = (payload.opt("home") as? JSONObject)?.let { HomePillPreferencesCodec.decode(it.toString()) }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_home_configuration")
        val cameras = (payload.opt("cameras") as? JSONObject)?.let { CameraPreferencesCodec.decode(it.toString()) }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_camera_configuration")
        return executeOnboarding(payload, OnboardingCommand.SaveHomeConfiguration(disabled, rules, home, cameras))
            .also { if (it.status == Response.Status.OK) onConfigSaved(WebConfigSection.HOME_ASSISTANT) }
    }

    /** Read-only catalog. Stored credentials stay on-device and never cross the LAN. */
    private fun homeAssistantCatalog(): Response {
        if (prefs.haUrl.isBlank() || prefs.haToken.isBlank()) {
            return errorJson(Response.Status.PRECONDITION_FAILED, "home_assistant_not_configured")
        }
        val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
        if (!result.ok) return errorJson(Response.Status.SERVICE_UNAVAILABLE, "home_assistant_unreachable")
        val states = runCatching { JSONArray(result.body.orEmpty()) }.getOrNull()
            ?: return errorJson(Response.Status.SERVICE_UNAVAILABLE, "home_assistant_invalid_response")
        val entities = JSONArray()
        val counts = linkedMapOf<String, Int>()
        for (index in 0 until states.length()) {
            val source = states.optJSONObject(index) ?: continue
            val id = source.optString("entity_id")
            if (!id.contains('.')) continue
            val domain = id.substringBefore('.').lowercase()
            counts[domain] = (counts[domain] ?: 0) + 1
            val attributes = source.optJSONObject("attributes") ?: JSONObject()
            entities.put(JSONObject()
                .put("entity_id", id)
                .put("name", attributes.optString("friendly_name").ifBlank { id.substringAfter('.') })
                .put("domain", domain)
                .put("integration", domain)
                .put("room", attributes.optString("area_id"))
                .put("device_class", attributes.optString("device_class"))
                .put("state", source.optString("state"))
                .put("available", source.optString("state") != "unavailable")
                .put("last_changed", source.optString("last_changed")))
        }
        val integrations = JSONArray()
        counts.toSortedMap().forEach { (domain, count) -> integrations.put(JSONObject()
            .put("id", domain).put("name", domain.replace('_', ' '))
            .put("entity_count", count).put("brand_url", "/api/ha/brand/$domain")) }
        return json(JSONObject().put("ok", true).put("stale", false)
            .put("integrations", integrations).put("entities", entities).toString())
    }

    /** Same-origin, allowlisted proxy for official HA brand thumbnails. */
    private fun homeAssistantBrand(domain: String): Response {
        if (domain.length !in 1..64 || domain.any { !it.isLowerCase() && !it.isDigit() && it != '_' }) {
            return text(Response.Status.BAD_REQUEST, "invalid brand")
        }
        return runCatching {
            val connection = URL("https://brands.home-assistant.io/$domain/icon.png").openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) return@runCatching text(Response.Status.NOT_FOUND, "not found")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.size > 256 * 1024) return@runCatching text(Response.Status.BAD_REQUEST, "brand too large")
            noStore(newFixedLengthResponse(Response.Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong()))
        }.getOrElse { text(Response.Status.SERVICE_UNAVAILABLE, "brand unavailable") }
    }

    private fun applyMqttConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val before = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)
        val host = strictString(payload, "broker_host")?.trim()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_host")
        val port = strictInt(payload, "broker_port")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_port")
        val username = strictString(payload, "username")?.trim()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_username")
        val password = strictString(payload, "password")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_password")
        val deviceName = strictString(payload, "device_name")?.trim()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_device_name")
        val sameBrokerIdentity = host == prefs.brokerHost && port == prefs.brokerPort && username == prefs.username
        val response = executeOnboarding(
            payload,
            OnboardingCommand.SaveMqtt(
                host = host,
                port = port,
                authEnabled = username.isNotBlank(),
                username = username,
                password = password.takeIf { it.isNotBlank() }
                    ?.let(com.iblu01.portallauncher.ui.onboarding.SecretInput::of),
                preservePassword = password.isBlank() && sameBrokerIdentity,
                deviceName = deviceName.ifBlank { prefs.deviceName },
            ),
        )
        val after = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)
        if (response.status == Response.Status.OK) {
            if (after != before) onMqttConfigChanged()
            onConfigSaved(WebConfigSection.MQTT)
        }
        return response
    }

    private fun testHomeAssistant(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val rawUrl = payload.optString("ha_url").trim().trimEnd('/')
        val parsed = runCatching { URL(rawUrl) }.getOrNull()
            ?.takeIf { it.protocol == "http" || it.protocol == "https" }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_ha_url")
        val host = parsed.host.takeIf { it.isNotBlank() }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_ha_url")
        val port = if (parsed.port != -1) parsed.port else parsed.defaultPort

        return when (payload.optString("stage")) {
            "host" -> {
                val reachable = runCatching { InetAddress.getAllByName(host).isNotEmpty() }.getOrDefault(false)
                if (reachable) json("""{"ok":true,"stage":"host"}""")
                else errorJson(Response.Status.BAD_REQUEST, "host_unresolved")
            }
            "port" -> {
                val reachable = runCatching {
                    Socket().use { it.connect(InetSocketAddress(host, port), HA_TEST_TIMEOUT_MS) }
                    true
                }.getOrDefault(false)
                if (reachable) json("""{"ok":true,"stage":"port"}""")
                else errorJson(Response.Status.BAD_REQUEST, "port_unreachable")
            }
            "token" -> {
                val accessToken = payload.optString("ha_token").trim()
                if (accessToken.isBlank()) return errorJson(Response.Status.BAD_REQUEST, "token_required")
                val result = HaApiClient(rawUrl, accessToken).testConnection()
                when {
                    result.ok -> json("""{"ok":true,"stage":"token"}""")
                    result.statusCode == 401 || result.statusCode == 403 ->
                        errorJson(Response.Status.BAD_REQUEST, "token_rejected")
                    else -> errorJson(Response.Status.BAD_REQUEST, "api_unreachable")
                }
            }
            else -> errorJson(Response.Status.BAD_REQUEST, "invalid_stage")
        }
    }

    private fun testMqtt(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val host = strictString(payload, "broker_host")?.trim()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_host")
        val port = strictInt(payload, "broker_port")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_port")
        if (host.isBlank()) return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_host")
        if (port !in 1..65535) return errorJson(Response.Status.BAD_REQUEST, "invalid_broker_port")

        return when (payload.optString("stage")) {
            "host" -> {
                val reachable = runCatching { InetAddress.getAllByName(host).isNotEmpty() }.getOrDefault(false)
                if (reachable) json("""{"ok":true,"stage":"host"}""")
                else errorJson(Response.Status.BAD_REQUEST, "mqtt_host_unresolved")
            }
            "port" -> {
                val reachable = runCatching {
                    Socket().use { it.connect(InetSocketAddress(host, port), HA_TEST_TIMEOUT_MS) }
                    true
                }.getOrDefault(false)
                if (reachable) json("""{"ok":true,"stage":"port"}""")
                else errorJson(Response.Status.BAD_REQUEST, "mqtt_port_unreachable")
            }
            "auth" -> {
                val requestedUsername = strictString(payload, "username")?.trim()
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_username")
                val requestedPassword = strictString(payload, "password")
                    ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_mqtt_password")
                val credentials = mqttTestCredentials(
                    requestedHost = host,
                    requestedPort = port,
                    requestedUsername = requestedUsername,
                    requestedPassword = requestedPassword,
                    persistedHost = prefs.brokerHost,
                    persistedPort = prefs.brokerPort,
                    persistedUsername = prefs.username,
                    persistedPassword = prefs.password,
                )
                val connected = runCatching {
                    val client = MqttClient(
                        "tcp://$host:$port",
                        "portal-web-test-${System.currentTimeMillis()}",
                        MemoryPersistence(),
                    )
                    client.timeToWait = 8_000L
                    try {
                        client.connect(MqttConnectOptions().apply {
                            isCleanSession = true
                            connectionTimeout = 6
                            keepAliveInterval = 10
                            credentials.username.takeIf { it.isNotEmpty() }?.let {
                                userName = it
                                password = credentials.password.toCharArray()
                            }
                        })
                        true
                    } finally {
                        if (client.isConnected) client.disconnect(1_000)
                        client.close()
                    }
                }.getOrDefault(false)
                if (connected) json("""{"ok":true,"stage":"auth"}""")
                else errorJson(Response.Status.BAD_REQUEST, "mqtt_auth_failed")
            }
            else -> errorJson(Response.Status.BAD_REQUEST, "invalid_stage")
        }
    }

    /**
     * Two stages, mirroring the Home Assistant test: the key is accepted, then the named model
     * can actually hold a Live session. The second is not pedantry — Google retires these preview
     * names on its own schedule, and a panel configured with a dead one fails silently at the
     * next wake word rather than here.
     */
    private fun testVoice(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val key = payload.optString("voice_gemini_key").trim()
        if (key.isBlank()) return errorJson(Response.Status.BAD_REQUEST, "voice_key_required")

        return when (val result = GeminiProbe.listModels(key)) {
            is GeminiProbe.Result.Unauthorized -> errorJson(Response.Status.BAD_REQUEST, "voice_key_rejected")
            is GeminiProbe.Result.Unreachable -> errorJson(Response.Status.BAD_REQUEST, "voice_unreachable")
            is GeminiProbe.Result.Ok -> when (payload.optString("stage")) {
                "key" -> json(JSONObject().put("ok", true).put("stage", "key")
                    .put("models", JSONArray(result.live.sorted())).toString())
                "model" -> {
                    val model = payload.optString("voice_gemini_model").trim()
                        .ifBlank { DEFAULT_GEMINI_LIVE_MODEL }
                    when {
                        GeminiProbe.supportsLive(result, model) -> json("""{"ok":true,"stage":"model"}""")
                        // Named a real model that cannot do speech-to-speech: a different
                        // mistake from a name that does not exist at all, and worth saying so.
                        GeminiProbe.exists(result, model) ->
                            errorJson(Response.Status.BAD_REQUEST, "voice_model_not_live")
                        else -> errorJson(Response.Status.BAD_REQUEST, "voice_model_unknown")
                    }
                }
                else -> errorJson(Response.Status.BAD_REQUEST, "unknown_stage")
            }
        }
    }

    private fun authorizeTest(payload: JSONObject): Response? {
        val envelope = webCommandEnvelope(payload)
            ?: return errorJson(Response.Status.BAD_REQUEST, envelopeError(payload))
        val takeOver = when {
            !payload.has("take_over") -> false
            else -> strictBoolean(payload, "take_over")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_take_over")
        }
        val result = onboarding.authorize(envelope.first, envelope.second, takeOver)
        if (result is OnboardingCommandResult.Applied) return null
        result as OnboardingCommandResult.Rejected
        val status = if (
            result.error == OnboardingCommandError.REVISION_CONFLICT ||
            result.error == OnboardingCommandError.EDITOR_ALREADY_ACTIVE
        ) Response.Status.CONFLICT else Response.Status.BAD_REQUEST
        return onboardingErrorJson(status, result.error, result.snapshot)
    }

    private fun launcherJson(): JSONObject {
        val context = prefs.context
        val capabilities = OnboardingCapabilities(context).read()
        val metrics = context.resources.displayMetrics
        val apps = installedAppsJson(prefs)
        return JSONObject()
            .put("ok", true)
            .put("device", JSONObject()
                .put("model", android.os.Build.MODEL.orEmpty())
                .put("width_px", metrics.widthPixels)
                .put("height_px", metrics.heightPixels)
                .put("density", metrics.density)
                .put("density_dpi", metrics.densityDpi))
            .put("capabilities", JSONObject()
                .put("default_launcher", capabilities.defaultLauncher.name.lowercase())
                .put("screen_control", capabilities.screenControl.name.lowercase())
                .put("brightness", capabilities.brightness.name.lowercase())
                .put("notifications", if (NotificationDots.isAccessGranted(context)) "granted" else "missing"))
            .put("apps", apps)
            .put("icon_packs", JSONArray().put(JSONObject().put("package", "").put("label", "Icônes système")).also { array ->
                installedIconPacks(context).forEach { pack ->
                    array.put(JSONObject().put("package", pack.packageName).put("label", pack.label))
                }
            })
    }

    private fun applyBackgroundConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val mode = strictString(payload, "background_mode")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_mode")
        val opacity = strictFloat(payload, "background_opacity")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_opacity")
        val albums = strictStringArray(payload, "immich_album_ids")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_album")
        val command = OnboardingCommand.SaveBackground(
            mode = mode,
            opacity = opacity,
            immichUrl = strictString(payload, "immich_url")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_url"),
            immichApiKey = strictString(payload, "immich_api_key")
                ?.trim()?.takeIf(String::isNotBlank)
                ?.let(com.iblu01.portallauncher.ui.onboarding.SecretInput::of),
            immichAlbumIds = albums,
            immichAllowInsecure = strictBoolean(payload, "immich_allow_insecure")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_allow_insecure"),
            immichShuffle = strictBoolean(payload, "immich_shuffle")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_shuffle"),
            immichRefreshMinutes = strictInt(payload, "immich_refresh_minutes")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_refresh"),
            immichCadenceSeconds = strictInt(payload, "immich_cadence_seconds")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_cadence"),
        )
        return executeOnboarding(payload, command).also {
            if (it.status == Response.Status.OK) onConfigSaved(WebConfigSection.ALL)
        }
    }

    private fun applyClockConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val command = OnboardingCommand.SaveClock(parseClock(payload)
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_clock"))
        return executeOnboarding(payload, command).also {
            if (it.status == Response.Status.OK) onConfigSaved(WebConfigSection.ALL)
        }
    }

    private fun applyAppsConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val hidden = strictStringArray(payload, "hidden_apps")?.toSet()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_app_configuration")
        val order = strictStringArray(payload, "app_order")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_app_configuration")
        val protected = installedApps(prefs).filter { it.protected }.mapTo(mutableSetOf()) { it.packageName }
        if (hidden.any { it in protected }) return errorJson(Response.Status.BAD_REQUEST, "protected_app")
        val installed = installedApps(prefs).mapTo(mutableSetOf()) { it.packageName }
        if (hidden.any { it !in installed } || order.any { it !in installed }) {
            return errorJson(Response.Status.BAD_REQUEST, "invalid_app_configuration")
        }
        val iconPack = strictString(payload, "icon_pack")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_icon_pack")
        val installedPacks = installedIconPacks(prefs.context).mapTo(mutableSetOf()) { it.packageName }
        if (iconPack.isNotBlank() && iconPack !in installedPacks) {
            return errorJson(Response.Status.BAD_REQUEST, "invalid_icon_pack")
        }
        val homeAssistantPackage = strictString(payload, "home_assistant_package")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_home_assistant_package")
        val tapAppPackage = strictString(payload, "tap_app_package")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_tap_app_package")
        if (homeAssistantPackage.isNotBlank() && homeAssistantPackage !in installed) {
            return errorJson(Response.Status.BAD_REQUEST, "invalid_home_assistant_package")
        }
        if (tapAppPackage.isNotBlank() && tapAppPackage !in installed) {
            return errorJson(Response.Status.BAD_REQUEST, "invalid_tap_app_package")
        }
        val command = OnboardingCommand.SaveApps(
            hiddenPackages = hidden,
            order = order,
            iconPack = iconPack,
            notificationDots = strictBoolean(payload, "notification_dots")
                ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_notification_dots"),
            homeAssistantPackage = homeAssistantPackage,
            tapAppPackage = tapAppPackage,
        )
        return executeOnboarding(payload, command).also {
            if (it.status == Response.Status.OK) onConfigSaved(WebConfigSection.ALL)
        }
    }

    private fun applyBehaviorConfig(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        val command = OnboardingCommand.SaveBehavior(
            keepScreenOn = strictBoolean(payload, "keep_screen_on") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
            screenTimeoutEnabled = strictBoolean(payload, "screen_timeout_enabled") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
            screenTimeoutMinutes = strictInt(payload, "screen_timeout_minutes") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
            autoReturnEnabled = strictBoolean(payload, "auto_return_enabled") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
            autoReturnDelaySeconds = strictInt(payload, "auto_return_delay_seconds") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
            gesturesSeen = strictBoolean(payload, "gestures_seen") ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_behavior"),
        )
        return executeOnboarding(payload, command).also {
            if (it.status == Response.Status.OK) onConfigSaved(WebConfigSection.ALL)
        }
    }

    private fun uploadBackground(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val mime = strictString(payload, "mime")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_image_type")
        if (mime !in WEB_IMAGE_MIME_TYPES) return errorJson(Response.Status.BAD_REQUEST, "invalid_image_type")
        val encoded = strictString(payload, "base64")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_image")
        if (encoded.length > MAX_UPLOAD_BASE64_CHARS) return errorJson(Response.Status.BAD_REQUEST, "image_too_large")
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_image")
        if (detectedImageMime(bytes) != mime) return errorJson(Response.Status.BAD_REQUEST, "invalid_image_type")
        val opacity = strictFloat(payload, "background_opacity")
            ?.takeIf { it.isFinite() && it in 0f..0.6f }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_background_opacity")
        val target = wallpaperFile(prefs.context)
        val backup = File(prefs.context.filesDir, "wallpaper.jpg.web.backup")
        backup.delete()
        if (target.isFile && !runCatching { target.copyTo(backup, overwrite = true) }.isSuccess) {
            return errorJson(Response.Status.INTERNAL_ERROR, "server_error")
        }
        if (!writeWebWallpaper(prefs.context, bytes)) return errorJson(Response.Status.BAD_REQUEST, "invalid_image")
        val response = executeOnboarding(payload, OnboardingCommand.SaveBackground("custom", opacity))
        if (response.status != Response.Status.OK) {
            if (backup.isFile) backup.copyTo(target, overwrite = true) else target.delete()
            backup.delete()
            return response
        }
        backup.delete()
        onConfigSaved(WebConfigSection.ALL)
        return response
    }

    private fun customBackgroundResponse(): Response {
        val file = wallpaperFile(prefs.context)
        if (!file.isFile || file.length() <= 0L) return text(Response.Status.NOT_FOUND, "not found")
        return noStore(newChunkedResponse(Response.Status.OK, "image/jpeg", file.inputStream()))
    }

    private fun testImmich(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val url = strictString(payload, "immich_url")?.trim()?.trimEnd('/')
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_url")
        val insecure = strictBoolean(payload, "immich_allow_insecure")
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_allow_insecure")
        val suppliedKey = strictString(payload, "immich_api_key")?.trim()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_key")
        val canReuse = url == prefs.immichUrl && insecure == prefs.immichAllowInsecure
        val key = suppliedKey.ifBlank { prefs.immichApiKey.takeIf { canReuse }.orEmpty() }
        if (key.isBlank()) return errorJson(Response.Status.BAD_REQUEST, "immich_key_required")
        val api = runCatching {
            ImmichApiClient(
                OkHttpTransport(), url, key,
                if (insecure) TransportPolicy.ALLOW_INSECURE else TransportPolicy.REQUIRE_SECURE,
            )
        }.getOrNull() ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_immich_url")
        return runBlocking {
            val health = api.health()
            if (!health.ok) return@runBlocking errorJson(Response.Status.BAD_REQUEST, "immich_${health.errorCategory}")
            val albums = runCatching { api.listAlbums() }.getOrElse {
                return@runBlocking errorJson(Response.Status.BAD_REQUEST, "immich_albums_failed")
            }
            val firstAlbum = albums.firstOrNull { it.assetCount > 0 } ?: albums.firstOrNull()
            val thumbnail = firstAlbum?.let { album ->
                runCatching {
                    val asset = api.listAlbumAssets(album.id, page = 0, pageSize = 1).assets.firstOrNull()
                        ?: return@runCatching null
                    api.fetchThumbnail(asset.id, DisplaySize(640, 360))
                        .takeIf { it.size in 1..MAX_WEB_THUMBNAIL_BYTES && hasSupportedImageSignature(it) }
                }.getOrNull()
            }
            json(JSONObject().put("ok", true).put("albums", JSONArray().also { array ->
                albums.forEach { album -> array.put(JSONObject().put("id", album.id).put("label", album.label).put("asset_count", album.assetCount)) }
            }).put("thumbnail", thumbnail?.let { bytes ->
                JSONObject().put("mime", detectedImageMime(bytes) ?: "image/jpeg")
                    .put("base64", Base64.getEncoder().encodeToString(bytes))
            } ?: JSONObject.NULL).toString())
        }
    }

    private fun openSystemAction(body: String): Response {
        val payload = runCatching { JSONObject(body) }.getOrNull()
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_json")
        authorizeTest(payload)?.let { return it }
        val action = strictString(payload, "action")
            ?.takeIf { it in SYSTEM_ACTIONS }
            ?: return errorJson(Response.Status.BAD_REQUEST, "invalid_system_action")
        if (!onSystemAction(action)) return errorJson(Response.Status.BAD_REQUEST, "system_action_unavailable")
        return json(launcherJson().put("action", action).toString())
    }

    private fun strictStringArray(payload: JSONObject, key: String): List<String>? {
        val array = payload.opt(key) as? JSONArray ?: return null
        if (array.length() > MAX_ARRAY_ITEMS) return null
        return (0 until array.length()).map { index ->
            array.opt(index).takeIf { it is String } as? String ?: return null
        }
    }

    private fun parseClock(payload: JSONObject): OnboardingClockConfig? {
        return OnboardingClockConfig(
            font = strictString(payload, "font") ?: return null,
            weight = strictInt(payload, "weight") ?: return null,
            size = strictFloat(payload, "size") ?: return null,
            letterSpacing = strictFloat(payload, "letter_spacing") ?: return null,
            tint = strictString(payload, "tint") ?: return null,
            format24h = strictBoolean(payload, "format_24h") ?: return null,
            dateFormat = strictString(payload, "date_format") ?: return null,
            elementSpacing = strictFloat(payload, "element_spacing") ?: return null,
        )
    }

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

    private fun svg(body: String) = noStore(
        newFixedLengthResponse(Response.Status.OK, "image/svg+xml; charset=utf-8", body)
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
        private const val HA_TEST_TIMEOUT_MS = 5_000
        private const val MAX_UPLOAD_BASE64_CHARS = 11_200_000
        private const val MAX_WEB_THUMBNAIL_BYTES = 2 * 1024 * 1024
        private const val MAX_ARRAY_ITEMS = 500
        private val WEB_IMAGE_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val SYSTEM_ACTIONS = setOf("default_launcher", "screen_control", "brightness", "notifications", "microphone")

        /** No O/0 or I/1: the code is read off a screen and typed by hand. */
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val GROUP = 4

        /**
         * Binds [PREFERRED_PORT], falling back to a kernel-assigned free port when it is taken.
         * Returns a listening server, or null when neither attempt could bind.
         */
        fun launch(
            prefs: Prefs,
            onMqttConfigChanged: () -> Unit,
            onConfigSaved: (WebConfigSection) -> Unit = {},
            onOnboardingComplete: () -> Unit = {},
            onBrowserConnected: (OnboardingSnapshot) -> Unit = {},
            onLauncherPreview: (WebLauncherPreview?) -> Boolean = { true },
            onSystemAction: (String) -> Boolean = { false },
            onScreenCapture: () -> ByteArray? = { null },
        ): WebConfigServer? {
            intArrayOf(PREFERRED_PORT, 0).forEach { port ->
                val server = WebConfigServer(
                    port,
                    prefs,
                    onMqttConfigChanged,
                    onConfigSaved,
                    onOnboardingComplete,
                    onBrowserConnected,
                    onLauncherPreview,
                    onSystemAction,
                    onScreenCapture,
                )
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
