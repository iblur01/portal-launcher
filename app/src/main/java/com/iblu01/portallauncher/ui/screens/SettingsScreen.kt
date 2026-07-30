package com.iblu01.portallauncher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.apps.gridSpecFor
import com.iblu01.portallauncher.ui.components.AppGridInsets
import com.iblu01.portallauncher.ui.components.ClockHeaderCollapsedHeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.CameraPair
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.PillCandidate
import com.iblu01.portallauncher.AdbControl
import com.iblu01.portallauncher.AutoReturnUiState
import com.iblu01.portallauncher.ui.components.AppEntry
import com.iblu01.portallauncher.ui.components.AppPickerDialog
import com.iblu01.portallauncher.ui.components.AutoReturnOverlay
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSlider
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTile
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.components.backgroundModes
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import java.net.URL

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
    fun onEnableAdb(port: Int, onComplete: (Boolean) -> Unit)
    fun onDisableAdb(onComplete: (Boolean) -> Unit)
    fun onToggleWebConfig(enabled: Boolean)
    fun onRegenerateWebConfigToken()
    fun onSetWebConfigPort(port: Int)
}

private enum class SettingsPage { MAIN, HOME, PILLS, APPLICATION, CAMERAS, DEVELOPER, SETUP }

/** Best-effort host extraction used to pre-fill the MQTT broker from the HA address. */
private fun hostOf(url: String): String = runCatching { URL(url.trim()).host }.getOrDefault("")

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
    var currentPage by remember {
        mutableStateOf(if (prefs.haToken.isBlank()) SettingsPage.SETUP else SettingsPage.MAIN)
    }

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

    var showAppPicker by remember { mutableStateOf(false) }

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
        haToken.isBlank() -> "À configurer"
        uiState.haTest == ConnStatus.TESTING -> "Vérification…"
        uiState.haTest == ConnStatus.OK -> "Connectée"
        uiState.haTest == ConnStatus.ERROR -> "Vérifier la connexion"
        else -> "Adresse, clé d'accès"
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            selectedPackage = haPackage,
            onDismiss = { showAppPicker = false },
            onAppSelected = { app -> haPackage = app.packageName; showAppPicker = false }
        )
    }

    Scaffold(
        containerColor = AppleColors.groupedBg,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
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
            ) { page ->
                when (page) {
                    SettingsPage.MAIN -> MainPage(
                        homeSubtitle = homeSubtitle,
                        onNavigate = { currentPage = it },
                    )
                    SettingsPage.SETUP -> SetupWizard(
                        uiState = uiState,
                        haUrl = haUrl, haToken = haToken,
                        onUrlChange = onUrlChange,
                        onTokenChange = onTokenChange,
                        onSelectInstance = onSelectInstance,
                        onTest = { callbacks.onTestHaApi(haUrl, haToken) },
                        onFinish = { save(); currentPage = SettingsPage.MAIN },
                        onSkip = { currentPage = SettingsPage.MAIN },
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
                    )
                    SettingsPage.APPLICATION -> AppPage(
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
                        gridScale = gridScale,
                        onGridScaleChange = { gridScale = it; prefs.gridScale = it },
                        onBack = { currentPage = SettingsPage.MAIN },
                    )
                    SettingsPage.PILLS -> PillsSettingsPage(
                        uiState = uiState,
                        onRefresh = callbacks::onLoadPillEntities,
                        onSetEnabled = callbacks::onSetPillEnabled,
                        onBack = { currentPage = SettingsPage.MAIN },
                    )
                    SettingsPage.CAMERAS -> CamerasPage(
                        prefs = prefs,
                        haStates = haStates,
                        onBack = { currentPage = SettingsPage.MAIN },
                    )
                    SettingsPage.DEVELOPER -> DeveloperPage(
                        prefs = prefs,
                        devKeep = devKeep,
                        onDevKeepChange = { devKeep = it; callbacks.onToggleDevKeepScreenOn(it) },
                        onGrantPermissions = { callbacks.onGrantPermissions() },
                        onEnableAdb = { port, onComplete -> callbacks.onEnableAdb(port, onComplete) },
                        onDisableAdb = { onComplete -> callbacks.onDisableAdb(onComplete) },
                        onToggleWebConfig = { callbacks.onToggleWebConfig(it) },
                        onRegenerateWebConfigToken = { callbacks.onRegenerateWebConfigToken() },
                        onBack = { currentPage = SettingsPage.MAIN },
                    )
                }
            }

            AutoReturnOverlay(state = autoReturnState, onCancel = { onAutoReturnCancel?.invoke() })
        }
    }
}

