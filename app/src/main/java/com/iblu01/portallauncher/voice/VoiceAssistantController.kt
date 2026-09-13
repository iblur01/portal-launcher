package com.iblu01.portallauncher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import androidx.core.content.ContextCompat
import com.iblu01.portallauncher.VoiceMuteState
import com.iblu01.portallauncher.HaApiClient
import com.iblu01.portallauncher.DeviceStateHub
import com.iblu01.portallauncher.DisplayMode
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.PillRepository
import com.rementia.openwakeword.lib.model.WakeWordModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.iblu01.portallauncher.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the voice session on the panel.
 *
 * Shape of one interaction:
 *
 *  1. openWakeWord owns the microphone and scores every 80 ms frame locally. Nothing leaves the
 *     panel while waiting, so no audio and no bill for a room that is merely occupied.
 *  2. The wake word fires. The wake engine's `AudioRecord` is stopped *and joined* before the
 *     session opens its own: these panels run Android 9, where concurrent capture does not exist
 *     (it arrived in Android 10), so an overlapping recorder would make the session start deaf.
 *  3. A Gemini Live WebSocket is opened straight from the panel: speech in, speech out, one hop.
 *     This replaced the Pipecat Assist satellite, whose STT → LLM → TTS chain behind a WebRTC
 *     negotiation was both the latency and the instability. The cost of talking to Gemini
 *     directly is that it knows nothing about the home, so Home Assistant is handed to it as
 *     tools ([VoiceTools]) — including the one it uses to hang up on itself.
 *  4. After [VoiceConfig.idleTimeoutMs] without anyone speaking, the session is dropped and the
 *     wake engine takes the microphone back.
 */
