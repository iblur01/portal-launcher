package com.iblu01.portallauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.StringReader

/**
 * Appfilter parsing. Icon packs are third-party APKs written by hand over fifteen years: the parser
 * has to survive whatever is in them, because the alternative is an app list that fails to load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IconPackAppfilterTest {

    private fun parse(xml: String): Map<String, String> {
        // android.util.Xml, not XmlPullParserFactory: the factory needs an implementation on the
        // classpath, which only exists inside Android — the app itself parses the asset the same way.
        val parser = android.util.Xml.newPullParser()
        parser.setInput(StringReader(xml))
        return parseAppfilter(parser)
    }

    @Test
    fun `component to drawable pairs are read`() {
        val mapping = parse(
            """
            <resources>
                <item component="ComponentInfo{com.a/com.a.Main}" drawable="alpha"/>
                <item component="ComponentInfo{com.b/com.b.Main}" drawable="beta"/>
            </resources>
            """.trimIndent()
        )

        assertEquals("alpha", mapping[IconPack.componentKey("com.a", "com.a.Main")])
        assertEquals("beta", mapping["ComponentInfo{com.b/com.b.Main}"])
    }

    @Test
    fun `entries that map nothing are skipped rather than stored empty`() {
        val mapping = parse(
            """
            <resources>
                <iconback img1="back"/>
                <iconmask img1="mask"/>
                <scale factor="0.8"/>
                <item component="ComponentInfo{com.a/com.a.Main}"/>
                <item drawable="orphan"/>
                <item component="ComponentInfo{com.b/com.b.Main}" drawable="beta"/>
            </resources>
            """.trimIndent()
        )

        assertEquals(1, mapping.size)
        assertEquals("beta", mapping["ComponentInfo{com.b/com.b.Main}"])
    }

    @Test
    fun `a component listed twice keeps the first drawable`() {
        val mapping = parse(
            """
            <resources>
                <item component="ComponentInfo{com.a/com.a.Main}" drawable="first"/>
                <item component="ComponentInfo{com.a/com.a.Main}" drawable="second"/>
            </resources>
            """.trimIndent()
        )

        assertEquals("first", mapping["ComponentInfo{com.a/com.a.Main}"])
    }

    @Test
    fun `an app the pack does not theme has no entry, so it keeps its own icon`() {
        val mapping = parse("""<resources><item component="ComponentInfo{com.a/com.a.Main}" drawable="alpha"/></resources>""")

        assertNull(mapping[IconPack.componentKey("com.other", "com.other.Main")])
    }
}
