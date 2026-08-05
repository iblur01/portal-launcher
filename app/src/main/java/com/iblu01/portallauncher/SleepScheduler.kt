package com.iblu01.portallauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

object SleepScheduler {
    private const val TAG = "PortalLauncherSleep"
    const val ACTION_IDLE = "com.iblu01.portallauncher.SLEEP_IDLE"
    private const val RC_IDLE = 4101

    // arm() does two synchronous binder calls (PendingIntent + AlarmManager). Callers hammer it —
    // every touch (down AND up) plus each foreground-package report, several times a second — which
    // saturates the main thread with IPC and makes the UI (e.g. the alarm keypad) lag badly. The
    // idle timeout is minutes long, so re-arming a few seconds late is harmless: throttle to one
    // real arm per window and coalesce the rest.
    private const val ARM_THROTTLE_MS = 5_000L
    @Volatile private var lastArmAtMs = 0L

    /**
     * Set while an alarm is in its entry delay or triggered: the screen must stay up so the disarm
     * keypad is reachable, so every path here becomes a no-op and any pending idle alarm is dropped.
     */
    @Volatile private var alarmHold = false

    /** Called by the UI when the alarm enters/leaves an alerting state. */
    fun setAlarmHold(context: Context, hold: Boolean) {
        if (alarmHold == hold) return
        alarmHold = hold
        Log.i(TAG, "alarm hold=$hold")
        if (hold) cancel(context) else apply(context)
    }

    fun apply(context: Context) {
        if (alarmHold) { cancel(context); return }
        val prefs = Prefs(context)
        if (prefs.devKeepScreenOn || !prefs.screenTimeoutEnabled || prefs.powerMode == PowerMode.ALWAYS_ON) {
            cancel(context)
            return
        }
        if (DeviceStateHub.current.display == DisplayMode.SCREENSAVER ||
            DeviceStateHub.current.display == DisplayMode.DREAMING
        ) {
            arm(context)
        } else {
            cancel(context)
        }
    }

    fun onInteraction(context: Context) {
        if (alarmHold) return
        val prefs = Prefs(context)
        if (prefs.devKeepScreenOn || !prefs.screenTimeoutEnabled || prefs.powerMode == PowerMode.ALWAYS_ON) return
        if (DeviceStateHub.current.display == DisplayMode.SCREENSAVER) arm(context)
    }

    fun onIdleElapsed(context: Context) {
        if (alarmHold) return
        val prefs = Prefs(context)
        if (prefs.devKeepScreenOn || !prefs.screenTimeoutEnabled || prefs.powerMode == PowerMode.ALWAYS_ON) return
        if (!isInteractive(context)) return
        val display = DeviceStateHub.current.display
        if (display != DisplayMode.SCREENSAVER && display != DisplayMode.DREAMING) return
        Log.i(TAG, "idle timeout elapsed; sleeping screen")
        ScreenControl.sleep(context)
    }

    fun arm(context: Context) {
        if (alarmHold) { cancel(context); return }
        val prefs = Prefs(context)
        if (prefs.devKeepScreenOn || !prefs.screenTimeoutEnabled || prefs.powerMode == PowerMode.ALWAYS_ON) {
            cancel(context)
            return
        }
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastArmAtMs < ARM_THROTTLE_MS) return   // coalesce the storm of re-arms
        lastArmAtMs = nowMs
        val atMs = nowMs + prefs.screenTimeoutMinutes * 60_000L
        val pending = pendingIntent(context, create = true) ?: return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        runCatching {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }.onFailure {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending)
        }
        Log.i(TAG, "idle timeout armed for ${prefs.screenTimeoutMinutes} min")
    }

    fun cancel(context: Context) {
        lastArmAtMs = 0L
        pendingIntent(context, create = false)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or
            PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(
            context,
            RC_IDLE,
            Intent(context, SleepReceiver::class.java).setAction(ACTION_IDLE),
            flags
        )
    }

    private fun isInteractive(context: Context): Boolean =
        runCatching { context.getSystemService(PowerManager::class.java).isInteractive }.getOrDefault(false)
}
