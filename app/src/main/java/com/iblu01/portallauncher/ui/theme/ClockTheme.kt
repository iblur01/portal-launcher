package com.iblu01.portallauncher.ui.theme

import androidx.compose.ui.graphics.Color

/** The bundled clock typefaces (all variable-`wght` .ttf in res/font). */
enum class ClockFont(val key: String, val label: String) {
    SPACE_GROTESK("space_grotesk", "Space Grotesk"),
    OSWALD("oswald", "Oswald"),
    PLAYFAIR("playfair", "Playfair Display"),
    MONTSERRAT("montserrat", "Montserrat"),
    JETBRAINS_MONO("jetbrains_mono", "JetBrains Mono"),
    ORBITRON("orbitron", "Orbitron"),
    TEKO("teko", "Teko"),
    ROBOTO_SLAB("roboto_slab", "Roboto Slab"),
    EXO2("exo2", "Exo 2"),
    INTER("inter", "Inter");

    companion object {
        fun fromKey(key: String): ClockFont = entries.firstOrNull { it.key == key } ?: SPACE_GROTESK
    }
}

/** Fixed palette of clock text tints. */
enum class ClockTint(val key: String, val label: String, val color: Color) {
    WHITE("white", "Blanc", Color(0xFFFFFFFF)),
    AMBER("amber", "Ambre", Color(0xFFFFB340)),
    MINT("mint", "Vert", Color(0xFF30D158)),
    BLUE("blue", "Bleu", Color(0xFF0A84FF)),
    PINK("pink", "Rose", Color(0xFFFF7EB3)),
    VIOLET("violet", "Violet", Color(0xFFB388FF));

    companion object {
        fun fromKey(key: String): ClockTint = entries.firstOrNull { it.key == key } ?: WHITE
    }
}

/**
 * The full clock styling. Defaults reproduce the launcher's current clock (Space Grotesk Black,
 * 138sp, white, 24h) so a defaulted [ClockTheme] leaves the historical look untouched.
 *
 * Weight/size/letter-spacing drive the time; the date follows only font + tint (see ClockScreen).
 */
data class ClockTheme(
    val font: ClockFont = ClockFont.SPACE_GROTESK,
    val weight: Int = 900,
    val size: Float = 138f,
    val letterSpacing: Float = 0f,
    val tint: ClockTint = ClockTint.WHITE,
    val format24h: Boolean = true,
    /** Multiplier on the vertical gaps between date/time/weather-pill rows (see ClockHeader). */
    val elementSpacing: Float = 1f,
) {
    companion object {
        val WeightRange = 100f..900f
        val SizeRange = 90f..180f
        val LetterSpacingRange = -2f..12f
        val ElementSpacingRange = 0.4f..2f
    }
}
