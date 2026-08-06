package com.iblu01.portallauncher.ui.onboarding

import com.iblu01.portallauncher.ui.apps.GridSpec
import com.iblu01.portallauncher.ui.apps.gridSpecFor
import kotlin.math.abs

/**
 * The three densities offered instead of an abstract percentage.
 *
 * A preset is only a [scale] — the launcher derives its grid from the page size at runtime
 * ([gridSpecFor]), so nothing here stores a column/row count. The counts shown under each preset are
 * computed for the current screen by [specForScale].
 */
enum class GridPreset(val scale: Float) {
    LARGE_ICONS(1.25f),
    BALANCED(1f),
    MORE_APPS(0.78f);

    companion object {
        /** The preset a stored scale corresponds to, or null once the slider has been touched. */
        fun forScale(scale: Float, tolerance: Float = 0.02f): GridPreset? =
            values().firstOrNull { abs(it.scale - scale) <= tolerance }
    }
}

/** Cell size the launcher uses at scale 1, mirroring [gridSpecFor]'s defaults. */
private const val BASE_CELL_WIDTH_DP = 112f
private const val BASE_CELL_HEIGHT_DP = 116f

/**
 * The grid a page of [widthDp] x [heightDp] would hold at [scale] — the same arithmetic the real
 * launcher does, so the "5 x 3" shown during onboarding is what the user actually gets.
 */
fun specForScale(widthDp: Float, heightDp: Float, scale: Float): GridSpec {
    val safe = scale.coerceIn(0.7f, 1.3f)
    return gridSpecFor(
        widthDp = widthDp,
        heightDp = heightDp,
        cellWidthDp = BASE_CELL_WIDTH_DP * safe,
        cellHeightDp = BASE_CELL_HEIGHT_DP * safe,
    )
}
