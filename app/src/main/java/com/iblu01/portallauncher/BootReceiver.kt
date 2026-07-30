package com.iblu01.portallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            DeviceStateHub.init(context)
            ScreenControl.enableAccessibility(context)
            SleepScheduler.apply(context)
            MqttBridgeService.start(context)
        }
    }
}
