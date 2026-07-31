package com.iblu01.portallauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.iblu01.portallauncher.ui.components.AppEntry
import com.iblu01.portallauncher.ui.components.ConnStatus
import com.iblu01.portallauncher.ui.screens.SettingsCallbacks
import com.iblu01.portallauncher.ui.screens.SettingsForm
import com.iblu01.portallauncher.ui.screens.SettingsScreen
import com.iblu01.portallauncher.ui.screens.SettingsUiState
import com.iblu01.portallauncher.ui.theme.PortalTheme
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var pills: PillRepository
    private val uiState = SettingsUiState()

    private val autoReturnTimer by lazy {
        AutoReturnTimer(lifecycleScope, prefs, onAutoReturn = { finish() })
    }

    /** Last persisted MQTT-relevant values, to restart the bridge only when they actually change. */
    private var savedMqttSignature = ""

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiState.pillRules.addAll(prefs.pillRules)
        savedMqttSignature = mqttSignature(prefs.brokerHost, prefs.brokerPort, prefs.username, prefs.password, prefs.deviceName)

        val apps = resolveInstalledApps()
        val appLabel = apps.find { it.packageName == prefs.homeAssistantPackage }?.label ?: prefs.homeAssistantPackage

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
                    currentAppLabel = appLabel,
                    haStates = pills.latestStates,
                    autoReturnState = autoReturnState,
                    onAutoReturnCancel = autoReturnTimer::onInteraction,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        MqttBridgeService.start(this)
        autoReturnTimer.start()
    }

    override fun onPause() {
        autoReturnTimer.stop()
        super.onPause()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            autoReturnTimer.onInteraction()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun mqttSignature(host: String, port: Int, username: String, password: String, deviceName: String) =
        "$host:$port:$username:$password:$deviceName"

    private val callbacks = object : SettingsCallbacks {
        override fun onSave(form: SettingsForm) {
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
        }

        override fun onOpenOpacityPreview() {
            startActivity(Intent(this@SettingsActivity, OpacityPreviewActivity::class.java))
        }

        override fun onOpenClockTheme() {
            startActivity(Intent(this@SettingsActivity, ClockThemeActivity::class.java))
        }

        override fun onLoadPillEntities() {
            uiState.pillLoading = true
            uiState.pillError = null
            Thread {
                val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
                val entities = if (result.ok) parseHaEntities(result.body.orEmpty()) else emptyList()
                runOnUiThread {
                    uiState.pillLoading = false
                    val candidates = PillSupport.candidates(entities).sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
                    uiState.pillCandidates.clear()
                    uiState.pillCandidates.addAll(candidates)
                    val hydrated = uiState.pillRules.map { old ->
                        candidates.firstOrNull { it.primary.entityId == old.entityId }?.let { candidate ->
                            old.copy(kind = candidate.kind, label = candidate.label, relatedEntityIds = candidate.related.map { it.entityId })
                        } ?: old
                    }
                    uiState.pillRules.clear(); uiState.pillRules.addAll(hydrated); prefs.pillRules = hydrated
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
        }
    }

    private fun parseHaEntities(raw: String): List<HaEntity> = runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("entity_id")
            if (id.isBlank()) null else HaEntity(id, o.optString("state"), o.optJSONObject("attributes") ?: JSONObject(), o.optString("last_changed"))
        }
    }.getOrDefault(emptyList())

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
                    uiState.mqttTestMessage = "Échec de connexion"
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun testHaApi(url: String, token: String) {
        val cleanUrl = url.trim().trimEnd('/').ifEmpty { "http://homeassistant.local:8123" }
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            uiState.haTest = ConnStatus.ERROR
            uiState.haTestMessage = "Colle d'abord ta clé"
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
                        401 -> "Clé invalide"
                        404 -> "Adresse incorrecte"
                        -1 -> "Serveur introuvable"
                        else -> "Erreur ${result.statusCode}"
                    }
                }
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun grantUsefulPermissions() {
        val missing = listOf(android.Manifest.permission.RECORD_AUDIO).filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 1)
            return
        }
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
}
