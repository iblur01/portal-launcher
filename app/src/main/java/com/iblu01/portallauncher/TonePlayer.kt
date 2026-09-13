package com.iblu01.portallauncher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// Synthesized alert tones — no bundled assets. Played on the media stream so
// the HA volume slider and volume mute apply.
object TonePlayer {

    private const val TAG = "PortalHA"
    private const val SAMPLE_RATE = 44100

    /** Slack for the track to actually be released after the last sample. */
    private const val TRACK_RELEASE_MS = 300L

    /**
     * Plays a synthesized chime and returns how long it lasts, in milliseconds (0 when there is
     * nothing to play).
     *
     * The duration matters to callers that also have speech to play: on these panels an open
     * `AudioTrack` leaves a `MediaPlayer` stuck in `prepareAsync` for good — the announcement then
     * never starts and never errors. Waiting out the chime is what keeps both audible.
     */
    fun play(name: String): Long {
        val pcm = when (name.trim().lowercase()) {
            "doorbell" -> doorbell()
            "alert" -> alert()
            "chime" -> chime()
            "success" -> success()
            "error" -> error()
            "ping" -> tone(1046.5, 0.4, decay = 5.0)
            "beep" -> tone(880.0, 0.09, decay = 1.0)
            "siren" -> siren()
            // An explicit "play nothing": a notification that wants the screen and not the room.
            "", "none", "off", "silent" -> return 0L
            else -> { Log.w(TAG, "unknown tone '$name'"); return 0L }
        }
        // Each play gets its own short-lived thread and track; overlaps just mix.
        Thread {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 200)
                track.release()
            }.onFailure { Log.w(TAG, "tone playback failed: ${it.message}") }
        }.also { it.isDaemon = true }.start()
        return pcm.size * 1000L / SAMPLE_RATE + TRACK_RELEASE_MS
    }

    // Classic two-tone "ding-dong" chime (E5 then C5)
    private fun doorbell(): ShortArray {
        val ding = tone(659.25, 0.45, decay = 4.0)
        val gap = ShortArray((0.06 * SAMPLE_RATE).toInt())
        val dong = tone(523.25, 0.65, decay = 3.0)
        return ding + gap + dong
    }

    // Three quick attention beeps
    private fun alert(): ShortArray {
        val beep = tone(880.0, 0.16, decay = 1.5)
        val gap = ShortArray((0.12 * SAMPLE_RATE).toInt())
        return beep + gap + beep + gap + beep
    }

    // Four soft descending notes, the "something happened, no hurry" end of the range (G5 E5 C5 G4)
    private fun chime(): ShortArray {
        val gap = ShortArray((0.04 * SAMPLE_RATE).toInt())
        return tone(783.99, 0.30, decay = 3.5) + gap +
            tone(659.25, 0.30, decay = 3.5) + gap +
            tone(523.25, 0.30, decay = 3.5) + gap +
            tone(392.00, 0.70, decay = 2.5)
    }

    // Rising major triad: a task that finished well (C5 E5 G5)
    private fun success(): ShortArray {
        val gap = ShortArray((0.02 * SAMPLE_RATE).toInt())
        return tone(523.25, 0.16, decay = 2.5) + gap +
            tone(659.25, 0.16, decay = 2.5) + gap +
            tone(783.99, 0.50, decay = 2.0)
    }

    // Two low falling notes: something went wrong (A3 then F3)
    private fun error(): ShortArray {
        val gap = ShortArray((0.05 * SAMPLE_RATE).toInt())
        return tone(220.0, 0.28, decay = 2.0) + gap + tone(174.61, 0.55, decay = 1.8)
    }

    // Two alternating tones, the sound a panel makes when it has stopped asking nicely
    private fun siren(): ShortArray {
        var out = ShortArray(0)
        repeat(3) {
            out += tone(1174.66, 0.22, decay = 0.4) + tone(880.0, 0.22, decay = 0.4)
        }
        return out
    }

    private fun tone(freq: Double, seconds: Double, decay: Double): ShortArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-decay * t / seconds)
            // fundamental plus a touch of 2nd harmonic for a bell-ish timbre
            val s = 0.8 * sin(2 * PI * freq * t) + 0.2 * sin(4 * PI * freq * t)
            out[i] = (s * env * 0.85 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
