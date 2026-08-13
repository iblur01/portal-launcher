package com.iblu01.portallauncher

import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notification dots on the app grid.
 *
 * A launcher cannot ask "does this app have a notification?" — the only way is to be a
 * notification listener and keep the answer yourself, which is what this does: it publishes the set
 * of packages with a visible notification and nothing else. No content is read, kept, or logged.
 *
 * The service is instantiated by the system, so the state it feeds lives in [NotificationDots]
 * rather than in the instance: the UI observes a process-wide flow whether or not the system has
 * bound us yet (it never binds until the user grants access in the system settings).
 */
class NotificationDotService : NotificationListenerService() {

    override fun onListenerConnected() {
        publish()
    }

    override fun onListenerDisconnected() {
        NotificationDots.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    /**
     * Recomputes from `getActiveNotifications()` rather than adding/removing the one that just
     * changed: an app can hold several notifications, and a removal only means "no dot" once the
     * last one is gone. Cheap — the list is tens of entries at most.
     */
    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationDots.set(active.filter(::countsAsDot).map { it.packageName }.toSet())
    }

    /**
     * Which notifications earn a dot: the ones a user would call "waiting for me".
     *
     * Ongoing notifications (a running download, a media session, a foreground service) are the
     * permanent kind — dotting them would leave a dot lit forever. Group summaries are excluded
     * because their children are counted already.
     */
    private fun countsAsDot(sbn: StatusBarNotification): Boolean {
        if (sbn.isOngoing) return false
        val notification = sbn.notification ?: return false
        val isGroupSummary =
            (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0
        return !isGroupSummary
    }
}

/** Process-wide dot state, written by [NotificationDotService] and read by the grid. */
object NotificationDots {
    private val _packages = MutableStateFlow<Set<String>>(emptySet())
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    internal fun set(value: Set<String>) { _packages.value = value }
    internal fun clear() { _packages.value = emptySet() }

    /**
     * True when the user has granted notification-listener access to us. Read from the secure
     * setting because there is no API for "is my own listener enabled" before API 27's
     * `isNotificationListenerAccessGranted`, which is a system-only call from an app.
     */
    fun isAccessGranted(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        return enabled.split(':').any { it.substringBefore('/') == context.packageName }
    }
}
