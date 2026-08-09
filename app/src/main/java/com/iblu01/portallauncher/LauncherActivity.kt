package com.iblu01.portallauncher
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.domain.model.MediaPlayerVolume
import com.iblu01.portallauncher.domain.model.TemperatureSummary

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.appwidget.AppWidgetManager
import android.app.WallpaperManager
import android.os.Environment
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iblu01.portallauncher.ui.BackAction
import com.iblu01.portallauncher.ui.CallService
import com.iblu01.portallauncher.ui.backAction
import com.iblu01.portallauncher.ui.LauncherViewModel
import com.iblu01.portallauncher.ui.LocalAreas
import com.iblu01.portallauncher.ui.LocalCallService
import com.iblu01.portallauncher.ui.LocalHaStates
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.iblu01.portallauncher.ui.mapper.toChipAction
import com.iblu01.portallauncher.ui.mapper.toPanelKind
import com.iblu01.portallauncher.ui.model.ChipAction
import com.iblu01.portallauncher.ui.model.PanelKind
import com.iblu01.portallauncher.ui.panel.PanelEvent
import com.iblu01.portallauncher.ui.panel.PanelSource
import com.iblu01.portallauncher.ui.panel.PanelRequest
import com.iblu01.portallauncher.ui.apps.AppListStore
import com.iblu01.portallauncher.ui.apps.AppShortcut
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.apps.GridSpec
import com.iblu01.portallauncher.ui.apps.appPageCount
import com.iblu01.portallauncher.ui.apps.placeItems
import com.iblu01.portallauncher.ui.apps.LauncherAppsFacade
import com.iblu01.portallauncher.ui.apps.LauncherLayoutStore
import com.iblu01.portallauncher.ui.apps.ShortcutIconStore
import com.iblu01.portallauncher.ui.apps.WidgetHostController
import com.iblu01.portallauncher.ui.apps.WidgetOffer
import com.iblu01.portallauncher.ui.components.AlertOverlay
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.AppContextMenu
import com.iblu01.portallauncher.ui.components.AppMenuTarget
import com.iblu01.portallauncher.ui.components.AppGridPage
import com.iblu01.portallauncher.ui.components.DraggedIconOverlay
import com.iblu01.portallauncher.ui.components.LauncherHeaderActions
import com.iblu01.portallauncher.ui.components.WidgetPickerDialog
import com.iblu01.portallauncher.ui.components.rememberGridDragState
import com.iblu01.portallauncher.ui.components.returnToClockPage
import com.iblu01.portallauncher.ui.components.AutoReturnOverlay
import com.iblu01.portallauncher.ui.components.ChipActionsPanel
import com.iblu01.portallauncher.ui.components.ClockHeader
import com.iblu01.portallauncher.ui.components.GroupBrowserPanel
import com.iblu01.portallauncher.ui.components.HomePillActions
import com.iblu01.portallauncher.ui.components.HomePillMove
import com.iblu01.portallauncher.ui.components.HomePillMoveAvailability
import com.iblu01.portallauncher.ui.components.HiddenAppsDialog
import com.iblu01.portallauncher.ui.components.ClockTray
import com.iblu01.portallauncher.ui.components.LauncherPager
import com.iblu01.portallauncher.ui.components.LauncherPagerLayout
import com.iblu01.portallauncher.ui.components.ManualGroupMenuOption
import com.iblu01.portallauncher.ui.components.PageIdentity
import com.iblu01.portallauncher.ui.components.collapseFraction
import com.iblu01.portallauncher.ui.components.clockHeaderAlpha
import com.iblu01.portallauncher.ui.components.pagerDragForward
import com.iblu01.portallauncher.ui.components.rememberLauncherPagerState
import com.iblu01.portallauncher.ui.components.MediaPlayerView
import com.iblu01.portallauncher.ui.components.MediaDevicesPanel
import com.iblu01.portallauncher.ui.components.PanelContent
import com.iblu01.portallauncher.ui.components.LauncherPanelLayout
import com.iblu01.portallauncher.ui.components.QuickActionsOverlay
import com.iblu01.portallauncher.ui.components.WeatherController
import com.iblu01.portallauncher.ui.components.WeatherPanel
import com.iblu01.portallauncher.ui.onboarding.OnboardingActivity
import com.iblu01.portallauncher.ui.onboarding.OnboardingStatus
import com.iblu01.portallauncher.ui.onboarding.shouldRunOnboarding
import com.iblu01.portallauncher.ui.home.HomePage
import com.iblu01.portallauncher.ui.home.HomePageEditActions
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.blurCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.snapshotFlow
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeCriticalCapacityPolicy
import com.iblu01.portallauncher.domain.home.HomePillComposer
import com.iblu01.portallauncher.domain.home.HomeSectionPreference
import com.iblu01.portallauncher.domain.home.PillGroupSnapshot
import com.iblu01.portallauncher.domain.home.PillRef

