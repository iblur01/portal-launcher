package com.iblu01.portallauncher.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The two earcons of a voice turn, synthesised rather than shipped as assets: two sine notes need
 * no file, no decoder and no `MediaPlayer` lifecycle.
 *
 * They exist because the panel is deaf between the wake word and the open WebRTC microphone (the
 * capture hand-off plus the server building its pipeline, see [VoiceAssistantController]). Smart
 * displays solve the same gap the same way: a low note acknowledges the wake word immediately, and
 * a brighter rising pair says "speak now" at the exact moment the microphone is live.
 *
 * Both are short and quiet on purpose: [READY] plays while the session microphone is already open,
 * so a long or loud cue would be fed straight into the server's VAD as if it were speech.
 */
enum class VoiceCue(private val notes: List<Note>) {
    /** Wake word heard, connecting. Low and single: an acknowledgement, not an invitation. */
    WAITING(listOf(Note(440f, 110), Note(0f, 40))),

    /** Microphone open. Rising pair, the conventional "go ahead" of a smart display. */
    READY(listOf(Note(660f, 80), Note(990f, 110))),
    ;

    internal data class Note(val freqHz: Float, val durationMs: Int)

    internal fun render(sampleRate: Int): ShortArray {
        val total = notes.sumOf { it.durationMs * sampleRate / 1000 }
        val pcm = ShortArray(total)
        var index = 0
        for (note in notes) {
            val count = note.durationMs * sampleRate / 1000
            var phase = 0.0
            // Per-note fades: a square-edged sine clicks, and a click is exactly the kind of
            // broadband transient the wake classifier and the server VAD react to.
            val fadeSamples = (0.012 * sampleRate).toInt().coerceAtLeast(1)
            for (i in 0 until count) {
                if (note.freqHz > 0f) {
                    phase += 2 * PI * note.freqHz / sampleRate
                    val fade = min(1.0, min(i, count - 1 - i).toDouble() / fadeSamples)
                    pcm[index] = (sin(phase) * fade * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
                }
                index++
            }
        }
        return pcm
    }

    val durationMs: Int get() = notes.sumOf { it.durationMs }

    private companion object {
        /** Quiet enough to sit under the room, loud enough to hear from across it. */
        const val AMPLITUDE = 0.35
    }
}

/** Plays [VoiceCue]s. Stateless: each cue owns a short-lived [AudioTrack] released on its own. */
object VoiceCues {

    private const val SAMPLE_RATE = 44100

    /**
     * Fires [cue] and releases its track once played. Failures are swallowed: an earcon that
     * cannot open the output must never take a voice session down with it.
     */
    fun play(scope: CoroutineScope, cue: VoiceCue) {
        scope.launch(Dispatchers.Default) {
            val track = runCatching { start(cue) }.getOrNull() ?: return@launch
            delay(cue.durationMs.toLong() + RELEASE_GRACE_MS)
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Lets the buffer drain before release; stopping mid-buffer truncates the cue. */
    private const val RELEASE_GRACE_MS = 150L

    private fun start(cue: VoiceCue): AudioTrack {
        val pcm = cue.render(SAMPLE_RATE)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // Same stream as the bot's TTS, so one volume knob governs the whole session.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
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
