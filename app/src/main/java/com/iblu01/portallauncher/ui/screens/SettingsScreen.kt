package com.iblu01.portallauncher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.apps.gridSpecFor
import com.iblu01.portallauncher.ui.components.AppGridInsets
import com.iblu01.portallauncher.ui.components.ClockHeaderCollapsedHeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iblu01.portallauncher.AppLanguage
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.PortalApp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.photo.OkHttpTransport
import com.iblu01.portallauncher.photo.PhotoAlbum
import com.iblu01.portallauncher.photo.PhotoCoordinatorConfig
import com.iblu01.portallauncher.photo.PhotoSourceException
import com.iblu01.portallauncher.photo.TransportPolicy
import com.iblu01.portallauncher.photo.immich.ImmichPhotoSource
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.PillCandidate
import com.iblu01.portallauncher.AutoReturnUiState
import com.iblu01.portallauncher.MqttBridgeService
import com.iblu01.portallauncher.session.AppClassification
import com.iblu01.portallauncher.session.SessionAllowlist
import com.iblu01.portallauncher.session.SessionAllowlistCodec
import com.iblu01.portallauncher.ui.components.AppEntry
import com.iblu01.portallauncher.ui.components.AppPickerDialog
import com.iblu01.portallauncher.ui.components.AutoReturnOverlay
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSidebar
import com.iblu01.portallauncher.ui.components.SettingsSlider
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTile
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.components.backgroundModes
import com.iblu01.portallauncher.ui.onboarding.OnboardingActivity
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val IMMICH_ALBUM_ID = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)

internal fun isValidImmichAlbumId(value: String): Boolean = IMMICH_ALBUM_ID.matches(value)

internal fun canApplyImmichConfig(
    url: String,
    hasApiKey: Boolean,
    albumIds: List<String>,
): Boolean = url.isNotBlank() && hasApiKey && albumIds.isNotEmpty() && albumIds.all(::isValidImmichAlbumId)

data class SettingsForm(
    val haPackage: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val deviceName: String,
    val haUrl: String,
    val haToken: String,
)

class SettingsUiState {
    var pillLoading by mutableStateOf(false)
    var pillError by mutableStateOf<String?>(null)
    val pillCandidates = mutableStateListOf<PillCandidate>()
    val pillRules = mutableStateListOf<PillRule>()
    var haTest by mutableStateOf(ConnStatus.IDLE)
    var haTestMessage by mutableStateOf<String?>(null)
    var mqttTest by mutableStateOf(ConnStatus.IDLE)
    var mqttTestMessage by mutableStateOf<String?>(null)
}

interface SettingsCallbacks {
    fun onSave(form: SettingsForm)
    fun onToggleDevKeepScreenOn(enabled: Boolean)
    fun onTogglePowerAlwaysOn(alwaysOn: Boolean)
    fun onToggleTimeoutEnabled(enabled: Boolean)
    fun onSetTimeoutMinutes(minutes: Int)
    fun onTestMqtt(host: String, port: Int, username: String, password: String)
    fun onTestHaApi(url: String, token: String)
    fun onConnectionEdited()
    fun onGrantPermissions()
    fun onSetBackgroundMode(mode: String)
    fun onOpenOpacityPreview()
    fun onOpenClockTheme()
    fun onLoadPillEntities()
    fun onSetPillEnabled(candidates: List<PillCandidate>, enabled: Boolean)
}

private enum class SettingsPage { MAIN, HOME, PILLS, APPLICATION, DEVELOPER }

/** Best-effort host extraction used to pre-fill the MQTT broker from the HA address. */
private fun hostOf(url: String): String = runCatching { URL(url.trim()).host }.getOrDefault("")

internal fun shouldShowAppSessions(mqttHost: String): Boolean = mqttHost.isNotBlank()

