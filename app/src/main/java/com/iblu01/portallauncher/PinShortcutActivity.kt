package com.iblu01.portallauncher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.iblu01.portallauncher.ui.apps.ShortcutIconStore
import com.iblu01.portallauncher.ui.apps.key
import com.iblu01.portallauncher.ui.apps.toAndroidBitmap

/**
 * Accepts `ACTION_CONFIRM_PIN_SHORTCUT` requests — the flow an app uses to put one of its shortcuts
 * on the home screen. Without this a launcher silently drops every such request.
 *
 * Headless by design (`Theme.PortalLauncher.Invisible`): a wall panel has nobody at the keyboard to
 * confirm a dialog, so the request is accepted and the shortcut lands at the end of the grid, where
 * the long-press menu can remove it.
 *
 * The icon has to be rasterized and stored right here: once the request is consumed, the
 * `ShortcutInfo`'s icon can no longer be resolved.
 */
class PinShortcutActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = acceptPinRequest()
        if (label != null) {
            Toast.makeText(this, getString(R.string.toast_shortcut_added_format, label), Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    /** Returns the accepted shortcut's label, or null when there was nothing valid to accept. */
    private fun acceptPinRequest(): String? {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return null
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull() ?: return null
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return null
        if (!request.isValid) return null
        val info = request.shortcutInfo ?: return null
        if (!runCatching { request.accept() }.getOrDefault(false)) return null

        val label = (info.longLabel ?: info.shortLabel)?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: info.`package`
        val shortcut = PinnedShortcut(info.`package`, info.id, label)

        runCatching {
            launcherApps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi)
        }.getOrNull()?.let { drawable ->
            ShortcutIconStore(this).save(shortcut.key(), drawable.toAndroidBitmap())
        }

        val prefs = Prefs(this)
        prefs.pinnedShortcuts = prefs.pinnedShortcuts
            .filterNot { it.packageName == shortcut.packageName && it.shortcutId == shortcut.shortcutId }
            .plus(shortcut)
        return label
    }
}
