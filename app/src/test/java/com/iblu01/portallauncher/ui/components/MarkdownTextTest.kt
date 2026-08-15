package com.iblu01.portallauncher.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextTest {
    @Test fun `inline markdown markers are resolved`() {
        val parsed = parseInlineMarkdown("**Rapide**, `state_changed`, *stable* et [notes](https://example.com)")

        assertEquals("Rapide, state_changed, stable et notes", parsed.text)
        assertTrue(parsed.spanStyles.size >= 4)
        assertEquals("https://example.com", parsed.getStringAnnotations("URL", 0, parsed.length).single().item)
    }
}