private data class TileDef(
    val page: SettingsPage,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

@Composable
fun SettingsScreen(
    prefs: Prefs,
    uiState: SettingsUiState,
    callbacks: SettingsCallbacks,
    installedApps: List<AppEntry> = emptyList(),
    currentAppLabel: String = "",
    haStates: Map<String, HaEntity> = emptyMap(),
    autoReturnState: AutoReturnUiState = AutoReturnUiState(),
    onAutoReturnCancel: (() -> Unit)? = null,
) {
    // First-run configuration is its own flow now (ui.onboarding), not a page of the settings, so
    // the settings always open on their own root — even when no home has been connected.
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }

    var haPackage by remember { mutableStateOf(prefs.homeAssistantPackage) }
    var host by remember { mutableStateOf(prefs.brokerHost) }
    var port by remember { mutableStateOf(prefs.brokerPort.toString()) }
    var username by remember { mutableStateOf(prefs.username) }
    var password by remember { mutableStateOf(prefs.password) }
    var deviceName by remember { mutableStateOf(prefs.deviceName) }
    var haUrl by remember { mutableStateOf(prefs.haUrl) }
    var haToken by remember { mutableStateOf(prefs.haToken) }

    // While true, the MQTT broker host follows the HA address automatically.
    var brokerAuto by remember {
        mutableStateOf(prefs.brokerHost == "homeassistant.local" || prefs.brokerHost == hostOf(prefs.haUrl))
    }

    var devKeep by remember { mutableStateOf(prefs.devKeepScreenOn) }
    var powerAlwaysOn by remember { mutableStateOf(prefs.powerMode == com.iblu01.portallauncher.PowerMode.ALWAYS_ON) }
    var timeoutEnabled by remember { mutableStateOf(prefs.screenTimeoutEnabled) }
    var timeoutMinutes by remember { mutableStateOf(prefs.screenTimeoutMinutes.toFloat()) }
    var bgMode by remember { mutableStateOf(prefs.backgroundMode) }
    var bgOverlayOpacity by remember { mutableStateOf(prefs.bgOverlayOpacity) }
    // Re-read the opacity when returning from the full-screen preview so the "NN %" row is fresh.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) bgOverlayOpacity = prefs.bgOverlayOpacity
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var autoReturnEnabled by remember { mutableStateOf(prefs.autoReturnEnabled) }
    var autoReturnDelay by remember { mutableStateOf(prefs.autoReturnDelaySeconds.toFloat()) }
    var gridScale by remember { mutableStateOf(prefs.gridScale) }
    var appSessionsEnabled by remember { mutableStateOf(prefs.appSessionsEnabled) }
    var sessionAllowlist by remember { mutableStateOf(prefs.appSessionAllowlist.toMap()) }
    val context = LocalContext.current

    var showAppPicker by remember { mutableStateOf(false) }
    var showSessionAppPicker by remember { mutableStateOf(false) }
    var sessionClassificationTarget by remember { mutableStateOf<String?>(null) }

    val save = {
        callbacks.onSave(
            SettingsForm(
                haPackage = haPackage, host = host,
                port = port.toIntOrNull() ?: 1883,
                username = username, password = password,
                deviceName = deviceName,
                haUrl = haUrl, haToken = haToken,
            )
        )
    }

    // Everything auto-saves: on page exit, and as a safety net when the screen goes away.
    val currentSave by rememberUpdatedState(save)
    DisposableEffect(Unit) {
        onDispose { currentSave() }
    }

    val onUrlChange: (String) -> Unit = {
        haUrl = it
        if (brokerAuto) hostOf(it).takeIf { h -> h.isNotEmpty() }?.let { h -> host = h }
        callbacks.onConnectionEdited()
    }
    val onTokenChange: (String) -> Unit = { haToken = it; callbacks.onConnectionEdited() }
    val onSelectInstance: (HaInstance) -> Unit = {
        haUrl = it.url
        if (brokerAuto) hostOf(it.url).takeIf { h -> h.isNotEmpty() }?.let { h -> host = h }
        callbacks.onConnectionEdited()
    }

    val homeSubtitle = when {
        haToken.isBlank() -> stringResource(R.string.settings_home_subtitle_not_configured)
        uiState.haTest == ConnStatus.TESTING -> stringResource(R.string.settings_home_subtitle_testing)
        uiState.haTest == ConnStatus.OK -> stringResource(R.string.settings_home_subtitle_connected)
        uiState.haTest == ConnStatus.ERROR -> stringResource(R.string.settings_home_subtitle_error)
        else -> stringResource(R.string.settings_home_subtitle_default)
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            selectedPackage = haPackage,
            onDismiss = { showAppPicker = false },
            onAppSelected = { app -> haPackage = app.packageName; showAppPicker = false }
        )
    }
    if (showSessionAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            selectedPackage = "",
            onDismiss = { showSessionAppPicker = false },
            onAppSelected = { app ->
                showSessionAppPicker = false
                sessionClassificationTarget = app.packageName
            }
        )
    }
    sessionClassificationTarget?.let { packageName ->
        val appLabel = installedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName
        SessionClassificationDialog(
            appLabel = appLabel,
            current = sessionAllowlist[packageName],
            onDismiss = { sessionClassificationTarget = null },
            onSelected = { classification ->
                val updated = sessionAllowlist + (packageName to classification)
                if (updated.size <= SessionAllowlistCodec.MAX_ENTRIES) {
                    sessionAllowlist = updated
                    prefs.appSessionAllowlist = SessionAllowlist(updated)
                    MqttBridgeService.reconnect(context)
                }
                sessionClassificationTarget = null
            },
        )
    }

    // Shared between the narrow (single sub-page, back button) and expanded (sidebar +
    // detail pane, no back button) layouts below.
    val detailContent: @Composable (SettingsPage, Boolean) -> Unit = { page, showBack ->
        when (page) {
            SettingsPage.MAIN -> MainPage(
                homeSubtitle = homeSubtitle,
                onNavigate = { currentPage = it },
            )
            SettingsPage.HOME -> HomeConnectionPage(
                uiState = uiState,
                haUrl = haUrl, haToken = haToken,
                mqttHost = host, mqttPort = port,
                mqttUsername = username, mqttPassword = password,
                deviceName = deviceName,
                onUrlChange = onUrlChange,
                onTokenChange = onTokenChange,
                onSelectInstance = onSelectInstance,
                onMqttHostChange = { host = it; brokerAuto = false },
                onMqttPortChange = { port = it },
                onMqttUsernameChange = { username = it },
                onMqttPasswordChange = { password = it },
                onDeviceNameChange = { deviceName = it },
                onTestHa = { callbacks.onTestHaApi(haUrl, haToken) },
                onTestMqtt = { callbacks.onTestMqtt(host, port.toIntOrNull() ?: 1883, username, password) },
                onBack = { save(); currentPage = SettingsPage.MAIN },
                showBack = showBack,
            )
            SettingsPage.APPLICATION -> AppPage(
                prefs = prefs,
                haPackage = haPackage,
                currentAppLabel = currentAppLabel,
                onShowAppPicker = { showAppPicker = true },
                bgMode = bgMode,
                onBgModeChange = { bgMode = it; callbacks.onSetBackgroundMode(it) },
                bgOverlayOpacity = bgOverlayOpacity,
                onOpenOpacityPreview = callbacks::onOpenOpacityPreview,
                onOpenClockTheme = callbacks::onOpenClockTheme,
                powerAlwaysOn = powerAlwaysOn,
                onPowerAlwaysOnChange = { powerAlwaysOn = it; callbacks.onTogglePowerAlwaysOn(it) },
                timeoutEnabled = timeoutEnabled,
                onTimeoutEnabledChange = { timeoutEnabled = it; callbacks.onToggleTimeoutEnabled(it) },
                timeoutMinutes = timeoutMinutes,
                onTimeoutMinutesChange = { timeoutMinutes = it; callbacks.onSetTimeoutMinutes(it.toInt()) },
                autoReturnEnabled = autoReturnEnabled,
                onAutoReturnEnabledChange = { autoReturnEnabled = it; prefs.autoReturnEnabled = it },
                autoReturnDelay = autoReturnDelay,
                onAutoReturnDelayChange = { autoReturnDelay = it; prefs.autoReturnDelaySeconds = it.toInt() },
                appSessionsEnabled = appSessionsEnabled,
                mqttConfigured = shouldShowAppSessions(host),
                installedApps = installedApps,
                onAppSessionsEnabledChange = {
                    appSessionsEnabled = it
                    prefs.appSessionsEnabled = it
                    MqttBridgeService.reconnect(context)
                },
                sessionAllowlist = sessionAllowlist,
                onAddSessionApp = { showSessionAppPicker = true },
                onSelectSessionClassification = { sessionClassificationTarget = it },
                onClearSessionApps = {
                    sessionAllowlist = emptyMap()
                    prefs.appSessionAllowlist = SessionAllowlist(emptyMap())
                    MqttBridgeService.reconnect(context)
                },
                gridScale = gridScale,
                onGridScaleChange = { gridScale = it; prefs.gridScale = it },
                onBack = { currentPage = SettingsPage.MAIN },
                showBack = showBack,
            )
            SettingsPage.PILLS -> PillsSettingsPage(
                uiState = uiState,
                onRefresh = callbacks::onLoadPillEntities,
                onSetEnabled = callbacks::onSetPillEnabled,
                onBack = { currentPage = SettingsPage.MAIN },
                showBack = showBack,
            )
            SettingsPage.DEVELOPER -> DeveloperPage(
                prefs = prefs,
                devKeep = devKeep,
                onDevKeepChange = { devKeep = it; callbacks.onToggleDevKeepScreenOn(it) },
                onGrantPermissions = { callbacks.onGrantPermissions() },
                onBack = { currentPage = SettingsPage.MAIN },
                showBack = showBack,
            )
        }
    }

    Scaffold(
        containerColor = AppleColors.groupedBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Mirrors androidx WindowSizeClass: Expanded starts at 840dp width, where a
                // tablet has room for a permanent nav sidebar next to the detail pane.
                val isExpanded = maxWidth >= 840.dp

                if (isExpanded) {
                    val sidebarItems = listOf(
                        Triple(SettingsPage.HOME, Icons.Outlined.Home, stringResource(R.string.settings_tile_home_title)),
                        Triple(SettingsPage.PILLS, Icons.Outlined.Dashboard, stringResource(R.string.settings_tile_pills_title)),
                        Triple(SettingsPage.APPLICATION, Icons.Outlined.Settings, stringResource(R.string.settings_tile_app_title)),
                        Triple(SettingsPage.DEVELOPER, Icons.Outlined.Build, stringResource(R.string.settings_tile_dev_title)),
                    )
                    // The MAIN tile grid only exists for the narrow layout; land on "Ma maison" instead.
                    val selectedPage = if (currentPage == SettingsPage.MAIN) SettingsPage.HOME else currentPage

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        SettingsSidebar(
                            items = sidebarItems,
                            selected = selectedPage,
                            onSelect = { currentPage = it },
                            modifier = Modifier.fillMaxHeight(),
                            header = stringResource(R.string.settings_main_title),
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            AnimatedContent(
                                targetState = selectedPage,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "settingsDetail"
                            ) { page -> detailContent(page, false) }
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = currentPage,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally { it * dir } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it * dir } + fadeOut())
                        },
                        label = "settingsPage"
                    ) { page -> detailContent(page, true) }
                }
            }

            AutoReturnOverlay(state = autoReturnState, onCancel = { onAutoReturnCancel?.invoke() })
        }
    }
}