@Singleton
class VoiceAssistantController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val prefs: Prefs,
    private val pills: PillRepository,
) {
    private companion object {
        const val TAG = "VoiceAssistant"

        /**
         * How long a session resumption handle is reused. The server keeps them valid for about
         * two hours, which is far too long here: resuming an hour-old exchange answers with a
         * picture of a house that has since changed. A few minutes is the window in which "and
         * the kitchen too" is still the same conversation.
         */
        const val RESUMPTION_WINDOW_MS = 5 * 60_000L

        /** How long an error stays on screen before the wake engine restarts. */
        const val ERROR_LINGER_MS = 4_000L

        /** Lets the speaker tail die before the wake microphone is armed again. */
        const val POST_SESSION_GUARD_MS = 2_500L

        /** Hang-up fallback when the model never speaks a goodbye after calling the tool. */
        const val GOODBYE_GRACE_MS = 10_000L

        /**
         * A genuine wake is followed by a question right away; if nothing is said in this window
         * the wake was almost certainly false (ambient conversation) and the session is dropped
         * without waiting for the full idle timeout.
         */
        const val FIRST_TURN_WINDOW_MS = 8_000L

        /** Safety net for a "speaking" state that never ends; rearmed by every transcript. */
        const val BOT_TURN_CEILING_MS = 90_000L

        /**
         * Lets the loudspeaker tail die before the microphone is opened again after the model's
         * turn. Without echo cancellation the tail is heard as the user starting to speak.
         */
        const val MIC_REOPEN_DELAY_MS = 400L

        /**
         * Silence after the user's last transcript chunk before the overlay says "thinking".
         * Server-side voice detection gives us no end-of-turn event, and the model's first audio
         * can be a second away — without this the panel looks like it stopped listening.
         */
        const val THINKING_DEBOUNCE_MS = 700L

        /**
         * Below this displayed level the microphone is not quiet, it is dead.
         *
         * Not `> 0` — that was the first attempt and it never triggered: a wedged HAL still
         * dithers by a bit or two, which is a non-zero level that reset the timer forever while
         * the telemetry printed a flat "rms 0.0000". The quietest real room measured on this
         * panel sits at 0.0007 RMS, which is level 0.007, so this floor keeps a 3x margin.
         */
        const val DEAD_MIC_LEVEL = 0.002f

        /** How long the microphone may stay under [DEAD_MIC_LEVEL] before the capture is rebuilt. */
        const val DEAD_MIC_TIMEOUT_MS = 8_000L

        /** Rebuilds attempted back to back before the retries space out. */
        const val DEAD_MIC_MAX_RECOVERIES = 2

        /**
         * How often a stubbornly flat microphone is retried after that. Long enough to be free,
         * short enough that a panel left alone for the night is listening again by morning.
         */
        const val DEAD_MIC_RETRY_INTERVAL_MS = 60_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Owns the wake engine's recording job exclusively, so cancelling it and joining its children
     * is a reliable "the microphone is free now" signal.
     */
    private val wakeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Serialises every change of the wake engine's lifecycle.
     *
     * Without it the dead-microphone recovery and a session opening interleaved: the recovery had
     * already nulled [engine] when [connect] called [stopWakeWord], so nothing stopped the engine
     * the recovery then created — and the panel ran a wake-word recorder alongside the live
     * session, which on Android 9 means both read silence.
     */
    private val engineMutex = Mutex()

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    /**
     * Panel navigation asked for by the assistant. A [SharedFlow] rather than state: showing the
     * thermostat is an event, and a launcher that comes back to the foreground must not replay
     * the panel someone asked for ten minutes ago. Dropped when nothing is collecting, which is
     * exactly right — no launcher on screen, nothing to navigate.
     */
    private val _portalCommands = MutableSharedFlow<PortalCommand>(extraBufferCapacity = 8)
    val portalCommands: SharedFlow<PortalCommand> = _portalCommands.asSharedFlow()

    private val _micCalibration = MutableStateFlow<MicCalibrationState?>(null)
    val micCalibration: StateFlow<MicCalibrationState?> = _micCalibration.asStateFlow()

    private var engine: PortalWakeWordEngine? = null
    private var engineKey: String? = null
    private var detectionJob: Job? = null
    private var scoreJob: Job? = null
    private var microphoneJob: Job? = null
    private var session: GeminiVoiceSession? = null
    private var tools: VoiceTools? = null
    private val portalSettings = PortalSettingsTool(appContext, prefs, pills)
    private var idleJob: Job? = null
    private var postSessionRestartJob: Job? = null
    private var thinkingJob: Job? = null
    private var muteExpiryJob: Job? = null
    private var sleepAfterTurnJob: Job? = null
    private var sleepRequestedUntilMs: Long? = null
    private var active = false
    private var bargeIn = false
    private var deadMicRecoveries = 0
    private var lastDeadMicRetryAt = 0L

    /**
     * Last resumption handle the server offered, and when. Kept in memory only: it is worth
     * exactly one follow-up ("and the kitchen too" after a hang-up), and a handle surviving a
     * reboot would resume a conversation about a house whose state has moved on.
     */
    private var resumptionHandle: String? = null
    private var resumptionHandleAt = 0L

    /** Tool calls the user interrupted; their results are dropped instead of being sent. */
    private val cancelledToolCalls = mutableSetOf<String>()

    /** A guarded action waiting for a tap on the screen. See [VoiceGuard]. */
    private var pendingConfirmation: PendingConfirmation? = null

    private val _toolCalls = MutableStateFlow<List<VoiceToolCall>>(emptyList())

    /** Last [MAX_TOOL_CALL_LOG] tool calls, newest first. Shown in the voice settings page. */
    val toolCalls: StateFlow<List<VoiceToolCall>> = _toolCalls.asStateFlow()

    private data class PendingConfirmation(val tool: String, val args: JSONObject, val label: String)

    /** Set when the model called [GeminiLive.END_CONVERSATION_TOOL]; hang-up waits for its goodbye. */
    private var hangUpRequested = false
    private var hangUpJob: Job? = null
    private var micReopenJob: Job? = null

    init {
        // A promise that fires during a live conversation is told to the assistant, so it can say
        // so instead of being asked about something it believes is still pending. Fired outside a
        // session, the action still runs — it simply has nobody to tell.
        VoiceScheduler.onFired = { action, result ->
            scope.launch {
                val ok = result["error"] == null && result["success"] != false
                session?.sendText(
                    if (ok) {
                        "The action you scheduled (\"${action.title}\") has just run. Mention it briefly."
                    } else {
                        "The action you scheduled (\"${action.title}\") has just failed: ${result["error"]}. Say so."
                    },
                )
            }
        }
        VoiceScheduler.arm(appContext, prefs)
    }

    /** True while the launcher is in the foreground; the wake engine only runs then. */
    fun onResume() {
        active = true
        postSessionRestartJob?.cancel()
        VoiceMuteState.restore(prefs)
        scheduleMuteExpiry()
        scope.launch { restartWakeWord() }
    }

    /**
     * The launcher losing the foreground is not the panel being left.
     *
     * A wall panel spends most of its life dreaming or on its own screensaver, and the screen
     * blanking mid-sentence used to run straight into [endSession]: the conversation died and the
     * user had to say the wake word again (seen in the field — the dream started 22 s into an
     * exchange and killed it). Worse, [active] going false left the wake engine unarmed for as
     * long as the panel dreamt, which is exactly when someone walks up and talks to it.
     *
     * So the assistant only stops when the panel is genuinely someone else's: a foreign app in
     * the foreground. The dream, the screensaver and a blank screen are still Portal.
     */
    fun onPause() {
        if (panelIsStillOurs()) return
        active = false
        postSessionRestartJob?.cancel()
        scope.launch {
            endSession()
            stopWakeWord()
            _state.value = _state.value.copy(phase = VoicePhase.DISABLED)
        }
    }

    private fun panelIsStillOurs(): Boolean {
        val state = DeviceStateHub.current
        if (state.display == DisplayMode.DREAMING ||
            state.display == DisplayMode.SCREENSAVER ||
            state.display == DisplayMode.OFF
        ) {
            return true
        }
        // Portal's own settings and web-config screens are not a foreign app either.
        return state.foregroundPackage.isNullOrBlank() || state.foregroundPackage == appContext.packageName
    }

    /** Re-reads settings; call after the voice settings page is left. */
    fun onConfigChanged() {
        VoiceMuteState.refresh(prefs)
        scheduleMuteExpiry()
        if (active) scope.launch { restartWakeWord() }
    }

    /** Manual trigger for a tile, a pill or the settings test: skips the wake word, same hand-off. */
    fun startSessionNow() {
        scope.launch {
            if (VoiceMuteState.muted) {
                Log.i(TAG, "session refused: assistant muted")
                return@launch
            }
            if (_state.value.phase.isSessionActive) return@launch
            stopWakeWord()
            // No false-wake fuse here: somebody pressed a button, so silence means they are
            // gathering their words, not that a passing conversation triggered the panel. The
            // short window closed a hand-started session after 8 s in the field.
            connect(firstTurnWindowMs = readConfig().idleTimeoutMs)
        }
    }

    /**
     * The user tapped Confirm on a guarded action. Runs it now and tells the assistant, which is
     * the only way it learns the outcome — the tool call it made was answered long before.
     */
    fun confirmPendingAction() {
        val pending = pendingConfirmation ?: return
        pendingConfirmation = null
        _state.value = _state.value.copy(pendingConfirmation = null)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runTool(pending.tool, pending.args) }
            val ok = result["success"] != false && result["error"] == null
            session?.sendText(
                if (ok) {
                    "The user confirmed \"${pending.label}\" on the panel and it is done. Say so briefly."
                } else {
                    "The user confirmed \"${pending.label}\" but it failed: ${result["error"] ?: "unknown error"}. Say so."
                },
            )
        }
    }

    /** The user tapped Cancel, or walked away and the session ended. */
    fun cancelPendingAction() {
        val pending = pendingConfirmation ?: return
        pendingConfirmation = null
        _state.value = _state.value.copy(pendingConfirmation = null)
        session?.sendText("The user refused \"${pending.label}\" on the panel. Do not do it. Acknowledge briefly.")
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
     * Model-initiated hang-up. The session ends when the goodbye sentence finishes playing, or
     * after [GOODBYE_GRACE_MS] if it never says one.
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
            session?.setMicEnabled(true)
        }
    }

    /**
     * Flips the UI out of the session immediately. Socket teardown and the post-session guard
     * delay both take a moment; without this the overlay lingers for their whole duration and
     * the hang-up feels broken.
     */
    private fun markSessionOver() {
        _state.value = _state.value.copy(phase = VoicePhase.WAITING_FOR_WAKE)
    }

    // --- wake word ---------------------------------------------------------------------------

    private suspend fun restartWakeWord() = engineMutex.withLock { restartWakeWordLocked() }

    private suspend fun restartWakeWordLocked() {
        // A live session owns the microphone, and Android 9 has no concurrent capture: arming the
        // wake engine now would make that session deaf. Happens for real when a session is started
        // remotely and the launcher is only then brought to the foreground.
        if (_state.value.phase.isSessionActive) return
        stopWakeWordLocked()
        if (!active) return

        VoiceMuteState.refresh(prefs)
        scheduleMuteExpiry()
        val config = readConfig()
        // Muted is not "off in settings": the feature stays configured, the microphone just does
        // not open. Same phase, because to everything downstream a closed microphone is a closed
        // microphone; the launcher reads VoiceMuteState for what to draw.
        if (VoiceMuteState.muted || !prefs.voiceAssistantEnabled) {
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
        var soundHeardAt = System.currentTimeMillis()
        microphoneJob = scope.launch {
            engine.microphoneLevels.collect { level ->
                _state.value = _state.value.copy(microphoneLevel = level)
                // Silence only means "dead" while the engine is supposed to own the microphone.
                // A live session owns it instead, and on Android 9 the loser of a concurrent
                // capture reads exact zeros — which this watchdog read as a hardware failure,
                // announced "restart the panel" after every conversation, and re-armed the engine
                // into the session's own microphone.
                if (_state.value.phase != VoicePhase.WAITING_FOR_WAKE) {
                    soundHeardAt = System.currentTimeMillis()
                    return@collect
                }
                if (level > DEAD_MIC_LEVEL) {
                    soundHeardAt = System.currentTimeMillis()
                    deadMicRecoveries = 0
                    return@collect
                }
                if (System.currentTimeMillis() - soundHeardAt < DEAD_MIC_TIMEOUT_MS) return@collect
                soundHeardAt = System.currentTimeMillis()
                // Slows down after the first tries, never stops: giving up was a deadlock. Only
                // a fresh recorder revives this panel's input stream, so a watchdog that stops
                // rebuilding guarantees the microphone stays dead — measured on the panel, where
                // a manual process restart brought a "permanently dead" stream straight back.
                if (deadMicRecoveries >= DEAD_MIC_MAX_RECOVERIES &&
                    System.currentTimeMillis() - lastDeadMicRetryAt < DEAD_MIC_RETRY_INTERVAL_MS
                ) {
                    return@collect
                }
                Log.w(TAG, "microphone flat (level=$level) for ${DEAD_MIC_TIMEOUT_MS}ms, rebuilding capture")
                recoverDeadMicrophone()
            }
        }
        runCatching { engine.start() }
            .onFailure {
                Log.e(TAG, "wake engine failed to start", it)
                _state.value = _state.value.copy(phase = VoicePhase.ERROR, error = it.message)
            }
    }

    /**
     * Stops capture and waits for the recorder to actually let go. [PortalWakeWordEngine.stop]
     * only cancels its recording job; the `AudioRecord.release()` runs in that job's `finally`,
     * so the join below is what makes the microphone hand-off deterministic instead of a race.
     */
    private suspend fun stopWakeWord() = engineMutex.withLock { stopWakeWordLocked() }

    private suspend fun stopWakeWordLocked() {
        detectionJob?.cancel()
        detectionJob = null
        scoreJob?.cancel()
        scoreJob = null
        microphoneJob?.cancel()
        microphoneJob = null
        engine?.stop()
        wakeScope.coroutineContext.job.children.forEach { it.join() }
    }

    /**
     * Rebuilds the whole capture chain after the HAL handed us a dead input stream. The audio
     * mode is bounced through NORMAL first: on this panel that is what makes the HAL open a
     * fresh input path instead of handing back the same silent one.
     */
    private fun recoverDeadMicrophone() {
        scope.launch {
            if (_state.value.phase.isSessionActive) return@launch
            deadMicRecoveries++
            lastDeadMicRetryAt = System.currentTimeMillis()
            engineMutex.withLock {
                stopWakeWordLocked()
                engine?.release()
                engine = null
                engineKey = null
            }
            // Bouncing the mode is the actual attempt: it makes the HAL tear the input path down
            // and open a new one. Reopening AudioRecord on its own hands back the same silent
            // stream — a whole process restart does, which is how this was diagnosed.
            withContext(Dispatchers.IO) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                Thread.sleep(400)
                audioManager.mode = AudioManager.MODE_NORMAL
                Thread.sleep(400)
            }
            // Re-checked after the wait: a session may have opened while the HAL was bouncing,
            // and re-arming into its microphone is the bug this recovery used to cause.
            if (!_state.value.phase.isSessionActive) restartWakeWord()
        }
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

    private fun connect(firstTurnWindowMs: Long = FIRST_TURN_WINDOW_MS) {
        val config = readConfig()
        if (!config.isUsable) {
            _state.value = _state.value.copy(phase = VoicePhase.MISCONFIGURED)
            return
        }
        val limit = prefs.voiceDailySessionLimit
        if (limit > 0 && prefs.voiceSessionsToday(today()) >= limit) {
            Log.w(TAG, "daily session limit ($limit) reached")
            failSession(appContext.getString(R.string.voice_error_daily_limit))
            return
        }
        prefs.recordVoiceSession(today())

        _state.value = _state.value.copy(
            phase = VoicePhase.CONNECTING,
            userText = "",
            botText = "",
            error = null,
            // An unfinished plan is kept when the previous session is still recent: someone who
            // re-wakes the panel ten seconds after a socket died mid-plan is continuing the same
            // work, and wiping the checklist would hide what was already done.
            plan = if (isRecentSession()) _state.value.plan else VoicePlan(),
        )
        // Single choke point for both the wake word and the manual trigger: the panel is deaf
        // from here until the session microphone is live, so say "heard you, hold on" out loud.
        VoiceCues.play(scope, VoiceCue.WAITING)

        hangUpRequested = false
        hangUpJob?.cancel()
        hangUpJob = null
        bargeIn = config.gemini.bargeIn
        tools = VoiceTools(HaApiClient(prefs.haUrl, prefs.haToken))

        // The capture source is VOICE_COMMUNICATION and playback runs on the voice-call usage,
        // but neither switches the audio mode to match. Every duplex-audio app does this itself:
        // on MediaTek HALs the mismatch leaves the communication path half-configured, which
        // surfaces as crackle in the assistant's speech.
        audioManager.mode = VOICE_SESSION_AUDIO_MODE

        // Warmed here rather than on the first tool call: the list is a full state download, and
        // paid inside a conversation it reads as the assistant freezing. The cue and the socket
        // handshake are dead time anyway.
        val warming = tools
        scope.launch(Dispatchers.IO) { runCatching { warming?.guardedNames() } }

        session = GeminiVoiceSession(config.gemini, listener).also { it.start() }
        armIdleTimeout(firstTurnWindowMs)
    }

    /**
     * A step that was running when the session died did not finish, and must not be left looking
     * like it is still going: the user gets a failed step on screen and the truth.
     */
    private fun markRunningStepFailed() {
        val plan = _state.value.plan
        if (plan.runningIndex != null) {
            _state.value = _state.value.copy(plan = plan.completeCurrent(failed = true))
        }
    }

    private suspend fun endSession() {
        markRunningStepFailed()
        pendingConfirmation = null
        _state.value = _state.value.copy(pendingConfirmation = null)
        idleJob?.cancel()
        idleJob = null
        hangUpJob?.cancel()
        hangUpJob = null
        hangUpRequested = false
        micReopenJob?.cancel()
        micReopenJob = null
        cancelledToolCalls.clear()
        thinkingJob?.cancel()
        thinkingJob = null
        sleepAfterTurnJob?.cancel()
        sleepAfterTurnJob = null
        sleepRequestedUntilMs = null
        val current = session
        session = null
        tools = null
        // Off the main thread: close() waits for the audio threads to hand the devices back, and
        // that wait is exactly what protects the microphone (see GeminiVoiceSession.close).
        if (current != null) withContext(Dispatchers.IO) { current.close() }
        // Outside the null check on purpose: an early return here left the panel in
        // MODE_IN_COMMUNICATION, and in that mode the wake engine's own capture is routed through
        // the call path and records far too quietly to ever fire again.
        if (audioManager.mode != AudioManager.MODE_NORMAL) audioManager.mode = AudioManager.MODE_NORMAL
    }

    /**
     * A freshly stopped session can leave the assistant's final syllables in the loudspeaker and
     * the ONNX feature window. Reusing that window immediately caused a 100% false wake and an
     * endless reconnect loop. Wait for the acoustic tail, then rebuild the processor from empty.
     */
    private fun scheduleWakeRestartAfterSession(delayMs: Long = POST_SESSION_GUARD_MS) {
        postSessionRestartJob?.cancel()
        postSessionRestartJob = scope.launch {
            delay(delayMs)
            engineMutex.withLock {
                stopWakeWordLocked()
                engine?.release()
                engine = null
                engineKey = null
                restartWakeWordLocked()
            }
        }
    }

    /** Arms the automatic reactivation for a persisted temporary mute. */
    private fun scheduleMuteExpiry() {
        muteExpiryJob?.cancel()
        muteExpiryJob = null
        val until = prefs.voiceMutedUntilMs
        if (until <= 0L || until == Long.MAX_VALUE) return
        val remaining = until - System.currentTimeMillis()
        if (remaining <= 0L) {
            if (VoiceMuteState.refresh(prefs) && active) scope.launch { restartWakeWord() }
            return
        }
        muteExpiryJob = scope.launch {
            delay(remaining)
            muteExpiryJob = null
            VoiceMuteState.refresh(prefs)
            if (active) restartWakeWord()
        }
    }

    /** Applies a sleep only after the acknowledgement has finished playing. */
    private fun completeAssistantSleep() {
        val until = sleepRequestedUntilMs ?: return
        sleepRequestedUntilMs = null
        sleepAfterTurnJob?.cancel()
        sleepAfterTurnJob = null
        VoiceMuteState.sleepUntil(prefs, until)
        scheduleMuteExpiry()
        scope.launch {
            markSessionOver()
            endSession()
            _state.value = VoiceUiState(phase = VoicePhase.DISABLED)
        }
    }

    /** A false wake has no closing phrase and must not leak into the next real conversation. */
    private fun dismissSessionSilently() {
        resumptionHandle = null
        resumptionHandleAt = 0L
        scope.launch {
            markSessionOver()
            endSession()
            scheduleWakeRestartAfterSession()
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
     * conversation is: a short one right after connect ([FIRST_TURN_WINDOW_MS]), the
     * user-configured idle timeout for the lull after an answer, and [BOT_TURN_CEILING_MS] as a
     * ceiling while the assistant is actually talking.
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

    private val listener = object : GeminiVoiceSession.Listener {
        override fun onResumptionHandle(handle: String) = onMain {
            resumptionHandle = handle
            resumptionHandleAt = System.currentTimeMillis()
        }

        override fun onToolCallsCancelled(ids: List<String>) = onMain {
            Log.i(TAG, "tool calls cancelled: $ids")
            cancelledToolCalls += ids
        }

        override fun onReady() = onMain {
            if (session == null) return@onMain
            // The socket being set up is the instant the microphone starts mattering; the
            // "speak now" cue belongs here and nowhere earlier.
            VoiceCues.play(scope, VoiceCue.READY)
            openMicAfter(VoiceCue.READY.durationMs.toLong())
            _state.value = _state.value.copy(phase = VoicePhase.LISTENING)
        }

        override fun onUserTranscript(text: String) = onMain {
            if (session == null) return@onMain
            onActivity()
            // Transcripts arrive as increments, not as the whole sentence.
            _state.value = _state.value.copy(
                phase = VoicePhase.LISTENING,
                userText = _state.value.userText + text,
                botText = "",
            )
            thinkingJob?.cancel()
            thinkingJob = scope.launch {
                delay(THINKING_DEBOUNCE_MS)
                if (_state.value.phase == VoicePhase.LISTENING) {
                    _state.value = _state.value.copy(phase = VoicePhase.THINKING)
                }
            }
        }

        override fun onBotTranscript(text: String) = onMain {
            if (session == null) return@onMain
            // Transcript keeps streaming for as long as the answer is being spoken: the clearest
            // "still busy" signal there is, so it keeps the ceiling from firing mid-sentence.
            armIdleTimeout(BOT_TURN_CEILING_MS)
            _state.value = _state.value.copy(botText = _state.value.botText + text)
        }

        override fun onBotStartedSpeaking() = onMain {
            if (session == null) return@onMain
            Log.i(TAG, "assistant speaking (bargeIn=$bargeIn)")
            thinkingJob?.cancel()
            // Half duplex unless the user enabled barge-in and the device can really cancel its
            // echo: an open microphone otherwise hears the assistant's own voice, which the
            // server's voice detection reads as the user interrupting.
            if (!bargeIn) {
                micReopenJob?.cancel()
                session?.setMicEnabled(false)
            }
            // A single arm of the user idle timeout here cut long answers off. While the
            // assistant talks nobody is expected to speak, so the only job of the timer is to
            // catch a "speaking" state that never ends.
            armIdleTimeout(BOT_TURN_CEILING_MS)
            _state.value = _state.value.copy(phase = VoicePhase.SPEAKING, userText = _state.value.userText)
        }

        override fun onBotStoppedSpeaking() = onMain {
            if (session == null) return@onMain
            Log.i(TAG, "assistant stopped speaking")
            if (sleepRequestedUntilMs != null) {
                completeAssistantSleep()
                return@onMain
            }
            if (hangUpRequested) {
                completeHangUp()
                return@onMain
            }
            if (!bargeIn) openMicAfter(0L)
            // Follow-up beat: the configured idle timeout, which is exactly the "silence after
            // which we hang up" the user sets. Only the first turn gets a shorter fuse, since
            // that one guards against a false wake rather than against a lull in a real exchange.
            armIdleTimeout(readConfig().idleTimeoutMs)
            _state.value = _state.value.copy(phase = VoicePhase.LISTENING, userText = "")
        }

        /**
         * Tool calls are answered even when the answer is an error: an unanswered call leaves the
         * model waiting in silence, which on a wall panel is indistinguishable from a crash.
         */
        override fun onToolCall(id: String?, name: String, args: JSONObject) = onMain {
            Log.i(TAG, "tool call $name $args")
            if (name == GeminiLive.DISMISS_CONVERSATION_TOOL) {
                // The model is explicitly instructed to call before producing audio. Acking the
                // protocol call then tearing down locally yields no spoken response or end cue.
                session?.sendToolResponse(id, name, mapOf("status" to "dismissed"))
                dismissSessionSilently()
                return@onMain
            }
            if (name == GeminiLive.SLEEP_ASSISTANT_TOOL) {
                val until = voiceSleepUntil(
                    nowMs = System.currentTimeMillis(),
                    durationMinutes = args.optLong("duration_minutes", Long.MIN_VALUE)
                        .takeUnless { it == Long.MIN_VALUE },
                    untilReactivated = args.optBoolean("until_reactivated", false),
                )
                if (until == null) {
                    session?.sendToolResponse(
                        id,
                        name,
                        mapOf("error" to "provide duration_minutes or until_reactivated=true"),
                    )
                } else {
                    sleepRequestedUntilMs = until
                    session?.sendToolResponse(
                        id,
                        name,
                        mapOf(
                            "status" to "will_sleep_after_this_turn",
                            "until_reactivated" to (until == Long.MAX_VALUE),
                        ),
                    )
                    // A model that calls the tool but never completes an audio turn must not
                    // leave the microphone open forever. Normal completion cancels this fuse.
                    sleepAfterTurnJob?.cancel()
                    sleepAfterTurnJob = scope.launch {
                        delay(GOODBYE_GRACE_MS)
                        completeAssistantSleep()
                    }
                }
                onActivity()
                return@onMain
            }
            localToolResult(name, args)?.let { result ->
                logToolCall(name, args, result)
                session?.sendToolResponse(id, name, result)
                onActivity()
                return@onMain
            }
            if (name == GeminiLive.END_CONVERSATION_TOOL) {
                // Acked immediately so the assistant can still speak its goodbye; the actual
                // teardown waits for that goodbye to finish playing.
                session?.sendToolResponse(id, name, mapOf("status" to "ending_session"))
                hangUpAfterBotTurn()
                return@onMain
            }
            onActivity()
            scope.launch {
                // The guard check reads Home Assistant (it needs this home's guarded names), so
                // it belongs on IO with the call itself.
                val guardLabel = withContext(Dispatchers.IO) { confirmationLabelFor(name, args) }
                val result = if (guardLabel != null) {
                    pendingConfirmation = PendingConfirmation(name, args, guardLabel)
                    _state.value = _state.value.copy(pendingConfirmation = guardLabel)
                    mapOf(
                        "status" to "awaiting_physical_confirmation",
                        "instruction" to "This action can let someone into the home, so the " +
                            "panel is asking for a tap on screen. Tell the user to confirm on " +
                            "the panel, then stop and wait: you will be told the outcome.",
                    )
                } else {
                    withContext(Dispatchers.IO) { runTool(name, args) }
                }
                logToolCall(name, args, result)
                // The user interrupted while Home Assistant was answering: the model has moved
                // on, so the result is dropped rather than pushed into a turn that no longer
                // expects it.
                if (id != null && cancelledToolCalls.remove(id)) {
                    Log.i(TAG, "dropping result of cancelled call $name")
                    return@launch
                }
                session?.sendToolResponse(id, name, result)
            }
        }

        override fun onClosed(reason: String?) = onMain {
            Log.i(TAG, "session closed: $reason")
            if (session == null) return@onMain
            markSessionOver()
            endSession()
            scheduleWakeRestartAfterSession()
        }

        override fun onError(message: String) = onMain {
            if (session == null) return@onMain
            Log.e(TAG, "session error: $message")
            failSession(message)
        }
    }

    /**
     * The session's callbacks arrive on its socket and audio threads; all state below is
     * main-thread only.
     */
    private inline fun onMain(crossinline block: suspend () -> Unit) {
        scope.launch { block() }
    }

    /**
     * The tools the panel answers itself: the plan the assistant shows on screen, and the
     * launcher's own navigation. They never touch Home Assistant, so they stay out of
     * [VoiceTools] and off the IO dispatcher. Returns null for anything else.
     */
    private fun localToolResult(name: String, args: JSONObject): Map<String, Any?>? = when (name) {
        GeminiLive.PLAN_TOOL -> {
            val titles = args.optJSONArray("tasks")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            } ?: emptyList()
            val plan = voicePlanOf(titles)
            _state.value = _state.value.copy(plan = plan)
            if (plan.isEmpty) {
                mapOf("error" to "no usable task titles")
            } else {
                mapOf("status" to "plan_shown", "steps" to plan.tasks.size)
            }
        }

        GeminiLive.COMPLETE_TASK_TOOL -> {
            val plan = _state.value.plan.completeCurrent(failed = args.optBoolean("failed"))
            _state.value = _state.value.copy(plan = plan)
            mapOf(
                "status" to if (plan.isFinished) "plan_finished" else "next_step",
                "remaining" to plan.tasks.count { it.status == VoiceTaskStatus.PENDING },
                "next" to (plan.runningIndex?.let { plan.tasks[it].title } ?: ""),
            )
        }

        GeminiLive.PORTAL_SHOW_TOOL -> {
            val target = args.optString("target")
            val command = portalCommandOf(target)
            if (command == null) {
                mapOf("error" to "unknown panel $target")
            } else {
                // Dropped when the launcher is not on screen; the assistant is told so rather
                // than left believing it displayed something.
                val delivered = _portalCommands.tryEmit(command)
                mapOf("success" to delivered, "shown" to target)
            }
        }

        GeminiLive.PORTAL_SETTINGS_TOOL -> portalSettings.execute(args)

        else -> null
    }

    /**
     * Everything that needs the network or the disk: Home Assistant's own tools, the promises the
     * assistant makes for later, and the facts it keeps between sessions. Blocking; callers are
     * on [Dispatchers.IO].
     */
    private fun runTool(name: String, args: JSONObject): Map<String, Any?> = when (name) {
        GeminiLive.SCHEDULE_TOOL -> {
            val action = VoiceScheduler.actionFrom(args)
            when {
                action == null -> mapOf("error" to "unusable schedule: need delay_minutes (1-$MAX_SCHEDULE_DELAY_MINUTES), tool and args")
                // A guarded action fires with nobody standing there to confirm it, which is the
                // one case where the tap cannot be asked for. Refused outright rather than
                // silently downgraded.
                confirmationLabelFor(action.tool, action.args) != null ->
                    mapOf("error" to "this action can let someone into the home and cannot be scheduled; it has to be asked for in the moment")
                VoiceScheduler.add(appContext, prefs, action) == null ->
                    mapOf("error" to "too many scheduled actions (max $MAX_SCHEDULED_ACTIONS)")
                else -> mapOf("success" to true, "id" to action.id, "fires_in_minutes" to args.optInt("delay_minutes"))
            }
        }

        GeminiLive.LIST_SCHEDULED_TOOL -> {
            val pending = VoiceScheduler.pending(prefs)
            mapOf(
                "count" to pending.size,
                "actions" to JSONArray(
                    pending.map {
                        mapOf(
                            "id" to it.id,
                            "title" to it.title,
                            "in_minutes" to ((it.atMs - System.currentTimeMillis()) / 60_000L),
                        )
                    },
                ),
            )
        }

        GeminiLive.CANCEL_SCHEDULED_TOOL -> {
            val id = args.optString("id")
            mapOf("success" to VoiceScheduler.cancel(appContext, prefs, id))
        }

        GeminiLive.REMEMBER_TOOL -> {
            val fact = args.optString("fact").trim()
            if (fact.isEmpty()) {
                mapOf("error" to "empty fact")
            } else {
                prefs.voiceFacts = prefs.voiceFacts + fact
                mapOf("success" to true, "remembered" to prefs.voiceFacts.size)
            }
        }

        GeminiLive.FORGET_TOOL -> {
            val fact = args.optString("fact").trim().lowercase()
            val before = prefs.voiceFacts
            val after = before.filterNot { it.lowercase().contains(fact) && fact.isNotEmpty() }
            prefs.voiceFacts = after
            mapOf("success" to (after.size < before.size))
        }

        else -> {
            val runner = tools
            if (runner == null) {
                mapOf("error" to "session closed")
            } else {
                runCatching { runner.execute(name, args) }
                    .getOrElse { mapOf("error" to (it.message ?: "tool failed")) }
            }
        }
    }

    /**
     * The label to show on the confirmation prompt, or null when this call needs no tap.
     *
     * Blocking (it reads the home's guarded entity names), so IO only.
     */
    private fun confirmationLabelFor(name: String, args: JSONObject): String? {
        val runner = tools ?: return null
        val guarded = when (name) {
            GeminiLive.CALL_SERVICE_TOOL -> {
                val domain = args.optString("domain")
                val entity = args.optString("entity_id")
                VoiceGuard.isGuardedDomain(domain) ||
                    entity.split(",").any { VoiceGuard.isGuardedEntity(it.trim()) }
            }
            GeminiLive.INTENT_TOOL -> VoiceGuard.intentNeedsConfirmation(
                domain = args.optString("domain").takeIf { it.isNotBlank() },
                deviceClass = args.optString("device_class").takeIf { it.isNotBlank() },
                name = args.optString("name").takeIf { it.isNotBlank() },
                guardedNames = runner::guardedNames,
            )
            else -> false
        }
        if (!guarded) return null
        // What the user will read on the button. Deliberately the raw ask, not a rephrasing: a
        // confirmation that summarises loses exactly the detail worth checking.
        val target = args.optString("name").ifBlank { args.optString("entity_id") }
        val verb = args.optString("intent").ifBlank {
            "${args.optString("domain")}.${args.optString("service")}"
        }
        return listOf(verb, target).filter { it.isNotBlank() }.joinToString(" · ")
    }

    private fun logToolCall(name: String, args: JSONObject, result: Map<String, Any?>) {
        val entry = VoiceToolCall(
            at = System.currentTimeMillis(),
            name = name,
            args = args.toString().take(160),
            result = result.toString().take(160),
            ok = result["error"] == null && result["success"] != false,
        )
        _toolCalls.value = (listOf(entry) + _toolCalls.value).take(MAX_TOOL_CALL_LOG)
    }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** True while the last session is recent enough to be treated as the same conversation. */
    private fun isRecentSession(): Boolean =
        resumptionHandle != null && System.currentTimeMillis() - resumptionHandleAt < RESUMPTION_WINDOW_MS

    /**
     * Rearms the idle timeout. A plan under way gets the long ceiling instead of the user's
     * silence timeout: nobody is expected to talk while the assistant works through four steps,
     * and hanging up mid-plan loses the work and the explanation of what was done.
     */
    private fun onActivity() {
        if (session == null) return
        val plan = _state.value.plan
        val running = !plan.isEmpty && !plan.isFinished
        armIdleTimeout(if (running) BOT_TURN_CEILING_MS else readConfig().idleTimeoutMs)
    }

    // --- config ------------------------------------------------------------------------------

    private fun readConfig(): VoiceConfig = VoiceConfig(
        gemini = GeminiConfig(
            apiKey = prefs.voiceGeminiApiKey,
            model = prefs.voiceGeminiModel,
            voice = prefs.voiceGeminiVoice,
            language = languageTag(),
            systemPrompt = systemPrompt(),
            bargeIn = prefs.voiceBargeIn,
            resumptionHandle = resumptionHandle
                ?.takeIf { System.currentTimeMillis() - resumptionHandleAt < RESUMPTION_WINDOW_MS },
        ),
        wakeWordAsset = prefs.voiceAssistantWakeWord,
        threshold = prefs.voiceAssistantThreshold / 100f,
        idleTimeoutMs = prefs.voiceAssistantIdleSeconds * 1000L,
    )

    /** BCP-47 tag for the Live API's speech config; the app language wins over the device's. */
    private fun languageTag(): String {
        val app = prefs.appLanguage
        if (app.isNotBlank()) return Locale.forLanguageTag(app).let { "${it.language}-${regionFor(it.language)}" }
        val locale = Locale.getDefault()
        return if (locale.country.isNotBlank()) locale.toLanguageTag() else "${locale.language}-${regionFor(locale.language)}"
    }

    /**
     * The Live voices want a region ("fr-FR"), not a bare language, and the app stores only the
     * language. Guessing the country from the language is wrong in general and right for the two
     * this launcher ships.
     */
    private fun regionFor(language: String): String = when (language) {
        "fr" -> "FR"
        "en" -> "US"
        else -> language.uppercase()
    }

    private fun systemPrompt(): String {
        val extra = prefs.voiceGeminiPrompt
        return buildString {
            append(
                "You are the voice assistant of a wall panel in a home, named ${prefs.deviceName}. " +
                    "You speak with someone standing in front of the panel. " +
                    "Answer out loud, in the user's language, in one or two short sentences: " +
                    "this is a spoken conversation, never a written one, so no lists, no markdown, " +
                    "no spelling out entity ids. " +
                    "To act on the home, use ${GeminiLive.INTENT_TOOL} with the name the user " +
                    "said: Home Assistant resolves rooms and aliases itself. Fall back to " +
                    "${GeminiLive.FIND_ENTITIES_TOOL} then ${GeminiLive.CALL_SERVICE_TOOL} only " +
                    "for what no intent covers. Never invent an entity id. " +
                    "Say what you did, briefly. " +
                    "When the user names a room, pass area *and* domain (light, switch, cover, " +
                    "media_player…), never a bare name: a home usually has both an area and a " +
                    "speaker called \"cuisine\", and a bare name hits whichever Home Assistant " +
                    "matches first. If a result comes back with nothing in acted_on, say you " +
                    "could not find it and ask which one, rather than trying another spelling. " +
                    "For a robot vacuum sent to one room, use ${GeminiLive.VACUUM_ROOM_TOOL}: " +
                    "its rooms are segments the vacuum stores itself and no intent can reach " +
                    "them. For music, HassMediaSearchAndPlay with the speaker as name " +
                    "or the room as area, and the volume and transport intents for the rest; " +
                    "grouping speakers (Sonos and the like) has no intent, use " +
                    "${GeminiLive.CALL_SERVICE_TOOL} with media_player.join. " +
                    "For the weather and the indoor temperature, HassGetWeather and " +
                    "HassClimateGetTemperature answer with what this home reports. " +
                    "When a request needs several actions, call ${GeminiLive.PLAN_TOOL} first, " +
                    "then ${GeminiLive.COMPLETE_TASK_TOOL} after each step, so the panel shows " +
                    "the user where you are. " +
                    "Use ${GeminiLive.PORTAL_SHOW_TOOL} when what you are saying is better " +
                    "looked at (the weather, a thermostat, what is playing). " +
                    "Use ${GeminiLive.PORTAL_SETTINGS_TOOL} when the user asks to change Portal " +
                    "itself: put a device first in the pinned pills, organize the Maison page " +
                    "by room or type, open an installed Android application, show or hide the " +
                    "Maison page, adjust screen or automatic-return timeouts, switch 12/24-hour " +
                    "clock format, resize the app grid, choose the background source, or tune " +
                    "voice interruption, idle time and wake sensitivity. Change only what was " +
                    "explicitly requested and report the tool's validated value. " +
                    "Locks, alarms and garage doors cannot be opened on a voice alone: the panel " +
                    "will ask for a tap on screen. When it does, say so and wait — you will be " +
                    "told whether the user confirmed. Do not try another way round it. " +
                    "A 200 from Home Assistant is not proof a device obeyed: when a result says " +
                    "a target was unavailable, say that rather than claiming success. " +
                    "Use ${GeminiLive.REMEMBER_TOOL} for a durable fact you had to be told twice " +
                    "(which room someone means, who lives here), never for a passing state. " +
                    "Call ${GeminiLive.END_CONVERSATION_TOOL} as soon as the exchange is over " +
                    "(a goodbye, a thank you that closes it, a request to stop), with a short " +
                    "goodbye in the same turn: leaving the session open keeps the microphone of " +
                    "someone's home open for nothing. " +
                    "If the user asks you to disable yourself for minutes or hours, or until " +
                    "they reactivate you, call ${GeminiLive.SLEEP_ASSISTANT_TOOL}; convert hours " +
                    "to minutes and acknowledge briefly before your turn ends. " +
                    "If a wake was accidental and what you hear is background speech, a " +
                    "conversation addressed to somebody else, or not a request to you, call " +
                    "${GeminiLive.DISMISS_CONVERSATION_TOOL} immediately and say absolutely " +
                    "nothing. Ask a short clarifying question instead when somebody really is " +
                    "addressing you but their request is merely unclear.",
            )
            if (extra.isNotBlank()) {
                append("\n\nHouse rules and context from the owner:\n")
                append(extra)
            }
            val facts = prefs.voiceFacts
            if (facts.isNotEmpty()) {
                append("\n\nWhat you were told to remember about this home:\n")
                facts.forEach { append("- ").append(it).append("\n") }
            }
            val scheduled = VoiceScheduler.pending(prefs)
            if (scheduled.isNotEmpty()) {
                append("\n\nAlready promised for later (do not promise them twice):\n")
                scheduled.forEach {
                    val minutes = (it.atMs - System.currentTimeMillis()) / 60_000L
                    append("- ").append(it.title).append(" (in ").append(minutes).append(" min)\n")
                }
            }
            // A plan interrupted by a dead socket: the steps are still on screen, so the
            // assistant must pick up where it stopped instead of starting the whole thing again.
            val plan = _state.value.plan
            if (!plan.isEmpty && !plan.isFinished) {
                append("\n\nA plan from the previous session is still on the panel's screen. ")
                append("Done: ")
                append(plan.tasks.filter { it.status == VoiceTaskStatus.DONE }.joinToString(", ") { it.title }.ifEmpty { "nothing" })
                append(". Left: ")
                append(
                    plan.tasks
                        .filter { it.status == VoiceTaskStatus.PENDING || it.status == VoiceTaskStatus.FAILED }
                        .joinToString(", ") { it.title },
                )
                append(". Continue from there, do not redo what is done.")
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Whether the platform claims an echo canceller on the capture path. Advisory only: some HALs
 * report one they never run, which is why barge-in stays a user setting the panel does not decide
 * on its own.
 */
fun deviceHasEchoCanceler(): Boolean = AcousticEchoCanceler.isAvailable()

/** `wakeword/hey_jarvis_v0.1.onnx` -> `hey jarvis`. */
internal fun wakeWordLabel(asset: String): String = asset
    .substringAfterLast('/')
    .removeSuffix(".onnx")
    .replace(Regex("_v\\d+(\\.\\d+)*$"), "")
    .replace('_', ' ')
