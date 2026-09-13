package com.iblu01.portallauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The assistant's mute switch, shared by the MQTT bridge, the wake engine and the launcher.
 *
 * Muted means the microphone is not open at all: the wake engine is stopped, and the MQTT button
 * that starts a session remotely is refused too. Anything less would be dishonest — a panel that
 * shows a crossed-out microphone while still listening for its wake word is worse than no switch.
 *
 * Kept as a process-wide holder rather than threaded through, in the same shape as
 * [ActionLockState]: the bridge (a service), the controller and the launcher all need it, and they
 * have no common owner.
 */
object VoiceMuteState {

    var muted by mutableStateOf(false)
        private set

    /** Set by the MQTT bridge so a mute toggled on the panel is reported back to Home Assistant. */
    @Volatile
    var onChanged: ((Boolean) -> Unit)? = null

    /** Restores the persisted value at process start; does not notify, nothing is listening yet. */
    fun restore(prefs: Prefs, nowMs: Long = System.currentTimeMillis()) {
        clearExpiredTimer(prefs, nowMs)
        muted = prefs.voiceMuted || prefs.voiceMutedUntilMs > nowMs
    }

    fun set(prefs: Prefs, muted: Boolean) {
        if (this.muted == muted && prefs.voiceMuted == muted && prefs.voiceMutedUntilMs == 0L) return
        this.muted = muted
        prefs.voiceMuted = muted
        prefs.voiceMutedUntilMs = 0L
        onChanged?.invoke(muted)
    }

    /** Mutes until [untilMs], or indefinitely when it is [Long.MAX_VALUE]. */
    fun sleepUntil(prefs: Prefs, untilMs: Long) {
        val resolved = untilMs.coerceAtLeast(System.currentTimeMillis() + 1L)
        prefs.voiceMuted = false
        prefs.voiceMutedUntilMs = resolved
        if (!muted) {
            muted = true
            onChanged?.invoke(true)
        }
    }

    /** Expires a timed mute. Returns true only when the visible state changed. */
    fun refresh(prefs: Prefs, nowMs: Long = System.currentTimeMillis()): Boolean {
        clearExpiredTimer(prefs, nowMs)
        val next = prefs.voiceMuted || prefs.voiceMutedUntilMs > nowMs
        if (next == muted) return false
        muted = next
        onChanged?.invoke(next)
        return true
    }

    private fun clearExpiredTimer(prefs: Prefs, nowMs: Long) {
        val until = prefs.voiceMutedUntilMs
        if (until in 1..nowMs) prefs.voiceMutedUntilMs = 0L
    }
}
