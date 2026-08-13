package com.iblu01.portallauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** One launchable app, icon already rasterized so the grid never touches the PackageManager. */
data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: ImageBitmap?,
)

/**
 * Owns the launchable-app list for the apps page.
 *
 * The old app drawer resolved labels and called `getApplicationIcon()` *inside composition*
 * (disk I/O on the main thread). That was hidden behind the overlay's fade; in a pager it would
 * show up as a stutter on the very first swipe. So the whole enumeration — `queryIntentActivities`,
 * `loadLabel`, icon rasterization — runs once on [Dispatchers.IO] and is published as a
 * `StateFlow` of ready-to-draw [ImageBitmap]s.
 *
 * Refreshed on install/uninstall through `LauncherApps.Callback` rather than package broadcasts:
 * it is the API meant for launchers and it covers managed profiles and storage availability.
 */
class AppListStore(
    private val context: Context,
    private val scope: CoroutineScope,
    private val launcherApps: LauncherAppsFacade = LauncherAppsFacade(context),
    private val iconSizePx: Int = ICON_SIZE_PX,
    /**
     * Package of the icon pack to theme icons with, read at every load rather than captured: the
     * user can change it in the settings while the launcher is alive.
     */
    private val iconPackPackage: () -> String = { "" },
) {
    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps.asStateFlow()

    private var loadJob: Job? = null
    private var packageCallback: LauncherApps.Callback? = null

    fun start() {
        if (packageCallback == null) {
            packageCallback = launcherApps.registerPackageCallback { refresh(force = true) }
        }
        refresh(force = false)
    }

    fun stop() {
        packageCallback?.let { launcherApps.unregisterPackageCallback(it) }
        packageCallback = null
    }

    /** Loads the list off-main. A no-op while a load is in flight, or when already loaded. */
    fun refresh(force: Boolean) {
        if (loadJob?.isActive == true) return
        if (!force && _apps.value.isNotEmpty()) return
        loadJob = scope.launch(Dispatchers.IO) { _apps.value = load() }
    }

    private fun load(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // One pack per load, shared by every icon: parsing appfilter.xml per app would re-read the
        // whole document a hundred times.
        val pack = IconPack.load(context, iconPackPackage())
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo?.packageName != context.packageName }
            .mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                val themed = pack?.drawableFor(IconPack.componentKey(ai.packageName, ai.name))
                LaunchableApp(
                    label = info.loadLabel(pm).toString(),
                    packageName = ai.packageName,
                    activityName = ai.name,
                    // An app the pack does not theme keeps its own icon rather than losing one.
                    icon = runCatching { (themed ?: info.loadIcon(pm)).toImageBitmap(iconSizePx) }
                        .getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }
}
