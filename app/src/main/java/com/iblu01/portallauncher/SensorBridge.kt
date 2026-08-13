package com.iblu01.portallauncher

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlin.math.abs

class SensorBridge(
    private val context: android.content.Context,
    private val onPublish: (topic: String, payload: String, qos: Int) -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "PortalHA"
        private const val LIGHT_THROTTLE_MS = 2_000L
        private const val LIGHT_MIN_DELTA = 1.5f
        private const val TEMP_THROTTLE_MS = 30_000L
        private const val TEMP_MIN_DELTA = 0.2f
    }

    // Whether this hardware exposes an ambient temperature sensor — drives whether the
    // matching HA entity is published. Detected at start() from the sensor list.
    var hasTemperature = false
        private set

    private val sm = context.getSystemService(SensorManager::class.java)
    private val thread = HandlerThread("portal-ha-sensors").also { it.start() }
    private val handler = Handler(thread.looper)

    private var lastLightMs = 0L
    private var lastLux = Float.MIN_VALUE
    private var lastTempMs = 0L
    private var lastTemp = Float.MIN_VALUE

    @Volatile private var prefs: Prefs? = null

    fun start(prefs: Prefs) {
        this.prefs = prefs
        sm.getDefaultSensor(Sensor.TYPE_LIGHT)
            ?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler) }
        sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
            hasTemperature = true
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        Log.i(TAG, "sensors: temperature=$hasTemperature")
    }

    fun stop() {
        sm.unregisterListener(this)
        thread.quitSafely()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val p = prefs ?: return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> handleLight(event, p)
            Sensor.TYPE_AMBIENT_TEMPERATURE -> handleTemp(event, p)
        }
    }

    private fun handleTemp(event: SensorEvent, p: Prefs) {
        val c = event.values[0]
        val now = System.currentTimeMillis()
        if (now - lastTempMs < TEMP_THROTTLE_MS && abs(c - lastTemp) < TEMP_MIN_DELTA) return
        lastTempMs = now
        lastTemp = c
        onPublish(HaDiscovery.tempStateTopic(p.deviceId), "%.1f".format(c), 0)
    }

    private fun handleLight(event: SensorEvent, p: Prefs) {
        val lux = event.values[0]
        val now = System.currentTimeMillis()
        if (now - lastLightMs < LIGHT_THROTTLE_MS && abs(lux - lastLux) < LIGHT_MIN_DELTA) return
        lastLightMs = now
        lastLux = lux
        onPublish(HaDiscovery.lightStateTopic(p.deviceId), "%.1f".format(lux), 0)
    }
}
