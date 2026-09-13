package com.iblu01.portallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iblu01.portallauncher.voice.VoiceScheduler

/**
 * Wakes the panel to run whatever the assistant promised for later. The work is blocking HTTP, so
 * it runs off the main thread under `goAsync` rather than in `onReceive` itself.
 */
class VoiceScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != VoiceScheduler.ACTION_FIRE) return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread {
            try {
                VoiceScheduler.runDue(appContext)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