@AndroidEntryPoint
class LauncherActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    @Inject lateinit var prefs: Prefs
    @Inject lateinit var pills: PillRepository
    private var lastLaunchMs = 0L
    private lateinit var autoReturnTimer: AutoReturnTimer
    private lateinit var appList: AppListStore
    private lateinit var layout: LauncherLayoutStore
    private lateinit var launcherApps: LauncherAppsFacade
    private lateinit var widgets: WidgetHostController
    /** Widget id waiting for the bind-consent or configure activity to come back. */
    private var pendingWidgetId = GridItem.NO_WIDGET
    /** Bumped on every HOME press while already home, so the UI can go back to its resting state. */
    private var homePresses by mutableStateOf(0)
    /**
     * True while the pause we are heading into was caused by the user opening something from the
     * launcher. Screen-off and every other pause resets the pager to the clock (a wall panel must
     * not wake up on the app grid); a launch must not, or the page snaps back before the app even
     * appears and coming back lands on the clock instead of where the icon was.
     */
    private var openingFromLauncher = false
    /** True while an alarm is counting down / triggered: the screen must not lock under the keypad. */
    private var alarmHold = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (openOnboardingIfNeeded()) return
        MqttBridgeService.start(this)
        applyPowerPolicy()

        autoReturnTimer = AutoReturnTimer(lifecycleScope, prefs)
        launcherApps = LauncherAppsFacade(applicationContext)
        appList = AppListStore(applicationContext, lifecycleScope, launcherApps)
        layout = LauncherLayoutStore(prefs, ShortcutIconStore(applicationContext), lifecycleScope)
        widgets = WidgetHostController(applicationContext, prefs, lifecycleScope)
        // Registered for the activity's whole life, not per-resume: an uninstall happens while the
        // launcher is paused, and re-registering on resume would miss the change.
        appList.start()

        setContent {
            PortalTheme {
                PortalLauncherApp(
                    prefs = prefs,
                    pills = pills,
                    autoReturnTimer = autoReturnTimer,
                    appList = appList,
                    layout = layout,
                    launcherApps = launcherApps,
                    widgets = widgets,
                    homePresses = homePresses,
                    onOpenHomeAssistant = ::openHomeAssistant,
                    onOpenSettings = { openFromLauncher(Intent(this, SettingsActivity::class.java)) },
                    onOpenPlayground = { openFromLauncher(Intent(this, PlaygroundActivity::class.java)) },
                    onLaunchItem = ::launchItem,
                    onAppInfo = ::openAppInfo,
                    onUninstall = ::uninstallApp,
                    onStartShortcut = ::startShortcut,
                    onOpenHomeSettings = ::openHomeSettings,
                    onSetWallpaper = ::setWallpaper,
                    onAddWidget = ::addWidget,
                    onRemoveWidget = { widgets.release(it) },
                    keepPageAcrossPause = ::keepPageAcrossPause,
                    onAlarmAlerting = ::onAlarmAlerting,
                )
            }
        }
    }

    /**
     * Hands over to the first-run assistant when this device has never been set up, and reports
     * whether it did — the launcher then skips its own start-up entirely rather than briefly
     * drawing a home screen behind the assistant.
     *
     * A panel that already had a working Home Assistant setup before the assistant existed counts
     * as configured (see [shouldRunOnboarding]), so an update never drops it into a wizard.
     */
    private fun openOnboardingIfNeeded(): Boolean {
        val status = OnboardingStatus(
            completed = prefs.onboardingCompleted,
            version = prefs.onboardingVersion,
            legacyConfigured = prefs.haToken.isNotBlank(),
        )
        if (!shouldRunOnboarding(status)) return false
        startActivity(OnboardingActivity.intent(this))
        finish()
        return true
    }

    /**
     * HOME pressed while the launcher is already in front. `singleTask` routes that here instead of
     * recreating the activity, and a launcher is expected to return to its resting screen.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePresses++
    }

    /** Consulted by the UI on `ON_PAUSE`: keep the current page, or fall back to the clock. */
    private fun keepPageAcrossPause(): Boolean = openingFromLauncher

    private fun openFromLauncher(intent: Intent) {
        openingFromLauncher = true
        runCatching { startActivity(intent) }.onFailure { openingFromLauncher = false }
    }

    private fun startShortcut(packageName: String, shortcutId: String) {
        openingFromLauncher = true
        DeviceStateHub.noteLaunchingApp(packageName, this)
        launcherApps.startShortcut(packageName, shortcutId)
    }

    override fun onStart() {
        super.onStart()
        // The host is a live connection to other processes: listen only while we are on screen.
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        openingFromLauncher = false
        widgets.reload()
        pills.start(prefs)
        // PinShortcutActivity writes pinned shortcuts straight to prefs, behind the store's back.
        layout.reload()
        applyPowerPolicy()
        DeviceStateHub.onLauncherForeground(true, this)
        enableImmersive()
    }

    override fun onPause() {
        DeviceStateHub.onLauncherForeground(false, this)
        super.onPause()
    }

    override fun onDestroy() {
        // The hold lives in a process-wide object: leaving it set would keep the idle timeout off
        // for good if the alarm is still alerting when the launcher goes away.
        onAlarmAlerting(false)
        appList.stop()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersive()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN || ev.actionMasked == MotionEvent.ACTION_UP) {
            SleepScheduler.onInteraction(this)
            autoReturnTimer.onInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Alarm entry delay / triggered: hold the screen awake and suspend the idle timeout, so the
     * disarm keypad the UI just forced open cannot be locked away before the code is typed.
     */
    private fun onAlarmAlerting(active: Boolean) {
        if (alarmHold == active) return
        alarmHold = active
        SleepScheduler.setAlarmHold(this, active)
        applyPowerPolicy()
    }

    private fun applyPowerPolicy() {
        if (alarmHold || prefs.powerMode == PowerMode.ALWAYS_ON || prefs.devKeepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun openHomeAssistant() {
        val now = System.currentTimeMillis()
        if (now - lastLaunchMs < 1_000L) return
        lastLaunchMs = now

        val pkg = prefs.homeAssistantPackage
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.toast_app_not_found_pkg_format, pkg), Toast.LENGTH_LONG).show()
            return
        }
        DeviceStateHub.noteLaunchingApp(pkg, this)
        openFromLauncher(intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Launches a grid tile: a pinned shortcut goes through LauncherApps, an app through an intent. */
    private fun launchItem(item: GridItem) {
        if (item.isShortcut) {
            startShortcut(item.packageName, item.shortcutId)
            return
        }
        DeviceStateHub.noteLaunchingApp(item.packageName, this)
        val intent = LauncherAppsFacade.launchIntent(this, item.packageName, item.activityName)
        if (intent == null) {
            Toast.makeText(this, getString(R.string.toast_app_not_found_label_format, item.label), Toast.LENGTH_SHORT).show()
            return
        }
        openingFromLauncher = true
        runCatching { startActivity(intent) }.onFailure {
            openingFromLauncher = false
            Toast.makeText(this, getString(R.string.toast_cannot_open_app_format, item.label), Toast.LENGTH_SHORT).show()
        }
    }

    /** The system's home-app chooser. Without the home role, shortcuts and pinning cannot work. */
    private fun openHomeSettings() {
        openFromLauncher(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun setWallpaper() {
        // The Android picker changes the system wallpaper, so switch Portal to the matching source
        // before leaving. This also makes live wallpapers visible as soon as their preview applies.
        prefs.backgroundMode = "system"
        SettingsChangeBus.get().emit("backgroundMode")
        openFromLauncher(
            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.toast_choose_wallpaper))
        )
    }

    /**
     * Adds a widget: allocate an id, bind it (asking the user when the launcher may not bind on its
     * own), then run the provider's configuration screen if it demands one. Every failure path
     * releases the id — a leaked id keeps the provider updating a widget nobody can see.
     */
    private fun addWidget(offer: WidgetOffer) {
        val id = widgets.allocateId()
        pendingWidgetId = id
        if (!widgets.bindIfAllowed(id, offer.provider)) {
            openingFromLauncher = true
            runCatching {
                startActivityForResult(widgets.bindConsentIntent(id, offer.provider), REQ_WIDGET_BIND)
            }.onFailure {
                openingFromLauncher = false
                widgets.release(id)
                pendingWidgetId = GridItem.NO_WIDGET
            }
            return
        }
        configureOrKeepWidget(id)
    }

    private fun configureOrKeepWidget(widgetId: Int) {
        if (widgets.needsConfigure(widgetId)) {
            openingFromLauncher = true
            widgets.startConfigure(this, widgetId, REQ_WIDGET_CONFIGURE)
            return
        }
        widgets.keep(widgetId)
        pendingWidgetId = GridItem.NO_WIDGET
    }

    @Deprecated("Widget bind/configure predate the result APIs and still use request codes.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_WIDGET_BIND && requestCode != REQ_WIDGET_CONFIGURE) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            ?: pendingWidgetId
        if (resultCode != RESULT_OK || id == GridItem.NO_WIDGET) {
            widgets.release(id)
            pendingWidgetId = GridItem.NO_WIDGET
            return
        }
        if (requestCode == REQ_WIDGET_BIND) configureOrKeepWidget(id) else {
            widgets.keep(id)
            pendingWidgetId = GridItem.NO_WIDGET
        }
    }

    private fun openAppInfo(packageName: String) {
        openFromLauncher(LauncherAppsFacade.appInfoIntent(packageName))
    }

    private fun uninstallApp(packageName: String) {
        openFromLauncher(LauncherAppsFacade.uninstallIntent(packageName))
    }

    private companion object {
        const val REQ_WIDGET_BIND = 4101
        const val REQ_WIDGET_CONFIGURE = 4102
    }

    @Suppress("DEPRECATION")
    private fun enableImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

/** Extra wallpaper dimming once the app grid is fully in view. */
private const val APPS_PAGE_SCRIM = 0.45f

/**
 * The whole launcher UI: ambient clock, floating widget tray, and the long-press
 * quick-actions overlay. All Android side-effects are injected as lambdas so this
 * composable stays previewable and free of Activity state.
 */
@Composable
private fun PortalLauncherApp(
    prefs: Prefs,
    pills: PillRepository,
    autoReturnTimer: AutoReturnTimer,
    appList: AppListStore,
    layout: LauncherLayoutStore,
    launcherApps: LauncherAppsFacade,
    widgets: WidgetHostController,
    homePresses: Int,
    onOpenHomeAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlayground: () -> Unit,
    onLaunchItem: (GridItem) -> Unit,
    onAppInfo: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onStartShortcut: (packageName: String, shortcutId: String) -> Unit,
    onOpenHomeSettings: () -> Unit,
    onSetWallpaper: () -> Unit,
    onAddWidget: (WidgetOffer) -> Unit,
    onRemoveWidget: (Int) -> Unit,
    keepPageAcrossPause: () -> Boolean,
    onAlarmAlerting: (Boolean) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var backgroundMode by remember { mutableStateOf(prefs.backgroundMode) }
    var bgOverlayOpacity by remember { mutableStateOf(prefs.bgOverlayOpacity) }
    var clockTheme by remember { mutableStateOf(prefs.clockTheme) }
    var gridScale by remember { mutableStateOf(prefs.gridScale) }
    // Bumped on every "backgroundMode" emission (even custom->custom) so CustomWallpaper
    // re-reads the file's lastModified() and Coil busts its stale cache on replacement.
    var wallpaperVersion by remember { mutableStateOf(0) }
    // Auto-return is about an *idle, visible* panel. While another app is in front, the launcher is
    // not idle — letting the countdown run there would drag the page home behind the user's back.
    var resumed by remember { mutableStateOf(true) }
    val context = LocalContext.current
    // Grid geometry is discovered by the pages themselves (they know their size); until the first
    // layout a sane default keeps placement resolvable.
    val gridSpecState = remember { mutableStateOf(GridSpec(4, 3)) }
    val gridDrag = rememberGridDragState()
    val pagerScope = rememberCoroutineScope()
    // The grid, resolved once and read by every page. `derivedStateOf` rather than plain vals so
    // the pager's pageCount lambda keeps reading live state instead of a captured snapshot.
    val gridItemsState =
        remember(layout) { layout.items(appList.apps, widgets.items) }.collectAsStateWithLifecycle()
    val hiddenItemsState = remember(layout) { layout.hiddenItems(appList.apps) }.collectAsStateWithLifecycle()
    val storedCellsState = layout.storedCells.collectAsStateWithLifecycle()
    val placedItems = remember(layout) {
        derivedStateOf { placeItems(gridItemsState.value, storedCellsState.value, gridSpecState.value) }
    }
    // The growth page only exists while an icon is in hand; `isDragging` is state, so the pager's
    // page count follows the drag.
    val appPages = remember {
        derivedStateOf { appPageCount(placedItems.value, spare = gridDrag.isDragging) }
    }

    // Collect the complete home snapshot before creating the pager: Maison's preference controls
    // both page count and the logical clock index, and must therefore come from the same atomic UI
    // state as its catalog/composition rather than from a second local preference read.
    val vm: LauncherViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    snapshots = pills.snapshotFlow(prefs),
                    callServiceFn = pills::callService,
                    updateHomePreferencesFn = { transform ->
                        pills.updateHomePillPreferences(prefs, transform)
                    },
                )
            }
        }
    )
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val pagerLayout = LauncherPagerLayout(
        homePageEnabled = ui.homePreferences.homePageEnabled,
        appPageCount = appPages.value,
    )
    val pagerState = rememberLauncherPagerState(
        homePageEnabled = { ui.homePreferences.homePageEnabled },
        appPageCount = { appPages.value },
    )
    val latestPagerLayout by rememberUpdatedState(pagerLayout)

    // Match Launcher3's wallpaper protocol: advertise the horizontal page step and continuously
    // report the pager position. WallpaperService handles static and live wallpaper movement;
    // failures are intentionally ignored because some fixed-wallpaper OEM implementations reject
    // offsets even though displaying the wallpaper still works.
    val hostView = LocalView.current
    LaunchedEffect(hostView, pagerState, pagerLayout, backgroundMode) {
        if (backgroundMode != "system") return@LaunchedEffect
        val manager = WallpaperManager.getInstance(hostView.context)
        val pageCount = pagerLayout.pageCount.coerceAtLeast(1)
        val xStep = if (pageCount > 1) 1f / (pageCount - 1) else 0f
        runCatching { manager.setWallpaperOffsetSteps(xStep, 0f) }
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .map { page ->
                if (pageCount > 1) (page / (pageCount - 1)).coerceIn(0f, 1f) else 0.5f
            }
            .distinctUntilChanged()
            .collect { x ->
                hostView.windowToken?.let { token ->
                    runCatching { manager.setWallpaperOffsets(token, x, 0.5f) }
                }
            }
    }

    LaunchedEffect(Unit) {
        SettingsChangeBus.get().changes.collect { key ->
            when (key) {
                "backgroundMode" -> {
                    backgroundMode = prefs.backgroundMode
                    wallpaperVersion++
                }
                "haUrl", "haToken" -> pills.start(prefs)
                "brokerHost", "brokerPort", "username", "password" ->
                    MqttBridgeService.reconnect(context)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumed = true
                backgroundMode = prefs.backgroundMode
                bgOverlayOpacity = prefs.bgOverlayOpacity
                clockTheme = prefs.clockTheme
                gridScale = prefs.gridScale
            }
            // Never *wake* on the app grid — but a pause caused by opening an app must keep the
            // page, or it snaps back before the app appears and coming back lands on the clock.
            if (event == Lifecycle.Event.ON_PAUSE) resumed = false
            if (event == Lifecycle.Event.ON_PAUSE && !keepPageAcrossPause()) {
                pagerScope.launch { pagerState.scrollToPage(latestPagerLayout.clockPage) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val chips = ui.chips
    val temperatures = ui.temperatures
    val mediaSessions = ui.mediaSessions
    val mediaDevices = ui.latestStates.values.filter { it.domain == "media_player" }.map { entity ->
        mediaSessions.firstOrNull { session -> session.players.any { it.entityId == entity.entityId } }
            ?: PlayingMedia(
                entityId = entity.entityId,
                title = entity.attributes.optString("media_title").ifBlank { "Aucun média" },
                artist = entity.name,
                album = entity.attributes.optString("media_album_name").takeIf { it.isNotBlank() },
                state = entity.state,
                coverUrl = null,
                volumePercent = (entity.attributes.optDouble("volume_level", 0.0) * 100).toInt(),
                isMuted = entity.attributes.optBoolean("is_volume_muted", false),
                playerNames = listOf(entity.name),
                players = listOf(MediaPlayerVolume(entity.entityId, entity.name, (entity.attributes.optDouble("volume_level", 0.0) * 100).toInt(), entity.attributes.optBoolean("is_volume_muted", false))),
                hasMedia = false,
            )
    }.distinctBy { it.entityId }.sortedBy { it.playerNames.firstOrNull().orEmpty() }
    val haConnected = ui.connected
    val haLastUpdateAt = ui.lastUpdateAt
    // Media-selection state stays local (moves to the panel reducer at step 6).
    var activeMedia by remember { mutableStateOf<PlayingMedia?>(null) }
    var secondaryMedia by remember { mutableStateOf(emptyList<PlayingMedia>()) }
    var displayedSecondaryMedia by remember { mutableStateOf(emptyList<PlayingMedia>()) }
    var selectedMediaEntityId by remember { mutableStateOf<String?>(null) }
    var browsedMediaEntityId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaSessions, selectedMediaEntityId) {
        val selected = mediaSessions.firstOrNull { session ->
            session.players.any { it.entityId == selectedMediaEntityId }
        } ?: mediaSessions.firstOrNull()
        activeMedia = selected
        selectedMediaEntityId = selected?.entityId
        secondaryMedia = mediaSessions.filterNot { it.entityId == selected?.entityId }
    }
    LaunchedEffect(mediaSessions, secondaryMedia) {
        val activePlayerIds = mediaSessions.flatMap { it.players }.map { it.entityId }.toSet()
        val recentlyRemoved = displayedSecondaryMedia.filter { session ->
            session.players.none { it.entityId in activePlayerIds }
        }
        displayedSecondaryMedia = secondaryMedia + recentlyRemoved
        if (recentlyRemoved.isNotEmpty()) {
            delay(6_000)
            displayedSecondaryMedia = secondaryMedia
        }
    }
    val weatherController = remember { WeatherController(context.applicationContext, pills) }
    val weather = weatherController.state
    DisposableEffect(weatherController) {
        weatherController.start()
        onDispose { weatherController.stop() }
    }
    var overlayVisible by remember { mutableStateOf(false) }
    var pillsExpanded by remember { mutableStateOf(false) }
    var homeEditing by remember { mutableStateOf(false) }
    var armedPillReorderKey by remember { mutableStateOf<String?>(null) }
    var pillDragActive by remember { mutableStateOf(false) }
    // Long-press menu of the app grid: which tile, and its shortcuts (queried lazily, off-main).
    var menuTarget by remember { mutableStateOf<AppMenuTarget?>(null) }
    var menuShortcuts by remember { mutableStateOf(emptyList<AppShortcut>()) }
    var appDragActive by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    // Non-null means the picker is open; the list itself is enumerated off-main on demand.
    var widgetOffers by remember { mutableStateOf<List<WidgetOffer>?>(null) }
    var widgetPickerRequested by remember { mutableStateOf(false) }
    LaunchedEffect(widgetPickerRequested) {
        if (!widgetPickerRequested) return@LaunchedEffect
        widgetOffers = withContext(Dispatchers.IO) { widgets.offers() }
    }
    val hiddenItems = hiddenItemsState.value
    LaunchedEffect(menuTarget?.item?.key) {
        val target = menuTarget
        menuShortcuts = emptyList()
        if (target == null || target.item.isShortcut) return@LaunchedEffect
        menuShortcuts = withContext(Dispatchers.IO) {
            launcherApps.shortcutsFor(target.item.packageName).take(4)
        }
    }

    // Side panel state lives in the reducer (VM, step 6). Media auto-open/stop is driven by the
    // media flow; user taps dispatch PanelEvents. Panel no longer closes on chip disappearance —
    // panelChip is resolved last-known-good by the VM.
    val panel by vm.panel.collectAsStateWithLifecycle()
    val panelChip by vm.panelChip.collectAsStateWithLifecycle()
    val autoReturnState by autoReturnTimer.state.collectAsStateWithLifecycle()

    // Alarm entry delay / triggered: the VM forces the keypad panel up (PanelSource.ALERT); the
    // Activity mirrors the flag onto the screen policy so nothing locks it away mid-countdown.
    val alarmAlerting by vm.alarmAlerting.collectAsStateWithLifecycle()
    LaunchedEffect(alarmAlerting) { onAlarmAlerting(alarmAlerting) }

    // Auto-return is for *user* state only: a USER panel, the expanded tray, the app overlay. An
    // AUTO (media) panel is the resting state while something plays, so it must not arm the timer.
    // Sitting on the apps page is user state too, exactly like the expanded tray: the wall panel
    // must fall back to the clock on its own.
    val awayFromClock = pagerLayout.identityOf(pagerState.currentPage) != PageIdentity.Clock
    val userState = pillsExpanded || overlayVisible || awayFromClock || menuTarget != null ||
        showHidden || armedPillReorderKey != null || pillDragActive
    // While an alarm is alerting the countdown is suspended outright: returning to the clock would
    // take the disarm keypad off screen exactly when it is needed.
    LaunchedEffect(panel.request, panel.source, userState, resumed, alarmAlerting) {
        val userPanelOpen = panel.request != null && panel.source == PanelSource.USER
        if (resumed && !alarmAlerting && (userPanelOpen || userState)) autoReturnTimer.start()
        else autoReturnTimer.stop()
    }

    LaunchedEffect(autoReturnState.shouldReturn) {
        if (autoReturnState.shouldReturn) {
            // Only a USER panel is dismissed: dismissing an AUTO media panel here would be read as
            // a user dismissal by the reducer (dismissedAutoKey) and suppress it for that session.
            if (panel.request != null && panel.source == PanelSource.USER) vm.onEvent(PanelEvent.Dismiss)
            if (pillsExpanded) pillsExpanded = false
            if (overlayVisible) overlayVisible = false
            if (menuTarget != null) menuTarget = null
            if (showHidden) showHidden = false
            homeEditing = false
            armedPillReorderKey = null
            pillDragActive = false
            // Deliberately not awaited here: `stop()` below (and the arming effect, once the page
            // midpoint is crossed) clears `shouldReturn`, which would cancel this very effect and
            // strand the pager mid-scroll.
            if (pagerState.currentPage != pagerLayout.clockPage) {
                returnToClockPage(pagerScope, pagerState, pagerLayout)
            }
            autoReturnTimer.stop()
        }
    }
    val media = activeMedia
    val groupRequest = panel.request as? PanelRequest.Group
    val livePanelGroup = groupRequest?.let { ui.catalog.groups[it.destination] }
    var lastPanelGroup by remember { mutableStateOf<PillGroupSnapshot?>(null) }
    LaunchedEffect(groupRequest?.destination, livePanelGroup) {
        if (groupRequest == null) lastPanelGroup = null
        else if (livePanelGroup != null) lastPanelGroup = livePanelGroup
    }
    val panelContent: PanelContent? = when (val req = panel.request) {
        is PanelRequest.Weather -> PanelContent.Weather(weather)
        is PanelRequest.Media -> {
            val session = mediaSessions.firstOrNull { it.entityId == req.key } ?: media
            session?.let { PanelContent.Media(it) }
        }
        is PanelRequest.Chip -> resolveChipPanelContent(req, panelChip, mediaDevices)
        is PanelRequest.Group -> (livePanelGroup ?: lastPanelGroup)?.let { group ->
            PanelContent.Group(
                group = group,
                selectedDevice = panelChip,
                deviceRequested = req.device != null,
            )
        }
        null -> null
    }
    // AnimatedVisibility keeps its subtree for the exit transition, but panelContent itself becomes
    // null as soon as the reducer dismisses it. Retain the last payload just long enough for the
    // full-sized panel to slide back into the screen edge instead of vanishing on the first frame.
    var retainedPanelContent by remember { mutableStateOf<PanelContent?>(null) }
    LaunchedEffect(panelContent) {
        if (panelContent != null) retainedPanelContent = panelContent
        else {
            delay(500)
            retainedPanelContent = null
        }
    }
    LaunchedEffect(panel.request) {
        val request = panel.request as? PanelRequest.Chip
        if (request?.panelKind != PanelKind.MEDIA) browsedMediaEntityId = null
    }
    // Dropping an icon back onto an earlier page removes the growth page from under our feet.
    LaunchedEffect(appPages.value) {
        val lastPage = (pagerLayout.pageCount - 1).coerceAtLeast(pagerLayout.clockPage)
        if (pagerState.currentPage > lastPage) pagerState.animateScrollToPage(page = lastPage)
    }
    // Back never escapes the launcher: finishing a home activity gives a black flash while the
    // system restarts it. Innermost surface first, then the page, then nothing.
    BackHandler(enabled = true) {
        if (armedPillReorderKey != null) {
            armedPillReorderKey = null
            pillDragActive = false
            return@BackHandler
        }
        when (
            backAction(
                itemMenuOpen = menuTarget != null,
                hiddenListOpen = showHidden,
                widgetPickerOpen = widgetPickerRequested,
                quickActionsOpen = overlayVisible,
                userPanelOpen = panel.request != null && panel.source == PanelSource.USER,
                currentPage = pagerLayout.identityOf(pagerState.currentPage) ?: PageIdentity.Clock,
            )
        ) {
            BackAction.CloseItemMenu -> menuTarget = null
            BackAction.CloseHiddenList -> showHidden = false
            BackAction.CloseWidgetPicker -> {
                widgetPickerRequested = false
                widgetOffers = null
            }
            BackAction.CloseQuickActions -> overlayVisible = false
            BackAction.DismissPanel -> vm.onEvent(PanelEvent.Back)
            BackAction.GoToClockPage -> returnToClockPage(pagerScope, pagerState, pagerLayout)
            BackAction.Nothing -> Unit
        }
    }

    // HOME pressed while already home: back to the resting screen, like any launcher.
    LaunchedEffect(homePresses) {
        menuTarget = null
        showHidden = false
        widgetPickerRequested = false
        widgetOffers = null
        overlayVisible = false
        pillsExpanded = false
        homeEditing = false
        armedPillReorderKey = null
        pillDragActive = false
        if (panel.request != null && panel.source == PanelSource.USER) {
            vm.onEvent(PanelEvent.Dismiss)
        }
        if (pagerState.currentPage != pagerLayout.clockPage) {
            returnToClockPage(pagerScope, pagerState, pagerLayout)
        }
    }
    // Remembered so it stays the same instance across the recompositions every HA push triggers
    // (unstable-param skip guard for the open panel, e.g. the alarm keypad — removed at step 10).
    val onPanelDismiss: () -> Unit = remember(vm) { { vm.onEvent(PanelEvent.Dismiss) } }
    // Dumb dispatcher (design §4): the mapper resolved the action, so no chip.id/kind branching here.
    val onChipClick: (LauncherChip) -> Unit = { chip ->
        when (val action = chip.toChipAction()) {
            is ChipAction.ServiceToggle -> vm.callService(action.domain, action.service, chip.entityId)
            is ChipAction.OpenPanel -> vm.onEvent(PanelEvent.OpenChip(PanelRequest.Chip(chip.id, action.panelKind)))
        }
    }
    val pinnedRefs = ui.homePreferences.pinnedOrder.toSet()
    val manualGroupOptions = ui.homePreferences.manualGroups.map { group ->
        ManualGroupMenuOption(
            groupId = group.id,
            name = group.name,
            memberEntityIds = group.members.mapTo(linkedSetOf()) { it.entityId },
        )
    }
    val onOpenResolvedPill: (com.iblu01.portallauncher.domain.home.ResolvedPill) -> Unit = { pill ->
        when (pill.ref) {
            is PillRef.Device -> onChipClick(pill.chip)
            else -> vm.onEvent(PanelEvent.OpenGroup(PanelRequest.Group(pill.ref)))
        }
    }
    val onOpenResolvedCommands: (com.iblu01.portallauncher.domain.home.ResolvedPill) -> Unit = { pill ->
        when (pill.ref) {
            is PillRef.Device -> vm.onEvent(
                PanelEvent.LongPressChip(PanelRequest.Chip(pill.chip.id, pill.chip.toPanelKind())),
            )
            else -> vm.onEvent(PanelEvent.OpenGroup(PanelRequest.Group(pill.ref)))
        }
    }
    val homePillActions = HomePillActions(
        onOpen = onOpenResolvedPill,
        onSetPinned = { pill, pinned -> vm.setPinned(pill.ref, pinned) },
        onAddToManualGroup = { pill, groupId ->
            val device = pill.ref as? PillRef.Device
            if (device != null) {
                vm.updateHomePillPreferences { preferences ->
                    preferences.copy(
                        manualGroups = preferences.manualGroups.map { group ->
                            if (group.id != groupId || device in group.members) group
                            else group.copy(members = group.members + device)
                        },
                    )
                }
            }
        },
        onStartReorder = { pill ->
            if (pill.ref in pinnedRefs) armedPillReorderKey = pill.ref.stableKey
        },
        onMove = { pill, move ->
            val order = ui.homePreferences.pinnedOrder
            val index = order.indexOf(pill.ref)
            if (index >= 0) {
                val target = when (move) {
                    HomePillMove.FIRST -> 0
                    HomePillMove.BEFORE -> index - 1
                    HomePillMove.AFTER -> index + 1
                    HomePillMove.LAST -> order.lastIndex
                }
                vm.movePinned(pill.ref, target)
            }
        },
        onOpenCommands = onOpenResolvedCommands,
        onHideDevice = { pill -> pills.setPillEnabled(prefs, pill, enabled = false) },
        canReorder = { it.ref in pinnedRefs },
        moveAvailability = { pill ->
            val index = ui.homePreferences.pinnedOrder.indexOf(pill.ref)
            val last = ui.homePreferences.pinnedOrder.lastIndex
            HomePillMoveAvailability(
                before = index > 0,
                after = index in 0 until last,
                first = index > 0,
                last = index in 0 until last,
            )
        },
        isDragReordering = { it.ref.stableKey == armedPillReorderKey },
        onDragActiveChange = { pillDragActive = it },
        onDragDrop = { pill, offset ->
            val order = ui.homePreferences.pinnedOrder
            val index = order.indexOf(pill.ref)
            if (index >= 0) vm.movePinned(pill.ref, index + offset)
        },
        onDragFinished = {
            pillDragActive = false
            armedPillReorderKey = null
        },
    )
    val onSecondaryPlayPause: (PlayingMedia) -> Unit = { session ->
        displayedSecondaryMedia = displayedSecondaryMedia.map {
            if (it.entityId == session.entityId) it.copy(
                state = if (it.state in setOf("playing", "buffering")) "paused" else "playing"
            ) else it
        }
        session.players.forEach { player ->
            vm.callService("media_player", "media_play_pause", player.entityId)
        }
    }

    val selectedChipKey = panel.request?.key

    val bottomGradientHeight by animateDpAsState(
        targetValue = if (pillsExpanded) 620.dp else 360.dp,
        animationSpec = tween(450),
        label = "bottomGradientHeight"
    )

    val alertMessage = AlertOverlayState.activeMessage
    val blurRadius by animateDpAsState(
        targetValue = if (alertMessage != null || overlayVisible) 16.dp else 0.dp,
        animationSpec = tween(300),
        label = "blurRadius"
    )

    val sidePanel: @Composable (PanelContent) -> Unit = { content ->
        when (content) {
            is PanelContent.Media -> MediaPlayerPanel(
                media = content.session,
                secondaryMedia = displayedSecondaryMedia,
                prefs = prefs,
                mediaSessions = mediaSessions,
                onSelectSession = { selectedMediaEntityId = it },
                onDismiss = onPanelDismiss,
                onSecondaryPlayPause = onSecondaryPlayPause,
            )
            is PanelContent.ChipActions -> ChipActionsPanel(
                chip = content.chip,
                onDismiss = onPanelDismiss,
            )
            is PanelContent.Weather -> WeatherPanel(
                weather = content.weather,
                onDismiss = onPanelDismiss,
            )
            is PanelContent.Group -> GroupBrowserPanel(
                group = content.group,
                selectedDevice = content.selectedDevice,
                deviceRequested = content.deviceRequested,
                onSelectMember = { member ->
                    vm.onEvent(
                        PanelEvent.OpenGroupDevice(
                            PanelRequest.Chip(member.chip.id, member.chip.toPanelKind()),
                        ),
                    )
                },
                onBack = { vm.onEvent(PanelEvent.Back) },
                onDismiss = onPanelDismiss,
                onCollectiveAction = { calls ->
                    calls.forEach { call -> vm.callService(call.domain, call.service, call.entityId) }
                },
            )
            PanelContent.MediaBrowser -> {
                val selectedDevice = mediaDevices.firstOrNull { it.entityId == browsedMediaEntityId }
                if (selectedDevice == null) {
                    MediaDevicesPanel(mediaDevices, onSelect = { browsedMediaEntityId = it.entityId }, onDismiss = onPanelDismiss)
                } else {
                    MediaPlayerPanel(
                        media = selectedDevice,
                        secondaryMedia = emptyList(),
                        prefs = prefs,
                        mediaSessions = mediaDevices,
                        onSelectSession = { browsedMediaEntityId = it },
                        onDismiss = { browsedMediaEntityId = null },
                        onSecondaryPlayPause = onSecondaryPlayPause,
                    )
                }
            }
        }
    }

    // Provide the HA service caller to the whole subtree (design §8): panels read LocalCallService
    // instead of the PillHub singleton. Remembered so the provided value stays a stable instance.
    val callServiceProvider = remember(vm) {
        object : CallService {
            override fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>?) =
                vm.callService(domain, service, entityId, data)
        }
    }
    CompositionLocalProvider(
        LocalCallService provides callServiceProvider,
        LocalHaStates provides ui.latestStates,
        LocalAreas provides ui.areaByEntity,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (blurRadius > 0.dp) Modifier.blurCompat(blurRadius) else Modifier)
        ) {
            AmbientBackground(
                mode = backgroundMode,
                wallpaperVersion = wallpaperVersion,
                overlayOpacity = bgOverlayOpacity,
                modifier = Modifier.fillMaxSize()
            )

            // Swiping to the app grid dims the wallpaper further, so the icons keep their contrast
            // whatever the photo is. Alpha is read in the layer phase — no recomposition per frame.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = pagerState.collapseFraction(pagerLayout) }
                    .background(Color.Black.copy(alpha = APPS_PAGE_SCRIM))
            )

            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(bottomGradientHeight)
                    // Read in the layer phase, not in composition: the swipe fades the tray
                    // gradient out without recomposing the launcher on every frame.
                    .graphicsLayer {
                        alpha = pagerState.clockHeaderAlpha(pagerLayout) *
                            (1f - pagerState.collapseFraction(pagerLayout))
                    }
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Black.copy(alpha = 0.28f),
                                0.72f to Color.Black.copy(alpha = 0.68f),
                                1f to Color.Black.copy(alpha = 0.92f),
                            )
                        )
                    )
            )

            // Page 0 = clock + chip tray, page 1 = the app grid. The clock header lives above both
            // (LauncherPager) and shrinks as the swipe progresses.
            val clockScreen: @Composable () -> Unit = {
                LauncherPager(
                    state = pagerState,
                    // Dragging an icon must not also swipe the page out from under it.
                    // The adaptive panel host remeasures this pager into the unobstructed area.
                    userScrollEnabled = !appDragActive && !pillDragActive,
                    pageLayout = pagerLayout,
                    onHeaderTap = onOpenHomeAssistant,
                    onHeaderLongPress = { context.startActivity(Intent(context, ClockThemeActivity::class.java)) },
                    header = { collapse ->
                        ClockHeader(
                            weather = weather,
                            temperatures = temperatures,
                            onWeatherClick = { vm.onEvent(PanelEvent.WeatherTap) },
                            connected = haConnected,
                            lastUpdateAt = haLastUpdateAt,
                            clockTheme = clockTheme,
                            collapse = collapse,
                        )
                    },
                    clockPage = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { onOpenHomeAssistant() },
                                        onLongPress = { overlayVisible = true },
                                    )
                                }
                        ) {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val density = LocalDensity.current
                                val responsiveCapacity = HomeCriticalCapacityPolicy.capacity(
                                    availableWidthDp = maxWidth.value,
                                    fontScale = density.fontScale,
                                )
                                val responsiveComposition = if (
                                    responsiveCapacity.extraCriticalPrimarySlots > 0
                                ) {
                                    HomePillComposer.compose(
                                        catalog = ui.catalog,
                                        preferences = ui.homePreferences,
                                        capacity = responsiveCapacity,
                                    )
                                } else {
                                    ui.homeComposition
                                }
                                ClockTray(
                                    composition = responsiveComposition,
                                    pinnedRefs = pinnedRefs,
                                    manualGroups = manualGroupOptions,
                                    actions = homePillActions,
                                    pillsExpanded = pillsExpanded,
                                    onPillsExpandedChange = { pillsExpanded = it },
                                    selectedChipKey = selectedChipKey,
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    },
                    housePage = if (pagerLayout.homePageEnabled) {
                        {
                            Box(Modifier.fillMaxSize()) {
                                HomePage(
                                    model = ui.homePage,
                                    pinnedRefs = pinnedRefs,
                                    actions = homePillActions,
                                    manualGroups = manualGroupOptions,
                                    editing = homeEditing,
                                    reordering = pillDragActive,
                                    stale = !ui.connected,
                                    onEditingChange = { homeEditing = it },
                                    editActions = HomePageEditActions(
                                        onMoveSection = { sectionId, move ->
                                            vm.updateHomePillPreferences { preferences ->
                                                moveVisibleHomeSection(
                                                    preferences = preferences,
                                                    visibleSectionIds = ui.homePage.sections.map { it.sectionId },
                                                    sectionId = sectionId,
                                                    move = move,
                                                )
                                            }
                                        },
                                        onHideSection = { sectionId ->
                                            vm.updateHomePillPreferences { preferences ->
                                                setHomeSectionVisible(
                                                    preferences = preferences,
                                                    visibleSectionIds = ui.homePage.sections.map { it.sectionId },
                                                    sectionId = sectionId,
                                                    visible = false,
                                                )
                                            }
                                        },
                                        onCreateManualGroup = onOpenSettings,
                                        onEditManualGroups = onOpenSettings,
                                    ),
                                    onOpenHomeAssistantSettings = onOpenSettings,
                                )
                                // Rails own their horizontal gestures. This dedicated 24dp edge is
                                // always pager-owned, guaranteeing Maison -> Accueil even beside a
                                // long rail while leaving the rail itself first priority.
                                Box(
                                    Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(24.dp)
                                        .fillMaxHeight()
                                        .pagerDragForward(
                                            pagerState,
                                            enabled = !appDragActive && !pillDragActive,
                                        ),
                                )
                            }
                        }
                    } else null,
                    appPage = { page, appear ->
                        AppGridPage(
                            page = page,
                            items = placedItems.value,
                            spec = gridSpecState.value,
                            drag = gridDrag,
                            onLaunch = onLaunchItem,
                            onLongPress = { item, span, anchor ->
                                menuTarget = AppMenuTarget(item, anchor, span)
                            },
                            onPickUp = { menuTarget = null; appDragActive = true },
                            onLongPressEmpty = { overlayVisible = true },
                            onDrop = { key, placement ->
                                appDragActive = false
                                if (placement != null) layout.place(key, placement.cell, placement.span)
                            },
                            widgetView = widgets::createView,
                            cellScale = gridScale,
                            onSpec = { spec ->
                                gridSpecState.value = spec
                                layout.lastKnownSpec = spec
                                layout.seedFromLegacyOrder(spec)
                            },
                            onCellSize = { widthDp, heightDp ->
                                widgets.cellWidthDp = widthDp
                                widgets.cellHeightDp = heightDp
                            },
                            appear = appear,
                        )
                    },
                    headerActions = {
                        LauncherHeaderActions(
                            hiddenCount = hiddenItems.size,
                            onShowHidden = { showHidden = true },
                            onSettings = onOpenSettings,
                        )
                    },
                    dragOverlay = { DraggedIconOverlay(gridDrag, iconSize = 56.dp * gridScale) },
                    drag = gridDrag,
                )
            }

            LauncherPanelLayout(
                panelVisible = panelContent != null,
                modifier = Modifier.fillMaxSize(),
                content = clockScreen,
                panel = {
                    (panelContent ?: retainedPanelContent)?.let { sidePanel(it) }
                },
            )
        }

        QuickActionsOverlay(
            visible = overlayVisible,
            onDismiss = { overlayVisible = false },
            onSettings = onOpenSettings,
            onOpenPlayground = onOpenPlayground,
            onSetWallpaper = onSetWallpaper,
            onOpenHomeSettings = onOpenHomeSettings,
            onAddWidget = { widgetPickerRequested = true },
            isDefaultHome = launcherApps.isDefaultHome,
        )

        WidgetPickerDialog(
            offers = widgetOffers.takeIf { widgetPickerRequested },
            onPick = { offer ->
                widgetPickerRequested = false
                widgetOffers = null
                onAddWidget(offer)
            },
            onDismiss = { widgetPickerRequested = false; widgetOffers = null },
        )

        // Above everything, and outside the pager, so it is never clipped by a page's bounds.
        AppContextMenu(
            target = menuTarget,
            shortcuts = menuShortcuts,
            canUninstall = menuTarget?.item?.let { launcherApps.canUninstall(it.packageName) } == true,
            isDefaultHome = launcherApps.isDefaultHome,
            onDismiss = { menuTarget = null },
            onShortcut = { onStartShortcut(it.packageName, it.id) },
            onRename = { label ->
                menuTarget?.item?.let { item ->
                    layout.rename(item.key, label, defaultLabel = item.defaultLabel)
                }
            },
            onHide = { menuTarget?.item?.let { layout.hide(it.key) } },
            onAppInfo = { menuTarget?.item?.let { onAppInfo(it.packageName) } },
            onUninstall = { menuTarget?.item?.let { onUninstall(it.packageName) } },
            onRemoveShortcut = { menuTarget?.item?.let { layout.removeShortcut(it.key) } },
            onOpenHomeSettings = onOpenHomeSettings,
            onResize = { span -> menuTarget?.let { target ->
                layout.resize(target.item.key, span)
                menuTarget = target.copy(span = span)
            } },
            onRemoveWidget = {
                menuTarget?.item?.let { item ->
                    layout.forget(item.key)
                    onRemoveWidget(item.widgetId)
                }
            },
            maxSpan = GridSpan(gridSpecState.value.columns, gridSpecState.value.rows),
        )

        HiddenAppsDialog(
            items = hiddenItems.takeIf { showHidden },
            onRestore = { layout.unhide(it.key) },
            onDismiss = { showHidden = false },
        )

        AlertOverlay(
            message = alertMessage,
            onDismiss = { AlertOverlayState.dismiss() }
        )

        AutoReturnOverlay(state = autoReturnState, onCancel = { autoReturnTimer.onInteraction() })
    }
    }
}

