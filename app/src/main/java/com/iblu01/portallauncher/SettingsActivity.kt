package com.iblu01.portallauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.iblu01.portallauncher.ui.apps.LayoutBackup
import com.iblu01.portallauncher.ui.components.AppEntry
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.domain.home.CameraSupport
import com.iblu01.portallauncher.domain.home.PillSpecials
import com.iblu01.portallauncher.ui.screens.CameraSettingsEntry
import com.iblu01.portallauncher.ui.screens.SettingsCallbacks
import com.iblu01.portallauncher.ui.screens.SettingsForm
import com.iblu01.portallauncher.ui.screens.SettingsScreen
import com.iblu01.portallauncher.ui.screens.SettingsUiState
import com.iblu01.portallauncher.ui.settings.HomeSettingsAction
import com.iblu01.portallauncher.ui.settings.HomeSettingsCatalogBuilder
import com.iblu01.portallauncher.ui.settings.HomeSettingsReducer
import com.iblu01.portallauncher.ui.theme.PortalTheme
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var pills: PillRepository
    private val uiState = SettingsUiState()
    private var settingsCatalogConnected = false
    private val pillListener = PillRepository.Listener {
        runOnUiThread {
            syncHomeSettingsFromRepository()
            refreshPillSettingsCatalog()
        }
    }

    private val autoReturnTimer by lazy {
        AutoReturnTimer(lifecycleScope, prefs, onAutoReturn = { finish() })
    }

    /** Last persisted MQTT-relevant values, to restart the bridge only when they actually change. */
    private var savedMqttSignature = ""
    private var loadedWebConfigSignature = ""

    /**
     * Layout export / restore, through the storage picker rather than a fixed path: the app holds no
     * storage permission, and the document the user picks is the only file it ever touches.
     */
    private val exportLayout = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val written = runCatching {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(LayoutBackup.export(prefs).toByteArray())
            } ?: error("no output stream for $uri")
        }.isSuccess
        toast(if (written) R.string.toast_layout_exported else R.string.toast_layout_export_failed)
    }

    private val importLayout = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val result = runCatching {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("no input stream for \$uri")
            LayoutBackup.import(prefs, json)
        }
        result.onSuccess { restored ->
            // The launcher's store holds the old arrangement in memory; tell it to re-read.
            SettingsChangeBus.get().emit("launcherLayout")
            Toast.makeText(
                this,
                getString(R.string.toast_layout_imported_format, restored.placements, restored.folders),
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure { toast(R.string.toast_layout_import_failed) }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiState.pillRules.addAll(prefs.pillRules)
        uiState.homePillPreferences = prefs.homePillPreferences
        syncHomeSettingsFromRepository()
        refreshPillSettingsCatalog()
        savedMqttSignature = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)
        loadedWebConfigSignature = webConfigSignature()

        val apps = resolveInstalledApps()

        // Check the saved connection right away so the « Ma maison » tile shows a live status.
        if (prefs.haToken.isNotBlank()) {
            testHaApi(prefs.haUrl, prefs.haToken)
        }

        setContent {
            PortalTheme {
                val autoReturnState by autoReturnTimer.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    prefs = prefs,
                    uiState = uiState,
                    callbacks = callbacks,
                    installedApps = apps,
                    haStates = pills.latestStates,
                    autoReturnState = autoReturnState,
                    onAutoReturnCancel = autoReturnTimer::onInteraction,
                    initialPage = intent?.getStringExtra(EXTRA_PAGE),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (loadedWebConfigSignature.isNotEmpty() && loadedWebConfigSignature != webConfigSignature()) {
            recreate()
            return
        }
        syncHomeSettingsFromRepository()
        refreshPillSettingsCatalog()
        pills.addListener(pillListener)
        MqttBridgeService.start(this)
        autoReturnTimer.start()
    }

    private fun webConfigSignature(): String = listOf(
        prefs.haUrl,
        prefs.haToken.hashCode().toString(),
        mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName),
    ).joinToString("|")

    override fun onPause() {
        pills.removeListener(pillListener)
        autoReturnTimer.stop()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            autoReturnTimer.onInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    private val callbacks = object : SettingsCallbacks {
        override fun onSave(form: SettingsForm) {
            // Returning from remote configuration recreates this activity so Compose can rebuild
            // every field from Prefs. The outgoing composition still runs its onDispose auto-save;
            // never let that stale form overwrite the values the web server has just persisted.
            if (
                loadedWebConfigSignature.isNotEmpty() &&
                loadedWebConfigSignature != webConfigSignature()
            ) return
            prefs.homeAssistantPackage = form.haPackage
            prefs.brokerHost = form.host
            prefs.brokerPort = form.port
            prefs.username = form.username
            prefs.password = form.password
            prefs.deviceName = form.deviceName
            prefs.haUrl = form.haUrl
            prefs.haToken = form.haToken
            SleepScheduler.apply(this@SettingsActivity)
            val newSignature = mqttSignature(form.host, form.port, form.username, form.password, form.deviceName)
            if (newSignature != savedMqttSignature) {
                savedMqttSignature = newSignature
                MqttBridgeService.stop(this@SettingsActivity)
                MqttBridgeService.start(this@SettingsActivity)
            }
        }

        override fun onToggleDevKeepScreenOn(enabled: Boolean) {
            prefs.devKeepScreenOn = enabled
            SleepScheduler.apply(this@SettingsActivity)
        }

        override fun onTogglePowerAlwaysOn(alwaysOn: Boolean) {
            prefs.powerMode = if (alwaysOn) PowerMode.ALWAYS_ON else PowerMode.FOLLOW_PRESENCE
            SleepScheduler.apply(this@SettingsActivity)
        }

        override fun onToggleTimeoutEnabled(enabled: Boolean) {
            prefs.screenTimeoutEnabled = enabled
            SleepScheduler.apply(this@SettingsActivity)
        }

        override fun onSetTimeoutMinutes(minutes: Int) {
            prefs.screenTimeoutMinutes = minutes
            SleepScheduler.apply(this@SettingsActivity)
        }

        override fun onTestMqtt(host: String, port: Int, username: String, password: String) =
            testMqtt(host, port, username, password)

        override fun onTestHaApi(url: String, token: String) =
            testHaApi(url, token)

        override fun onConnectionEdited() {
            uiState.haTest = ConnStatus.IDLE
            uiState.haTestMessage = null
            uiState.mqttTest = ConnStatus.IDLE
            uiState.mqttTestMessage = null
        }

        override fun onGrantPermissions() = grantUsefulPermissions()

        override fun onSetBackgroundMode(mode: String) {
            prefs.backgroundMode = mode
            SettingsChangeBus.get().emit("backgroundMode")
        }

        override fun onOpenSystemWallpaperPicker() {
            prefs.backgroundMode = "system"
            SettingsChangeBus.get().emit("backgroundMode")
            runCatching {
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SET_WALLPAPER),
                    getString(R.string.toast_choose_wallpaper),
                ))
            }.onFailure {
                Toast.makeText(this@SettingsActivity, R.string.toast_cannot_open_wallpaper_picker, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onOpenOpacityPreview() {
            startActivity(Intent(this@SettingsActivity, OpacityPreviewActivity::class.java))
        }

        override fun onOpenClockTheme() {
            startActivity(Intent(this@SettingsActivity, ClockThemeActivity::class.java))
        }

        override fun onLoadPillEntities() {
            // Cameras come from the live cache, not from the /api/states round-trip below: they
            // must list even while Home Assistant is unreachable, so the settings never lock up.
            refreshCameras()
            uiState.pillLoading = true
            uiState.pillError = null
            Thread {
                val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
                val entities = if (result.ok) parseHaEntities(result.body.orEmpty()) else emptyList()
                runOnUiThread {
                    uiState.pillLoading = false
                    settingsCatalogConnected = result.ok
                    if (result.ok) {
                        val candidates = PillSupport.candidates(entities, pills.latestDeviceIds).sortedWith(
                            compareBy({ it.kind.ordinal }, { it.label.lowercase() }),
                        )
                        uiState.pillCandidates.clear()
                        uiState.pillCandidates.addAll(candidates)
                        val hydrated = uiState.pillRules.map { old ->
                            candidates.firstOrNull { it.primary.entityId == old.entityId }?.let { candidate ->
                                old.copy(kind = candidate.kind, label = candidate.label, relatedEntityIds = candidate.related.map { it.entityId })
                            } ?: old
                        }
                        uiState.pillRules.clear()
                        uiState.pillRules.addAll(hydrated)
                        prefs.pillRules = hydrated
                        SettingsChangeBus.get().emit("pillRules")
                    }
                    refreshPillSettingsCatalog()
                    if (!result.ok) uiState.pillError = "Maison injoignable (code ${result.statusCode})"
                }
            }.also { it.isDaemon = true }.start()
        }

        override fun onSetPillEnabled(candidates: List<PillCandidate>, enabled: Boolean) {
            val rules = uiState.pillRules.toMutableList()
            candidates.forEach { candidate ->
                val index = rules.indexOfFirst { it.entityId == candidate.primary.entityId }
                if (enabled && index < 0) rules += PillSupport.defaultRule(candidate)
                else if (index >= 0) rules[index] = rules[index].copy(enabled = enabled)
            }
            uiState.pillRules.clear(); uiState.pillRules.addAll(rules)
            prefs.pillRules = rules
            refreshPillSettingsCatalog()
            SettingsChangeBus.get().emit("pillRules")
        }

        override fun onExportLayout() {
            exportLayout.launch(LayoutBackup.fileName(prefs.deviceName))
        }

        override fun onImportLayout() {
            // Any MIME type: file managers and cloud providers routinely hand back
            // "application/octet-stream" for a .json, and a strict filter greys the file out.
            importLayout.launch(arrayOf("application/json", "text/plain", "*/*"))
        }

        override fun onHomeSettingsAction(action: HomeSettingsAction) {
            uiState.homePillPreferences = prefs.updateHomePillPreferences { current ->
                HomeSettingsReducer.reduce(current, action)
            }
            refreshPillSettingsCatalog()
        }

        override fun onCameraPreferences(transform: (CameraPreferences) -> CameraPreferences) {
            uiState.cameraPreferences = prefs.updateCameraPreferences(transform)
        }

        override fun onCamerasPillPinned(pinned: Boolean) {
            uiState.homePillPreferences = prefs.updateHomePillPreferences { current ->
                val without = current.pinnedOrder.filterNot { it == PillSpecials.cameras }
                current.copy(
                    pinnedOrder = if (pinned) without + PillSpecials.cameras else without,
                )
            }
            uiState.camerasPillPinned = pinned
            refreshPillSettingsCatalog()
        }
    }

    /**
     * The cameras Home Assistant currently exposes. Read from the live state cache rather than
     * from the preference, so a camera added in Home Assistant appears here with no extra step and
     * a removed one simply stops being listed — without blocking the page or the other cameras.
     */
    private fun refreshCameras() {
        val cameras = pills.latestStates.values
            .filter { it.domain == "camera" }
            .sortedBy { it.name.lowercase() }
            .map { entity ->
                CameraSettingsEntry(
                    entityId = entity.entityId,
                    label = entity.name,
                    available = CameraSupport.isAvailable(entity),
                )
            }
        uiState.cameras.clear()
        uiState.cameras.addAll(cameras)
        uiState.cameraPreferences = prefs.cameraPreferences
        uiState.camerasPillPinned = PillSpecials.cameras in prefs.homePillPreferences.pinnedOrder
    }

    private fun refreshPillSettingsCatalog() {
        uiState.settingsPillCatalog = HomeSettingsCatalogBuilder.build(
            context = this,
            candidates = uiState.pillCandidates,
            rules = uiState.pillRules,
            areaIdByEntity = pills.latestAreaIdByEntity,
            areaNameById = pills.latestAreaNameById,
            connected = settingsCatalogConnected,
        )
    }

    /** Resynchronizes mutable Settings state after edits performed from Launcher/Maison. */
    private fun syncHomeSettingsFromRepository() {
        val persistedRules = prefs.pillRules
        uiState.pillRules.clear()
        uiState.pillRules.addAll(persistedRules)
        uiState.homePillPreferences = prefs.homePillPreferences
        settingsCatalogConnected = pills.latestConnected
        if (pills.latestStates.isNotEmpty()) {
            val candidates = PillSupport.candidates(pills.latestStates.values.toList(), pills.latestDeviceIds)
                .sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
            uiState.pillCandidates.clear()
            uiState.pillCandidates.addAll(candidates)
        }
    }

    private fun resolveInstalledApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo?.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
            .mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                AppEntry(
                    label = info.loadLabel(packageManager).toString(),
                    packageName = ai.packageName,
                    activityName = ai.name
                )
            }
    }

    private fun testMqtt(host: String, port: Int, username: String, password: String) {
        val cleanHost = host.trim().ifEmpty { "homeassistant.local" }
        val cleanPort = port.coerceIn(1, 65535)
        val uri = "tcp://$cleanHost:$cleanPort"
        uiState.mqttTest = ConnStatus.TESTING
        uiState.mqttTestMessage = null
        Thread {
            val result = runCatching {
                val client = MqttClient(
                    uri,
                    "portallauncher-test-${System.currentTimeMillis()}",
                    MemoryPersistence()
                )
                client.timeToWait = 8_000L
                client.connect(MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = 6
                    keepAliveInterval = 10
                    if (username.trim().isNotEmpty()) {
                        userName = username.trim()
                        this.password = password.toCharArray()
                    }
                })
                val topic = "portal/${prefs.deviceId}/mqtt/test"
                val payload = """{"ok":true,"source":"settings","ts":${System.currentTimeMillis()}}"""
                client.publish(topic, MqttMessage(payload.toByteArray()).apply {
                    qos = 0
                    isRetained = false
                })
                client.disconnect(1_000)
            }
            runOnUiThread {
                if (result.isSuccess) {
                    uiState.mqttTest = ConnStatus.OK
                    uiState.mqttTestMessage = null
                } else {
                    uiState.mqttTest = ConnStatus.ERROR
                    uiState.mqttTestMessage = getString(R.string.connection_error_failed)
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun testHaApi(url: String, token: String) {
        val cleanUrl = url.trim().trimEnd('/').ifEmpty { "http://homeassistant.local:8123" }
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            uiState.haTest = ConnStatus.ERROR
            uiState.haTestMessage = getString(R.string.connection_error_token_required)
            return
        }
        uiState.haTest = ConnStatus.TESTING
        uiState.haTestMessage = null
        Thread {
            val client = HaApiClient(cleanUrl, cleanToken)
            val result = client.testConnection()
            runOnUiThread {
                if (result.ok) {
                    uiState.haTest = ConnStatus.OK
                    uiState.haTestMessage = null
                } else {
                    uiState.haTest = ConnStatus.ERROR
                    uiState.haTestMessage = when (result.statusCode) {
                        401 -> getString(R.string.connection_error_token_invalid)
                        404 -> getString(R.string.connection_error_address_invalid)
                        -1 -> getString(R.string.connection_error_server_not_found)
                        else -> getString(R.string.connection_error_code, result.statusCode)
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun grantUsefulPermissions() {
        if (!Settings.System.canWrite(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        if (!ScreenControl.isAccessibilityEnabled(this)) {
            if (!ScreenControl.enableAccessibility(this)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            return
        }
        Toast.makeText(this, getString(R.string.toast_permissions_ok), Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Optional `SettingsPage` name to open directly instead of the settings root. */
        const val EXTRA_PAGE = "page"
        const val PAGE_HOME = "HOME_SCREEN"
        const val PAGE_APPEARANCE = "APPEARANCE"
        const val PAGE_CONNECTED_HOME = "CONNECTED_HOME"
        const val PAGE_CONNECTED_HOME_CONNECTION = "CONNECTED_HOME_CONNECTION"
        const val PAGE_DEVICE = "DEVICE"
        const val PAGE_ABOUT = "ABOUT"
    }
}
