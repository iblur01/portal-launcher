package com.iblu01.portallauncher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.iblu01.portallauncher.photo.PhotoStatusSerializer
import com.iblu01.portallauncher.session.SessionCoordinator
import com.iblu01.portallauncher.session.SessionAllowlist
import com.iblu01.portallauncher.session.SessionManager
import com.iblu01.portallauncher.session.SessionMqttContract
import com.iblu01.portallauncher.session.SessionResult
import com.iblu01.portallauncher.session.SessionRuntime
import com.iblu01.portallauncher.session.SessionSerializer
import com.iblu01.portallauncher.session.RealSessionTimeSource
import com.iblu01.portallauncher.voice.VoiceAssistantController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class MqttBridgeService : Service() {
    /** Shared with the launcher: the same singleton owns the microphone hand-off. */
    @Inject lateinit var voice: VoiceAssistantController

    companion object {
        private const val TAG = "PortalLauncher"
        private const val CHANNEL = "portal_launcher_bridge"
        private const val NOTIF_ID = 1
        private const val ACTION_RECONNECT = "com.iblu01.portallauncher.action.RECONNECT"
        private const val EXTRA_FOREGROUND = "foreground"

        fun start(context: Context) = launch(context, action = null)

        /**
         * Starts the bridge, silently when possible.
         *
         * A rooted device whitelists us from doze and background restrictions during provisioning,
         * so the bridge can run as a plain background service — which is what removes the permanent
         * notification. Everywhere else (and whenever that plain start is refused, e.g. when the
         * boot receiver fires) it falls back to a foreground service with its notification.
         */
        private fun launch(context: Context, action: String?) {
            val intent = Intent(context, MqttBridgeService::class.java)
            action?.let { intent.action = it }
            if (Prefs(context).rootProvisioned &&
                runCatching { context.startService(intent) }.isSuccess
            ) {
                return
            }
            intent.putExtra(EXTRA_FOREGROUND, true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MqttBridgeService::class.java))
        }

        /**
         * Forces a live reconnect of the MQTT bridge, e.g. after broker settings change via
         * the web config UI. If the service is already running, the current client is dropped
         * so the outer loop reconnects with freshly-read prefs. If the service is not running,
         * this simply starts it normally.
         */
        fun reconnect(context: Context) {
            launch(context, ACTION_RECONNECT)
        }
    }

    private val running = AtomicBoolean(false)
    private val commands = Executors.newSingleThreadExecutor { r ->
        Thread(r, "portal-launcher-cmd").also { it.isDaemon = true }
    }
    @Volatile private var mqtt: MqttClient? = null
    private lateinit var prefs: Prefs
    private var sensorBridge: SensorBridge? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var audioReceiver: BroadcastReceiver? = null
    @Volatile private var sessionCoordinator: SessionCoordinator? = null
    private var sessionAllowlist: SessionAllowlist = SessionAllowlist.EMPTY
    private var lastSessionsEnabled: Boolean? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionTick = object : Runnable {
        override fun run() {
            if (running.get()) {
                sessionCoordinator?.takeIf { it.hasActiveSession }
                    ?.onDeviceState(DeviceStateHub.current.foregroundPackage)
                mainHandler.postDelayed(this, 1_000L)
            }
        }
    }
    private val deviceStateListener = DeviceStateHub.Listener { state ->
        publishDeviceState(state)
        sessionCoordinator?.onDeviceState(state.foregroundPackage)
    }
    @Volatile private var lastVolumePercent = -1
    @Volatile private var lastVolumeMuted = false
    @Volatile private var lastBrightnessPercent = -1
    /** False in the root-provisioned silent mode, where there is no notification to update. */
    @Volatile private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The language chosen in Settings applies here too. Every Activity wraps its base context; a
     * Service does not get that for free, and the alarm's own wording is composed right here, so
     * without this the panel speaks and writes in the device language while the rest of the UI
     * follows the user's choice.
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        prefs = Prefs(this)
        sessionAllowlist = prefs.appSessionAllowlist
        sessionCoordinator = createSessionCoordinator().also { it.setEnabled(prefs.appSessionsEnabled) }
        lastSessionsEnabled = prefs.appSessionsEnabled
        // An away home that was locked stays locked through a reboot; reopening itself would be
        // the one failure mode this switch exists to prevent.
        ActionLockState.set(prefs.actionLocked)
        // Same reasoning for the microphone: a panel muted before a power cut must not wake up
        // listening again.
        VoiceMuteState.restore(prefs)
        VoiceMuteState.onChanged = { muted ->
            publishVoiceMuteState(prefs)
            voice.onConfigChanged()
            if (muted) voice.stopSession()
        }

        DeviceStateHub.init(this)
        DeviceStateHub.addListener(deviceStateListener)
        ScreenControl.enableAccessibility(this)
        sensorBridge = SensorBridge(this, ::publishRaw).also { it.start(prefs) }

        registerScreenReceiver()
        registerAudioReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent is a sticky restart: the system may have started us as a foreground
        // service, so promote to be safe. `startForeground` is idempotent.
        if (intent == null || intent.getBooleanExtra(EXTRA_FOREGROUND, false)) {
            startForeground(NOTIF_ID, notification(getString(R.string.app_name)))
            foreground = true
        }
        if (intent?.action == ACTION_RECONNECT) {
            Log.i(TAG, "Reconnect requested (broker config changed)")
            refreshSessionConfiguration(prefs)
            Thread({
                com.iblu01.portallauncher.ui.ConnectionStatus.connected = false
                runCatching { mqtt?.disconnect(0) }
                mqtt = null
            }, "portal-launcher-reconnect").also { it.isDaemon = true }.start()
        }
        if (running.compareAndSet(false, true)) {
            mainHandler.post(sessionTick)
            Thread(::mqttLoop, "portal-launcher-mqtt").also { it.isDaemon = true }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        mainHandler.removeCallbacks(sessionTick)
        sessionCoordinator = null
        commands.shutdownNow()
        runCatching { mqtt?.disconnect(0) }
        sensorBridge?.stop()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        audioReceiver?.let { runCatching { unregisterReceiver(it) } }
        DeviceStateHub.removeListener(deviceStateListener)
        VoiceMuteState.onChanged = null
        super.onDestroy()
    }

    private fun mqttLoop() {
        var backoff = 2_000L
        while (running.get()) {
            try {
                connectAndRun()
                backoff = 5_000L
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "MQTT error, retry in ${backoff / 1000}s: ${e.message}")
            }
            if (running.get()) Thread.sleep(backoff)
            backoff = minOf(backoff * 2, 60_000L)
        }
    }

    private fun connectAndRun() {
        val p = prefs
        val client = MqttClient(p.brokerUri, "portallauncher-${p.deviceId.take(8)}", MemoryPersistence())
        client.timeToWait = 30_000L
        client.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                mqtt = null
            }

            override fun messageArrived(topic: String, msg: MqttMessage) {
                val payload = msg.toString().trim()
                commands.submit {
                    runCatching { handleMessage(topic, payload, p) }
                        .onFailure { Log.w(TAG, "command failed: ${it.message}") }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })

        client.connect(MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 15
            keepAliveInterval = 30
            maxInflight = 100
            if (p.username.isNotEmpty()) {
                userName = p.username
                password = p.password.toCharArray()
            }
            setWill(HaDiscovery.screenStateTopic(p.deviceId), "OFF".toByteArray(), 1, true)
        })
        mqtt = client
        if (!runConfigurationOnMain { refreshSessionConfiguration(p, publishEnabledState = false) }) {
            mqtt = null
            runCatching { client.disconnect(0) }
            throw IllegalStateException("session_config_apply_failed")
        }
        com.iblu01.portallauncher.ui.ConnectionStatus.connected = true
        Log.i(TAG, "MQTT connected to ${p.brokerUri}")

        HaDiscovery.commandTopics(p.deviceId).forEach { client.publish(it, emptyRetained()) }
        HaDiscovery.commandTopics(p.deviceId).forEach { client.subscribe(it, 1) }

        publishDiscovery(client, p)
        publishInitialStates(p)
        updateNotification("Connected - ${p.brokerHost}")

        try {
            while (running.get() && client.isConnected) {
                Thread.sleep(5_000)
                pollChangedStates(p)
                publishDeviceState(DeviceStateHub.current)
            }
        } finally {
            com.iblu01.portallauncher.ui.ConnectionStatus.connected = false
            mqtt = null
            runCatching { client.disconnect(0) }
        }
    }

    private fun publishDiscovery(client: MqttClient, p: Prefs) {
        fun pub(topic: String, payload: String) = client.publish(topic, retained(payload))

        pub(HaDiscovery.screenDiscoveryTopic(p.deviceId), HaDiscovery.screenConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenModeDiscoveryTopic(p.deviceId), HaDiscovery.screenModeConfigPayload(p.deviceId, p.deviceName))
        if (DeviceStateHub.presenceCapable) {
            pub(HaDiscovery.presenceDiscoveryTopic(p.deviceId), HaDiscovery.presenceConfigPayload(p.deviceId, p.deviceName))
        } else {
            client.publish(HaDiscovery.presenceDiscoveryTopic(p.deviceId), emptyRetained())
        }
        pub(HaDiscovery.ipDiscoveryTopic(p.deviceId), HaDiscovery.ipConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.lightDiscoveryTopic(p.deviceId), HaDiscovery.lightConfigPayload(p.deviceId, p.deviceName))
        if (sensorBridge?.hasTemperature == true) {
            pub(HaDiscovery.tempDiscoveryTopic(p.deviceId), HaDiscovery.tempConfigPayload(p.deviceId, p.deviceName))
        } else {
            client.publish(HaDiscovery.tempDiscoveryTopic(p.deviceId), emptyRetained())
        }
        pub(HaDiscovery.volumeDiscoveryTopic(p.deviceId), HaDiscovery.volumeConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.volumeMuteDiscoveryTopic(p.deviceId), HaDiscovery.volumeMuteConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.doorbellDiscoveryTopic(p.deviceId), HaDiscovery.doorbellConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.alertDiscoveryTopic(p.deviceId), HaDiscovery.alertConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.voiceDiscoveryTopic(p.deviceId), HaDiscovery.voiceConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.brightnessDiscoveryTopic(p.deviceId), HaDiscovery.brightnessConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenTimeoutDiscoveryTopic(p.deviceId), HaDiscovery.screenTimeoutConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenTimeoutMinutesDiscoveryTopic(p.deviceId), HaDiscovery.screenTimeoutMinutesConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.powerModeDiscoveryTopic(p.deviceId), HaDiscovery.powerModeConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.photoStatusDiscoveryTopic(p.deviceId), HaDiscovery.photoStatusConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.sessionDiscoveryTopic(p.deviceId), HaDiscovery.sessionConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.sessionEnabledDiscoveryTopic(p.deviceId), HaDiscovery.sessionEnabledConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.actionLockDiscoveryTopic(p.deviceId), HaDiscovery.actionLockConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.voiceMuteDiscoveryTopic(p.deviceId), HaDiscovery.voiceMuteConfigPayload(p.deviceId, p.deviceName))
    }

    private fun publishInitialStates(p: Prefs) {
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        publishRaw(HaDiscovery.screenStateTopic(p.deviceId), if (interactive) "ON" else "OFF", 1, retained = true)
        publishDeviceState(DeviceStateHub.current)
        publishRaw(HaDiscovery.ipStateTopic(p.deviceId), localIp() ?: "unknown", 1, retained = true)
        publishVolumeState(p)
        publishVolumeMuteState(p)
        publishBrightnessState(p)
        publishPowerState(p)
        publishPhotoStatus(p)
        publishSessionsEnabledState(p)
        publishActionLockState(p)
        publishVoiceMuteState(p)
        sessionCoordinator?.publishCurrentState()
    }

    private fun pollChangedStates(p: Prefs) {
        val vol = currentVolumePercent()
        if (vol != lastVolumePercent) publishVolumeState(p)
        val muted = getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_MUSIC)
        if (muted != lastVolumeMuted) publishVolumeMuteState(p)
        val bright = currentBrightnessPercent()
        if (bright != lastBrightnessPercent) publishBrightnessState(p)
        publishPhotoStatus(p)
        mainHandler.post { refreshSessionConfiguration(p) }
        sessionCoordinator?.publishCurrentState()
    }

    private fun publishPhotoStatus(p: Prefs) {
        val status = (application as PortalApp).photoCoordinator.status.value
        publishRaw(
            HaDiscovery.photoStatusStateTopic(p.deviceId),
            PhotoStatusSerializer.state(status),
            1,
            retained = true,
        )
        publishRaw(
            HaDiscovery.photoStatusAttributesTopic(p.deviceId),
            PhotoStatusSerializer.attributes(status),
            1,
            retained = true,
        )
    }

    private fun handleMessage(topic: String, payload: String, p: Prefs) {
        when (topic) {
            HaDiscovery.sessionCommandTopic(p.deviceId) -> sessionCoordinator?.onCommand(payload)
            HaDiscovery.screenCommandTopic(p.deviceId) -> when (payload.uppercase()) {
                "ON" -> ScreenControl.wake(this)
                "OFF" -> ScreenControl.sleep(this)
            }
            HaDiscovery.volumeCommandTopic(p.deviceId) -> {
                val pct = (payload.toIntOrNull() ?: return).coerceIn(0, 100)
                val am = getSystemService(AudioManager::class.java)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, pct * am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100, 0)
                publishVolumeState(p)
            }
            HaDiscovery.volumeMuteCommandTopic(p.deviceId) -> {
                val muted = payload.uppercase() == "ON"
                getSystemService(AudioManager::class.java).adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    0
                )
                publishVolumeMuteState(p)
                toast(if (muted) "Volume muted" else "Volume unmuted")
            }
            HaDiscovery.soundCommandTopic(p.deviceId) -> {
                TonePlayer.play(payload)
                val msg = when (payload.trim().lowercase()) {
                    "doorbell" -> "Sonnette !"
                    "alert" -> "Alerte !"
                    else -> payload
                }
                AlertOverlayState.showAlert(msg)
            }
            HaDiscovery.notificationCommandTopic(p.deviceId) -> showNotification(p, payload)
            HaDiscovery.alarmCommandTopic(p.deviceId) -> showAlarm(p, payload)
            HaDiscovery.actionLockCommandTopic(p.deviceId) -> {
                val locked = payload.trim().uppercase() == "ON"
                p.actionLocked = locked
                ActionLockState.set(locked)
                publishActionLockState(p)
                toast(getString(if (locked) R.string.action_lock_on else R.string.action_lock_off))
            }
            HaDiscovery.voiceMuteCommandTopic(p.deviceId) -> {
                VoiceMuteState.set(p, payload.trim().uppercase() == "ON")
                toast(getString(if (VoiceMuteState.muted) R.string.voice_mute_on else R.string.voice_mute_off))
            }
            HaDiscovery.voiceCommandTopic(p.deviceId) -> {
                // A muted assistant refuses the remote trigger too, otherwise the switch only
                // hides the microphone instead of closing it.
                if (VoiceMuteState.muted) Log.i(TAG, "voice: ignored remote start, assistant muted")
                else startVoiceSession()
            }
            HaDiscovery.brightnessCommandTopic(p.deviceId) -> {
                val pct = (payload.toIntOrNull() ?: return).coerceIn(0, 100)
                runCatching {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, (pct * 255 / 100).coerceIn(0, 255))
                }.onFailure {
                    Log.w(TAG, "WRITE_SETTINGS not granted - run appops WRITE_SETTINGS allow")
                }
                publishBrightnessState(p)
            }
            HaDiscovery.screenTimeoutCommandTopic(p.deviceId) -> {
                p.screenTimeoutEnabled = payload.uppercase() == "ON"
                SleepScheduler.apply(this)
                publishPowerState(p)
            }
            HaDiscovery.screenTimeoutMinutesCommandTopic(p.deviceId) -> {
                p.screenTimeoutMinutes = payload.toIntOrNull() ?: return
                SleepScheduler.apply(this)
                publishPowerState(p)
            }
            HaDiscovery.powerModeCommandTopic(p.deviceId) -> {
                p.powerMode = when (payload.trim().lowercase()) {
                    "always on", "always_on", "on" -> PowerMode.ALWAYS_ON
                    else -> PowerMode.FOLLOW_PRESENCE
                }
                SleepScheduler.apply(this)
                publishPowerState(p)
            }
        }
    }

    /**
     * Remote "press to talk". The screen is woken and the launcher brought forward first: the
     * session overlay lives in the launcher, so starting the session on a panel showing something
     * else would leave the user talking to an invisible assistant. The launcher's own onResume
     * would normally re-arm the wake engine and steal the microphone from the session, which
     * VoiceAssistantController refuses while a session is live.
     */
    /**
     * Shows a notification pushed by Home Assistant: a plain message, or the JSON payload parsed by
     * [AlertPayload] carrying its own icon, level, chime and speech.
     *
     * Speech is always rendered on the Home Assistant side. Either the payload names a finished
     * clip, or it carries the words and the panel asks Home Assistant for their URL with the
     * credentials it already holds — it never synthesises anything itself.
     */
    private fun showNotification(p: Prefs, payload: String) {
        if (AlertPayload.isDismiss(payload)) {
            // Disarming during an entry delay lands here: the countdown and its beeps go at once.
            AudioUrlPlayer.stop()
            AlertOverlayState.dismiss()
            return
        }
        val alert = AlertPayload.parse(payload) ?: return
        if (alert.wake) ScreenControl.wake(this)

        val clip = alert.playableAudio(p.haUrl)
        when {
            clip != null -> speak(alert, clip)
            alert.audio != null -> {
                Log.w(TAG, "notification audio refused, not the configured HA host: ${alert.audio}")
                chimeAndShow(alert)
            }
            alert.tts != null -> {
                // The overlay goes up now and the words are fetched behind it: a round trip to
                // Home Assistant must not delay what the room can already read.
                AlertOverlayState.show(alert, awaitAudio = alert.durationMs == null)
                fetchSpeech(p, alert)
            }
            else -> chimeAndShow(alert)
        }
    }

    /**
     * The alarm's own layer, driven by state rather than by a composed notification: Home Assistant
     * says `pending` or `triggered`, the panel decides whether that deserves the whole screen.
     *
     * An empty payload clears it — which is also what wiping the retained topic does.
     */
    private fun showAlarm(p: Prefs, payload: String) {
        val state = AlarmState.parse(payload)
        val alert = state?.toAlert(this)
        if (alert == null) {
            AlarmOverlayState.dismiss()
            return
        }
        if (alert.wake) ScreenControl.wake(this)
        AlarmOverlayState.show(alert)
        val spokenAt = System.currentTimeMillis()
        val chimeMs = alert.tone?.let { TonePlayer.play(it) } ?: 0L
        alert.tts?.let { words ->
            val baseUrl = p.haUrl
            val token = p.haToken
            Thread {
                val clip = HaApiClient(baseUrl, token).ttsUrl(words, alert.engine, alert.language)
                Log.i(TAG, "alarm speech: \"$words\" -> ${clip ?: "no url"}")
                clip ?: return@Thread
                // The chime has to be over before the announcement starts, or it never starts at
                // all. Fetching the clip already ate part of that wait.
                (chimeMs - (System.currentTimeMillis() - spokenAt)).takeIf { it > 0 }
                    ?.let { runCatching { Thread.sleep(it) } }
                AudioUrlPlayer.play(this, clip)
            }.also { it.isDaemon = true }.start()
        }
    }

    private fun publishVoiceMuteState(p: Prefs) {
        publishRaw(
            HaDiscovery.voiceMuteStateTopic(p.deviceId),
            if (VoiceMuteState.muted) "ON" else "OFF",
            1,
            retained = true,
        )
    }

    private fun publishActionLockState(p: Prefs) {
        publishRaw(
            HaDiscovery.actionLockStateTopic(p.deviceId),
            if (p.actionLocked) "ON" else "OFF",
            1,
            retained = true,
        )
    }

    private fun chimeAndShow(alert: AlertPayload) {
        alert.tone?.let { TonePlayer.play(it) }
        AlertOverlayState.show(alert)
    }

    private fun speak(alert: AlertPayload, clip: String) {
        // With no explicit duration the notification stands until the announcement has been read
        // out, however long that takes.
        AlertOverlayState.show(alert, awaitAudio = alert.durationMs == null)
        AudioUrlPlayer.play(this, clip) { AlertOverlayState.onAudioFinished() }
    }

    /** Asks Home Assistant to render [AlertPayload.tts], off the MQTT callback thread. */
    private fun fetchSpeech(p: Prefs, alert: AlertPayload) {
        val words = alert.tts ?: return
        val baseUrl = p.haUrl
        val token = p.haToken
        Thread {
            // The URL comes back over the authenticated REST call this panel made itself, so unlike
            // an `audio` field pushed by whoever can reach the broker, it needs no host check.
            val clip = HaApiClient(baseUrl, token).ttsUrl(words, alert.engine, alert.language)
            if (clip == null) {
                Log.w(TAG, "speech unavailable, falling back to the chime")
                TonePlayer.play(alert.tone ?: AlertPayload.DEFAULT_TONE)
                AlertOverlayState.onAudioFinished()
                return@Thread
            }
            AudioUrlPlayer.play(this, clip) { AlertOverlayState.onAudioFinished() }
        }.also { it.isDaemon = true }.start()
    }

    private fun startVoiceSession() {
        ScreenControl.wake(this)
        runCatching {
            startActivity(
                Intent(this, LauncherActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
            )
        }.onFailure { Log.w(TAG, "voice: could not bring the launcher forward", it) }
        voice.startSessionNow()
    }

    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> publishRaw(HaDiscovery.screenStateTopic(prefs.deviceId), "ON", 1, retained = true)
                    Intent.ACTION_SCREEN_OFF -> publishRaw(HaDiscovery.screenStateTopic(prefs.deviceId), "OFF", 1, retained = true)
                }
                publishDeviceState(DeviceStateHub.current)
            }
        }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
    }

    private fun registerAudioReceiver() {
        audioReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    "android.media.VOLUME_CHANGED_ACTION" -> publishVolumeState(prefs)
                    "android.media.STREAM_MUTE_CHANGED_ACTION" -> publishVolumeMuteState(prefs)
                }
            }
        }
        registerReceiver(audioReceiver, IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        })
    }

    private fun publishVolumeState(p: Prefs) {
        val vol = currentVolumePercent()
        lastVolumePercent = vol
        publishRaw(HaDiscovery.volumeStateTopic(p.deviceId), vol.toString(), 1, retained = true)
    }

    private fun publishVolumeMuteState(p: Prefs) {
        val muted = getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_MUSIC)
        lastVolumeMuted = muted
        publishRaw(HaDiscovery.volumeMuteStateTopic(p.deviceId), if (muted) "ON" else "OFF", 1, retained = true)
    }

    private fun publishBrightnessState(p: Prefs) {
        val bright = currentBrightnessPercent()
        lastBrightnessPercent = bright
        publishRaw(HaDiscovery.brightnessStateTopic(p.deviceId), bright.toString(), 1, retained = true)
    }

    private fun publishPowerState(p: Prefs) {
        publishRaw(
            HaDiscovery.screenTimeoutStateTopic(p.deviceId),
            if (p.screenTimeoutEnabled) "ON" else "OFF",
            1,
            retained = true
        )
        publishRaw(
            HaDiscovery.screenTimeoutMinutesStateTopic(p.deviceId),
            p.screenTimeoutMinutes.toString(),
            1,
            retained = true
        )
        publishRaw(
            HaDiscovery.powerModeStateTopic(p.deviceId),
            powerModeLabel(p.powerMode),
            1,
            retained = true
        )
    }

    private fun publishSessionsEnabledState(p: Prefs) {
        publishRaw(
            HaDiscovery.sessionEnabledStateTopic(p.deviceId),
            if (p.appSessionsEnabled) "ON" else "OFF",
            1,
            retained = true,
        )
    }

    private fun refreshSessionConfiguration(p: Prefs, publishEnabledState: Boolean = true) {
        val configuredAllowlist = p.appSessionAllowlist
        if (configuredAllowlist != sessionAllowlist) {
            sessionCoordinator?.setEnabled(false)
            sessionAllowlist = configuredAllowlist
            sessionCoordinator = createSessionCoordinator()
            lastSessionsEnabled = null
        }
        if (lastSessionsEnabled != p.appSessionsEnabled) {
            lastSessionsEnabled = p.appSessionsEnabled
            sessionCoordinator?.setEnabled(p.appSessionsEnabled)
            if (publishEnabledState) publishSessionsEnabledState(p)
        }
    }

    private fun createSessionCoordinator(): SessionCoordinator {
        val allowlist = sessionAllowlist
        val manager = SessionManager(
            timeSource = RealSessionTimeSource,
            allowlist = allowlist,
            launcherPackage = packageName,
        )
        val runtime = object : SessionRuntime {
            override fun publishEvent(result: SessionResult) {
                runCatching {
                    mqtt?.publish(
                        HaDiscovery.sessionEventTopic(prefs.deviceId),
                        SessionMqttContract.eventMessage(SessionSerializer.toJson(result)),
                    )
                }
            }

            override fun publishState(result: SessionResult) {
                runCatching {
                    mqtt?.publish(
                        HaDiscovery.sessionStateTopic(prefs.deviceId),
                        SessionMqttContract.stateMessage(SessionSerializer.toJson(result)),
                    )
                }
            }

            override fun launchApp(packageName: String): Boolean {
                if (allowlist.classificationFor(packageName) == null) return false
                val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return runCatching {
                    startActivity(intent)
                    DeviceStateHub.noteLaunchingApp(packageName, this@MqttBridgeService)
                }.isSuccess
            }

            override fun returnToLauncher(): Boolean = runCatching {
                startActivity(
                    Intent(this@MqttBridgeService, LauncherActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                )
                SleepScheduler.apply(this@MqttBridgeService)
            }.isSuccess
        }
        return SessionCoordinator(manager, allowlist, RealSessionTimeSource, runtime)
    }

    /** Serialize local configuration replacement with main-thread settings callbacks. */
    private fun runConfigurationOnMain(action: () -> Unit): Boolean {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(action).isSuccess
        val task = FutureTask { runCatching(action).isSuccess }
        if (!mainHandler.post(task)) return false
        return runCatching { task.get(5, TimeUnit.SECONDS) }.getOrElse {
            mainHandler.removeCallbacks(task)
            task.cancel(false)
            false
        }
    }

    private fun publishDeviceState(state: DeviceState) {
        val p = prefs
        publishRaw(HaDiscovery.screenModeStateTopic(p.deviceId), state.display.name.lowercase(), 1, retained = true)
        if (!DeviceStateHub.presenceCapable) return
        val present = when (state.presence) {
            Presence.PRESENT -> true
            Presence.ABSENT -> false
            Presence.UNKNOWN -> state.display != DisplayMode.OFF
        }
        publishRaw(HaDiscovery.presenceStateTopic(p.deviceId), if (present) "ON" else "OFF", 1, retained = true)
        val attrs = """{"confident":${state.confident},"source":"${state.source}","raw":"${state.presence.name.lowercase()}","screen":"${state.display.name.lowercase()}","foreground_package":${state.foregroundPackage?.let { "\"$it\"" } ?: "null"},"since_ms":${state.sinceMs}}"""
        publishRaw(HaDiscovery.presenceAttributesTopic(p.deviceId), attrs, 1, retained = true)
    }

    private fun currentVolumePercent(): Int {
        val am = getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max).coerceIn(0, 100)
    }

    private fun currentBrightnessPercent(): Int {
        val raw = runCatching {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(0)
        return (raw * 100 / 255).coerceIn(0, 100)
    }

    private fun publishRaw(topic: String, payload: String, qos: Int = 0, retained: Boolean = false) {
        runCatching {
            mqtt?.publish(topic, MqttMessage(payload.toByteArray()).also {
                it.qos = qos
                it.isRetained = retained
            })
        }
    }

    private fun retained(payload: String) = MqttMessage(payload.toByteArray()).also {
        it.qos = 1
        it.isRetained = true
    }

    private fun emptyRetained() = MqttMessage(ByteArray(0)).also {
        it.qos = 1
        it.isRetained = true
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.notification_content_title))
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        if (!foreground) return
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun toast(text: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun powerModeLabel(mode: PowerMode) = when (mode) {
        PowerMode.ALWAYS_ON -> getString(R.string.power_mode_always_on)
        PowerMode.FOLLOW_PRESENCE -> getString(R.string.power_mode_follow_presence)
    }

    private fun localIp(): String? = localIpv4()
}

/** This device's first non-loopback IPv4 address, or null when it is off-network. */
fun localIpv4(): String? = try {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
} catch (_: Exception) {
    null
}

/** Identity of everything the MQTT bridge connects with; a change means "reconnect". */
fun mqttSignature(host: String, port: Int, username: String, password: String, deviceName: String) =
    "$host:$port:$username:$password:$deviceName"
