package com.iblu01.portallauncher.ui.icons

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.iblu01.portallauncher.HaEntity

/**
 * Covers the three pieces that can silently produce the wrong icon: the binary search over the
 * bundled MDI index, the line scanner for third-party icon-set modules, and HA's own resolution
 * order. The pack samples are verbatim lines from `custom-brand-icons.js` and `hass-hue-icons.js`.
 */
@RunWith(RobolectricTestRunner::class)
class HaIconsTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun glyph(name: String) = MdiCodepoints.glyph(context, name)

    private fun codepoint(name: String) = glyph(name)?.codePointAt(0)

    @Test
    fun `mdi index resolves names across the whole table`() {
        // First record, a mid-table name, and one from the tail of the codepoint range.
        assertEquals(0xF01C9, codepoint("ab-testing"))
        assertEquals(0xF04C3, codepoint("speaker"))
        assertEquals(0xF0335, codepoint("lightbulb"))
        assertEquals(0xF0004, codepoint("account"))
        assertEquals(0xF0A88, codepoint("zodiac-virgo"))
        assertEquals(0xF16E0, codepoint("abacus"))
    }

    @Test
    fun `mdi index reports misses rather than a neighbouring icon`() {
        // "sonos" is famously *not* in MDI — it only exists in custom packs, which is the whole
        // reason this feature has a pack store. A binary search that returned the nearest record
        // would silently draw the wrong glyph here.
        assertNull(glyph("sonos"))
        assertNull(glyph("speake"))
        assertNull(glyph("speakerz"))
        assertNull(glyph(""))
        assertNull(glyph("a".repeat(200)))
    }

    @Test
    fun `parser reads the custom-brand-icons array form`() {
        val line = """  "sonos-arc":[0,0,24,24,"m4.093 4.764-.026.074.036.044.027-.074Z"],"""
        val wanted = mutableSetOf("sonos-arc")
        val found = mutableListOf<PackIcon>()
        val count = IconPackParser.extract(sequenceOf(line), wanted) { _, w, h, p -> found += PackIcon(w, h, p) }

        assertEquals(1, count)
        assertTrue(wanted.isEmpty())
        assertEquals(24f, found.single().width, 0f)
        assertTrue(found.single().path.startsWith("m4.093"))
    }

    @Test
    fun `parser reads the hass-hue-icons object form across lines`() {
        val lines = sequenceOf(
            "const HUE_ICONS_MAP = {",
            """  "adore":{""",
            """    path:"M21.6,8.8H2.4C1.6,8.8,1,9.7,1,10.7Z", """,
            """    keywords: ["bathroom","light","wall"]""",
            "  },",
        )
        val wanted = mutableSetOf("adore")
        var path: String? = null
        val count = IconPackParser.extract(lines, wanted) { _, _, _, p -> path = p }

        assertEquals(1, count)
        assertEquals("M21.6,8.8H2.4C1.6,8.8,1,9.7,1,10.7Z", path)
    }

    @Test
    fun `parser ignores icons nobody asked for and leaves the rest pending`() {
        val lines = sequenceOf(
            """  "sonos-beam":[0,0,24,24,"M1 1Z"],""",
            """  "sonos-arc":[0,0,24,24,"M2 2Z"],""",
        )
        val wanted = mutableSetOf("sonos-arc", "never-shipped")
        val names = mutableListOf<String>()
        val count = IconPackParser.extract(lines, wanted) { n, _, _, _ -> names += n }

        assertEquals(1, count)
        assertEquals(listOf("sonos-arc"), names)
        // Still pending, so the store can cache it as a miss rather than re-download the module.
        assertEquals(setOf("never-shipped"), wanted)
    }

    @Test
    fun `resolver prefers the entity icon then falls back to HA component defaults`() {
        val resolver = HaIconResolver()
        resolver.componentIcons = JSONObject(
            """
            {
              "cover": {
                "_": { "default": "mdi:window-open", "state": { "closed": "mdi:window-closed" } },
                "blind": { "default": "mdi:blinds-horizontal" }
              }
            }
            """.trimIndent()
        )

        val customised = HaEntity("media_player.living", "playing", JSONObject("""{"icon":"phu:sonos-arc"}"""))
        assertEquals(IconRef("phu", "sonos-arc"), resolver.refFor(customised))

        val openCover = HaEntity("cover.hall", "open", JSONObject("{}"))
        assertEquals(IconRef("mdi", "window-open"), resolver.refFor(openCover))

        val closedCover = HaEntity("cover.hall", "closed", JSONObject("{}"))
        assertEquals(IconRef("mdi", "window-closed"), resolver.refFor(closedCover))

        // Device class wins over the domain default, and inherits nothing from it.
        val blind = HaEntity("cover.bedroom", "closed", JSONObject("""{"device_class":"blind"}"""))
        assertEquals(IconRef("mdi", "blinds-horizontal"), resolver.refFor(blind))

        assertNull(resolver.refFor(HaEntity("light.desk", "on", JSONObject("{}"))))
    }

    @Test
    fun `icon references reject anything unusable as a namespace or cache filename`() {
        assertEquals(IconRef("mdi", "sonos"), IconRef.parse("MDI:Sonos"))
        assertNull(IconRef.parse(null))
        assertNull(IconRef.parse("lightbulb"))
        assertNull(IconRef.parse("mdi:"))
        assertNull(IconRef.parse(":lightbulb"))
        assertNull(IconRef.parse("mdi:../../etc/passwd"))
        assertNull(IconRef.parse("../x:y"))
    }
}
