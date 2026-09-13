package com.iblu01.portallauncher.ui.onboarding

import com.iblu01.portallauncher.DEFAULT_GEMINI_LIVE_MODEL
import com.iblu01.portallauncher.DEFAULT_GEMINI_VOICE
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint

/** A warning that must stay visible until the corresponding destructive-looking choice is confirmed. */
enum class OnboardingWarning { DEFAULTS_WILL_BE_USED }

/** Public credential state. Secret values deliberately have no representation in a snapshot. */
data class CredentialStatus(val configured: Boolean)

data class OnboardingClockConfig(
    val font: String,
    val weight: Int,
    val size: Float,
    val letterSpacing: Float,
    val tint: String,
    val format24h: Boolean,
    val dateFormat: String,
    val elementSpacing: Float,
)

data class OnboardingImmichConfig(
    val url: String,
    val keyConfigured: Boolean,
    val albumIds: List<String>,
    val allowInsecure: Boolean,
    val shuffle: Boolean,
    val refreshMinutes: Int,
    val cadenceSeconds: Int,
)

data class OnboardingBehaviorConfig(
    val keepScreenOn: Boolean,
    val screenTimeoutEnabled: Boolean,
    val screenTimeoutMinutes: Int,
    val autoReturnEnabled: Boolean,
    val autoReturnDelaySeconds: Int,
)

/** Coordinator-owned browser preview; a stale id can never be committed after a newer preview. */
data class OnboardingLauncherPreview(
    val id: String,
    val sequence: Long,
    val gridScale: Float,
    val backgroundMode: String,
    val backgroundOpacity: Float,
    val clock: OnboardingClockConfig? = null,
)

/**
 * Serializable, UI-independent state shared by Compose and the Web configurator.
 *
 * This is the only onboarding state safe to return over HTTP. In particular it exposes credential
 * presence, never the Home Assistant token, MQTT password, or Gemini API key.
 */
data class OnboardingSnapshot(
    val version: Int,
    val revision: Long,
    val step: OnboardingStep,
    val channel: OnboardingChannel?,
    val completed: Boolean,
    val flags: OnboardingFlags,
    val gridScale: Float,
    val backgroundMode: String,
    val backgroundOpacity: Float,
    val customBackgroundConfigured: Boolean,
    val immich: OnboardingImmichConfig,
    val clock: OnboardingClockConfig,
    val hiddenApps: Set<String>,
    val appOrder: List<String>,
    val iconPack: String,
    val notificationDots: Boolean,
    val homeAssistantPackage: String,
    val tapAppPackage: String,
    val behavior: OnboardingBehaviorConfig,
    val homeAssistant: CredentialStatus,
    val mqtt: CredentialStatus,
    val gemini: CredentialStatus,
    val homeAssistantSelected: Boolean,
    val mqttSelected: Boolean,
    val geminiSelected: Boolean,
    val geminiEnabled: Boolean,
    val disabledHaIntegrations: Set<String>,
    val pillRules: List<PillRule>,
    val homePillPreferences: HomePillPreferences,
    val cameraPreferences: CameraPreferences,
    val geminiPrompt: String,
    val geminiBargeIn: Boolean,
    val geminiWakeWord: String,
    val geminiThreshold: Int,
    val geminiIdleSeconds: Int,
    val geminiDailyLimit: Int,
    val geminiCalibrated: Boolean,
    val launcherPreview: OnboardingLauncherPreview?,
    val gesturesSeen: Boolean,
    val editorActive: Boolean,
    val warnings: Set<OnboardingWarning>,
)

/** Secret-bearing request value whose string representation is always redacted. */
class SecretInput private constructor(internal val value: String) {
    override fun toString(): String = "***"

    companion object {
        fun of(value: String): SecretInput = SecretInput(value)
    }
}

sealed interface OnboardingCommand {
    data class SelectChannel(val channel: OnboardingChannel) : OnboardingCommand
    data class Navigate(val step: OnboardingStep) : OnboardingCommand
    data class SaveLauncher(
        val gridScale: Float,
        val backgroundMode: String,
        val backgroundOpacity: Float,
        val previewId: String? = null,
        val previewSequence: Long? = null,
    ) : OnboardingCommand
    data class PreviewLauncher(
        val previewId: String,
        val gridScale: Float,
        val backgroundMode: String,
        val backgroundOpacity: Float,
        val clock: OnboardingClockConfig? = null,
    ) : OnboardingCommand
    data class RevertLauncher(val previewId: String, val previewSequence: Long? = null) : OnboardingCommand
    data class SelectProviders(
        val homeAssistant: Boolean,
        val mqtt: Boolean,
        val gemini: Boolean,
    ) : OnboardingCommand