@Composable
private fun MainPage(homeSubtitle: String, onNavigate: (SettingsPage) -> Unit) {
    val tiles = listOf(
        TileDef(SettingsPage.HOME, Icons.Outlined.Home, "Ma maison", homeSubtitle),
        TileDef(SettingsPage.PILLS, Icons.Outlined.Dashboard, "Informations affichées", "Ce que Portal montre en haut de l'écran"),
        TileDef(SettingsPage.APPLICATION, Icons.Outlined.Settings, "Application", "Fond d'écran, veille, applications"),
        TileDef(SettingsPage.CAMERAS, Icons.Outlined.Videocam, "Caméras", "Affichage auto quand on sonne ou bouge"),
        TileDef(SettingsPage.DEVELOPER, Icons.Outlined.Build, "Développeur", "Options avancées"),
    )

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Portal Launcher", style = AppleTypography.headlineLarge, color = AppleColors.primary, modifier = Modifier.padding(start = 4.dp))

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
    haPackage: String, currentAppLabel: String, onShowAppPicker: () -> Unit,
    bgMode: String, onBgModeChange: (String) -> Unit,
    bgOverlayOpacity: Float = 0.25f, onOpenOpacityPreview: () -> Unit = {},
    onOpenClockTheme: () -> Unit = {},
    powerAlwaysOn: Boolean, onPowerAlwaysOnChange: (Boolean) -> Unit,
    timeoutEnabled: Boolean, onTimeoutEnabledChange: (Boolean) -> Unit,
    timeoutMinutes: Float, onTimeoutMinutesChange: (Float) -> Unit,
    autoReturnEnabled: Boolean, onAutoReturnEnabledChange: (Boolean) -> Unit,
    autoReturnDelay: Float, onAutoReturnDelayChange: (Float) -> Unit,
    gridScale: Float = 1f, onGridScaleChange: (Float) -> Unit = {},
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSubPageHeader(title = "Application", onBack = onBack)

        // Any app, not just Home Assistant: this is the launcher's headline gesture, and HA is
        // only the default choice.
        SettingsSection(title = "TAP SUR L'ÉCRAN D'ACCUEIL") {
            SettingsRow(
                label = "Application à ouvrir",
                value = currentAppLabel.ifBlank { haPackage },
                onClick = onShowAppPicker,
            )
        }

        SettingsSection(title = "FOND D'ÉCRAN") {
            backgroundModes.forEach { (key, label) ->
                SettingsRow(label = label, value = if (key == bgMode) "✓" else "", onClick = { onBgModeChange(key) })
                if (key != backgroundModes.last().first) SettingsDivider()
            }
            if (bgMode != "neutral") {
                SettingsDivider()
                SettingsRow(
                    label = "Opacité du voile",
                    value = "${(bgOverlayOpacity * 100).toInt()} %",
                    onClick = onOpenOpacityPreview,
                )
            }
        }

        SettingsSection(title = "HORLOGE") {
            SettingsRow(label = "Thème de l'horloge", value = "", onClick = onOpenClockTheme)
        }

        SettingsSection(title = "GRILLE D'APPLICATIONS") {
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
                label = "Taille des icônes",
                value = gridScale * 100f,
                valueRange = 70f..130f,
                steps = 11,
                onValueChange = { onGridScaleChange(it / 100f) },
                valueText = "${spec.columns} × ${spec.rows}",
            )
        }

        SettingsSection(title = "ÉCRAN & VEILLE") {
            SettingsToggle(label = "Mode toujours allumé", checked = powerAlwaysOn, onCheckedChange = onPowerAlwaysOnChange)
            SettingsDivider()
            SettingsToggle(label = "Extinction automatique", checked = timeoutEnabled, onCheckedChange = onTimeoutEnabledChange)
            if (timeoutEnabled) {
                SettingsDivider()
                SettingsSlider(label = "Délai", value = timeoutMinutes, valueRange = 1f..240f, steps = 238, onValueChange = onTimeoutMinutesChange, valueSuffix = " min")
            }
        }

        SettingsSection(title = "RETOUR AUTOMATIQUE") {
            SettingsToggle(label = "Retour à l'écran principal", checked = autoReturnEnabled, onCheckedChange = onAutoReturnEnabledChange)
            if (autoReturnEnabled) {
                SettingsDivider()
                SettingsSlider(label = "Délai d'inactivité", value = autoReturnDelay, valueRange = 5f..60f, steps = 54, onValueChange = onAutoReturnDelayChange, valueSuffix = " s")
            }
        }

    }
}

