package com.iblu01.portallauncher.domain.home

/**
 * Pure responsive policy for the exceptional fourth primary alert slot. It adapts before pills
 * are compressed and never assumes a localized label width.
 */
object HomeCriticalCapacityPolicy {
    fun capacity(
        availableWidthDp: Float,
        fontScale: Float,
        estimatedPillWidthDp: Float = 154f,
        horizontalPaddingDp: Float = 24f,
        gapDp: Float = 10f,
    ): HomeCapacity {
        val scale = fontScale.coerceAtLeast(1f)
        val fourPillsWidth = horizontalPaddingDp + estimatedPillWidthDp * scale * 4f + gapDp * 3f
        return HomeCapacity(extraCriticalPrimarySlots = if (availableWidthDp >= fourPillsWidth) 1 else 0)
    }
}
