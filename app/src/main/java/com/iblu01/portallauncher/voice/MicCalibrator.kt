package com.iblu01.portallauncher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Where the calibration routine currently is; rendered live on the voice settings page. */
sealed interface MicCalibrationState {
    data object MeasuringNoise : MicCalibrationState
    data object PlayingTone : MicCalibrationState
    data class Done(val result: MicCalibration) : MicCalibrationState
    data class Failed(val reason: MicCalibrationFailure) : MicCalibrationState
}

enum class MicCalibrationFailure { SPEAKER_INAUDIBLE, MIC_UNAVAILABLE }

/**
 * Speaker-to-microphone loop calibration for the wake engine.
 *
 * The panel plays a known speech-band sweep through its own loudspeaker while the wake engine's
 * capture chain listens. Two numbers come out:
 *
 *  - the ambient noise floor (RMS of ~1.5 s of room silence), which sets the gate below which the
 *    engine must not apply speech gain — otherwise a quiet-but-noisy room gets its noise amplified
 *    straight into the classifier;
 *  - the captured playback level, a proxy for how sensitive this particular microphone is to sound
 *    that is loud at the panel (both a person nearby and the bot's own TTS), which caps the gain.
 *
 * Median RMS per phase, so a door slam during calibration does not poison the result.
 */
object MicCalibrator {

    private const val FRAME_MS = 80L
    private const val SETTLE_FRAMES = 6 // AudioRecord warm-up, same reasoning as the engine's guard
    private const val NOISE_FRAMES = 18 // ~1.5 s
    private const val TONE_SKIP_FRAMES = 4 // AudioTrack start latency + sweep fade-in
    private const val TONE_FRAMES = 24 // ~1.9 s measured inside the ~2.4 s sweep

    suspend fun calibrate(context: Context, onPhase: (MicCalibrationState) -> Unit): MicCalibrationState {
        val frames = Channel<Float>(Channel.UNLIMITED)
        val state = runCatching {
            coroutineScope {
                val recording = launch {
                    OpenWakeWordBridge.recordRaw(context).collect { raw ->
                        val rms = sqrt(raw.fold(0.0) { sum, v -> sum + v * v } / raw.size).toFloat()
                        frames.send(rms)
                    }
                }
                try {
                    onPhase(MicCalibrationState.MeasuringNoise)
                    repeat(SETTLE_FRAMES) { frames.receive() }
                    val noiseFloor = median(List(NOISE_FRAMES) { frames.receive() })

                    onPhase(MicCalibrationState.PlayingTone)
                    val tone = startSweep()
                    val playbackRms = try {
                        repeat(TONE_SKIP_FRAMES) { frames.receive() }
                        median(List(TONE_FRAMES) { frames.receive() })
                    } finally {
                        runCatching { tone.stop() }
                        tone.release()
                    }

                    if (playbackRms < noiseFloor * 2f || playbackRms < 0.001f) {
                        MicCalibrationState.Failed(MicCalibrationFailure.SPEAKER_INAUDIBLE)
                    } else {
                        MicCalibrationState.Done(MicCalibration(noiseFloor, playbackRms))
                    }
                } finally {
                    recording.cancel()
                }
            }
        }.getOrElse { MicCalibrationState.Failed(MicCalibrationFailure.MIC_UNAVAILABLE) }
        onPhase(state)
        return state
    }

    private fun median(values: List<Float>): Float = values.sorted()[values.size / 2]

    /**
     * ~2.4 s logarithmic sweep over the speech band (200–2000 Hz), played on the media stream at
     * the volume the bot's TTS will use, so the captured level predicts real playback.
     */
    private fun startSweep(): AudioTrack {
        val sampleRate = 44100
        val seconds = (TONE_SKIP_FRAMES + TONE_FRAMES + 2) * FRAME_MS / 1000.0 + 0.3
        val n = (seconds * sampleRate).toInt()
        val pcm = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val freq = 200.0 * Math.pow(10.0, t) // 200 Hz -> 2 kHz
            phase += 2 * PI * freq / sampleRate
            val fade = minOf(1.0, i / (0.05 * sampleRate), (n - i) / (0.05 * sampleRate))
            pcm[i] = (sin(phase) * fade * 0.7 * Short.MAX_VALUE).toInt().toShort()
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also {
                it.write(pcm, 0, pcm.size)
                it.play()
            }
    }
}
