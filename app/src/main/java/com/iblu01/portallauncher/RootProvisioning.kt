package com.iblu01.portallauncher

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * Grants every system capability Portal needs from a root shell, so a rooted panel never shows the
 * permission walkthrough at all.
 *
 * The same commands the onboarding tells the user to run over ADB, run locally as uid 0. Blocking:
 * call it off the main thread.
 */
object RootProvisioning {

    private const val TAG = "PortalLauncher"

    /** True when this device can provision itself (a root shell answers). */
    fun isAvailable(): Boolean = RootShell.isAvailable()

    /**
     * Runs the whole grant batch and records the outcome in [Prefs].
     *
     * Returns true when a root shell was obtained; individual grants may still have been refused,
     * which the caller sees by re-reading the capabilities afterwards.
     */
    fun provision(context: Context): Boolean {
        val pkg = context.packageName
        val accessibility = "$pkg/${ScreenAccessibility::class.java.name}"
        val listener = "$pkg/${NotificationDotService::class.java.name}"

        val commands = listOf(
            // Unlocks the in-app paths: accessibility toggle, secure settings writes.
            "pm grant $pkg android.permission.WRITE_SECURE_SETTINGS",
            // Brightness control (WRITE_SETTINGS is an appop, not a runtime permission).
            "appops set $pkg WRITE_SETTINGS allow",
            // Home role. `cmd package` on modern releases, `pm` on the older panels.
            "cmd package set-home-activity $pkg/.LauncherActivity",
            "pm set-home-activity $pkg/.LauncherActivity",
            "settings put secure ${Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES} " +
                merged(context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, accessibility),
            "settings put secure ${Settings.Secure.ACCESSIBILITY_ENABLED} 1",
            "settings put secure enabled_notification_listeners " +
                merged(context, "enabled_notification_listeners", listener),
            "cmd notification allow_listener $listener",
            // Lets the MQTT bridge live without a foreground-service notification.
            "dumpsys deviceidle whitelist +$pkg",
            "cmd appops set $pkg RUN_IN_BACKGROUND allow",
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow",
        )

        val output = RootShell.run(commands)
        if (output == null) {
            Log.w(TAG, "RootProvisioning: no root shell")
            return false
        }
        Log.i(TAG, "RootProvisioning: done\n$output")
        Prefs(context).rootProvisioned = true
        return true
    }

    /** The colon-separated secure setting [key] with [component] appended if it is not there yet. */
    private fun merged(context: Context, key: String, component: String): String {
        val current = Settings.Secure.getString(context.contentResolver, key).orEmpty()
        val value = when {
            current.split(':').any { it.equals(component, ignoreCase = true) } -> current
            current.isEmpty() -> component
            else -> "$current:$component"
        }
        return "'$value'"
    }
}
