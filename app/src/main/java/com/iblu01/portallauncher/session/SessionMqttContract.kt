package com.iblu01.portallauncher.session

import org.eclipse.paho.client.mqttv3.MqttMessage

/**
 * MQTT envelope rules for the app-session topics.
 *
 * - command topic: QoS 1, non-retained for incoming commands. The broker clear-message is retained so
 *   a stale retained command cannot be replayed after a reconnect.
 * - event topic: QoS 1, never retained (transient events).
 * - state topic: QoS 1, always retained (last-known state).
 */
object SessionMqttContract {
    const val COMMAND_QOS = 1
    const val EVENT_QOS = 1
    const val STATE_QOS = 1

    fun commandClearMessage(): MqttMessage = MqttMessage(ByteArray(0)).apply {
        qos = COMMAND_QOS
        isRetained = true
    }

    fun eventMessage(payload: String): MqttMessage = MqttMessage(payload.toByteArray()).apply {
        qos = EVENT_QOS
        isRetained = false
    }

    fun stateMessage(payload: String): MqttMessage = MqttMessage(payload.toByteArray()).apply {
        qos = STATE_QOS
        isRetained = true
    }
}
