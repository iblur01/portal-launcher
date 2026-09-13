package com.iblu01.portallauncher

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression guard for the mixed-language bug: a string added to one language only shows up in the
 * other as an English fallback in an otherwise French interface. Comparing the two resource files
 * catches it at build time rather than on a user's wall panel.
 */
class StringParityTest {
    private val names = Regex("""<(?:string|plurals) name="([^"]+)"""")

    /** `app_name` is intentionally single-language: a launcher is not renamed per locale. */
    private val intentionallyEnglishOnly = setOf("app_name")

    private fun keysOf(path: String): Set<String> {
        val file = File(path)
        check(file.exists()) { "missing resource file: $path" }
        return names.findAll(file.readText()).map { it.groupValues[1] }.toSet()
    }

    @Test fun `every user-visible string exists in both French and English`() {
        val english = keysOf("src/main/res/values/strings.xml") - intentionallyEnglishOnly
        val french = keysOf("src/main/res/values-fr/strings.xml") - intentionallyEnglishOnly

        assertEquals("strings missing from values-fr", emptySet<String>(), english - french)
        assertEquals("strings missing from values", emptySet<String>(), french - english)
    }
}
