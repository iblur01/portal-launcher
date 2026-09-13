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
    fun `pill selection preserves known rules and adds discovered enabled entities`() {
        val existing = listOf(PillRule("light.kitchen", PillKind.LIGHTS, "Kitchen", false, 7))
        val merged = mergePillSelection(
            existing,
            listOf("light.kitchen" to true, "light.hall" to true, "light.unknown" to false),
            listOf(candidate("light.hall")),
        )

        assertTrue(merged.first { it.entityId == "light.kitchen" }.enabled)
        assertEquals(7, merged.first { it.entityId == "light.kitchen" }.priorityBoost)
        assertTrue(merged.any { it.entityId == "light.hall" })
        assertFalse(merged.any { it.entityId == "light.unknown" })
    }

    @Test
    fun `configuration shell is local dense and exposes required states`() {
        val page = WebConfigPage.render("AB2C-D3EF")
        val css = WebConfigPage.asset("webconfig.css")

        assertTrue(page.contains("AB2C-D3EF"))
        assertFalse(page.contains("%TOKEN%"))
        assertFalse(page.contains("cdn.tailwindcss.com"))
        assertTrue(page.contains("id=\"loading-state\""))
        assertTrue(page.contains("id=\"offline-state\""))
        assertTrue(page.contains("id=\"request-error\""))
        assertTrue(page.contains("id=\"conflict-state\""))
        assertTrue(page.contains("Session en lecture seule"))
        assertTrue(page.contains("id=\"take-over\""))
        assertTrue(page.contains("id=\"defaults-warning\""))
        assertTrue(page.contains("id=\"device-preview\""))
        assertTrue(css.contains("font-variant-numeric: tabular-nums"))
        assertFalse(css.contains("linear-gradient"))
        assertFalse(css.contains("shadow"))
        assertFalse(css.contains("border-radius: 18px"))
    }

    @Test
    fun `client sends strict concurrency envelope on every mutation`() {
        val script = WebConfigPage.asset("config.js")

        assertTrue(script.contains("session_id: sessionId"))
        assertTrue(script.contains("expected_revision: snapshot ? snapshot.revision : -1"))
        assertTrue(script.contains("error.status === 409"))
        assertTrue(script.contains("take_over"))
        assertTrue(script.contains("launcherBody('preview_launcher')"))
        assertTrue(script.contains("command: 'revert_launcher'"))
        assertTrue(script.contains("launcherBody('save_launcher')"))
        assertTrue(script.contains("new EventSource"))
        assertTrue(script.contains("startPolling"))
        assertTrue(script.contains("var mutationQueue = Promise.resolve()"))
        assertFalse(script.contains("var previewQueue"))
        assertTrue(script.contains("preview_sequence: activePreviewSequence"))
        assertTrue(script.contains("data.editor_owned"))
        assertTrue(script.contains("data.device_ack === true"))
        assertFalse(script.contains("data.preview && data.preview.device_ack"))
        assertTrue(script.contains("el('reset-onboarding').disabled = value"))
        assertTrue(script.contains("if (!data.editor_active || data.editor_owned)"))
        assertTrue(script.contains("if (previewDirty)"))
        assertTrue(script.contains("providerDraftDirty"))
        assertTrue(script.contains("markConnected(null)"))
        assertTrue(script.contains("showFieldError"))
        assertTrue(script.contains("aria-invalid"))
        assertTrue(script.contains("return post('/api/test-"))
    }

    @Test
    fun `server rejects incomplete or malformed mutation envelopes`() {
        assertEquals(null, webCommandEnvelope(JSONObject()))
        assertEquals(null, webCommandEnvelope(JSONObject().put("session_id", "browser-123")))
        assertEquals(null, webCommandEnvelope(JSONObject().put("expected_revision", 4)))
        assertEquals(
            null,
            webCommandEnvelope(JSONObject().put("session_id", "bad session").put("expected_revision", 4)),
        )
        assertEquals(
            null,
            webCommandEnvelope(JSONObject().put("session_id", "browser-123").put("expected_revision", "4")),
        )
        assertEquals(
            null,
            webCommandEnvelope(JSONObject().put("session_id", "browser-123").put("expected_revision", 4.5)),
        )
        assertEquals(
            null,
            webCommandEnvelope(JSONObject().put("session_id", "browser-123").put("expected_revision", 4.0)),
        )
        assertEquals(
            "browser-123" to 4L,
            webCommandEnvelope(JSONObject().put("session_id", "browser-123").put("expected_revision", 4)),
        )
    }

    @Test
    fun `strict mutation fields reject coercion and missing values`() {
        assertEquals(null, strictBoolean(JSONObject(), "enabled"))
        assertEquals(null, strictBoolean(JSONObject().put("enabled", "true"), "enabled"))
        assertEquals(true, strictBoolean(JSONObject().put("enabled", true), "enabled"))
        assertEquals(null, strictInt(JSONObject().put("port", "1883"), "port"))
        assertEquals(null, strictInt(JSONObject().put("port", 1883.0), "port"))
        assertEquals(1883, strictInt(JSONObject().put("port", 1883), "port"))
        assertEquals(null, strictFloat(JSONObject().put("grid", "1.0"), "grid"))
        assertEquals(1.0f, strictFloat(JSONObject().put("grid", 1.0), "grid"))
    }

    @Test
    fun `mqtt remains independent and gemini is explicitly optional`() {
        val page = WebConfigPage.render("AB2C-D3EF")
        val script = WebConfigPage.asset("config.js")

        assertTrue(page.contains("Home Assistant n’active pas MQTT"))
        assertTrue(page.contains("Ignorer MQTT"))
        assertTrue(page.contains("Ignorer Gemini"))
        assertTrue(page.contains("L’échec de Gemini ne bloque pas"))
        assertFalse(script.contains("mqttSkipped || haSkipped"))
        assertFalse(script.contains("providerState.mqtt = providerState.ha"))
    }

    @Test
    fun `lot four shell exposes dense home catalogs and advanced optional voice controls`() {
        val page = WebConfigPage.render("AB2C-D3EF")
        val script = WebConfigPage.asset("config.js")

        assertTrue(page.contains("id=\"ha-integrations-table\""))
        assertTrue(page.contains("id=\"ha-entities-table\""))
        assertTrue(page.contains("id=\"ha-cameras-table\""))
        assertTrue(page.contains("id=\"home-grouping\""))
        assertTrue(page.contains("id=\"gemini-prompt\""))
        assertTrue(page.contains("id=\"gemini-wake-word\""))
        assertTrue(page.contains("id=\"gemini-daily-limit\""))
        assertTrue(page.contains("id=\"request-microphone\""))
        assertTrue(page.contains("id=\"home-sections-table\""))
        assertTrue(page.contains("id=\"manual-groups-table\""))
        assertTrue(page.contains("id=\"add-manual-group\""))
        assertTrue(script.contains("request('/api/ha/catalog')"))
        assertTrue(script.contains("post('/api/config/home'"))
        assertTrue(script.contains("voice_daily_limit"))
        assertTrue(script.contains("member-move"))
        assertTrue(script.contains("section.item_order"))
        assertTrue(script.contains("homeDraftDirty"))
        assertFalse(script.contains("providerState.mqtt = providerState.ha"))
    }

    @Test
    fun `access page and configuration page have no network asset dependency`() {
        val access = WebConfigPage.renderAccess(invalidCode = false)
        val config = WebConfigPage.render("AB2C-D3EF")

        assertFalse(access.contains("cdn.tailwindcss.com"))
        assertFalse(config.contains("cdn.tailwindcss.com"))
        assertFalse(access.contains("https://"))
        assertFalse(config.contains("https://"))
        assertTrue(access.contains("name=\"t\""))
        assertTrue(access.contains("aria-describedby=\"access-help\""))
        assertTrue(config.contains("Réseau local de confiance requis"))
        assertTrue(config.contains("utilise HTTP sur le réseau local"))
        assertTrue(config.contains("value=\"custom\""))
        assertTrue(config.contains("value=\"immich\""))
        assertFalse(config.contains("Image · Lot 3"))
        assertTrue(config.contains("id=\"title-system\">Accès système"))
        assertTrue(config.contains("id=\"title-clock\">Horloge"))
        assertTrue(config.contains("id=\"title-apps\">Applications"))
        assertTrue(config.contains("id=\"title-behavior\">Comportement"))
        assertTrue(config.contains("image/jpeg,image/png,image/webp"))
        assertTrue(config.contains("id=\"transport-warning\" class=\"notice warning compact\" role=\"note\""))
        assertTrue(WebConfigPage.asset("webconfig.css").contains("(min-width: 721px) and (max-width: 1050px)"))
        assertTrue(WebConfigPage.asset("webconfig.css").contains("min-height: 44px"))
    }

    @Test
    fun `english shell keeps functional labels translated`() {
        val page = WebConfigPage.render("AB2C-D3EF", "en")
        val access = WebConfigPage.renderAccess(invalidCode = true, language = "en")

        assertTrue(page.contains("lang=\"en\""))
        assertTrue(page.contains("Read-only session"))
        assertTrue(page.contains("Take over editing"))
        assertFalse(page.contains("Session en lecture seule"))
        assertFalse(page.contains("Reprendre la main"))
        assertTrue(access.contains("Remote configuration"))
        assertTrue(access.contains("content=\"true\""))
    }

    @Test
    fun `lot three client exposes strict launcher configuration routes and states`() {
        val page = WebConfigPage.render("AB2C-D3EF")
        val script = WebConfigPage.asset("config.js")

        assertTrue(page.contains("id=\"capability-loading\""))
        assertTrue(page.contains("id=\"capability-error\""))
        assertTrue(page.contains("id=\"apps-empty\""))
        assertTrue(page.contains("id=\"apps-filter-empty\""))
        assertTrue(script.contains("post('/api/system/action'"))
        assertTrue(script.contains("post('/api/background/upload'"))
        assertTrue(script.contains("post('/api/config/background'"))
        assertTrue(script.contains("post('/api/config/clock'"))
        assertTrue(script.contains("post('/api/config/apps'"))
        assertTrue(script.contains("post('/api/config/behavior'"))
        assertTrue(script.contains("post('/api/test-immich'"))
        assertFalse(script.contains("transition: all"))
    }

    @Test
    fun `browser language wins with panel language as fallback`() {
        assertEquals("en", webLanguage("en-US,en;q=0.9,fr;q=0.8", "fr"))
        assertEquals("fr", webLanguage("de-DE,de;q=0.9", "fr"))
        assertEquals("en", webLanguage(null, ""))
        assertEquals("fr", webLanguage("en-US", "en", "fr"))
    }
}
