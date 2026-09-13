package com.iblu01.portallauncher.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Base64
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One live speech-to-speech session with Gemini: a WebSocket, a recorder and a player.
 *
 * Threading: two dedicated threads (capture and playback) plus okhttp's, and [Listener] is
 * called from whichever of them produced the event — the controller marshals onto the main
 * dispatcher. [close] is safe to call from anywhere and more than once.
 */
class GeminiVoiceSession(
    private val config: GeminiConfig,
    private val listener: Listener,
) {
    interface Listener {
        fun onReady()
        fun onResumptionHandle(handle: String)
        fun onToolCallsCancelled(ids: List<String>)
        fun onUserTranscript(text: String)
        fun onBotTranscript(text: String)
        fun onBotStartedSpeaking()
        fun onBotStoppedSpeaking()
        fun onToolCall(id: String?, name: String, args: JSONObject)
        fun onClosed(reason: String?)
        fun onError(message: String)
    }

    private companion object {
        const val TAG = "GeminiVoice"

        /** 100 ms of 16 kHz mono 16-bit audio: few enough frames to be cheap, small enough to be live. */
        const val CAPTURE_CHUNK_BYTES = GeminiLive.INPUT_SAMPLE_RATE / 10 * 2

        /** Longest wait for one audio thread to release its device on [close]. */
        const val CLOSE_JOIN_MS = 800L

        /** How often the playback thread checks whether the speaker has caught up. */
        const val DRAIN_POLL_MS = 60L
    }

    private val http = OkHttpClient.Builder()
        // The socket is idle whenever nobody talks; without a keepalive the first NAT on the way
        // out drops it and the next wake word fails for no visible reason.
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private val closed = AtomicBoolean(false)
    private val micEnabled = AtomicBoolean(false)

    private var socket: WebSocket? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private var record: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var track: AudioTrack? = null

    /** Playback work queue. [FLUSH] is the interruption marker, so it is honoured in order. */
    private val playback = LinkedBlockingQueue<ByteArray>()
    private val speaking = AtomicBoolean(false)

    /** Set when the model's turn ended: the player announces the end once the queue is empty. */
    private val turnEnded = AtomicBoolean(false)

    fun start() {
        val request = Request.Builder().url(GeminiLive.endpoint(config.apiKey)).build()
        socket = http.newWebSocket(request, socketListener)
    }

    /**
     * Half duplex switch. With no working echo canceller the microphone must be shut while the
     * panel's own loudspeaker is active, otherwise the server's voice activity detection hears
     * the model interrupting itself and the conversation collapses into a loop.
     */
    fun setMicEnabled(enabled: Boolean) {
        micEnabled.set(enabled)
    }

    /** A turn on the user's behalf: see [GeminiLive.textMessage]. */
    fun sendText(text: String) {
        socket?.send(GeminiLive.textMessage(text))
    }

    fun sendToolResponse(id: String?, name: String, result: Map<String, Any?>) {
        socket?.send(GeminiLive.toolResponseMessage(id, name, result))
    }

    /**
     * Blocks until the recorder and the player have actually let go.
     *
     * The join is the whole point, and it is not politeness: `AudioRecord.release()` runs in the
     * capture thread's `finally`, so returning early means the caller restores the audio mode
     * while a VOICE_COMMUNICATION input stream is still open. On this panel's HAL that
     * reconfiguration under an open stream leaves the microphone delivering digital silence
     * (-101 dB, "frames muted 0") for every recorder that follows, until the process restarts —
     * the wake word simply never fires again.
     *
     * Bounded so a wedged HAL cannot hang the caller; callers run it off the main thread.
     */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        socket?.close(1000, null)
        socket = null
        val capture = captureThread
        val playback = playbackThread
        captureThread = null
        playbackThread = null
        capture?.interrupt()
        playback?.interrupt()
        runCatching { capture?.join(CLOSE_JOIN_MS) }
        runCatching { playback?.join(CLOSE_JOIN_MS) }
        if (capture?.isAlive == true) Log.w(TAG, "capture thread did not release the microphone in time")
    }

    // --- socket ------------------------------------------------------------------------------

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(
                GeminiLive.setupMessage(
                    model = config.model,
                    voiceName = config.voice,
                    languageCode = config.language,
                    systemPrompt = config.systemPrompt,
                    resumptionHandle = config.resumptionHandle,
                ),
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handle(text)

        /** The Live API answers in binary frames as often as in text ones. */
        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) = handle(bytes.utf8())

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closed.get()) return
            Log.e(TAG, "socket failed (${response?.code})", t)
            listener.onError(t.message ?: "connection failed")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (closed.get()) return
            listener.onClosed(reason.ifEmpty { "closed ($code)" })
        }
    }

    private fun handle(raw: String) {
        for (event in parseGeminiMessage(raw)) {
            when (event) {
                GeminiEvent.SetupComplete -> {
                    startAudio()
                    listener.onReady()
                }
                is GeminiEvent.Audio -> enqueueAudio(event.base64Pcm)
                is GeminiEvent.UserTranscript -> listener.onUserTranscript(event.text)
                is GeminiEvent.BotTranscript -> listener.onBotTranscript(event.text)
                GeminiEvent.TurnComplete -> turnEnded.set(true)
                // Explicitly not a turn end: the model may still speak after answering a tool
                // call within the same turn.
                GeminiEvent.GenerationComplete -> Unit
                is GeminiEvent.ToolCallCancellation -> listener.onToolCallsCancelled(event.ids)
                is GeminiEvent.ResumptionHandle -> listener.onResumptionHandle(event.handle)
                GeminiEvent.Interrupted -> flushPlayback()
                is GeminiEvent.ToolCall -> listener.onToolCall(event.id, event.name, event.args)
                is GeminiEvent.GoAway -> listener.onClosed("server: ${event.reason}")
                is GeminiEvent.Error -> listener.onError(event.message)
            }
        }
    }

    // --- audio -------------------------------------------------------------------------------

    private fun startAudio() {
        if (closed.get() || captureThread != null) return
        playbackThread = Thread(::playbackLoop, "gemini-playback").apply { start() }
        captureThread = Thread(::captureLoop, "gemini-capture").apply { start() }
    }

    @SuppressLint("MissingPermission") // The controller gates the whole feature on RECORD_AUDIO.
    private fun captureLoop() {
        val minBuffer = AudioRecord.getMinBufferSize(
            GeminiLive.INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val recorder = runCatching {
            AudioRecord(
                // VOICE_COMMUNICATION is the only source that asks the HAL for its echo and
                // noise processing; VOICE_RECOGNITION explicitly turns them off.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                GeminiLive.INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CAPTURE_CHUNK_BYTES * 4),
            )
        }.getOrNull()

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder?.release()
            listener.onError("microphone unavailable")
            return
        }
        record = recorder
        if (config.bargeIn) attachEchoCanceler(recorder)

        val buffer = ByteArray(CAPTURE_CHUNK_BYTES)
        try {
            recorder.startRecording()
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read <= 0) continue
                // The stream is read even while muted: leaving the recorder idle lets the HAL
                // buffer fill with stale audio that is then sent as the user's next sentence.
                if (!micEnabled.get()) continue
                val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                socket?.send(GeminiLive.audioMessage(Base64.encodeToString(chunk, Base64.NO_WRAP)))
            }
        } catch (e: Exception) {
            if (!closed.get()) {
                Log.e(TAG, "capture failed", e)
                listener.onError(e.message ?: "capture failed")
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            record = null
            echoCanceler?.release()
            echoCanceler = null
        }
    }

    /**
     * Some HALs report a hardware canceller they do not actually run (this panel's MediaTek path
     * is one), so this is best effort: it is why barge-in stays a user-visible setting instead of
     * being decided from [AcousticEchoCanceler.isAvailable] alone.
     */
    private fun attachEchoCanceler(recorder: AudioRecord) {
        if (!AcousticEchoCanceler.isAvailable()) return
        echoCanceler = runCatching {
            AcousticEchoCanceler.create(recorder.audioSessionId)?.apply { enabled = true }
        }.getOrNull()
        Log.i(TAG, "echo canceler enabled=${echoCanceler?.enabled}")
    }

    private fun enqueueAudio(base64Pcm: String) {
        val pcm = runCatching { Base64.decode(base64Pcm, Base64.DEFAULT) }.getOrNull() ?: return
        turnEnded.set(false)
        playback.offer(pcm)
    }

    private fun flushPlayback() {
        playback.clear()
        runCatching {
            track?.pause()
            track?.flush()
            track?.play()
        }
        turnEnded.set(true)
    }

    private fun playbackLoop() {
        val minBuffer = AudioTrack.getMinBufferSize(
            GeminiLive.OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val player = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Voice-call usage, matching MODE_IN_COMMUNICATION and the capture source:
                        // a mismatch leaves the communication path half-configured on MediaTek
                        // HALs, which is audible as crackle.
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(GeminiLive.OUTPUT_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(minBuffer, GeminiLive.OUTPUT_SAMPLE_RATE))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull()

        if (player == null) {
            listener.onError("speaker unavailable")
            return
        }
        track = player
        player.play()

        var framesWritten = 0L
        try {
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                val chunk = playback.poll(DRAIN_POLL_MS, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    if (speaking.compareAndSet(false, true)) listener.onBotStartedSpeaking()
                    framesWritten += chunk.size / 2
                    player.write(chunk, 0, chunk.size)
                    continue
                }
                // Nothing queued. The turn is only over once the speaker has played what was
                // written: announcing it on an empty queue would cut the last syllables, which
                // is exactly when the hang-up tool fires.
                if (speaking.get() && turnEnded.get() && playback.isEmpty() &&
                    player.playbackHeadPosition.toLong() >= framesWritten
                ) {
                    speaking.set(false)
                    listener.onBotStoppedSpeaking()
                }
            }
        } catch (e: InterruptedException) {
            // close() interrupting us: nothing to report.
        } catch (e: Exception) {
            if (!closed.get()) Log.e(TAG, "playback failed", e)
        } finally {
            runCatching { player.pause() }
            runCatching { player.flush() }
            player.release()
            track = null
        }
    }
}

/** Everything one session needs, resolved from prefs by the controller. */
data class GeminiConfig(
    val apiKey: String,
    val model: String,
    val voice: String,
    val language: String,
    val systemPrompt: String,
    /**
     * Handle from a previous session on this panel, when there is a recent one: the assistant
     * then remembers the last exchange across a hang-up, which is what makes "and the kitchen
     * too" work after a fresh wake word.
     */
    val resumptionHandle: String? = null,
    /**
     * Keep the microphone open while the model speaks, so the user can cut it off. Only safe with
     * real echo cancellation; the panel is half duplex otherwise.
     */
    val bargeIn: Boolean,
)

/** [AudioManager] mode the session needs; set by the controller around the session's lifetime. */
internal const val VOICE_SESSION_AUDIO_MODE = AudioManager.MODE_IN_COMMUNICATION