/** Pure routing for a user-opened device panel; individual media must not fall into an empty shell. */
internal fun resolveChipPanelContent(
    request: PanelRequest.Chip,
    panelChip: LauncherChip?,
    mediaDevices: List<PlayingMedia>,
): PanelContent? = if (request.panelKind == PanelKind.MEDIA) {
    mediaDevices.firstOrNull { device ->
        device.entityId == request.key || device.players.any { it.entityId == request.key }
    }?.let(PanelContent::Media) ?: PanelContent.MediaBrowser
} else {
    panelChip?.let(PanelContent::ChipActions)
}

/** Pure preference reducer used by Maison's accessible section-order controls. */
internal fun moveVisibleHomeSection(
    preferences: HomePillPreferences,
    visibleSectionIds: List<String>,
    sectionId: String,
    move: HomePillMove,
): HomePillPreferences {
    val ordered = visibleSectionIds.distinct().toMutableList()
    val from = ordered.indexOf(sectionId)
    if (from < 0) return preferences
    val to = when (move) {
        HomePillMove.FIRST -> 0
        HomePillMove.BEFORE -> from - 1
        HomePillMove.AFTER -> from + 1
        HomePillMove.LAST -> ordered.lastIndex
    }.coerceIn(0, ordered.lastIndex)
    if (from != to) ordered.add(to, ordered.removeAt(from))
    return writeVisibleSectionOrder(preferences, ordered)
}