@Composable
private fun MainPage(homeSubtitle: String, onNavigate: (SettingsPage) -> Unit) {
    val tiles = listOf(
        TileDef(SettingsPage.HOME, Icons.Outlined.Home, stringResource(R.string.settings_tile_home_title), homeSubtitle),
        TileDef(SettingsPage.PILLS, Icons.Outlined.Dashboard, stringResource(R.string.settings_tile_pills_title), stringResource(R.string.settings_tile_pills_subtitle)),
        TileDef(SettingsPage.APPLICATION, Icons.Outlined.Settings, stringResource(R.string.settings_tile_app_title), stringResource(R.string.settings_tile_app_subtitle)),
        TileDef(SettingsPage.DEVELOPER, Icons.Outlined.Build, stringResource(R.string.settings_tile_dev_title), stringResource(R.string.settings_tile_dev_subtitle)),
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(stringResource(R.string.settings_main_title), style = AppleTypography.headlineLarge, color = AppleColors.primary, modifier = Modifier.padding(start = 4.dp))

        tiles.chunked(2).forEach { rowTiles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowTiles.forEach { tile ->
                    Column(modifier = Modifier.weight(1f)) {
                        SettingsTile(icon = tile.icon, title = tile.title, subtitle = tile.subtitle, onClick = { onNavigate(tile.page) })
                    }
                }
                if (rowTiles.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppPage(
    prefs: Prefs,
    haPackage: String, currentAppLabel: String, onShowAppPicker: () -> Unit,
    bgMode: String, onBgModeChange: (String) -> Unit,
    bgOverlayOpacity: Float = 0.25f, onOpenOpacityPreview: () -> Unit = {},
    onOpenClockTheme: () -> Unit = {},
    powerAlwaysOn: Boolean, onPowerAlwaysOnChange: (Boolean) -> Unit,
    timeoutEnabled: Boolean, onTimeoutEnabledChange: (Boolean) -> Unit,
    timeoutMinutes: Float, onTimeoutMinutesChange: (Float) -> Unit,
    autoReturnEnabled: Boolean, onAutoReturnEnabledChange: (Boolean) -> Unit,
    autoReturnDelay: Float, onAutoReturnDelayChange: (Float) -> Unit,
    appSessionsEnabled: Boolean, onAppSessionsEnabledChange: (Boolean) -> Unit,
    mqttConfigured: Boolean,
    installedApps: List<AppEntry>,
    sessionAllowlist: Map<String, AppClassification>,
    onAddSessionApp: () -> Unit,
    onSelectSessionClassification: (String) -> Unit,
    onClearSessionApps: () -> Unit,
    gridScale: Float = 1f, onGridScaleChange: (Float) -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    var showLanguagePage by remember { mutableStateOf(false) }
    var immichUrl by remember { mutableStateOf(prefs.immichUrl) }
    var immichKeyDraft by remember { mutableStateOf("") }
    var immichAlbumIds by remember { mutableStateOf(prefs.immichAlbumIds) }
    var immichAllowInsecure by remember { mutableStateOf(prefs.immichAllowInsecure) }
    var immichShuffle by remember { mutableStateOf(prefs.immichShuffle) }
    var immichRefresh by remember { mutableStateOf(prefs.immichRefreshMinutes) }
    var immichCadence by remember { mutableStateOf(prefs.immichCadenceSeconds) }
    var immichAlbums by remember { mutableStateOf<List<PhotoAlbum>>(emptyList()) }
    var showImmichAlbumPicker by remember { mutableStateOf(false) }
    var showImmichRefreshPresets by remember { mutableStateOf(false) }
    var showImmichCadencePresets by remember { mutableStateOf(false) }
    var immichBusy by remember { mutableStateOf(false) }
    var immichErrorCategory by remember { mutableStateOf<String?>(null) }
    var immichConnectedAlbumCount by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val settingsContext = LocalContext.current
    val app = settingsContext.applicationContext as PortalApp

    fun loadImmichAlbums(openPicker: Boolean) {
        if (immichBusy) return
        immichBusy = true
        immichErrorCategory = null
        immichConnectedAlbumCount = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val apiKey = immichKeyDraft.ifBlank { prefs.immichApiKey }
                    val source = ImmichPhotoSource(
                        transport = OkHttpTransport(),
                        baseUrl = immichUrl,
                        apiKey = apiKey,
                        policy = if (immichAllowInsecure) {
                            TransportPolicy.ALLOW_INSECURE
                        } else {
                            TransportPolicy.REQUIRE_SECURE
                        },
                    )
                    val health = source.health()
                    if (!health.ok) throw PhotoSourceException(health.errorCategory ?: "unknown")
                    source.listAlbums()
                }
            }
            result.onSuccess { albums ->
                immichAlbums = albums
                immichConnectedAlbumCount = albums.size
                if (openPicker) showImmichAlbumPicker = true
            }.onFailure { failure ->
                immichErrorCategory = (failure as? PhotoSourceException)?.category ?: "config"
            }
            immichBusy = false
        }
    }
    if (showLanguagePage) {
        LanguagePage(prefs = prefs, onBack = { showLanguagePage = false })
        return
    }
    if (showImmichAlbumPicker) {
        ImmichAlbumPickerDialog(
            albums = immichAlbums,
            selected = immichAlbumIds.toSet(),
            onDismiss = { showImmichAlbumPicker = false },
            onApply = {
                immichAlbumIds = it.toList()
                showImmichAlbumPicker = false
            },
        )
    }
    if (showImmichRefreshPresets) {
        IntPresetDialog(
            title = stringResource(R.string.settings_immich_refresh),
            values = listOf(5, 15, 30, 60, 180, 360, 720, 1440),
            suffix = stringResource(R.string.settings_minutes_short),
            onDismiss = { showImmichRefreshPresets = false },
            onSelect = {
                immichRefresh = it
                showImmichRefreshPresets = false
            },
        )
    }
    if (showImmichCadencePresets) {
        IntPresetDialog(
            title = stringResource(R.string.settings_immich_cadence),
            values = listOf(5, 10, 15, 30, 60, 120, 300, 600, 1800, 3600),
            suffix = stringResource(R.string.settings_seconds_short),
            onDismiss = { showImmichCadencePresets = false },
            onSelect = {
                immichCadence = it
                showImmichCadencePresets = false
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSubPageHeader(title = stringResource(R.string.settings_app_page_title), onBack = onBack, showBack = showBack)

        SettingsSection(title = stringResource(R.string.settings_app_section_language)) {
            val currentLanguage = AppLanguage.from(prefs.appLanguage)
            SettingsRow(
                label = stringResource(R.string.settings_app_label_language),
                value = "${currentLanguage.flag} ${stringResource(currentLanguage.nameRes)}",
                onClick = { showLanguagePage = true },
            )
            SettingsDivider()
            // The first-run assistant is offered again from here, and only from here: it never
            // reopens by itself once it has been completed.
            SettingsRow(
                label = stringResource(R.string.onb_settings_restart_setup_label),
                onClick = {
                    settingsContext.startActivity(
                        OnboardingActivity.intent(settingsContext, reset = true)
                    )
                },
            )
            Text(
                stringResource(R.string.onb_settings_restart_setup_subtitle),
                style = AppleTypography.bodySmall,
                color = AppleColors.secondary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }

        // Any app, not just Home Assistant: this is the launcher's headline gesture, and HA is
        // only the default choice.
        SettingsSection(title = stringResource(R.string.settings_app_section_tap_home)) {
            SettingsRow(
                label = stringResource(R.string.settings_app_label_app_to_open),
                value = currentAppLabel.ifBlank { haPackage },
                onClick = onShowAppPicker,
            )
        }

        if (mqttConfigured) SettingsSection(title = stringResource(R.string.settings_app_section_sessions)) {
            SettingsToggle(
                label = stringResource(R.string.settings_app_sessions_enabled),
                checked = appSessionsEnabled,
                onCheckedChange = onAppSessionsEnabledChange,
            )
            SettingsDivider()
            if (sessionAllowlist.isEmpty()) {
                SettingsRow(
                    label = stringResource(R.string.settings_app_sessions_empty),
                    value = "",
                    onClick = onAddSessionApp,
                )
            } else {
                sessionAllowlist.toSortedMap().forEach { (packageName, classification) ->
                    val appLabel = installedApps.firstOrNull { it.packageName == packageName }?.label
                        ?: packageName
                    SettingsRow(
                        label = appLabel,
                        value = stringResource(
                            R.string.settings_app_sessions_classification_value,
                            stringResource(classification.labelRes()),
                            classification.defaultDurationSeconds,
                            classification.maxDurationSeconds,
                        ),
                        onClick = { onSelectSessionClassification(packageName) },
                    )
                }
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_app_sessions_clear),
                    value = "",
                    onClick = onClearSessionApps,
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.settings_app_sessions_add),
                value = "",
                onClick = onAddSessionApp,
            )
        }

        SettingsSection(title = stringResource(R.string.settings_app_section_wallpaper)) {
            backgroundModes.forEach { (key, label) ->
                SettingsRow(label = stringResource(label), value = if (key == bgMode) "✓" else "", onClick = { onBgModeChange(key) })
                if (key != backgroundModes.last().first) SettingsDivider()
            }
            if (bgMode != "neutral") {
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_app_label_overlay_opacity),
                    value = "${(bgOverlayOpacity * 100).toInt()} %",
                    onClick = onOpenOpacityPreview,
                )
            }
        }

        if (bgMode == "immich") {
            SettingsSection(title = stringResource(R.string.settings_immich_section)) {
                SettingsTextField(
                    label = stringResource(R.string.settings_immich_url),
                    value = immichUrl,
                    onValueChange = { immichUrl = it },
                    placeholder = "https://photos.example.com",
                )
                SettingsDivider()
                SettingsTextField(
                    label = stringResource(R.string.settings_immich_api_key),
                    value = immichKeyDraft,
                    onValueChange = { immichKeyDraft = it },
                    placeholder = if (prefs.hasImmichApiKey) {
                        stringResource(R.string.settings_immich_key_configured)
                    } else {
                        stringResource(R.string.settings_immich_key_not_configured)
                    },
                    isPassword = true,
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_test_connection),
                    value = when {
                        immichBusy -> stringResource(R.string.settings_immich_testing)
                        immichErrorCategory != null -> stringResource(
                            R.string.settings_immich_test_failed,
                            immichErrorCategory.orEmpty(),
                        )
                        immichConnectedAlbumCount != null -> stringResource(
                            R.string.settings_immich_test_success,
                            immichConnectedAlbumCount ?: 0,
                        )
                        else -> null
                    },
                    onClick = { loadImmichAlbums(openPicker = false) },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_albums),
                    value = stringResource(R.string.settings_immich_albums_selected, immichAlbumIds.size),
                    onClick = { loadImmichAlbums(openPicker = true) },
                )
                SettingsDivider()
                SettingsToggle(
                    label = stringResource(R.string.settings_immich_allow_insecure),
                    checked = immichAllowInsecure,
                    onCheckedChange = { immichAllowInsecure = it },
                )
                if (immichAllowInsecure) {
                    Text(
                        text = stringResource(R.string.settings_immich_insecure_warning),
                        style = AppleTypography.bodySmall,
                        color = AppleColors.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                SettingsDivider()
                SettingsToggle(
                    label = stringResource(R.string.settings_immich_shuffle),
                    checked = immichShuffle,
                    onCheckedChange = { immichShuffle = it },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_refresh),
                    value = "$immichRefresh ${stringResource(R.string.settings_minutes_short)}",
                    onClick = { showImmichRefreshPresets = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_cadence),
                    value = "$immichCadence ${stringResource(R.string.settings_seconds_short)}",
                    onClick = { showImmichCadencePresets = true },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_apply),
                    value = if (prefs.hasImmichApiKey) stringResource(R.string.settings_immich_key_configured) else null,
                    onClick = applyImmich@{
                        if (!canApplyImmichConfig(
                                url = immichUrl,
                                hasApiKey = immichKeyDraft.isNotBlank() || prefs.hasImmichApiKey,
                                albumIds = immichAlbumIds,
                            )
                        ) {
                            immichErrorCategory = "config"
                            return@applyImmich
                        }
                        prefs.immichUrl = immichUrl
                        if (immichKeyDraft.isNotBlank()) prefs.immichApiKey = immichKeyDraft
                        prefs.immichAlbumIds = immichAlbumIds
                        prefs.immichAllowInsecure = immichAllowInsecure
                        prefs.immichShuffle = immichShuffle
                        prefs.immichRefreshMinutes = immichRefresh
                        prefs.immichCadenceSeconds = immichCadence
                        immichKeyDraft = ""
                        app.photoCoordinator.reconfigure(
                            PhotoCoordinatorConfig(
                                refreshIntervalMinutes = prefs.immichRefreshMinutes,
                                cadenceSeconds = prefs.immichCadenceSeconds,
                                shuffle = prefs.immichShuffle,
                            ),
                        )
                    },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_immich_remove),
                    onClick = {
                        app.photoCoordinator.removeProvider("immich")
                        prefs.clearImmichConfiguration()
                        immichUrl = ""
                        immichKeyDraft = ""
                        immichAlbumIds = emptyList()
                        immichAlbums = emptyList()
                        immichAllowInsecure = prefs.immichAllowInsecure
                        immichShuffle = prefs.immichShuffle
                        immichRefresh = prefs.immichRefreshMinutes
                        immichCadence = prefs.immichCadenceSeconds
                        immichErrorCategory = null
                        immichConnectedAlbumCount = null
                        onBgModeChange("neutral")
                    },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_app_section_clock)) {
            SettingsRow(label = stringResource(R.string.settings_app_label_clock_theme), value = "", onClick = onOpenClockTheme)
        }

        SettingsSection(title = stringResource(R.string.settings_app_section_app_grid)) {
            val configuration = LocalConfiguration.current
            val spec = remember(gridScale, configuration.screenWidthDp, configuration.screenHeightDp) {
                gridSpecFor(
                    widthDp = configuration.screenWidthDp - AppGridInsets.horizontal.value * 2,
                    heightDp = configuration.screenHeightDp - ClockHeaderCollapsedHeight.value - AppGridInsets.bottom.value,
                    cellWidthDp = 112f * gridScale,
                    cellHeightDp = 116f * gridScale,
                )
            }
            SettingsSlider(
                label = stringResource(R.string.settings_app_label_icon_size),
                value = gridScale * 100f,
                valueRange = 70f..130f,
                steps = 11,
                onValueChange = { onGridScaleChange(it / 100f) },
                valueText = "${spec.columns} × ${spec.rows}",
            )
        }

        SettingsSection(title = stringResource(R.string.settings_app_section_screen_sleep)) {
            SettingsToggle(label = stringResource(R.string.settings_app_toggle_always_on), checked = powerAlwaysOn, onCheckedChange = onPowerAlwaysOnChange)
            SettingsDivider()
            SettingsToggle(label = stringResource(R.string.settings_app_toggle_auto_timeout), checked = timeoutEnabled, onCheckedChange = onTimeoutEnabledChange)
            if (timeoutEnabled) {
                SettingsDivider()
                SettingsSlider(label = stringResource(R.string.settings_app_label_timeout_delay), value = timeoutMinutes, valueRange = 1f..240f, steps = 238, onValueChange = onTimeoutMinutesChange, valueSuffix = " min")
            }
        }

        SettingsSection(title = stringResource(R.string.settings_app_section_auto_return)) {
            SettingsToggle(label = stringResource(R.string.settings_app_toggle_auto_return), checked = autoReturnEnabled, onCheckedChange = onAutoReturnEnabledChange)
            if (autoReturnEnabled) {
                SettingsDivider()
                SettingsSlider(label = stringResource(R.string.settings_app_label_auto_return_delay), value = autoReturnDelay, valueRange = 5f..60f, steps = 54, onValueChange = onAutoReturnDelayChange, valueSuffix = " s")
            }
        }

    }
}

