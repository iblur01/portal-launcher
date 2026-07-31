package com.iblu01.portallauncher.session

import org.eclipse.paho.client.mqttv3.MqttMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMqttContractTest {

    @Test fun `command clear message is retained with QoS 1 and empty payload`() {
        val msg = SessionMqttContract.commandClearMessage()
        assertEquals(1, msg.qos)
        assertTrue(msg.isRetained)
        assertEquals(0, msg.payload.size)
    }

    @Test fun `event message is non retained with QoS 1`() {
        val payload = """{"lifecycle":"active"}"""
        val msg = SessionMqttContract.eventMessage(payload)
        assertEquals(1, msg.qos)
        assertFalse(msg.isRetained)
        assertArrayEquals(payload.toByteArray(), msg.payload)
    }

    @Test fun `state message is retained with QoS 1`() {
        val payload = """{"lifecycle":"completed"}"""
        val msg = SessionMqttContract.stateMessage(payload)
        assertEquals(1, msg.qos)
        assertTrue(msg.isRetained)
        assertArrayEquals(payload.toByteArray(), msg.payload)
    }

    @Test fun `clear message has zero length payload`() {
        val msg = SessionMqttContract.commandClearMessage()
        assertEquals(0, MqttMessage(msg.payload).payload.size)
    }
}
