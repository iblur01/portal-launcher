package com.iblu01.portallauncher.voice

import ai.pipecat.client.PipecatClient
import ai.pipecat.client.PipecatClientOptions
import ai.pipecat.client.PipecatEventCallbacks
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransport
import ai.pipecat.client.small_webrtc_transport.SmallWebRTCTransportConnectParams
import ai.pipecat.client.types.APIRequest
import ai.pipecat.client.types.LLMFunctionCallData
import ai.pipecat.client.types.LLMFunctionCallHandler
import ai.pipecat.client.types.Participant
import ai.pipecat.client.types.Transcript
import ai.pipecat.client.types.TransportState
import ai.pipecat.client.types.Value
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.iblu01.portallauncher.Prefs
import com.rementia.openwakeword.lib.model.WakeWordModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the Pipecat voice session on the panel.
 *
 * Shape of one interaction:
 *
 *  1. openWakeWord owns the microphone and scores every 80 ms frame locally. Nothing leaves the
 *     panel while waiting, so no audio and no STT bill for a room that is merely occupied.
 *  2. The wake word fires. The wake engine's `AudioRecord` is stopped *and joined* before WebRTC
 *     is offered: these panels run Android 9, where concurrent capture does not exist (it
 *     arrived in Android 10), so an overlapping recorder would make the session start deaf.
 *  3. A SmallWebRTC session is negotiated straight against the Pipecat Assist add-on's
 *     `/api/offer`. STT, LLM, tools and TTS all live server-side — the panel is a satellite.
 *  4. After [VoiceConfig.idleTimeoutMs] without anyone speaking, the session is dropped and the
 *     wake engine takes the microphone back.
 *
 * The Pipecat client requires a Looper thread, so every SDK call is made on the main dispatcher.
 */
