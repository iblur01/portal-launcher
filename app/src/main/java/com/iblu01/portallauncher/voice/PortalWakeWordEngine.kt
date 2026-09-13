package com.iblu01.portallauncher.voice

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.model.WakeWordDetection
import com.rementia.openwakeword.lib.model.WakeWordModel
import com.rementia.openwakeword.lib.model.WakeWordScore
import java.util.Locale
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Small, observable wake-word engine built from openwakeword-android's public audio/ML building
 * blocks. The upstream engine only exposes classifier scores, which made a silent microphone and
 * a low-confidence model indistinguishable. This version also exposes the raw RMS level and
 * applies conservative speech gain before inference for low-output wall-panel microphones.
 */
class PortalWakeWordEngine(
    context: Context,
    models: List<WakeWordModel>,
    private val scope: CoroutineScope,
    private val detectionCooldownMs: Long = 2_000L,
    private val startupGuardMs: Long = 3_000L,
    /** Frames below this RMS are room noise: no speech gain. Calibrated per panel when possible. */
    private val noiseGateRms: Float = MicCalibration.DEFAULT_NOISE_GATE_RMS,
    /** Gain ceiling; calibration lowers it on hot microphones and raises it on quiet ones. */
    private val maxGain: Float = MicCalibration.DEFAULT_MAX_GAIN,
    /**
     * Consecutive 80 ms frames that must score above the threshold before a detection fires.
     * A stray phoneme in ambient conversation produces a single-frame spike; a real utterance of
     * the wake word holds its score across several frames. openWakeWord calls this "patience".
     */
    private val patienceFrames: Int = DEFAULT_PATIENCE_FRAMES,
) {
    companion object {
        const val DEFAULT_PATIENCE_FRAMES = 2

        private const val TAG = "VoiceWake"

        /** openWakeWord's capture frame: 1280 samples at 16 kHz. */
        private const val FRAME_MS = 80f
        private const val LOG_INTERVAL_MS = 2_000L
    }

    private class Processor(context: Context, val model: WakeWordModel) : AutoCloseable {
        private val pipeline = OpenWakeWordPipeline(context, model.modelPath)

        fun score(samples: FloatArray): Float = pipeline.process(samples)

        override fun close() {
            pipeline.close()
        }
    }

    private val processors = models.map { Processor(context, it) }
    private val appContext = context.applicationContext
    private val lastDetections = mutableMapOf<String, Long>()
    private val consecutiveHits = mutableMapOf<String, Int>()
    private val _scores = MutableSharedFlow<WakeWordScore>(extraBufferCapacity = 16)
    private val _detections = MutableSharedFlow<WakeWordDetection>(extraBufferCapacity = 4)
    private val _microphoneLevels = MutableSharedFlow<Float>(extraBufferCapacity = 16)
    val scores: Flow<WakeWordScore> = _scores.asSharedFlow()
    val detections: Flow<WakeWordDetection> = _detections.asSharedFlow()
    val microphoneLevels: Flow<Float> = _microphoneLevels.asSharedFlow()
    private var recordingJob: Job? = null

    /** Slow RMS average driving the speech gain; see the capture loop. */
    private var smoothedRms = 0f
    private val telemetry = WakeTelemetry()

    /**
     * Rate-limited health line for the capture loop. Worth keeping in the build: on slow panels
     * the failure mode is silent — inference falls behind the 80 ms frame rate, AudioRecord
     * overflows, and the model sees chopped audio in which no wake word can ever be recognised.
     * A score that never rises looks identical to a badly tuned threshold without these numbers.
     */
    private inner class WakeTelemetry {
        private var frames = 0
        private var rmsSum = 0.0
        private var rmsPeak = 0f
        private var scorePeak = 0f
        private var inferenceMsSum = 0f
        private var windowStartedAt = System.currentTimeMillis()

        fun onFrame(rms: Float, score: Float, inferenceMs: Float) {
            frames++
            rmsSum += rms
            rmsPeak = maxOf(rmsPeak, rms)
            scorePeak = maxOf(scorePeak, score)
            inferenceMsSum += inferenceMs
            val elapsed = System.currentTimeMillis() - windowStartedAt
            if (elapsed < LOG_INTERVAL_MS) return

            // FRAME_MS per frame is real time; anything above 1.0 means the loop cannot keep up.
            val load = (elapsed.toFloat() / frames) / FRAME_MS
            Log.i(
                TAG,
                (
                    "wake %d frames in %d ms (load %.2fx real time, inference %.0f ms/frame), " +
                        "rms mean %.4f peak %.4f gain %.1fx, score peak %.3f"
                    ).format(
                    Locale.US,
                    frames, elapsed, load, inferenceMsSum / frames,
                    rmsSum / frames, rmsPeak, smoothedRms.let { if (it >= noiseGateRms) (MicCalibration.TARGET_SPEECH_RMS / it).coerceIn(1f, maxGain) else 1f }, scorePeak,
                ),
            )
            frames = 0
            rmsSum = 0.0
            rmsPeak = 0f
            scorePeak = 0f
            inferenceMsSum = 0f
            windowStartedAt = System.currentTimeMillis()
        }
    }

    fun start() {
        if (recordingJob?.isActive == true) return
        recordingJob = scope.launch {
            // AudioRecord and the model both need a few frames to settle. In particular, opening
            // the launcher can produce a short transient that must never start a Pipecat session.
            // Scores and microphone levels remain live during this guard for diagnostics.
            val armedAt = System.currentTimeMillis() + startupGuardMs
            // Capture only: recordRaw opens an AudioRecorder and nothing else. Going through a
            // full bridge here used to load a second set of ONNX sessions (mel, embedding,
            // classifier) that never scored anything, on a panel with under 1 GB of RAM.
            OpenWakeWordBridge.recordRaw(appContext).collect { raw ->
                val rms = sqrt(raw.fold(0.0) { sum, value -> sum + value * value } / raw.size)
                    .toFloat()
                // 0.10 RMS is already a strong close-range voice. Scale only the display here.
                _microphoneLevels.tryEmit((rms / 0.10f).coerceIn(0f, 1f))

                // Honor wall panels deliver a notably quiet 16 kHz MIC stream. Bring speech into
                // the range used to train openWakeWord, while leaving silence alone and capping
                // gain so ambient noise cannot explode.
                // Gain follows a slow average, never this frame: a per-frame AGC renormalises
                // every 80 ms window to the same level, flattening the syllable envelope the
                // classifier keys on. ~1.6 s EMA tracks the room and the talker, not phonemes.
                smoothedRms = if (smoothedRms <= 0f) rms else smoothedRms * 0.95f + rms * 0.05f
                val gain = if (smoothedRms >= noiseGateRms) {
                    (MicCalibration.TARGET_SPEECH_RMS / smoothedRms).coerceIn(1f, maxGain)
                } else 1f
                val samples = if (gain > 1.01f) {
                    FloatArray(raw.size) { index -> (raw[index] * gain).coerceIn(-1f, 1f) }
                } else raw

                val now = System.currentTimeMillis()
                processors.forEach { processor ->
                    val startedAt = System.nanoTime()
                    val score = processor.score(samples).coerceIn(0f, 1f)
                    telemetry.onFrame(rms, score, (System.nanoTime() - startedAt) / 1_000_000f)
                    // tryEmit, never emit: a suspending emit here applies backpressure from the UI
                    // collectors onto the capture loop, and audio that is not drained fast enough
                    // overflows AudioRecord — which breaks the very continuity the model needs.
                    _scores.tryEmit(WakeWordScore(processor.model, score, now))
                    val name = processor.model.name
                    val hits = if (score >= processor.model.threshold) {
                        (consecutiveHits[name] ?: 0) + 1
                    } else 0
                    consecutiveHits[name] = hits
                    val last = lastDetections[name] ?: 0L
                    if (
                        now >= armedAt &&
                        hits >= patienceFrames &&
                        now - last >= detectionCooldownMs
                    ) {
                        lastDetections[name] = now
                        consecutiveHits[name] = 0
                        _detections.tryEmit(WakeWordDetection(processor.model, score, now))
                    }
                }
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
    }

    fun release() {
        stop()
        processors.forEach { it.close() }
    }
}