private fun AppClassification.labelRes(): Int = when (this) {
    AppClassification.HOME -> R.string.settings_app_sessions_class_home
    AppClassification.MEDIA -> R.string.settings_app_sessions_class_media
    AppClassification.UTILITY -> R.string.settings_app_sessions_class_utility
    AppClassification.COMMUNICATION -> R.string.settings_app_sessions_class_communication
}

@Composable
private fun SessionClassificationDialog(
    appLabel: String,
    current: AppClassification?,
    onDismiss: () -> Unit,
    onSelected: (AppClassification) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_app_sessions_choose_class, appLabel)) },
        text = {
            Column {
                AppClassification.entries.forEach { classification ->
                    TextButton(
                        onClick = { onSelected(classification) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (classification == current) {
                                    "✓ ${stringResource(classification.labelRes())}"
                                } else {
                                    stringResource(classification.labelRes())
                                },
                                color = AppleColors.primary,
                                style = AppleTypography.titleMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.settings_app_sessions_duration_consequence,
                                    classification.defaultDurationSeconds,
                                    classification.maxDurationSeconds,
                                ),
                                color = AppleColors.secondary,
                                style = AppleTypography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_app_sessions_cancel))
            }
        },
    )
}

@Composable
private fun ImmichAlbumPickerDialog(
    albums: List<PhotoAlbum>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
) {
    var draft by remember(albums, selected) { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_immich_albums)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            ) {
                if (albums.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_immich_no_albums),
                        style = AppleTypography.bodySmall,
                        color = AppleColors.secondary,
                    )
                }
                albums.forEach { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                draft = if (album.id in draft) draft - album.id else draft + album.id
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = album.id in draft,
                            onCheckedChange = { checked ->
                                draft = if (checked) draft + album.id else draft - album.id
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.label, style = AppleTypography.bodyLarge)
                            Text(
                                text = stringResource(R.string.settings_immich_album_asset_count, album.assetCount),
                                style = AppleTypography.bodySmall,
                                color = AppleColors.secondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.isNotEmpty() && draft.all(::isValidImmichAlbumId),
                onClick = { onApply(draft) },
            ) { Text(stringResource(R.string.settings_immich_select_albums)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun IntPresetDialog(
    title: String,
    values: List<Int>,
    suffix: String,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                values.forEach { value ->
                    TextButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("$value $suffix", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun DeveloperPage(
    prefs: Prefs,
    devKeep: Boolean,
    onDevKeepChange: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    var rebootLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = stringResource(R.string.settings_dev_page_title), onBack = onBack, showBack = showBack)

        SettingsSection(title = stringResource(R.string.settings_dev_section_debug)) {
            SettingsToggle(label = stringResource(R.string.settings_dev_toggle_keep_screen_on), checked = devKeep, onCheckedChange = onDevKeepChange)
        }

        SettingsSection(title = stringResource(R.string.settings_dev_section_system)) {
            SettingsRow(label = stringResource(R.string.settings_dev_label_permissions), onClick = onGrantPermissions)
            SettingsDivider()
            SettingsRow(
                label = if (rebootLoading) stringResource(R.string.settings_dev_rebooting) else stringResource(R.string.settings_dev_reboot),
                value = null,
                onClick = {
                    if (!rebootLoading) {
                        rebootLoading = true
                        Thread {
                            runCatching {
                                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
                                process.waitFor()
                            }
                        }.also { it.isDaemon = true }.start()
                    }
                },
            )
        }
    }
}