@Composable
private fun DeveloperPage(
    prefs: Prefs,
    devKeep: Boolean,
    onDevKeepChange: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit,
    onEnableAdb: (Int, (Boolean) -> Unit) -> Unit,
    onDisableAdb: ((Boolean) -> Unit) -> Unit,
    onToggleWebConfig: (Boolean) -> Unit,
    onRegenerateWebConfigToken: () -> Unit,
    onBack: () -> Unit,
) {
    var adbPort by remember { mutableStateOf(AdbControl.getAdbPort() ?: if (prefs.adbEnabled) prefs.adbPort.toString() else null) }
    val wifiIp = remember { AdbControl.getWifiIpAddress() ?: "Non connecté" }
    var adbLoading by remember { mutableStateOf(false) }
    var rebootLoading by remember { mutableStateOf(false) }
    var tapThreshold by remember { mutableStateOf(prefs.tapThreshold) }
    var tempOffset by remember { mutableStateOf(prefs.tempOffset) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = "Développeur", onBack = onBack)

        SettingsSection(title = "DÉBOGAGE") {
            SettingsToggle(label = "Écran toujours allumé (dev)", checked = devKeep, onCheckedChange = onDevKeepChange)
        }

        SettingsSection(title = "CAPTEURS") {
            SettingsSlider(
                label = "Sensibilité tap", value = tapThreshold, valueRange = 2f..15f, steps = 12,
                onValueChange = { tapThreshold = it; prefs.tapThreshold = it }, valueSuffix = "",
            )
            SettingsDivider()
            SettingsSlider(
                label = "Décalage température", value = tempOffset, valueRange = -20f..20f, steps = 39,
                onValueChange = { tempOffset = it; prefs.tempOffset = it }, valueSuffix = "°",
            )
        }
        Text(
            text = "Principalement pour l'application sur une tablette autre que Portal afin de la maintenir activée.",
            style = AppleTypography.bodyMedium,
            color = AppleColors.secondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        SettingsSection(title = "ADB SANS FIL") {
            SettingsRow(
                label = "Adresse IP",
                value = wifiIp,
                onClick = {}
            )
            SettingsDivider()
            SettingsRow(
                label = "Port ADB",
                value = adbPort ?: "Désactivé",
                onClick = {}
            )
            SettingsDivider()
            if (adbPort == null) {
                SettingsRow(
                    label = if (adbLoading) "Activation en cours…" else "Activer ADB sans fil (port 5555)",
                    onClick = {
                        if (!adbLoading) {
                            adbLoading = true
                            onEnableAdb(5555) { success ->
                                adbLoading = false
                                if (success) {
                                    adbPort = "5555"
                                }
                            }
                        }
                    }
                )
            } else {
                SettingsRow(
                    label = if (adbLoading) "Désactivation en cours…" else "Désactiver ADB sans fil",
                    onClick = {
                        if (!adbLoading) {
                            adbLoading = true
                            onDisableAdb { success ->
                                adbLoading = false
                                if (success) {
                                    adbPort = null
                                }
                            }
                        }
                    }
                )
            }
        }

        if (adbPort != null && wifiIp != "Non connecté") {
            Text(
                text = "Pour vous connecter, exécutez :\nadb connect $wifiIp:$adbPort",
                style = AppleTypography.bodyMedium,
                color = AppleColors.secondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        WebConfigSection(
            prefs = prefs,
            onToggleWebConfig = onToggleWebConfig,
            onRegenerateWebConfigToken = onRegenerateWebConfigToken,
        )

        SettingsSection(title = "SYSTÈME") {
            SettingsRow(label = "Permissions capteurs / son / luminosité", onClick = onGrantPermissions)
            SettingsDivider()
            SettingsRow(
                label = if (rebootLoading) "Redémarrage…" else "Redémarrer le Portal",
                value = null,
                onClick = {
                    if (!rebootLoading) {
                        rebootLoading = true
                        Thread {
                            AdbControl.rebootDevice()
                        }.also { it.isDaemon = true }.start()
                    }
                },
            )
        }
    }
}

@Composable
private fun WebConfigSection(
    prefs: Prefs,
    onToggleWebConfig: (Boolean) -> Unit,
    onRegenerateWebConfigToken: () -> Unit,
) {
    var enabled by remember { mutableStateOf(prefs.webConfigEnabled) }
    var token by remember { mutableStateOf(if (enabled) prefs.webConfigToken else "") }
    val ip = remember {
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress?.contains('.') == true }
                ?.hostAddress
        }.getOrNull() ?: "?"
    }

    SettingsSection(title = "CONFIGURATION WEB") {
        SettingsToggle(
            label = "Configuration à distance",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                onToggleWebConfig(it)
                token = if (it) prefs.webConfigToken else ""
            },
        )
        if (enabled) {
            SettingsDivider()
            SettingsRow(
                label = "Régénérer le token",
                onClick = {
                    onRegenerateWebConfigToken()
                    token = prefs.webConfigToken
                },
            )
        }
    }
    if (enabled) {
        Text(
            text = "Ouvrir : http://$ip:${prefs.webConfigPort}/?token=$token",
            style = AppleTypography.bodyMedium,
            color = AppleColors.secondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun CamerasPage(prefs: Prefs, haStates: Map<String, HaEntity>, onBack: () -> Unit) {
    var pairs by remember { mutableStateOf(prefs.cameraPairs) }
    var selTrigger by remember { mutableStateOf<String?>(null) }
    var selCamera by remember { mutableStateOf<String?>(null) }
    val states = haStates
    val triggers = remember(states.size) { states.values.filter { it.domain == "binary_sensor" }.sortedBy { it.name } }
    val cameras = remember(states.size) { states.values.filter { it.domain == "camera" }.sortedBy { it.name } }
    fun nameOf(id: String) = states[id]?.name ?: id.substringAfter('.')

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsSubPageHeader(title = "Caméras", onBack = onBack)
        Text("Quand le capteur choisi passe à « on » (sonnette, mouvement…), la caméra associée s'affiche en plein écran.", style = AppleTypography.bodyLarge, color = AppleColors.secondary)

        if (pairs.isNotEmpty()) SettingsSection(title = "PAIRES ACTIVES") {
            pairs.forEachIndexed { i, p ->
                SettingsRow(label = "${nameOf(p.trigger)} → ${nameOf(p.camera)}", value = "Supprimer", onClick = {
                    pairs = pairs.filterIndexed { j, _ -> j != i }; prefs.cameraPairs = pairs
                })
                if (i != pairs.lastIndex) SettingsDivider()
            }
        }

        if (cameras.isEmpty()) {
            Text("Aucune caméra détectée dans Home Assistant.", style = AppleTypography.bodyLarge, color = AppleColors.secondary)
        } else {
            SettingsSection(title = "DÉCLENCHEUR (CAPTEUR)") {
                triggers.forEachIndexed { i, e ->
                    SettingsRow(label = e.name, value = if (selTrigger == e.entityId) "✓" else null, onClick = { selTrigger = e.entityId })
                    if (i != triggers.lastIndex) SettingsDivider()
                }
            }
            SettingsSection(title = "CAMÉRA") {
                cameras.forEachIndexed { i, e ->
                    SettingsRow(label = e.name, value = if (selCamera == e.entityId) "✓" else null, onClick = { selCamera = e.entityId })
                    if (i != cameras.lastIndex) SettingsDivider()
                }
            }
            PillButton(label = "Ajouter la paire", primary = true, onClick = {
                val t = selTrigger; val c = selCamera
                if (t != null && c != null) { pairs = pairs + CameraPair(t, c); prefs.cameraPairs = pairs; selTrigger = null; selCamera = null }
            })
        }
    }
}
