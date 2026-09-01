package com.iblu01.portallauncher.voice

import java.net.URI

/**
 * Voice assistant contract: the state the launcher renders, plus the pure endpoint parsing that
 * turns whatever the user pastes in settings into the SmallWebRTC offer URL the Pipecat add-on
 * exposes (`/api/offer`, guarded by the satellite shared secret).
 *
 * The moving parts live in [VoiceAssistantController]; everything here is deliberately free of
 * Android and of the Pipecat SDK so it can be unit-tested on the JVM.
 */

/** Where a session currently is. Only [isOverlayVisible] phases draw over the launcher. */
enum class VoicePhase {
    /** Turned off in settings. */
    DISABLED,

    /** Enabled, but RECORD_AUDIO has not been granted yet. */
    MISSING_PERMISSION,

    /** Enabled and permitted, but no usable server address. */
    MISCONFIGURED,

    /** Wake word engine running, microphone owned by openWakeWord. */
    WAITING_FOR_WAKE,

    /** Wake word fired; negotiating WebRTC with the add-on. */
    CONNECTING,

    /** Session live, waiting for the user to speak. */
    LISTENING,

    /** User turn finished; STT/LLM working. */
    THINKING,

    /** Bot is speaking through the panel speaker. */
    SPEAKING,

    /** Last session failed. Falls back to [WAITING_FOR_WAKE] after a short delay. */
    ERROR,
    ;

    val isSessionActive: Boolean
        get() = this == CONNECTING || this == LISTENING || this == THINKING || this == SPEAKING

    val isOverlayVisible: Boolean
        get() = isSessionActive || this == ERROR
}

/**
 * Everything the overlay needs. [userText] and [botText] hold the current turn only: they are
 * cleared when a new session starts, so the panel never shows a stale conversation.
 */
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.DISABLED,
    val userText: String = "",
    val botText: String = "",
    val error: String? = null,
    val wakeWord: String = "",
    /** Latest score emitted by openWakeWord (0..1), useful for live microphone diagnostics. */
    val wakeScore: Float = 0f,
    /** Raw microphone RMS mapped to 0..1, independent from wake-word confidence. */
    val microphoneLevel: Float = 0f,
    /** Timestamp and confidence of the last wake-word detection in this process. */
    val lastWakeDetectionAt: Long? = null,
    val lastWakeDetectionScore: Float? = null,
)

/** Resolved configuration for one session. */
data class VoiceConfig(
    val offerUrl: String,
    val token: String,
    val wakeWordAsset: String,
    val threshold: Float,
    val idleTimeoutMs: Long,
) {
    val isUsable: Boolean
        get() = offerUrl.isNotBlank() && wakeWordAsset.isNotBlank()
}

/**
 * Raw measurements of the panel's own audio loop, produced by [MicCalibrator] and persisted in
 * prefs. The engine parameters are derived at read time so the mapping can evolve without
 * invalidating stored calibrations.
 */