    data object SkipHomeAssistant : OnboardingCommand
    data object SkipMqtt : OnboardingCommand
    data object ConfigureMqtt : OnboardingCommand
    data class SaveHomeAssistant(
        val url: String,
        val token: SecretInput? = null,
    ) : OnboardingCommand
    data class SaveMqtt(
        val host: String,
        val port: Int,
        val authEnabled: Boolean,
        val username: String,
        val password: SecretInput? = null,
        val preservePassword: Boolean = true,
        val deviceName: String,
    ) : OnboardingCommand
    data class SaveGemini(
        val enabled: Boolean,
        val apiKey: SecretInput? = null,
        val model: String = DEFAULT_GEMINI_LIVE_MODEL,
        val voice: String = DEFAULT_GEMINI_VOICE,
        val bargeIn: Boolean = false,
        val prompt: String = "",
        val wakeWord: String = "",
        val threshold: Int = 50,
        val idleSeconds: Int = 20,
        val dailyLimit: Int = 200,
    ) : OnboardingCommand
    data class SaveHomeConfiguration(
        val disabledIntegrations: Set<String>,
        val pillRules: List<PillRule>,
        val preferences: HomePillPreferences,
        val cameras: CameraPreferences,
    ) : OnboardingCommand
    data class SaveFinishOptions(
        val skipAppCleanup: Boolean,
        val gesturesSeen: Boolean,
    ) : OnboardingCommand
    data class SavePillRules(val rules: List<PillRule>) : OnboardingCommand
    data class SaveHiddenApps(
        val packages: Set<String>,
        val skipped: Boolean,
    ) : OnboardingCommand
    data class SaveTapApp(val packageName: String) : OnboardingCommand
    data class SaveBackground(
        val mode: String,
        val opacity: Float,
        val immichUrl: String = "",
        val immichApiKey: SecretInput? = null,
        val immichAlbumIds: List<String> = emptyList(),
        val immichAllowInsecure: Boolean = false,
        val immichShuffle: Boolean = true,
        val immichRefreshMinutes: Int = 60,
        val immichCadenceSeconds: Int = 30,
    ) : OnboardingCommand
    data class SaveClock(val config: OnboardingClockConfig) : OnboardingCommand
    data class SaveApps(
        val hiddenPackages: Set<String>,
        val order: List<String>,
        val iconPack: String,
        val notificationDots: Boolean,
        val homeAssistantPackage: String,
        val tapAppPackage: String,
    ) : OnboardingCommand
    data class SaveBehavior(
        val keepScreenOn: Boolean,
        val screenTimeoutEnabled: Boolean,
        val screenTimeoutMinutes: Int,
        val autoReturnEnabled: Boolean,
        val autoReturnDelaySeconds: Int,
        val gesturesSeen: Boolean,
    ) : OnboardingCommand
    data object AcknowledgeGestures : OnboardingCommand

    data class Complete(val defaultsWarningAccepted: Boolean) : OnboardingCommand
    data class ResetProgress(val confirmed: Boolean, val channel: OnboardingChannel = OnboardingChannel.WEB) : OnboardingCommand
}

enum class OnboardingCommandError {
    INVALID_SESSION,
    EDITOR_ALREADY_ACTIVE,
    REVISION_CONFLICT,
    INVALID_GRID_SCALE,
    INVALID_BACKGROUND_MODE,
    INVALID_BACKGROUND_OPACITY,
    CUSTOM_BACKGROUND_REQUIRED,
    INVALID_IMMICH_URL,
    IMMICH_KEY_REQUIRED,
    INVALID_IMMICH_ALBUM,
    INVALID_CLOCK,
    INVALID_APP_CONFIGURATION,
    INVALID_BEHAVIOR,
    INVALID_HOME_ASSISTANT_URL,
    HOME_ASSISTANT_TOKEN_REQUIRED,
    INVALID_MQTT_HOST,
    INVALID_MQTT_PORT,
    MQTT_USERNAME_REQUIRED,
    GEMINI_KEY_REQUIRED,
    DEFAULTS_WARNING_REQUIRED,
    CONFIRMATION_REQUIRED,
    INVALID_PREVIEW_ID,
    INVALID_PREVIEW_SEQUENCE,
    INVALID_HOME_CONFIGURATION,
    INVALID_GEMINI_CONFIGURATION,
    STALE_PREVIEW,
    DEVICE_PREVIEW_NOT_APPLIED,
}

sealed interface OnboardingCommandResult {
    data class Applied(val snapshot: OnboardingSnapshot) : OnboardingCommandResult
    data class Rejected(
        val error: OnboardingCommandError,
        val snapshot: OnboardingSnapshot,
    ) : OnboardingCommandResult
}

