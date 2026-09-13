package com.iblu01.portallauncher

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One overlay's worth of state: what stands, until when, and what it is chiming.
 *
 * There are two instances rather than one queue. An alarm and a parcel delivery are not competing
 * for the same slot — the alarm sits above and outlives the rest, and a notification arriving
 * mid-intrusion must not be able to replace the screen that demands a code.
 */
open class AlertOverlayLayer {
    var activeAlert by mutableStateOf<AlertPayload?>(null)
        private set

    /**
     * When the standing notification's timer runs out, in `System.currentTimeMillis()` terms, or
     * null when it carries no timer. Null again once it has rung: the overlay then shows its end
     * message instead of a counter.
     */
    var timerEndsAt by mutableStateOf<Long?>(null)
        private set

    private companion object {
        /** How long the notification stays up after its audio has finished. */

        const val AUDIO_TAIL_MS = 1500L

        /** A clip that never ends must not leave the overlay swallowing every touch forever. */
        const val AUDIO_MAX_MS = 60_000L

        /** However loud the alarm, a panel nobody comes to must stop screaming eventually. */
        const val REPEAT_MAX_MS = 5 * 60_000L
    }

    protected val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var tickRunnable: Runnable? = null
    private var awaitingAudio = false

    /**
     * Shows [alert].
     *
     * With [awaitAudio] the notification stands until [onAudioFinished] (plus a short tail) rather
     * than on a timer: an announcement that is still being read out has no business disappearing
     * mid-sentence.
     */
    fun show(alert: AlertPayload, awaitAudio: Boolean = false) {
        val endsAt = alert.endsAt(System.currentTimeMillis())
        handler.post {
            cancelDismiss()
            cancelTimer()
            cancelTicking()
            activeAlert = alert
            // A siren outlives its own announcement: the end of the speech must not take down a
            // notification that is still ringing.
            awaitingAudio = awaitAudio && endsAt == null && alert.repeatMs == null
            startTicking(alert, endsAt)
            if (endsAt != null) {
                // A timer owns the overlay until it rings: nothing else may take it down early.
                timerEndsAt = endsAt
                val runnable = Runnable { ring(alert) }
                timerRunnable = runnable
                handler.postDelayed(runnable, (endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
                return@post
            }
            val timeout = when {
                // Rings until someone deals with it — or until the siren gives up, for a level
                // that is not critical.
                alert.repeatMs != null -> REPEAT_MAX_MS.takeIf { alert.level != AlertLevel.CRITICAL }
                awaitingAudio -> AUDIO_MAX_MS
                else -> alert.durationMs ?: alert.level.defaultDurationMs
            }
            timeout?.let { scheduleDismiss(it) }
        }
    }

    /**
     * The beeps of a countdown, and the siren of a notification that repeats — an alarm panel's
     * two voices. Both run on the same handler as everything else here.
     */
    private fun startTicking(alert: AlertPayload, endsAt: Long?) {
        if (alert.tick != null && endsAt != null) {
            val beat = object : Runnable {
                override fun run() {
                    val remaining = endsAt - System.currentTimeMillis()
                    if (remaining <= 0) return
                    TonePlayer.play(alert.tick)
                    // Twice as fast in the last stretch: the panel is telling you to hurry.
                    val period = if (remaining <= alert.tickUrgentAtMs) 500L else 1000L
                    handler.postDelayed(this, period)
                }
            }
            tickRunnable = beat
            handler.post(beat)
            return
        }
        val every = alert.repeatMs ?: return
        val stopAt = System.currentTimeMillis() + REPEAT_MAX_MS
        val siren = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() > stopAt) return
                TonePlayer.play(alert.tone ?: AlertPayload.DEFAULT_TONE)
                handler.postDelayed(this, every)
            }
        }
        tickRunnable = siren
        handler.post(siren)
    }

    private fun cancelTicking() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    /** Zero o'clock: the chime, then the end message for as long as the level says. */
    private fun ring(alert: AlertPayload) {
        timerRunnable = null
        timerEndsAt = null
        cancelTicking()
        TonePlayer.play(alert.endTone ?: AlertPayload.DEFAULT_END_TONE)
        alert.endMessage?.let { activeAlert = alert.copy(message = it) }
        // A payload that repeats keeps repeating past zero: that is the siren after the entry delay.
        alert.repeatMs?.let { startTicking(alert.copy(countdownMs = null, untilEpochMs = null), null) }
        if (alert.repeatMs == null) {
            (alert.durationMs ?: alert.level.defaultDurationMs)?.let { scheduleDismiss(it) }
        }
    }

    /** The audio that came with the standing notification is over. */
    fun onAudioFinished() {
        handler.post {
            if (!awaitingAudio || activeAlert == null) return@post
            awaitingAudio = false
            cancelDismiss()
            scheduleDismiss(AUDIO_TAIL_MS)
        }
    }

    /** Plain-text notification, as the topic accepted before the JSON schema existed. */
    fun showAlert(message: String, durationMs: Long = 5000L) =
        show(AlertPayload(message = message, durationMs = durationMs))

    fun dismiss() {
        handler.post {
            cancelDismiss()
            cancelTimer()
            cancelTicking()
            activeAlert = null
            awaitingAudio = false
        }
    }

    private fun scheduleDismiss(delayMs: Long) {
        val runnable = Runnable {
            activeAlert = null
            awaitingAudio = false
            dismissRunnable = null
            cancelTimer()
            cancelTicking()
        }
        dismissRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun cancelDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
    }

    private fun cancelTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
        timerEndsAt = null
    }
}

/** Everything that is not the alarm: notifications, chimes, announcements. */
object AlertOverlayState : AlertOverlayLayer()

/** The alarm, drawn above [AlertOverlayState] and never displaced by it. */
object AlarmOverlayState : AlertOverlayLayer()