/** Pure visibility reducer; automatic membership and ids are deliberately untouched. */
internal fun setHomeSectionVisible(
    preferences: HomePillPreferences,
    visibleSectionIds: List<String>,
    sectionId: String,
    visible: Boolean,
): HomePillPreferences {
    val existing = preferences.homeSections.associateBy { it.sectionId }
    val visibleOrder = visibleSectionIds.distinct().filterNot { it == sectionId }.toMutableList()
    if (visible) visibleOrder += sectionId
    val normalized = writeVisibleSectionOrder(preferences, visibleOrder)
    val byId = normalized.homeSections.associateBy { it.sectionId }.toMutableMap()
    val previous = byId[sectionId] ?: existing[sectionId]
    byId[sectionId] = HomeSectionPreference(
        sectionId = sectionId,
        visible = visible,
        order = previous?.order ?: visibleOrder.size,
        itemOrder = previous?.itemOrder.orEmpty(),
    )
    return normalized.copy(homeSections = byId.values.sortedWith(
        compareBy<HomeSectionPreference> { it.order }.thenBy { it.sectionId },
    ))
}

private fun writeVisibleSectionOrder(
    preferences: HomePillPreferences,
    orderedIds: List<String>,
): HomePillPreferences {
    val existing = preferences.homeSections.associateBy { it.sectionId }
    val visible = orderedIds.mapIndexed { index, id ->
        val previous = existing[id]
        HomeSectionPreference(
            sectionId = id,
            visible = true,
            order = index,
            itemOrder = previous?.itemOrder.orEmpty(),
        )
    }
    val visibleIds = orderedIds.toSet()
    val remaining = preferences.homeSections.filter { it.sectionId !in visibleIds }
        .mapIndexed { index, section -> section.copy(order = orderedIds.size + index) }
    return preferences.copy(homeSections = visible + remaining)
}