@Singleton
class VoiceAssistantController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: Prefs,
) {
    private companion object {
        const val TAG = "VoiceAssistant"

        /** How long an error stays on screen before the wake engine restarts. */
        const val ERROR_LINGER_MS = 4_000L

        /** Lets the speaker tail die before the wake microphone is armed again. */
        const val POST_SESSION_GUARD_MS = 2_500L

        /**
         * Client-side tool the LLM calls to hang up ("stop", "au revoir", …). The server pipeline
         * must declare a function with this name and route it to the client through RTVI.
         */
        const val END_CONVERSATION_TOOL = "end_conversation"

        /** Hang-up fallback when the bot never speaks a goodbye after calling the tool. */
        const val GOODBYE_GRACE_MS = 10_000L

        /**
         * A genuine wake is followed by a question right away; if nothing is said in this window
         * the wake was almost certainly false (ambient conversation) and the session is dropped
         * without waiting for the full idle timeout.
         */
        const val FIRST_TURN_WINDOW_MS = 8_000L

        /**
         * Follow-up window after the bot's answer, Echo-style: a short beat for a rebound
         * question, then the microphone goes back to the wake engine instead of letting room
         * chatter keep the session alive indefinitely.
         */
        const val FOLLOW_UP_WINDOW_MS = 8_000L

        /** Safety net for a bot "speaking" state that never ends; rearmed by every transcript. */
        const val BOT_TURN_CEILING_MS = 90_000L

        /**
         * Lets the loudspeaker tail die before the microphone is opened again after the bot's
         * turn. Without echo cancellation the tail is heard as the user starting to speak.
         */
        const val MIC_REOPEN_DELAY_MS = 400L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Owns the wake engine's recording job exclusively, so cancelling it and joining its children
     * is a reliable "the microphone is free now" signal.
     */
    private val wakeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val _micCalibration = MutableStateFlow<MicCalibrationState?>(null)
    val micCalibration: StateFlow<MicCalibrationState?> = _micCalibration.asStateFlow()

    private var engine: PortalWakeWordEngine? = null
    private var engineKey: String? = null
    private var detectionJob: Job? = null
    private var scoreJob: Job? = null
    private var microphoneJob: Job? = null
    private var client: PipecatClient<SmallWebRTCTransport, SmallWebRTCTransportConnectParams>? = null
    private var idleJob: Job? = null
    private var postSessionRestartJob: Job? = null
    private var active = false

    /** Set when the LLM called [END_CONVERSATION_TOOL]; the hang-up waits for its goodbye. */
    private var hangUpRequested = false
    private var hangUpJob: Job? = null
    private var micReopenJob: Job? = null

    /** True while the launcher is in the foreground; the wake engine only runs then. */
    fun onResume() {
        active = true
        postSessionRestartJob?.cancel()
        scope.launch { restartWakeWord() }
    }

    fun onPause() {
        active = false
        postSessionRestartJob?.cancel()
        scope.launch {
            endSession()
            stopWakeWord()
            _state.value = _state.value.copy(phase = VoicePhase.DISABLED)
        }
    }

    /** Re-reads settings; call after the voice settings page is left. */
    fun onConfigChanged() {
        if (active) scope.launch { restartWakeWord() }
    }

    /** Manual trigger for a tile or pill: skips the wake word but follows the same hand-off. */
    fun startSessionNow() {
        scope.launch {
            if (_state.value.phase.isSessionActive) return@launch
            stopWakeWord()
            connect()
        }
    }

    /** User-initiated hang-up. */
    fun stopSession() {
        scope.launch {
            markSessionOver()
            endSession()
            scheduleWakeRestartAfterSession()
        }
    }

    /**
     * LLM-initiated hang-up. The session ends when the bot finishes its goodbye sentence
     * ([callbacks].onBotStoppedSpeaking), or after [GOODBYE_GRACE_MS] if it never says one.
     */
    private fun hangUpAfterBotTurn() {
        hangUpRequested = true
        hangUpJob?.cancel()
        hangUpJob = scope.launch {
            delay(GOODBYE_GRACE_MS)
            completeHangUp()
        }
    }

    private fun completeHangUp() {
        if (!hangUpRequested) return
        hangUpRequested = false
        hangUpJob?.cancel()
        hangUpJob = null
        scope.launch {
            markSessionOver()
            endSession()
            scheduleWakeRestartAfterSession()
        }
    }

    /**
     * Opens the session microphone once [afterMs] of panel-produced sound has finished, plus the
     * loudspeaker tail. Every caller is a moment where the panel itself was making noise, which
     * an uncancelled microphone would otherwise hear as the user speaking.
     */
    private fun openMicAfter(afterMs: Long) {
        micReopenJob?.cancel()
        micReopenJob = scope.launch {
            delay(afterMs + MIC_REOPEN_DELAY_MS)
            client?.enableMic(true)
        }
    }

    /**
     * Flips the UI out of the session immediately. The WebRTC teardown and the post-session guard
     * delay both take seconds; without this the overlay stays on screen for their whole duration
     * and the hang-up feels broken.
     */
    private fun markSessionOver() {
        _state.value = _state.value.copy(phase = VoicePhase.WAITING_FOR_WAKE)
    }

    // --- wake word ---------------------------------------------------------------------------

    private suspend fun restartWakeWord() {
        stopWakeWord()
        if (!active) return

        val config = readConfig()
        if (!prefs.voiceAssistantEnabled) {
            _state.value = VoiceUiState(phase = VoicePhase.DISABLED)
            return
        }
        if (!hasMicPermission()) {
            _state.value = VoiceUiState(phase = VoicePhase.MISSING_PERMISSION)
            return
        }
        if (!config.isUsable) {
            _state.value = VoiceUiState(phase = VoicePhase.MISCONFIGURED)
            return
        }

        val engine = obtainEngine(config) ?: run {
            _state.value = VoiceUiState(
                phase = VoicePhase.ERROR,
                error = "wake word model unavailable",
            )
            return
        }

        _state.value = VoiceUiState(
            phase = VoicePhase.WAITING_FOR_WAKE,
            wakeWord = wakeWordLabel(config.wakeWordAsset),
        )

        detectionJob = scope.launch {
            engine.detections.collect {
                Log.i(TAG, "wake word ${it.model.name} score=${it.score}")
                if (_state.value.phase != VoicePhase.WAITING_FOR_WAKE) return@collect
                _state.value = _state.value.copy(
                    lastWakeDetectionAt = it.timestamp,
                    lastWakeDetectionScore = it.score,
                )
                // The hand-off runs in its own coroutine: it cancels this collector as part of
                // releasing the microphone, and doing that from inside the collector would kill
                // the coroutine that still has to open the session.
                _state.value = _state.value.copy(phase = VoicePhase.CONNECTING)
                scope.launch {
                    stopWakeWord()
                    connect()
                }
            }
        }
        scoreJob = scope.launch {
            engine.scores.collect {
                _state.value = _state.value.copy(wakeScore = it.score.coerceIn(0f, 1f))
            }
        }
        microphoneJob = scope.launch {
            engine.microphoneLevels.collect {
                _state.value = _state.value.copy(microphoneLevel = it)
            }
        }
        runCatching { engine.start() }
            .onFailure {
                Log.e(TAG, "wake engine failed to start", it)
                _state.value = _state.value.copy(phase = VoicePhase.ERROR, error = it.message)
            }
    }

    /**
     * Stops capture and waits for the recorder to actually let go. [WakeWordEngine.stop] only
     * cancels its recording job; the `AudioRecord.release()` runs in that job's `finally`, so the
     * join below is what makes the microphone hand-off deterministic instead of a race.
     */
    private suspend fun stopWakeWord() {
        detectionJob?.cancel()
        detectionJob = null
        scoreJob?.cancel()
        scoreJob = null
        microphoneJob?.cancel()
        microphoneJob = null
        engine?.stop()
        wakeScope.coroutineContext.job.children.forEach { it.join() }
    }

    /** Keeps ONNX sessions loaded across wake cycles; only rebuilt when model or tuning change. */
    private fun obtainEngine(config: VoiceConfig): PortalWakeWordEngine? {
        val calibration = prefs.voiceMicCalibration
        // The threshold is baked into the WakeWordModel at construction, so it belongs in the key:
        // without it a cached engine kept answering with the threshold it was built with, and the
        // sensitivity slider did nothing at all until the process restarted.
        val key = "${config.wakeWordAsset}|${config.threshold}|$calibration"
        engine?.let { if (engineKey == key) return it }
        engine?.release()
        engine = null
        engineKey = null

        return runCatching {
            PortalWakeWordEngine(
                context = appContext,
                models = listOf(
                    WakeWordModel(
                        name = wakeWordLabel(config.wakeWordAsset),
                        modelPath = config.wakeWordAsset,
                        threshold = config.threshold,
                    ),
                ),
                scope = wakeScope,
                noiseGateRms = calibration?.noiseGateRms ?: MicCalibration.DEFAULT_NOISE_GATE_RMS,
                maxGain = calibration?.maxGain ?: MicCalibration.DEFAULT_MAX_GAIN,
            )
        }
            .onFailure { Log.e(TAG, "wake engine init failed", it) }
            .getOrNull()
            ?.also {
                engine = it
                engineKey = key
            }
    }

    /**
     * Runs the speaker-to-microphone calibration. The wake engine and the calibrator cannot share
     * the microphone (Android 9, no concurrent capture), so the engine is fully stopped first and
     * restarted after, picking up the new parameters through the engine key.
     */
    fun startMicCalibration() {
        scope.launch {
            if (_state.value.phase.isSessionActive) return@launch
            if (_micCalibration.value.let { it is MicCalibrationState.MeasuringNoise || it is MicCalibrationState.PlayingTone }) return@launch
            stopWakeWord()
            val outcome = MicCalibrator.calibrate(appContext) { _micCalibration.value = it }
            if (outcome is MicCalibrationState.Done) {
                Log.i(TAG, "mic calibration: floor=${outcome.result.noiseFloor} playback=${outcome.result.playbackRms}")
                prefs.voiceMicCalibration = outcome.result
            }
            if (active) restartWakeWord()
        }
    }

    // --- session -----------------------------------------------------------------------------

    private suspend fun connect() {
        val config = readConfig()
        if (!config.isUsable) {
            _state.value = _state.value.copy(phase = VoicePhase.MISCONFIGURED)
            return
        }

        _state.value = _state.value.copy(
            phase = VoicePhase.CONNECTING,
            userText = "",
            botText = "",
            error = null,
        )
        // Single choke point for both the wake word and the manual trigger: the panel is deaf
        // from here until the WebRTC microphone is live, so say "heard you, hold on" out loud.
        VoiceCues.play(scope, VoiceCue.WAITING)

        val newClient = PipecatClient(
            transport = SmallWebRTCTransport(appContext),
            // The microphone stays shut until the "speak now" cue has finished playing. With no
            // echo cancellation on this panel, a cue that sounds while the session microphone is
            // open is captured, transcribed as gibberish, and answered by the bot before the user
            // has said a word.
            options = PipecatClientOptions(callbacks = callbacks, enableMic = false, enableCam = false),
        )
        client = newClient
        hangUpRequested = false
        hangUpJob?.cancel()
        hangUpJob = null

        // The LLM hangs up by itself when the user says goodbye: the server declares this tool
        // and forwards its calls to us over RTVI. The result is acked immediately so the bot can
        // still speak a goodbye; the actual teardown waits for that goodbye to finish.
        newClient.registerFunctionCallHandler(
            END_CONVERSATION_TOOL,
            object : LLMFunctionCallHandler {
                override fun handleFunctionCall(data: LLMFunctionCallData, onResult: (Value) -> Unit) {
                    Log.i(TAG, "LLM requested hang-up via $END_CONVERSATION_TOOL")
                    onResult(Value.Object("status" to Value.Str("ending_session")))
                    scope.launch { hangUpAfterBotTurn() }
                }
            },
        )

        val request = APIRequest(
            endpoint = config.offerUrl,
            // The add-on keys its per-satellite conversation memory off this id, so every panel
            // gets its own thread instead of sharing one with the ESP32 satellites.
            requestData = Value.Object("device_id" to Value.Str(prefs.deviceName)),
            headers = if (config.token.isBlank()) {
                emptyMap()
            } else {
                mapOf("Authorization" to "Bearer ${config.token}")
            },
        )

        // The transport's audio module records on VOICE_COMMUNICATION and plays on the voice-call
        // usage, but never switches the audio mode to match. Every WebRTC app does this itself:
        // on MediaTek HALs the mismatch leaves the communication path half-configured, which
        // surfaces as crackle in the bot's speech.
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        val result = newClient.connect(SmallWebRTCTransportConnectParams(webrtcRequestParams = request))
            .awaitNoThrow()

        result.errorOrNull?.let { error ->
            Log.e(TAG, "connect failed: $error")
            failSession(error.description)
            return
        }

        // Wall panels have no earpiece worth using; force the loudspeaker.
        newClient.updateMic(SmallWebRTCTransport.AudioDevices.Speakerphone.id)
        armIdleTimeout(FIRST_TURN_WINDOW_MS)
    }

    private suspend fun endSession() {
        idleJob?.cancel()
        idleJob = null
        hangUpJob?.cancel()
        hangUpJob = null
        hangUpRequested = false
        micReopenJob?.cancel()
        micReopenJob = null
        val current = client ?: return
        client = null
        runCatching { current.disconnect().awaitNoThrow() }
        current.release()
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    /**
     * A freshly stopped WebRTC session can leave the bot's final syllables in the loudspeaker and
     * the ONNX feature window. Reusing that window immediately caused a 100% false wake and an
     * endless reconnect loop. Wait for the acoustic tail, then rebuild the processor from empty.
     */
    private fun scheduleWakeRestartAfterSession(delayMs: Long = POST_SESSION_GUARD_MS) {
        postSessionRestartJob?.cancel()
        postSessionRestartJob = scope.launch {
            delay(delayMs)
            stopWakeWord()
            engine?.release()
            engine = null
            engineKey = null
            restartWakeWord()
        }
    }

    private fun failSession(message: String?) {
        scope.launch {
            endSession()
            _state.value = _state.value.copy(phase = VoicePhase.ERROR, error = message)
            delay(ERROR_LINGER_MS)
            if (_state.value.phase == VoicePhase.ERROR) scheduleWakeRestartAfterSession(0L)
        }
    }

    /**
     * Drops the session after a silent stretch. Which window applies depends on where the
     * conversation is: a short one right after connect ([FIRST_TURN_WINDOW_MS]) and after the
     * bot's answer ([FOLLOW_UP_WINDOW_MS]), the user-configured idle timeout as the in-turn
     * ceiling while an exchange is actually under way.
     */
    private fun armIdleTimeout(timeoutMs: Long) {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(timeoutMs)
            Log.i(TAG, "idle timeout, closing session")
            idleJob = null
            markSessionOver()
            endSession()
            scheduleWakeRestartAfterSession()
        }
    }

    private fun onActivity() {
        val timeout = readConfig().idleTimeoutMs
        if (client != null) armIdleTimeout(timeout)
    }

    private val callbacks = object : PipecatEventCallbacks() {
        override fun onBackendError(message: String) {
            Log.e(TAG, "backend error: $message")
            failSession(message)
        }

        override fun onTransportStateChanged(state: TransportState) {
            // A client being torn down still emits transitions; letting them through would
            // resurrect the overlay right after markSessionOver() dismissed it.
            if (client == null) return
            val previous = _state.value.phase
            val phase = phaseForTransportState(state.name, previous)
            // The transport reaching LISTENING is the exact instant the microphone is live; the
            // "speak now" cue belongs here and nowhere earlier.
            if (previous == VoicePhase.CONNECTING && phase == VoicePhase.LISTENING) {
                VoiceCues.play(scope, VoiceCue.READY)
                openMicAfter(VoiceCue.READY.durationMs.toLong())
            }
            // Disconnections are handled by whoever asked for them (idle timeout, pause, error),
            // which already schedules the wake engine restart; don't race it from here.
            if (phase != VoicePhase.WAITING_FOR_WAKE) {
                _state.value = _state.value.copy(phase = phase)
            }
        }

        override fun onBotDisconnected(participant: Participant) {
            scope.launch {
                markSessionOver()
                endSession()
                scheduleWakeRestartAfterSession()
            }
        }

        override fun onUserStartedSpeaking() {
            Log.i(TAG, "user started speaking")
            onActivity()
            _state.value = _state.value.copy(phase = VoicePhase.LISTENING, botText = "")
        }

        override fun onUserStoppedSpeaking() {
            Log.i(TAG, "user stopped speaking")
            onActivity()
            _state.value = _state.value.copy(phase = VoicePhase.THINKING)
        }

        override fun onUserTranscript(data: Transcript) {
            Log.i(TAG, "user transcript: ${data.text.take(80)}")
            onActivity()
            _state.value = _state.value.copy(userText = data.text)
        }

        override fun onBotTranscript(text: String) {
            // Transcript keeps streaming for as long as the answer is being spoken: the clearest
            // "still busy" signal there is, so it keeps the ceiling from firing mid-sentence.
            if (client != null) armIdleTimeout(BOT_TURN_CEILING_MS)
            _state.value = _state.value.copy(botText = text)
        }

        override fun onBotStartedSpeaking() {
            Log.i(TAG, "bot started speaking")
            // Half duplex, because this panel has no working echo cancellation: the HAL reports a
            // hardware canceller, so WebRTC leaves its own switched off, but the MediaTek capture
            // path runs with BesRecord disabled and cancels nothing. An open microphone therefore
            // hears the bot's own voice, which the server transcribes as the user interrupting.
            micReopenJob?.cancel()
            client?.enableMic(false)
            // A single arm of the user idle timeout here cut long answers off after 20 s. While
            // the bot talks nobody is expected to speak, so the only job of the timer is to catch
            // a "speaking" state that never ends.
            if (client != null) armIdleTimeout(BOT_TURN_CEILING_MS)
            _state.value = _state.value.copy(phase = VoicePhase.SPEAKING)
        }

        override fun onBotStoppedSpeaking() {
            Log.i(TAG, "bot stopped speaking")
            if (hangUpRequested) {
                completeHangUp()
                return
            }
            openMicAfter(0L)
            // Follow-up beat: the configured idle timeout, which is exactly the "silence after
            // which we hang up" the user sets. Only the first turn gets a shorter fuse, since
            // that one guards against a false wake rather than against a lull in a real exchange.
            if (client != null) armIdleTimeout(readConfig().idleTimeoutMs)
            _state.value = _state.value.copy(phase = VoicePhase.LISTENING)
        }
    }

    // --- config ------------------------------------------------------------------------------

    private fun readConfig(): VoiceConfig {
        val endpoint = parseVoiceEndpoint(prefs.voiceAssistantUrl)
        return VoiceConfig(
            offerUrl = endpoint?.offerUrl.orEmpty(),
            // A token pasted inside the URL wins only when no explicit one is stored, so the
            // secure field stays authoritative once the user fills it in.
            token = prefs.voiceAssistantToken.ifBlank { endpoint?.token.orEmpty() },
            wakeWordAsset = prefs.voiceAssistantWakeWord,
            threshold = prefs.voiceAssistantThreshold / 100f,
            idleTimeoutMs = prefs.voiceAssistantIdleSeconds * 1000L,
        )
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/** `wakeword/hey_jarvis_v0.1.onnx` -> `hey jarvis`. */
internal fun wakeWordLabel(asset: String): String = asset
    .substringAfterLast('/')
    .removeSuffix(".onnx")
    .replace(Regex("_v\\d+(\\.\\d+)*$"), "")
    .replace('_', ' ')
