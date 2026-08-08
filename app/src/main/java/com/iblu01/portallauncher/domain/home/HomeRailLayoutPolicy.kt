package com.iblu01.portallauncher.domain.home

/** Pure layout decision used by the Compose rail; it never shrinks touch targets below 48dp. */
object HomeRailLayoutPolicy {
    fun calculate(
        itemCount: Int,
        availableWidthDp: Float,
        availableHeightDp: Float,
        fontScale: Float,
        estimatedItemWidthDp: Float = 156f,
        minimumTouchHeightDp: Float = 48f,
    ): HomeRailLayout {
        if (itemCount <= 0) return HomeRailLayout(1, emptyList())
        val safeFontScale = fontScale.coerceAtLeast(1f)
        val estimatedVisibleColumns = (availableWidthDp / (estimatedItemWidthDp * safeFontScale))
            .toInt()
            .coerceAtLeast(1)
        val twoRowsFit = availableHeightDp >= (minimumTouchHeightDp * safeFontScale * 2f)
        val rows = if (twoRowsFit && itemCount > estimatedVisibleColumns + 1) 2 else 1
        return HomeRailLayout(
            rowCount = rows,
            // Column-major fill gives a deterministic top-to-bottom, then left-to-right order.
            placements = (0 until itemCount).map { index ->
                HomeRailPlacement(index, row = index % rows, column = index / rows)
            },
        )
    }
}