@Composable
private fun MediaPlayerPanel(
    media: PlayingMedia,
    secondaryMedia: List<PlayingMedia>,
    prefs: Prefs,
    mediaSessions: List<PlayingMedia>,
    onSelectSession: (String?) -> Unit,
    onDismiss: () -> Unit,
    onSecondaryPlayPause: (PlayingMedia) -> Unit,
) {
    val callService = LocalCallService.current
    MediaPlayerView(
        media = media,
        secondaryMedia = secondaryMedia,
        haToken = prefs.haToken,
        onPlayPause = {
            callService("media_player", "media_play_pause", media.entityId)
        },
        onPrevious = {
            callService("media_player", "media_previous_track", media.entityId)
        },
        onNext = {
            callService("media_player", "media_next_track", media.entityId)
        },
        onVolumeChange = { entityId, volumeFraction ->
            callService(
                "media_player",
                "volume_set",
                entityId,
                mapOf("volume_level" to volumeFraction)
            )
        },
        onSecondaryPlayPause = onSecondaryPlayPause,
        onSecondaryPrevious = { session ->
            session.players.forEach { player ->
                callService("media_player", "media_previous_track", player.entityId)
            }
        },
        onSecondaryNext = { session ->
            session.players.forEach { player ->
                callService("media_player", "media_next_track", player.entityId)
            }
        },
        onSelectSecondary = { session -> onSelectSession(session.entityId) },
        onSwipePlayer = { direction ->
            val currentIndex = mediaSessions.indexOfFirst { it.entityId == media.entityId }
            if (currentIndex >= 0 && mediaSessions.size > 1) {
                val nextIndex = (currentIndex + direction + mediaSessions.size) % mediaSessions.size
                onSelectSession(mediaSessions[nextIndex].entityId)
            }
        },
        onJoinPlayer = { entityId ->
            callService(
                "media_player",
                "join",
                media.entityId,
                mapOf("group_members" to listOf(entityId)),
            )
        },
        onUnjoinPlayer = { entityId ->
            callService("media_player", "unjoin", entityId)
        },
        onDismiss = onDismiss
    )
}
