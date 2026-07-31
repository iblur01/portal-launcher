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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MqttBridgeService : Service() {
    companion object {
        private const val TAG = "PortalLauncher"
        private const val CHANNEL = "portal_launcher_bridge"
        private const val NOTIF_ID = 1
        private const val ACTION_RECONNECT = "com.iblu01.portallauncher.action.RECONNECT"

        fun start(context: Context) {
            val intent = Intent(context, MqttBridgeService::class.java)
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
            val intent = Intent(context, MqttBridgeService::class.java).setAction(ACTION_RECONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }
    }

    private val running = AtomicBoolean(false)
    private val commands = Executors.newSingleThreadExecutor { r ->
        Thread(r, "portal-launcher-cmd").also { it.isDaemon = true }
    }
    @Volatile private var mqtt: MqttClient? = null
    private lateinit var prefs: Prefs
    private var sensorBridge: SensorBridge? = null
    private var soundMonitor: SoundMonitor? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var audioReceiver: BroadcastReceiver? = null
    private val deviceStateListener = DeviceStateHub.Listener { state -> publishDeviceState(state) }
    @Volatile private var lastVolumePercent = -1
    @Volatile private var lastVolumeMuted = false
    @Volatile private var lastBrightnessPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        prefs = Prefs(this)
        startForeground(NOTIF_ID, notification(getString(R.string.app_name)))

        DeviceStateHub.init(this)
        ScreenControl.enableAccessibility(this)
        sensorBridge = SensorBridge(this, ::publishRaw).also { it.start(prefs) }
        soundMonitor = SoundMonitor(this) { level ->
            publishRaw(HaDiscovery.soundStateTopic(prefs.deviceId), level.toString(), 0)
        }.also { it.start() }

        registerScreenReceiver()
        registerAudioReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT) {
            Log.i(TAG, "Reconnect requested (broker config changed)")
            Thread({
                com.iblu01.portallauncher.ui.ConnectionStatus.connected = false
                runCatching { mqtt?.disconnect(0) }
                mqtt = null
            }, "portal-launcher-reconnect").also { it.isDaemon = true }.start()
        }
        if (running.compareAndSet(false, true)) {
            Thread(::mqttLoop, "portal-launcher-mqtt").also { it.isDaemon = true }.start()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        commands.shutdownNow()
        runCatching { mqtt?.disconnect(0) }
        sensorBridge?.stop()
        soundMonitor?.stop()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        audioReceiver?.let { runCatching { unregisterReceiver(it) } }
        DeviceStateHub.removeListener(deviceStateListener)
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
        com.iblu01.portallauncher.ui.ConnectionStatus.connected = true
        Log.i(TAG, "MQTT connected to ${p.brokerUri}")

        HaDiscovery.commandTopics(p.deviceId).forEach { client.publish(it, emptyRetained()) }
        HaDiscovery.commandTopics(p.deviceId).forEach { client.subscribe(it, 1) }
        HaDiscovery.staleTopics(p.deviceId).forEach { client.publish(it, emptyRetained()) }

        publishDiscovery(client, p)
        publishInitialStates(p)
        DeviceStateHub.addListener(deviceStateListener)
        updateNotification("Connected - ${p.brokerHost}")

        try {
            while (running.get() && client.isConnected) {
                Thread.sleep(5_000)
                pollChangedStates(p)
                publishDeviceState(DeviceStateHub.current)
            }
        } finally {
            com.iblu01.portallauncher.ui.ConnectionStatus.connected = false
            DeviceStateHub.removeListener(deviceStateListener)
            mqtt = null
            runCatching { client.disconnect(0) }
        }
    }

    private fun publishDiscovery(client: MqttClient, p: Prefs) {
        fun pub(topic: String, payload: String) = client.publish(topic, retained(payload))

        pub(HaDiscovery.screenDiscoveryTopic(p.deviceId), HaDiscovery.screenConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenModeDiscoveryTopic(p.deviceId), HaDiscovery.screenModeConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.presenceDiscoveryTopic(p.deviceId), HaDiscovery.presenceConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.ipDiscoveryTopic(p.deviceId), HaDiscovery.ipConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.lightDiscoveryTopic(p.deviceId), HaDiscovery.lightConfigPayload(p.deviceId, p.deviceName))
        if (sensorBridge?.hasTemperature == true) {
            pub(HaDiscovery.tempDiscoveryTopic(p.deviceId), HaDiscovery.tempConfigPayload(p.deviceId, p.deviceName))
        } else {
            client.publish(HaDiscovery.tempDiscoveryTopic(p.deviceId), emptyRetained())
        }
        pub(HaDiscovery.soundDiscoveryTopic(p.deviceId), HaDiscovery.soundConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.micMuteDiscoveryTopic(p.deviceId), HaDiscovery.micMuteConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.volumeDiscoveryTopic(p.deviceId), HaDiscovery.volumeConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.volumeMuteDiscoveryTopic(p.deviceId), HaDiscovery.volumeMuteConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.doorbellDiscoveryTopic(p.deviceId), HaDiscovery.doorbellConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.alertDiscoveryTopic(p.deviceId), HaDiscovery.alertConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.brightnessDiscoveryTopic(p.deviceId), HaDiscovery.brightnessConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenTimeoutDiscoveryTopic(p.deviceId), HaDiscovery.screenTimeoutConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.screenTimeoutMinutesDiscoveryTopic(p.deviceId), HaDiscovery.screenTimeoutMinutesConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.powerModeDiscoveryTopic(p.deviceId), HaDiscovery.powerModeConfigPayload(p.deviceId, p.deviceName))
        pub(HaDiscovery.photoStatusDiscoveryTopic(p.deviceId), HaDiscovery.photoStatusConfigPayload(p.deviceId, p.deviceName))
    }

    private fun publishInitialStates(p: Prefs) {
        val interactive = getSystemService(PowerManager::class.java).isInteractive
        publishRaw(HaDiscovery.screenStateTopic(p.deviceId), if (interactive) "ON" else "OFF", 1, retained = true)
        publishDeviceState(DeviceStateHub.current)
        publishRaw(HaDiscovery.ipStateTopic(p.deviceId), localIp() ?: "unknown", 1, retained = true)
        publishMicState(p)
        publishVolumeState(p)
        publishVolumeMuteState(p)
        publishBrightnessState(p)
        publishPowerState(p)
        publishPhotoStatus(p)
    }

    private fun pollChangedStates(p: Prefs) {
        val vol = currentVolumePercent()
        if (vol != lastVolumePercent) publishVolumeState(p)
        val muted = getSystemService(AudioManager::class.java).isStreamMute(AudioManager.STREAM_MUSIC)
        if (muted != lastVolumeMuted) publishVolumeMuteState(p)
        val bright = currentBrightnessPercent()
        if (bright != lastBrightnessPercent) publishBrightnessState(p)
        publishPhotoStatus(p)
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
            HaDiscovery.screenCommandTopic(p.deviceId) -> when (payload.uppercase()) {
                "ON" -> ScreenControl.wake(this)
                "OFF" -> ScreenControl.sleep(this)
            }
            HaDiscovery.micMuteCommandTopic(p.deviceId) -> {
                val muted = payload.uppercase() == "ON"
                getSystemService(AudioManager::class.java).setMicrophoneMute(muted)
                publishMicState(p)
                toast(if (muted) "Microphone muted" else "Microphone unmuted")
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
            HaDiscovery.notificationCommandTopic(p.deviceId) -> {
                if (payload.isNotEmpty()) {
                    TonePlayer.play("alert")
                    AlertOverlayState.showAlert(payload)
                }
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
                    AudioManager.ACTION_MICROPHONE_MUTE_CHANGED -> publishMicState(prefs)
                    "android.media.VOLUME_CHANGED_ACTION" -> publishVolumeState(prefs)
                    "android.media.STREAM_MUTE_CHANGED_ACTION" -> publishVolumeMuteState(prefs)
                }
            }
        }
        registerReceiver(audioReceiver, IntentFilter().apply {
            addAction(AudioManager.ACTION_MICROPHONE_MUTE_CHANGED)
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        })
    }

    private fun publishMicState(p: Prefs) {
        val muted = getSystemService(AudioManager::class.java).isMicrophoneMute
        publishRaw(HaDiscovery.micMuteStateTopic(p.deviceId), if (muted) "ON" else "OFF", 1, retained = true)
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

    private fun publishDeviceState(state: DeviceState) {
        val p = prefs
        publishRaw(HaDiscovery.screenModeStateTopic(p.deviceId), state.display.name.lowercase(), 1, retained = true)
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

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces()
            .asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
}