/**
 * Single business-state boundary for both onboarding surfaces.
 *
 * Remote writers use [execute] and must hold the short editor lease. Device UI uses [executeLocal]
 * because it is already the trusted foreground owner. Both paths still use the same monotonic
 * revision, so an old browser response can never overwrite a newer device or browser decision.
 */
class OnboardingCoordinator(
    private val prefs: Prefs,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val previewApplier: (OnboardingLauncherPreview?) -> Boolean = { true },
) {
    private var launcherPreview: OnboardingLauncherPreview? = null
    private var previewSequence: Long = 0L
    private val retiredPreviewIds = LinkedHashSet<String>()

    fun snapshot(): OnboardingSnapshot = synchronized(Prefs.onboardingMutationLock) {
        snapshotLocked(clockMillis())
    }

    fun execute(
        sessionId: String,
        expectedRevision: Long,
        command: OnboardingCommand,
        takeOver: Boolean = false,
    ): OnboardingCommandResult = synchronized(Prefs.onboardingMutationLock) {
        val now = clockMillis()
        if (!validSessionId(sessionId)) return@synchronized rejected(OnboardingCommandError.INVALID_SESSION, now)
        if (prefs.onboardingRevision != expectedRevision) {
            return@synchronized rejected(OnboardingCommandError.REVISION_CONFLICT, now)
        }
        val previousEditor = prefs.onboardingEditorSession
        val previousLeaseUntil = prefs.onboardingEditorLeaseUntil
        val previousPreview = launcherPreview
        if (!claimEditorLocked(sessionId, now, takeOver)) {
            return@synchronized rejected(OnboardingCommandError.EDITOR_ALREADY_ACTIVE, now)
        }
        val replacesOtherEditor = takeOver && previousEditor.isNotBlank() && previousEditor != sessionId
        val clearedPreviewBeforeCommand = replacesOtherEditor && previousPreview != null &&
            command !is OnboardingCommand.PreviewLauncher
        if (clearedPreviewBeforeCommand && !previewApplier(null)) {
            prefs.onboardingEditorSession = previousEditor
            prefs.onboardingEditorLeaseUntil = previousLeaseUntil
            return@synchronized rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
        }
        if (replacesOtherEditor) launcherPreview = null
        val result = applyLocked(command, now)
        if (result is OnboardingCommandResult.Rejected) {
            prefs.onboardingEditorSession = previousEditor
            prefs.onboardingEditorLeaseUntil = previousLeaseUntil
            launcherPreview = previousPreview
            if (clearedPreviewBeforeCommand) previewApplier(previousPreview)
        } else if (replacesOtherEditor) {
            previousPreview?.id?.takeIf { it != launcherPreview?.id }?.let(::retirePreview)
        }
        result
    }

    fun executeLocal(command: OnboardingCommand): OnboardingCommandResult =
        synchronized(Prefs.onboardingMutationLock) { applyLocked(command, clockMillis()) }

    /** Validates and renews a remote editor lease without mutating onboarding state. */
    fun authorize(
        sessionId: String,
        expectedRevision: Long,
        takeOver: Boolean = false,
    ): OnboardingCommandResult = synchronized(Prefs.onboardingMutationLock) {
        val now = clockMillis()
        if (!validSessionId(sessionId)) return@synchronized rejected(OnboardingCommandError.INVALID_SESSION, now)
        if (prefs.onboardingRevision != expectedRevision) {
            return@synchronized rejected(OnboardingCommandError.REVISION_CONFLICT, now)
        }
        val previousEditor = prefs.onboardingEditorSession
        val previousLeaseUntil = prefs.onboardingEditorLeaseUntil
        val previousPreview = launcherPreview
        if (!claimEditorLocked(sessionId, now, takeOver)) {
            return@synchronized rejected(OnboardingCommandError.EDITOR_ALREADY_ACTIVE, now)
        }
        val replacesOtherEditor = takeOver && previousEditor.isNotBlank() && previousEditor != sessionId
        if (replacesOtherEditor && previousPreview != null) {
            if (!previewApplier(null)) {
                prefs.onboardingEditorSession = previousEditor
                prefs.onboardingEditorLeaseUntil = previousLeaseUntil
                return@synchronized rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
            }
            retirePreview(previousPreview.id)
            launcherPreview = null
        }
        OnboardingCommandResult.Applied(snapshotLocked(now))
    }

    /**
     * Transitional adapter for rich sub-pages whose settings already have their own Prefs model.
     * The mutation and revision bump share the process-wide lock, so Web CAS still observes them.
     */
    fun updateLocalConfiguration(mutation: (Prefs) -> Unit): OnboardingSnapshot =
        synchronized(Prefs.onboardingMutationLock) {
            mutation(prefs)
            prefs.onboardingRevision = prefs.onboardingRevision + 1
            snapshotLocked(clockMillis())
        }

    /** A stopped Web host has no editor. Clearing its lease lets a recreated host start now. */
    fun releaseEditorLease() = synchronized(Prefs.onboardingMutationLock) {
        prefs.onboardingEditorSession = ""
        prefs.onboardingEditorLeaseUntil = 0L
        if (launcherPreview != null && previewApplier(null)) launcherPreview = null
    }

    fun editorOwnedBy(sessionId: String): Boolean = synchronized(Prefs.onboardingMutationLock) {
        prefs.onboardingEditorSession == sessionId && prefs.onboardingEditorLeaseUntil > clockMillis()
    }

    private fun applyLocked(command: OnboardingCommand, now: Long): OnboardingCommandResult {
        when (command) {
            is OnboardingCommand.SelectChannel -> {
                prefs.onboardingChannel = command.channel.name
                prefs.onboardingMetricsChannel = command.channel.name
                if (prefs.onboardingMetricsStartedAt == 0L) prefs.onboardingMetricsStartedAt = now
            }
            is OnboardingCommand.Navigate -> {
                prefs.onboardingStep = command.step.name
                val previous = OnboardingStep.entries.indexOfFirst { it.name == prefs.onboardingMetricsHighestStep }
                val next = OnboardingStep.entries.indexOf(command.step)
                if (next > previous) prefs.onboardingMetricsHighestStep = command.step.name
            }
            is OnboardingCommand.SelectProviders -> {
                prefs.onboardingHomeAssistantSelected = command.homeAssistant
                prefs.onboardingMqttSelected = command.mqtt
                prefs.onboardingGeminiSelected = command.gemini
                prefs.homeAssistantOnboardingSkipped = !command.homeAssistant
                prefs.mqttOnboardingSkipped = !command.mqtt
            }
            is OnboardingCommand.PreviewLauncher -> {
                if (!validPreviewId(command.previewId)) {
                    return rejected(OnboardingCommandError.INVALID_PREVIEW_ID, now)
                }
                if (command.previewId in retiredPreviewIds) {
                    return rejected(OnboardingCommandError.STALE_PREVIEW, now)
                }
                validateLauncher(command.gridScale, command.backgroundMode, command.backgroundOpacity, now)
                    ?.let { return it }
                val nextPreview = OnboardingLauncherPreview(
                    id = command.previewId,
                    sequence = ++previewSequence,
                    gridScale = command.gridScale,
                    backgroundMode = command.backgroundMode,
                    backgroundOpacity = command.backgroundOpacity,
                    clock = command.clock,
                )
                if (!previewApplier(nextPreview)) {
                    previewSequence -= 1
                    return rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
                }
                launcherPreview = nextPreview
            }
            is OnboardingCommand.RevertLauncher -> {
                val currentPreview = launcherPreview
                if (currentPreview?.id != command.previewId) {
                    return rejected(OnboardingCommandError.STALE_PREVIEW, now)
                }
                if (command.previewSequence == null || command.previewSequence != currentPreview.sequence) {
                    return rejected(OnboardingCommandError.INVALID_PREVIEW_SEQUENCE, now)
                }
                if (!previewApplier(null)) {
                    return rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
                }
                retirePreview(command.previewId)
                launcherPreview = null
            }
            is OnboardingCommand.SaveLauncher -> {
                validateLauncher(command.gridScale, command.backgroundMode, command.backgroundOpacity, now)
                    ?.let { return it }
                if (launcherPreview != null && (command.previewId == null || command.previewSequence == null)) {
                    return rejected(OnboardingCommandError.INVALID_PREVIEW_SEQUENCE, now)
                }
                if (command.previewId != null) {
                    val currentPreview = launcherPreview
                    if (currentPreview?.id != command.previewId) {
                        return rejected(OnboardingCommandError.STALE_PREVIEW, now)
                    }
                    if (command.previewSequence == null || command.previewSequence != currentPreview.sequence) {
                        return rejected(OnboardingCommandError.INVALID_PREVIEW_SEQUENCE, now)
                    }
                }
                val previousScale = prefs.gridScale
                val previousMode = prefs.backgroundMode
                val previousOpacity = prefs.bgOverlayOpacity
                prefs.gridScale = command.gridScale
                prefs.backgroundMode = command.backgroundMode
                prefs.bgOverlayOpacity = command.backgroundOpacity
                if (!previewApplier(null)) {
                    prefs.gridScale = previousScale
                    prefs.backgroundMode = previousMode
                    prefs.bgOverlayOpacity = previousOpacity
                    return rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
                }
                command.previewId?.let(::retirePreview)
                launcherPreview = null
            }
            OnboardingCommand.SkipHomeAssistant -> {
                prefs.homeAssistantOnboardingSkipped = true
                prefs.onboardingHomeAssistantSelected = false
            }
            OnboardingCommand.SkipMqtt -> {
                prefs.mqttOnboardingSkipped = true
                prefs.onboardingMqttSelected = false
            }
            OnboardingCommand.ConfigureMqtt -> {
                prefs.mqttOnboardingSkipped = false
                prefs.onboardingMqttSelected = true
            }
            is OnboardingCommand.SaveHomeAssistant -> {
                val normalizedUrl = OnboardingUrls.normalizeHaUrl(command.url)
                if (!OnboardingUrls.isValidHaUrl(normalizedUrl)) {
                    return rejected(OnboardingCommandError.INVALID_HOME_ASSISTANT_URL, now)
                }
                val token = command.token?.value?.trim().orEmpty()
                if (token.isBlank() && prefs.haToken.isBlank()) {
                    return rejected(OnboardingCommandError.HOME_ASSISTANT_TOKEN_REQUIRED, now)
                }
                prefs.haUrl = normalizedUrl
                if (token.isNotBlank()) prefs.haToken = token
                prefs.homeAssistantOnboardingSkipped = false
                prefs.onboardingHomeAssistantSelected = true
            }
            is OnboardingCommand.SaveMqtt -> {
                if (command.host.isBlank()) return rejected(OnboardingCommandError.INVALID_MQTT_HOST, now)
                if (command.port !in 1..65535) return rejected(OnboardingCommandError.INVALID_MQTT_PORT, now)
                if (command.authEnabled && command.username.isBlank()) {
                    return rejected(OnboardingCommandError.MQTT_USERNAME_REQUIRED, now)
                }
                prefs.brokerHost = command.host
                prefs.brokerPort = command.port
                prefs.username = if (command.authEnabled) command.username else ""
                if (command.authEnabled) {
                    val nextPassword = command.password?.value
                    if (nextPassword != null) prefs.password = nextPassword
                    else if (!command.preservePassword) prefs.password = ""
                } else {
                    prefs.password = ""
                }
                prefs.deviceName = command.deviceName
                prefs.mqttOnboardingConfigured = true
                prefs.mqttOnboardingSkipped = false
                prefs.onboardingMqttSelected = true
            }
            is OnboardingCommand.SaveGemini -> {
                val apiKey = command.apiKey?.value?.trim().orEmpty()
                if (command.enabled && apiKey.isBlank() && !prefs.hasVoiceGeminiApiKey) {
                    return rejected(OnboardingCommandError.GEMINI_KEY_REQUIRED, now)
                }
                if (apiKey.isNotBlank()) prefs.voiceGeminiApiKey = apiKey
                command.model.trim().takeIf { it.isNotBlank() }?.let { prefs.voiceGeminiModel = it }
                command.voice.trim().takeIf { it.isNotBlank() }?.let { prefs.voiceGeminiVoice = it }
                prefs.voiceBargeIn = command.bargeIn
                if (command.prompt.length > 4096 || command.threshold !in 1..95 ||
                    command.idleSeconds !in 5..300 || command.dailyLimit !in 0..5000
                ) return rejected(OnboardingCommandError.INVALID_GEMINI_CONFIGURATION, now)
                prefs.voiceGeminiPrompt = command.prompt
                command.wakeWord.takeIf(String::isNotBlank)?.let { prefs.voiceAssistantWakeWord = it }
                prefs.voiceAssistantThreshold = command.threshold
                prefs.voiceAssistantIdleSeconds = command.idleSeconds
                prefs.voiceDailySessionLimit = command.dailyLimit
                prefs.voiceAssistantEnabled = command.enabled
            }
            is OnboardingCommand.SaveHomeConfiguration -> {
                val ids = command.pillRules.map { it.entityId }
                val pinned = command.preferences.pinnedOrder.map { it.stableKey }
                if (ids.any { it.isBlank() } || ids.distinct().size != ids.size ||
                    pinned.distinct().size != pinned.size || command.preferences.manualGroups.any {
                        it.id.isBlank() || it.name.isBlank() || it.members.isEmpty()
                    }
                ) return rejected(OnboardingCommandError.INVALID_HOME_CONFIGURATION, now)
                prefs.disabledHaIntegrations = command.disabledIntegrations
                prefs.pillRules = command.pillRules
                prefs.homePillPreferences = command.preferences
                prefs.cameraPreferences = command.cameras
            }
            is OnboardingCommand.SaveFinishOptions -> {
                prefs.appCleanupOnboardingSkipped = command.skipAppCleanup
                prefs.gestureHintsSeen = command.gesturesSeen
            }
            is OnboardingCommand.SavePillRules -> prefs.pillRules = command.rules
            is OnboardingCommand.SaveHiddenApps -> {
                prefs.hiddenApps = command.packages
                prefs.appCleanupOnboardingSkipped = command.skipped
            }
            is OnboardingCommand.SaveTapApp -> prefs.homeAssistantPackage = command.packageName
            is OnboardingCommand.SaveBackground -> {
                validateLauncher(prefs.gridScale, command.mode, command.opacity, now)?.let { return it }
                if (command.mode == "custom" &&
                    !com.iblu01.portallauncher.ui.components.wallpaperFile(prefs.context).exists()
                ) return rejected(OnboardingCommandError.CUSTOM_BACKGROUND_REQUIRED, now)
                if (command.mode == "immich") {
                    val normalizedUrl = command.immichUrl.trim().trimEnd('/')
                    val validScheme = normalizedUrl.startsWith("https://") ||
                        (command.immichAllowInsecure && normalizedUrl.startsWith("http://"))
                    if (!validScheme) return rejected(OnboardingCommandError.INVALID_IMMICH_URL, now)
                    if (command.immichApiKey == null && !prefs.hasImmichApiKey) {
                        return rejected(OnboardingCommandError.IMMICH_KEY_REQUIRED, now)
                    }
                    if (command.immichAlbumIds.any { !validAlbumId(it) }) {
                        return rejected(OnboardingCommandError.INVALID_IMMICH_ALBUM, now)
                    }
                    if (command.immichRefreshMinutes !in 5..1_440 ||
                        command.immichCadenceSeconds !in 5..3_600
                    ) return rejected(OnboardingCommandError.INVALID_BEHAVIOR, now)
                    prefs.immichUrl = normalizedUrl
                    command.immichApiKey?.value?.takeIf(String::isNotBlank)?.let { prefs.immichApiKey = it }
                    prefs.immichAlbumIds = command.immichAlbumIds
                    prefs.immichAllowInsecure = command.immichAllowInsecure
                    prefs.immichShuffle = command.immichShuffle
                    prefs.immichRefreshMinutes = command.immichRefreshMinutes
                    prefs.immichCadenceSeconds = command.immichCadenceSeconds
                }
                prefs.backgroundMode = command.mode
                prefs.bgOverlayOpacity = command.opacity
            }
            is OnboardingCommand.SaveClock -> {
                if (!validClock(command.config)) return rejected(OnboardingCommandError.INVALID_CLOCK, now)
                prefs.clockTheme = command.config.toTheme()
            }
            is OnboardingCommand.SaveApps -> {
                if (command.hiddenPackages.any { !validPackageOrKey(it) } ||
                    command.order.any { !validPackageOrKey(it) } ||
                    !validOptionalPackage(command.iconPack) ||
                    !validOptionalPackage(command.homeAssistantPackage) ||
                    !validOptionalPackage(command.tapAppPackage)
                ) return rejected(OnboardingCommandError.INVALID_APP_CONFIGURATION, now)
                prefs.hiddenApps = command.hiddenPackages
                prefs.appOrder = command.order.distinct()
                prefs.iconPack = command.iconPack
                prefs.notificationDots = command.notificationDots
                prefs.haCompanionPackage = command.homeAssistantPackage
                prefs.homeAssistantPackage = command.tapAppPackage
            }
            is OnboardingCommand.SaveBehavior -> {
                if (command.screenTimeoutMinutes !in 1..240 || command.autoReturnDelaySeconds !in 5..60) {
                    return rejected(OnboardingCommandError.INVALID_BEHAVIOR, now)
                }
                prefs.devKeepScreenOn = command.keepScreenOn
                prefs.screenTimeoutEnabled = command.screenTimeoutEnabled
                prefs.screenTimeoutMinutes = command.screenTimeoutMinutes
                prefs.autoReturnEnabled = command.autoReturnEnabled
                prefs.autoReturnDelaySeconds = command.autoReturnDelaySeconds
                prefs.gestureHintsSeen = command.gesturesSeen
            }
            OnboardingCommand.AcknowledgeGestures -> prefs.gestureHintsSeen = true
            is OnboardingCommand.Complete -> {
                if (usesOnlyDefaults() && !command.defaultsWarningAccepted) {
                    return rejected(OnboardingCommandError.DEFAULTS_WARNING_REQUIRED, now)
                }
                val channel = OnboardingChannel.from(prefs.onboardingChannel) ?: OnboardingChannel.DEVICE
                if (launcherPreview != null && !previewApplier(null)) {
                    return rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
                }
                launcherPreview?.id?.let(::retirePreview)
                launcherPreview = null
                prefs.completeOnboarding(channel.name, ONBOARDING_VERSION)
                prefs.onboardingMetricsChannel = channel.name
                prefs.onboardingMetricsHighestStep = OnboardingStep.COMPLETE.name
                val startedAt = prefs.onboardingMetricsStartedAt
                if (startedAt in 1..now) prefs.onboardingMetricsLastDurationMs = now - startedAt
                prefs.onboardingMetricsStartedAt = 0L
                return OnboardingCommandResult.Applied(snapshotLocked(now))
            }
            is OnboardingCommand.ResetProgress -> {
                if (!command.confirmed) return rejected(OnboardingCommandError.CONFIRMATION_REQUIRED, now)
                if (launcherPreview != null && !previewApplier(null)) {
                    return rejected(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, now)
                }
                launcherPreview?.id?.let(::retirePreview)
                launcherPreview = null
                if (!prefs.onboardingCompleted && prefs.onboardingMetricsStartedAt > 0L) {
                    prefs.onboardingMetricsAbandonCount = prefs.onboardingMetricsAbandonCount + 1
                }
                prefs.resetOnboarding(command.channel.name)
                prefs.onboardingMetricsChannel = command.channel.name
                prefs.onboardingMetricsHighestStep = OnboardingStep.WELCOME.name
                prefs.onboardingMetricsStartedAt = now
                return OnboardingCommandResult.Applied(snapshotLocked(now))
            }
        }
        prefs.onboardingRevision = prefs.onboardingRevision + 1
        return OnboardingCommandResult.Applied(snapshotLocked(now))
    }

    private fun claimEditorLocked(sessionId: String, now: Long, takeOver: Boolean): Boolean {
        val current = prefs.onboardingEditorSession
        val expired = prefs.onboardingEditorLeaseUntil <= now
        if (current.isNotBlank() && current != sessionId && !expired && !takeOver) return false
        prefs.onboardingEditorSession = sessionId
        prefs.onboardingEditorLeaseUntil = now + EDITOR_LEASE_MS
        return true
    }

    private fun rejected(error: OnboardingCommandError, now: Long) =
        OnboardingCommandResult.Rejected(error, snapshotLocked(now))

    private fun snapshotLocked(now: Long): OnboardingSnapshot {
        val defaults = usesOnlyDefaults()
        return OnboardingSnapshot(
            version = ONBOARDING_VERSION,
            revision = prefs.onboardingRevision,
            step = resumeStep(prefs.onboardingStep, prefs.onboardingCompleted),
            channel = OnboardingChannel.from(prefs.onboardingChannel),
            completed = prefs.onboardingCompleted,
            flags = OnboardingFlags(
                homeAssistantSkipped = prefs.homeAssistantOnboardingSkipped,
                mqttSkipped = prefs.mqttOnboardingSkipped,
                geminiSkipped = !prefs.onboardingGeminiSelected,
                appCleanupSkipped = prefs.appCleanupOnboardingSkipped,
            ),
            gridScale = prefs.gridScale,
            backgroundMode = prefs.backgroundMode,
            backgroundOpacity = prefs.bgOverlayOpacity,
            customBackgroundConfigured = com.iblu01.portallauncher.ui.components.wallpaperFile(prefs.context).exists(),
            immich = OnboardingImmichConfig(
                url = prefs.immichUrl,
                keyConfigured = prefs.hasImmichApiKey,
                albumIds = prefs.immichAlbumIds,
                allowInsecure = prefs.immichAllowInsecure,
                shuffle = prefs.immichShuffle,
                refreshMinutes = prefs.immichRefreshMinutes,
                cadenceSeconds = prefs.immichCadenceSeconds,
            ),
            clock = prefs.clockTheme.toOnboarding(),
            hiddenApps = prefs.hiddenApps,
            appOrder = prefs.appOrder,
            iconPack = prefs.iconPack,
            notificationDots = prefs.notificationDots,
            homeAssistantPackage = prefs.haCompanionPackage,
            tapAppPackage = prefs.homeAssistantPackage,
            behavior = OnboardingBehaviorConfig(
                keepScreenOn = prefs.devKeepScreenOn,
                screenTimeoutEnabled = prefs.screenTimeoutEnabled,
                screenTimeoutMinutes = prefs.screenTimeoutMinutes,
                autoReturnEnabled = prefs.autoReturnEnabled,
                autoReturnDelaySeconds = prefs.autoReturnDelaySeconds,
            ),
            homeAssistant = CredentialStatus(prefs.haToken.isNotBlank()),
            mqtt = CredentialStatus(prefs.mqttOnboardingConfigured),
            gemini = CredentialStatus(prefs.hasVoiceGeminiApiKey),
            homeAssistantSelected = prefs.onboardingHomeAssistantSelected,
            mqttSelected = prefs.onboardingMqttSelected,
            geminiSelected = prefs.onboardingGeminiSelected,
            geminiEnabled = prefs.voiceAssistantEnabled,
            disabledHaIntegrations = prefs.disabledHaIntegrations,
            pillRules = prefs.pillRules,
            homePillPreferences = prefs.homePillPreferences,
            cameraPreferences = prefs.cameraPreferences,
            geminiPrompt = prefs.voiceGeminiPrompt,
            geminiBargeIn = prefs.voiceBargeIn,
            geminiWakeWord = prefs.voiceAssistantWakeWord,
            geminiThreshold = prefs.voiceAssistantThreshold,
            geminiIdleSeconds = prefs.voiceAssistantIdleSeconds,
            geminiDailyLimit = prefs.voiceDailySessionLimit,
            geminiCalibrated = prefs.voiceMicCalibration != null,
            launcherPreview = launcherPreview,
            gesturesSeen = prefs.gestureHintsSeen,
            editorActive = prefs.onboardingEditorSession.isNotBlank() && prefs.onboardingEditorLeaseUntil > now,
            warnings = if (!prefs.onboardingCompleted && defaults) {
                setOf(OnboardingWarning.DEFAULTS_WILL_BE_USED)
            } else {
                emptySet()
            },
        )
    }

    private fun usesOnlyDefaults(): Boolean =
        prefs.haToken.isBlank() &&
            !prefs.mqttOnboardingConfigured &&
            !prefs.hasVoiceGeminiApiKey &&
            prefs.gridScale == 1f &&
            prefs.backgroundMode in setOf("system", "neutral") &&
            prefs.bgOverlayOpacity == 0.25f &&
            prefs.hiddenApps.isEmpty() &&
            prefs.homeAssistantPackage.isBlank()

    private fun validSessionId(value: String): Boolean =
        value.length in 8..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun validPreviewId(value: String): Boolean =
        value.length in 8..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun validClock(value: OnboardingClockConfig): Boolean =
        ClockFont.entries.any { it.key == value.font } &&
            value.weight in 100..900 && value.weight % 100 == 0 &&
            value.size.isFinite() && value.size in ClockTheme.SizeRange &&
            value.letterSpacing.isFinite() && value.letterSpacing in ClockTheme.LetterSpacingRange &&
            ClockTint.entries.any { it.key == value.tint } &&
            ClockDateFormat.entries.any { it.key == value.dateFormat } &&
            value.elementSpacing.isFinite() && value.elementSpacing in ClockTheme.ElementSpacingRange

    private fun validAlbumId(value: String): Boolean =
        value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun validPackageOrKey(value: String): Boolean =
        value.length in 1..256 && value.none(Char::isWhitespace)

    private fun validOptionalPackage(value: String): Boolean = value.isBlank() ||
        (value.length <= 256 && value.contains('.') && value.none(Char::isWhitespace))

    private fun OnboardingClockConfig.toTheme() = ClockTheme(
        font = ClockFont.fromKey(font), weight = weight, size = size, letterSpacing = letterSpacing,
        tint = ClockTint.fromKey(tint), format24h = format24h,
        dateFormat = ClockDateFormat.fromKey(dateFormat), elementSpacing = elementSpacing,
    )

    private fun ClockTheme.toOnboarding() = OnboardingClockConfig(
        font.key, weight, size, letterSpacing, tint.key, format24h, dateFormat.key, elementSpacing,
    )

    private fun retirePreview(id: String) {
        retiredPreviewIds += id
        while (retiredPreviewIds.size > MAX_RETIRED_PREVIEWS) {
            retiredPreviewIds.remove(retiredPreviewIds.first())
        }
    }

    private fun validateLauncher(
        gridScale: Float,
        backgroundMode: String,
        backgroundOpacity: Float,
        now: Long,
    ): OnboardingCommandResult.Rejected? = when {
        !gridScale.isFinite() || gridScale !in GRID_SCALE_RANGE ->
            rejected(OnboardingCommandError.INVALID_GRID_SCALE, now)
        backgroundMode !in BACKGROUND_MODES ->
            rejected(OnboardingCommandError.INVALID_BACKGROUND_MODE, now)
        !backgroundOpacity.isFinite() || backgroundOpacity !in 0f..0.6f ->
            rejected(OnboardingCommandError.INVALID_BACKGROUND_OPACITY, now)
        else -> null
    }

    companion object {
        private val GRID_SCALE_RANGE = 0.7f..1.3f
        private val BACKGROUND_MODES = setOf("system", "neutral", "custom", "immich")
        const val EDITOR_LEASE_MS = 2 * 60 * 1000L
        private const val MAX_RETIRED_PREVIEWS = 32
    }
}
