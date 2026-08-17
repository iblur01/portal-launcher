package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebConfigServerTest {

    private fun candidate(entityId: String) = PillCandidate(
        primary = HaEntity(entityId, "on", JSONObject()),
        kind = PillKind.LIGHTS,
        label = entityId.substringAfter('.'),
        related = emptyList(),
    )

    @Test
    fun `enabling a known entity keeps its rule and flips the flag`() {
        val existing = listOf(PillRule("light.kitchen", PillKind.LIGHTS, "Kitchen", enabled = false, priorityBoost = 7))

        val merged = mergePillSelection(existing, listOf("light.kitchen" to true), emptyList())

        assertEquals(1, merged.size)
        assertTrue(merged[0].enabled)
        assertEquals(7, merged[0].priorityBoost)
    }

    @Test
    fun `enabling an unknown entity adds its discovered candidate`() {
        val merged = mergePillSelection(
            existing = emptyList(),
            selection = listOf("light.hall" to true),
            candidates = listOf(candidate("light.hall")),
        )

        assertEquals(listOf("light.hall"), merged.map { it.entityId })
        assertTrue(merged[0].enabled)
    }

    @Test
    fun `disabling an unknown entity adds nothing`() {
        val merged = mergePillSelection(emptyList(), listOf("light.hall" to false), listOf(candidate("light.hall")))

        assertTrue(merged.isEmpty())
    }

    @Test
    fun `entities absent from the selection are left untouched`() {
        val existing = listOf(PillRule("lock.front", PillKind.LOCK, "Front", enabled = true))

        val merged = mergePillSelection(existing, listOf("light.hall" to true), listOf(candidate("light.hall")))

        assertEquals(existing[0], merged.first { it.entityId == "lock.front" })
        assertEquals(2, merged.size)
    }

    @Test
    fun `the served page carries the access code and no placeholder`() {
        val page = WebConfigPage.render("AB2C-D3EF")

        assertTrue(page.contains("AB2C-D3EF"))
        assertFalse(page.contains("%TOKEN%"))
    }

    @Test
    fun `configuration page exposes three guided steps then a close-tab confirmation`() {
        val page = WebConfigPage.render("AB2C-D3EF")

        assertTrue(page.contains("data-step=\"0\""))
        assertTrue(page.contains("data-step=\"1\""))
        assertTrue(page.contains("data-step=\"2\""))
        assertFalse(page.contains("data-step=\"3\""))
        assertFalse(page.contains("id=\"load_pills\""))
        assertTrue(page.contains("id=\"saved-view\""))
        assertTrue(page.contains("Vous pouvez fermer cet onglet"))
        assertTrue(page.contains("id=\"next\""))
        assertTrue(page.contains("id=\"back\""))
        assertTrue(page.contains("id=\"ha_mdns_warning\""))
        assertTrue(page.contains("adresse IP locale du serveur"))
        assertTrue(page.contains("id=\"ha_check_host\""))
        assertTrue(page.contains("id=\"ha_check_port\""))
        assertTrue(page.contains("id=\"ha_check_token\""))
        assertTrue(page.contains("id=\"mqtt_check_host\""))
        assertTrue(page.contains("id=\"mqtt_check_port\""))
        assertTrue(page.contains("id=\"mqtt_check_auth\""))
        assertTrue(page.contains("id=\"mqtt_mdns_warning\""))
        assertTrue(page.contains("id=\"server-offline-view\""))
        assertTrue(page.contains("Le launcher n’est pas en mode configuration"))
    }

    @Test
    fun `home step tests host then port then token before continuing`() {
        val script = WebConfigPage.asset("config.js")

        val host = script.indexOf("runHaCheck('host')")
        val port = script.indexOf("runHaCheck('port')", host + 1)
        val token = script.indexOf("runHaCheck('token')", port + 1)
        val continueToMqtt = script.indexOf("showStep(1)", token + 1)

        assertTrue(script.contains("/api/test-ha"))
        assertTrue(host >= 0)
        assertTrue(port > host)
        assertTrue(token > port)
        assertTrue(continueToMqtt > token)
    }

    @Test
    fun `mqtt step tests host then port then authentication before continuing`() {
        val script = WebConfigPage.asset("config.js")

        val host = script.indexOf("runMqttCheck('host')")
        val port = script.indexOf("runMqttCheck('port')", host + 1)
        val auth = script.indexOf("runMqttCheck('auth')", port + 1)
        val continueToSummary = script.indexOf("showStep(2)", auth + 1)

        assertTrue(script.contains("/api/test-mqtt"))
        assertTrue(host >= 0)
        assertTrue(port > host)
        assertTrue(auth > port)
        assertTrue(continueToSummary > auth)
    }

    @Test
    fun `access page asks for token without exposing one`() {
        val page = WebConfigPage.renderAccess(invalidCode = false)

        assertTrue(page.contains("name=\"t\""))
        assertTrue(page.contains("cdn.tailwindcss.com"))
        assertTrue(page.contains("content=\"false\""))
        assertFalse(page.contains("%INVALID_CODE%"))
    }

    @Test
    fun `access page explains an invalid code`() {
        val page = WebConfigPage.renderAccess(invalidCode = true)

        assertTrue(page.contains("content=\"true\""))
    }
}
