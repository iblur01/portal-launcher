package com.iblu01.portallauncher.session

import com.iblu01.portallauncher.HaDiscovery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDiscoveryContractTest {
    @Test fun `session command is subscribed but enabled status is read only`() {
        val deviceId = "portal-test"
        val topics = HaDiscovery.commandTopics(deviceId)
        assertTrue(topics.contains(HaDiscovery.sessionCommandTopic(deviceId)))
        assertFalse(topics.any { it.contains("session/enabled") || it.contains("kill_switch") })
    }

    @Test fun `enabled discovery payload has no command topic`() {
        val payload = HaDiscovery.sessionEnabledConfigPayload("portal-test", "Portal")
        assertFalse(payload.contains("binary_sensor")) // topic owns the component type
        assertTrue(payload.contains("state_topic"))
        assertFalse(payload.contains("command_topic"))
    }

    @Test fun `session diagnostic is read only and expires after heartbeat loss`() {
        val payload = HaDiscovery.sessionConfigPayload("portal-test", "Portal")
        assertFalse(payload.contains("command_topic"))
        assertTrue(payload.contains("\"expire_after\":15"))
        assertEquals("portal/portal-test/session/state", HaDiscovery.sessionStateTopic("portal-test"))
    }

    @Test fun `enabled discovery uses binary sensor component`() {
        assertTrue(HaDiscovery.sessionEnabledDiscoveryTopic("portal-test").startsWith("homeassistant/binary_sensor/"))
    }
}
