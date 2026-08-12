package com.iblu01.portallauncher.domain.home

/**
 * How many pills a Maison section fits on one line.
 *
 * Maison scrolls vertically only: a section wraps its pills across full-width lines instead of
 * hiding them in a horizontal rail, so everything a section holds is reachable with the single
 * gesture the page already owns. The minimum width is what keeps a pill's label and value readable
 * next to its glyph; large text simply yields fewer columns — never a narrower pill.
 */
object HomeGridLayoutPolicy {
    fun columns(
        availableWidthDp: Float,
        fontScale: Float,
        minimumPillWidthDp: Float = 220f,
        maximumColumns: Int = 4,
    ): Int {
        val safeFontScale = fontScale.coerceAtLeast(1f)
        return (availableWidthDp / (minimumPillWidthDp * safeFontScale))
            .toInt()
            .coerceIn(1, maximumColumns)
    }
}
