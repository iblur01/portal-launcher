package com.iblu01.portallauncher.voice

/**
 * Voice assistant contract: the phases the launcher renders and the state behind them.
 *
 * The moving parts live in [VoiceAssistantController]; everything here is deliberately free of
 * Android so it can be unit-tested on the JVM.
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

    /** Wake word fired; opening the Live socket. */
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
    /**
     * A guarded action waiting for a physical tap, as the label to show. Non-null puts the
     * confirmation row on the overlay; see [VoiceGuard] for why voice alone is not enough.
     */
    val pendingConfirmation: String? = null,
    /** What the assistant announced it would do, and where it is in that list. */
    val plan: VoicePlan = VoicePlan(),
    /** Timestamp and confidence of the last wake-word detection in this process. */
    val lastWakeDetectionAt: Long? = null,
    val lastWakeDetectionScore: Float? = null,
)

/** Resolved configuration for one session. */
data class VoiceConfig(
    val gemini: GeminiConfig,
    val wakeWordAsset: String,
    val threshold: Float,
    val idleTimeoutMs: Long,
) {
    val isUsable: Boolean
        get() = gemini.apiKey.isNotBlank() && gemini.model.isNotBlank() && wakeWordAsset.isNotBlank()
}

/** Longest voice-requested timed sleep: one year, mainly a guard against malformed tool calls. */
const val MAX_VOICE_SLEEP_MINUTES = 365 * 24 * 60

/** Resolves the assistant's sleep tool without depending on Android, so boundary cases are tested. */
fun voiceSleepUntil(nowMs: Long, durationMinutes: Long?, untilReactivated: Boolean): Long? {
    if (untilReactivated) return Long.MAX_VALUE
    val minutes = durationMinutes ?: return null
    if (minutes !in 1..MAX_VOICE_SLEEP_MINUTES.toLong()) return null
    return nowMs + minutes * 60_000L
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
     *
     * The high clamp used to be 0.02, which is *above* the speech this panel actually captures
     * (measured: 0.008 to 0.017 RMS for someone talking to it from across the room). A bad
     * calibration therefore gated real speech away and the wake word went permanently deaf. The
     * cap now sits below anything that has ever been measured as speech here, while still leaving
     * a wide margin over a quiet room's 0.0002-0.0008.
     */
    val noiseGateRms: Float
        get() = (noiseFloor * 3f).coerceIn(DEFAULT_NOISE_GATE_RMS, MAX_NOISE_GATE_RMS)

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
        get() = (TARGET_SPEECH_RMS / (plausibleFloor * SPEECH_OVER_NOISE)).coerceIn(MIN_MAX_GAIN, 12f)

    /**
     * The floor as used by the derivations, kept inside what a room can physically read.
     *
     * A measurement of 0.0286 was recorded in the field — speech level, so the "silence" step had
     * heard someone talk. Fed to the formula it produced a gain cap of 1, which is the formula's
     * way of saying "amplify nothing", and the panel stopped hearing its wake word entirely. A
     * calibration is a hint about a room, never a licence to switch the gain off.
     */
    private val plausibleFloor: Float
        get() = noiseFloor.coerceIn(0.0001f, MAX_PLAUSIBLE_NOISE_FLOOR)

    /** False when the "silence" measurement clearly was not silence; the defaults are safer. */
    val isPlausible: Boolean
        get() = noiseFloor > 0f && noiseFloor <= MAX_PLAUSIBLE_NOISE_FLOOR && playbackRms > 0f

    companion object {
        /** RMS openWakeWord's training data centres on; the gain formula pulls speech toward it. */
        const val TARGET_SPEECH_RMS = 0.08f
        const val DEFAULT_NOISE_GATE_RMS = 0.002f
        const val DEFAULT_MAX_GAIN = 12f

        /** ~18 dB: what speech at 2-3 m typically measures above the room's noise floor. */
        const val SPEECH_OVER_NOISE = 8f

        /** Speech across the room reads 0.008-0.017 here: the gate must stay well under that. */
        const val MAX_NOISE_GATE_RMS = 0.005f

        /**
         * Above this, the "silence" measurement was not silence. A real room this loud would
         * make the wake word hopeless anyway, so the honest answer is to refuse the measurement
         * rather than derive settings from it.
         */
        const val MAX_PLAUSIBLE_NOISE_FLOOR = 0.01f

        /** The derivation may reduce the gain, never switch it off. */
        const val MIN_MAX_GAIN = 2f
    }
}