data class MicCalibration(
    /** Ambient room RMS with nobody speaking (0..1 full-scale). */
    val noiseFloor: Float,
    /** RMS captured by the microphone while the panel played the reference sweep. */
    val playbackRms: Float,
) {
    /**
     * Below this RMS the engine treats the frame as room noise and applies no speech gain.
     * 3x the measured floor, clamped to the engine's historical fixed gate on the low end and to
     * a level that would swallow distant speech on the high end.
     */
    val noiseGateRms: Float
        get() = (noiseFloor * 3f).coerceIn(DEFAULT_NOISE_GATE_RMS, 0.02f)

    /**
     * Gain cap sized from the room, not from the panel's own loudspeaker.
     *
     * It used to divide the target by [playbackRms], which was wrong in a way that silently
     * disabled the feature: the reference sweep plays at 70% full scale through a speaker sitting
     * centimetres from the microphone, so it is captured far louder than a person speaking across
     * the room. On any panel whose speaker works, that ratio clamped to 1x — calibrating turned
     * the speech gain *off* and cost the wake word its range.
     *
     * Distant speech instead lands a fairly stable margin above the room's own noise, so the floor
     * is the honest predictor: estimate speech at [SPEECH_OVER_NOISE] times the floor and pull
     * that estimate up to [TARGET_SPEECH_RMS]. A quiet room earns headroom, a noisy one gets none
     * (amplifying there would only feed the classifier louder noise).
     */
    val maxGain: Float
        get() = (TARGET_SPEECH_RMS / (noiseFloor * SPEECH_OVER_NOISE)).coerceIn(1f, 12f)

    companion object {
        /** RMS openWakeWord's training data centres on; the gain formula pulls speech toward it. */
        const val TARGET_SPEECH_RMS = 0.08f
        const val DEFAULT_NOISE_GATE_RMS = 0.002f
        const val DEFAULT_MAX_GAIN = 6f

        /** ~18 dB: what speech at 2-3 m typically measures above the room's noise floor. */
        const val SPEECH_OVER_NOISE = 8f
    }
}

/** An address plus, when the pasted URL carried one, the satellite token found in its query. */
data class VoiceEndpoint(val offerUrl: String, val token: String?)

private const val OFFER_PATH = "/api/offer"
private const val DEFAULT_PORT = 7860

/**
 * Accepts anything a user is likely to paste from the Pipecat Assist add-on page and returns the
 * SmallWebRTC offer endpoint:
 *
 *  - `http://ha.local:7860/api/offer?token=abc` — used as-is, token extracted
 *  - `ws://ha.local:7860/api/assist/esphome?token=abc` — the ESPHome satellite URL shown in the
 *    add-on UI. Same host, same port, same secret, different path: rewritten to the offer path
 *    rather than rejected, because that is the string most panels will be configured with.
 *  - `ha.local:7860` or `ha.local` — scheme and path filled in (port defaults to the add-on's).
 *
 * Returns null when nothing usable can be built, which surfaces as [VoicePhase.MISCONFIGURED].
 */
fun parseVoiceEndpoint(raw: String): VoiceEndpoint? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withScheme = when {
        trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
        trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
        trimmed.contains("://") -> trimmed
        else -> "http://$trimmed"
    }

    val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
    val port = if (uri.port > 0) uri.port else DEFAULT_PORT
    val token = uri.rawQuery
        ?.split('&')
        ?.firstOrNull { it.startsWith("token=") }
        ?.removePrefix("token=")
        ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        ?.takeIf { it.isNotBlank() }

    // Anything that is not already the offer path (including the ESPHome satellite websocket) is
    // rewritten: the add-on serves the offer on the same host/port for every satellite kind.
    val path = uri.path?.takeIf { it.endsWith(OFFER_PATH) } ?: OFFER_PATH

    return VoiceEndpoint(offerUrl = "$scheme://$host:$port$path", token = token)
}

/**
 * Maps `TransportState` names onto our phases. Takes the name rather than the SDK enum so this
 * file (and its tests) stay independent of the Pipecat dependency.
 *
 * `Connected`/`Ready` only move us to [VoicePhase.LISTENING] from the connecting phase: once a
 * turn is under way, the finer THINKING/SPEAKING phases come from the bot callbacks and must not
 * be overwritten by a repeated transport notification.
 */
fun phaseForTransportState(state: String, current: VoicePhase): VoicePhase = when (state) {
    "Initializing", "Initialized", "Authorizing", "Authorized", "Connecting" -> VoicePhase.CONNECTING
    "Connected", "Ready" -> if (current.isSessionActive && current != VoicePhase.CONNECTING) {
        current
    } else {
        VoicePhase.LISTENING
    }
    "Disconnected" -> VoicePhase.WAITING_FOR_WAKE
    "Error" -> VoicePhase.ERROR
    else -> current
}
