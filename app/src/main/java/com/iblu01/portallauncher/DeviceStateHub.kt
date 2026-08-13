package com.iblu01.portallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

enum class Presence {
    PRESENT,
    ABSENT,
    UNKNOWN,
}

enum class DisplayMode {
    OFF,
    SCREENSAVER,
    APP,
    DREAMING,
}

data class DeviceState(
    val presence: Presence,
    val display: DisplayMode,
    val confident: Boolean,
    val source: String,
    val foregroundPackage: String?,
    val sinceMs: Long,
)

object DeviceStateHub {
    private const val TAG = "PortalLauncherState"
    private const val USER_EXIT_GRACE_MS = 4_000L

    fun interface Listener {
        fun onStateChanged(state: DeviceState)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private var app: Context? = null
    private var userExitAt = 0L
    private var launcherForeground = false
    private var foregroundPackage: String? = null

    @Volatile
    var current = DeviceState(
        Presence.UNKNOWN,
        DisplayMode.OFF,
        confident = false,
        source = "boot",
        foregroundPackage = null,
        sinceMs = 0L
    )
        private set

    /**
     * Whether this device can meaningfully infer occupancy. The presence proxy relies on the
     * screensaver/daydream lifecycle, so it is only exposed on hardware that has a dream component
     * configured. Detected once at init; read by the MQTT bridge to gate presence discovery/state.
     */
    @Volatile
    var presenceCapable = false
        private set

    fun init(context: Context) {
        if (app != null) return
        app = context.applicationContext
        presenceCapable = hasDreamComponent(context.applicationContext)
        context.applicationContext.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_DREAMING_STARTED -> set(
                            Presence.PRESENT,
                            DisplayMode.DREAMING,
                            confident = true,
                            source = "dream_started",
                            context = c
                        )
                        Intent.ACTION_DREAMING_STOPPED -> onDreamStopped(c)
                        Intent.ACTION_SCREEN_ON -> recompute(c, "screen_on")
                        Intent.ACTION_SCREEN_OFF -> set(
                            Presence.ABSENT,
                            DisplayMode.OFF,
                            confident = true,
                            source = "screen_off",
                            context = c
                        )
                        Intent.ACTION_USER_PRESENT -> set(
                            Presence.PRESENT,
                            displayFor(c),
                            confident = true,
                            source = "user_present",
                            context = c
                        )
                    }
                }
            },
            IntentFilter().apply {
                addAction(Intent.ACTION_DREAMING_STARTED)
                addAction(Intent.ACTION_DREAMING_STOPPED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
        )
        recompute(context, "init")
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        listener.onStateChanged(current)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun onLauncherForeground(foreground: Boolean, context: Context) {
        launcherForeground = foreground
        if (foreground) {
            foregroundPackage = context.packageName
            set(Presence.PRESENT, DisplayMode.SCREENSAVER, confident = true, source = "launcher", context = context)
            SleepScheduler.apply(context)
        } else {
            recompute(context, "launcher_paused")
            SleepScheduler.cancel(context)
        }
    }

    fun noteLaunchingApp(packageName: String?, context: Context) {
        foregroundPackage = packageName
        userExitAt = System.currentTimeMillis()
        set(Presence.PRESENT, DisplayMode.APP, confident = true, source = "launch_app", context = context)
    }

    fun onForegroundPackage(packageName: String?, context: Context) {
        if (packageName.isNullOrBlank()) return
        foregroundPackage = packageName
        val mode = if (packageName == context.packageName && launcherForeground) DisplayMode.SCREENSAVER else DisplayMode.APP
        set(Presence.PRESENT, mode, confident = true, source = "accessibility", context = context)
        SleepScheduler.apply(context)
    }

    fun onScreenSleepRequested(context: Context) {
        set(
            Presence.ABSENT,
            DisplayMode.OFF,
            confident = true,
            source = "sleep_requested",
            context = context
        )
    }

    private fun onDreamStopped(context: Context) {
        val interactive = isInteractive(context)
        val userExitAgo = System.currentTimeMillis() - userExitAt
        when {
            userExitAgo in 0..USER_EXIT_GRACE_MS -> set(
                Presence.PRESENT,
                DisplayMode.APP,
                confident = true,
                source = "dream_user_exit",
                context = context
            )
            interactive -> set(
                Presence.PRESENT,
                displayFor(context),
                confident = true,
                source = "dream_redream",
                context = context
            )
            else -> set(
                Presence.ABSENT,
                DisplayMode.OFF,
                confident = true,
                source = "dream_sleep",
                context = context
            )
        }
    }

    private fun recompute(context: Context, source: String) {
        val mode = displayFor(context)
        val presence = when (mode) {
            DisplayMode.OFF -> Presence.ABSENT
            DisplayMode.SCREENSAVER, DisplayMode.APP, DisplayMode.DREAMING -> Presence.PRESENT
        }
        set(presence, mode, confident = mode != DisplayMode.APP || foregroundPackage != null, source = source, context = context)
    }

    private fun displayFor(context: Context): DisplayMode {
        if (!isInteractive(context)) return DisplayMode.OFF
        return if (launcherForeground) DisplayMode.SCREENSAVER else DisplayMode.APP
    }

    /** True when the device has a screensaver/daydream component configured to run. */
    private fun hasDreamComponent(context: Context): Boolean =
        runCatching {
            // Raw key: Settings.Secure.SCREENSAVER_COMPONENTS was removed from the public SDK in
            // API 33, but the underlying setting name is unchanged since API 17.
            !Settings.Secure.getString(
                context.contentResolver,
                "screensaver_components",
            ).isNullOrBlank()
        }.getOrDefault(false)

    private fun isInteractive(context: Context): Boolean =
        runCatching {
            context.getSystemService(PowerManager::class.java).isInteractive
        }.getOrDefault(false)

    private fun set(
        presence: Presence,
        display: DisplayMode,
        confident: Boolean,
        source: String,
        context: Context,
    ) {
        val next = DeviceState(
            presence = presence,
            display = display,
            confident = confident,
            source = source,
            foregroundPackage = foregroundPackage,
            sinceMs = System.currentTimeMillis()
        )
        val prev = current
        if (
            prev.presence == next.presence &&
            prev.display == next.display &&
            prev.confident == next.confident &&
            prev.foregroundPackage == next.foregroundPackage
        ) return
        current = next
        Log.i(TAG, "${prev.presence}/${prev.display} -> ${next.presence}/${next.display} source=$source")
        listeners.forEach { runCatching { it.onStateChanged(next) } }
        SleepScheduler.apply(context)
    }
}
