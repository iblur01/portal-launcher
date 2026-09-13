package com.iblu01.portallauncher

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Plays one remote clip at a time — the speech Home Assistant rendered for a notification.
 *
 * Streams straight from the URL: these are a couple of seconds of TTS from a host on the same LAN,
 * so a download-then-play step would only add latency and a temp file to clean up.
 */
object AudioUrlPlayer {

    private const val TAG = "PortalHA"

    /**
     * Every player is built and driven from the main thread.
     *
     * `MediaPlayer` binds its callbacks to the `Looper` of the thread that constructed it; built on
     * a bare worker thread — which is where a fetched TTS URL naturally lands — `onPrepared`,
     * `onCompletion` and `onError` are never delivered, so the clip silently never starts.
     */
    private val main = Handler(Looper.getMainLooper())

    private var current: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    /** Ends the clip in flight, whichever way it ended, and hands control back. Runs once. */
    private var finishCurrent: (() -> Unit)? = null

    /**
     * Starts [url], ending whatever was already playing.
     *
     * [onDone] fires exactly once — clip finished, clip failed, or clip replaced by the next one —
     * so a caller waiting on it to dismiss an overlay is never left hanging.
     */
    fun play(context: Context, url: String, onDone: () -> Unit = {}) {
        main.post { start(context, url, onDone) }
    }

    @Synchronized
    private fun start(context: Context, url: String, onDone: () -> Unit) {
        stop()
        val app = context.applicationContext
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        // Transient: whatever was playing gets its focus back once the announcement is over.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        app.getSystemService(AudioManager::class.java).requestAudioFocus(request)

        val player = MediaPlayer()
        current = player
        focus = request
        val finish = {
            release(app, player)
            onDone()
        }
        finishCurrent = finish

        runCatching {
            player.setAudioAttributes(attrs)
            player.setDataSource(url)
            player.setOnPreparedListener {
                Log.i(TAG, "notification audio playing: $url")
                it.start()
            }
            player.setOnCompletionListener { finishNow(finish) }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "notification audio failed ($what/$extra): $url")
                finishNow(finish)
                true
            }
            player.prepareAsync()
        }.onFailure {
            Log.w(TAG, "notification audio could not start: ${it.message}")
            finishNow(finish)
        }
    }

    /** Stops the clip in flight, if any, and notifies its caller. */
    @Synchronized
    fun stop() {
        finishCurrent?.let { finishNow(it) }
    }

    @Synchronized
    private fun finishNow(finish: () -> Unit) {
        // Only the callbacks of the clip that is still current may fire: a completion arriving
        // late, after a replacement started, must not tear the new player down.
        if (finishCurrent !== finish) return
        finishCurrent = null
        finish()
    }

    private fun release(context: Context, player: MediaPlayer) {
        runCatching { player.reset() }
        runCatching { player.release() }
        if (current === player) current = null
        focus?.let {
            context.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it)
            focus = null
        }
    }
}
