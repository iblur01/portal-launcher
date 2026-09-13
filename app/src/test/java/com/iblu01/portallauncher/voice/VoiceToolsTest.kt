package com.iblu01.portallauncher.voice

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceToolsTest {

    @Test
    fun `target slots are forwarded and the intent's own slots are merged in`() {
        val slots = intentSlots(
            JSONObject()
                .put("intent", "HassLightSet")
                .put("name", "plafond cuisine")
                .put("area", "cuisine")
                .put("extras", JSONObject().put("brightness", 40)),
        )
        assertEquals(
            mapOf<String, Any>("name" to "plafond cuisine", "area" to "cuisine", "brightness" to 40),
            slots,
        )
    }

    /** The model sends `extras` as a JSON string about as often as as an object. */
    @Test
    fun `extras are accepted as a json string`() {
        val slots = intentSlots(
            JSONObject().put("name", "salon").put("extras", """{"temperature":20.5}"""),
        )
        assertEquals("salon", slots["name"])
        // The number's boxed type is org.json's business (Double here, BigDecimal in other
        // builds); what matters is that it reaches Home Assistant as a number, not as a string.
        assertEquals(20.5, (slots["temperature"] as Number).toDouble(), 1e-9)
    }

    /**
     * An empty name is worse than no name: Home Assistant matches it against nothing and answers
     * that it found no device, so the assistant sounds broken instead of asking again.
     */
    @Test
    fun `blank and missing slots are dropped rather than sent empty`() {
        val slots = intentSlots(
            JSONObject().put("name", "").put("area", "  ").put("domain", "light"),
        )
        assertEquals(mapOf<String, Any>("domain" to "light"), slots)
    }

    @Test
    fun `unparseable extras are ignored instead of failing the call`() {
        val slots = intentSlots(JSONObject().put("name", "salon").put("extras", "not json"))
        assertEquals(mapOf<String, Any>("name" to "salon"), slots)
    }
}
