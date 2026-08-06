package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Pager index of the clock; every later index is an app page. */
const val PAGE_CLOCK = 0
const val PAGE_FIRST_APP = 1

/**
 * One flat pager holds the clock and every app page: `[clock, app 0, app 1, …]`.
 *
 * Not a pager nested inside the apps page — two horizontal scrollers competing for the same drag
 * is a gesture problem with no good answer, and stock launchers treat their home screens as one
 * pager for exactly that reason.
 */
@Composable
fun rememberLauncherPagerState(appPageCount: () -> Int): PagerState =
    rememberPagerState(initialPage = PAGE_CLOCK) { PAGE_FIRST_APP + appPageCount() }

/**
 * Continuous 0f..1f progress of the clock→apps swipe. Read this instead of `currentPage` so the
 * header shrink tracks the finger frame by frame, and stays collapsed across further app pages.
 */
fun PagerState.collapseFraction(): Float =
    (currentPage + currentPageOffsetFraction).coerceIn(0f, 1f)

/**
 * Sends the pager back to the clock, from a scope that outlives the caller's effect.
 *
 * It must not be awaited inside a `LaunchedEffect` keyed on whatever triggered it. Auto-return is
 * the case that proved it: crossing the page midpoint clears the trigger, the effect's key changes,
 * the effect is cancelled — and the pager is left stranded mid-scroll, showing a half-faded app page
 * because the fade tracks the scroll position.
 */
fun returnToClockPage(scope: CoroutineScope, state: PagerState) {
    scope.launch { state.animateScrollToPage(PAGE_CLOCK) }
}

/** The app page index shown at pager index [pagerPage], or null for the clock. */
fun appPageOf(pagerPage: Int): Int? =
    (pagerPage - PAGE_FIRST_APP).takeIf { it >= 0 }

/**
 * The two-page launcher surface: the clock page and the apps grid, with the clock header pinned
 * above both.
 *
 * The header is a sibling *on top* of the pager, not a page, so it never scrolls away. That would
 * normally make the whole clock area a dead zone for the swipe, so it forwards its own horizontal
 * drags to the pager ([pagerDragForward]) and reports a height that shrinks with the clock
 * ([collapsingHeight]) — otherwise the invisible full-size header would eat taps meant for the
 * first row of app icons.
 */
@Composable
fun LauncherPager(
    state: PagerState,
    userScrollEnabled: Boolean,
    header: @Composable (collapse: () -> Float) -> Unit,
    clockPage: @Composable () -> Unit,
    appPage: @Composable (page: Int, appear: () -> Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Launcher chrome pinned to the top bar next to the clock; fades in with the swipe. */
    headerActions: @Composable (collapse: () -> Float) -> Unit = {},
    /** Drawn above every page so an icon drag can cross them. */
    dragOverlay: @Composable () -> Unit = {},
    /** The drag in flight, if any. Drives the edge-held page flip. */
    drag: GridDragState? = null,
    /** Tap on the (expanded) clock — same contract as before the pager: opens Home Assistant. */
    onHeaderTap: () -> Unit = {},
    /** Long-press on the (expanded) clock — the quick-actions menu. */
    onHeaderLongPress: () -> Unit = {},
) {
    // Keep the rapidly changing pager offset out of composition. Consumers invoke this only from
    // layout or draw/layer blocks, which Compose can invalidate without rebuilding the subtree.
    val collapse = remember(state) { { state.collapseFraction() } }
    // This derived boolean changes only twice per complete swipe. It prevents the transparent
    // actions layer from intercepting clock taps without subscribing composition to every offset.
    val showHeaderActions by remember(state) {
        derivedStateOf { state.collapseFraction() > 0.01f }
    }
    val edgePx = with(LocalDensity.current) { EDGE_FLIP_WIDTH.toPx() }

    // Holding a dragged icon against a page edge flips pages, which is the only way to move an app
    // to another page — and, on the trailing empty page, to create one. User scrolling is off while
    // dragging, so this is the sole page change during a drag.
    val dragging = drag?.isDragging == true
    LaunchedEffect(dragging, state) {
        if (drag == null || !dragging) return@LaunchedEffect
        snapshotFlow { drag.edgeDirection(edgePx) }
            .distinctUntilChanged()
            .collectLatest { direction ->
                if (direction == 0) return@collectLatest
                while (true) {
                    delay(EDGE_FLIP_DWELL_MS)
                    val target = (state.currentPage + direction)
                        .coerceIn(PAGE_FIRST_APP, (state.pageCount - 1).coerceAtLeast(PAGE_FIRST_APP))
                    if (target == state.currentPage) break
                    state.animateScrollToPage(target)
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The pager's bounds are what the edge-held page flip measures against.
            .onGloballyPositioned { drag?.viewportRect = Rect(it.positionInRoot(), it.size.toSize()) }
    ) {
        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = userScrollEnabled,
            // Keep neighbours composed so the first swipe reveals content, not a blank page. While
            // an icon is in hand, keep *every* page composed: the drag's gesture belongs to the page
            // it started on, and disposing that page would drop the icon in mid-air.
            beyondViewportPageCount = if (dragging) state.pageCount else 1,
        ) { page ->
            val appPageIndex = appPageOf(page)
            // The fade is passed as a lambda so each page reads it in its own draw phase: as a
            // value it would recompose every page on every frame of the swipe.
            if (appPageIndex == null) clockPage() else appPage(appPageIndex) { state.collapseFraction() }
        }

        dragOverlay()

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Both pointer nodes sit OUTSIDE collapsingHeight on purpose. A pointer modifier
                // placed after it would wrap the content, which is still measured at full height —
                // and Compose does not clip hit-testing to the parent's bounds, so the invisible
                // full-size clock band would swallow taps on the first rows of app icons.
                .pagerDragForward(state, enabled = userScrollEnabled)
                // Tap detection never consumes drags, so the drag forwarder above still wins past
                // the touch slop. The guard is read at gesture time (not via the modifier chain) so
                // nothing is swapped mid-drag.
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (state.collapseFraction() < 0.5f) onHeaderTap() },
                        onLongPress = { if (state.collapseFraction() < 0.5f) onHeaderLongPress() },
                    )
                }
                .collapsingHeight { headerHeight ->
                    (headerHeight * headerScale(collapse())).roundToInt()
                },
            // The wrapper fills the width (so the whole clock band forwards drags), so the clock
            // itself has to be centred here — it wraps its own width.
            contentAlignment = Alignment.TopCenter,
        ) {
            header(collapse)
        }

        // Top-right chrome. Gated on the collapse so the idle clock screen stays bare: these are
        // launcher controls, and they appear as soon as the swipe starts. Removing them mid-swipe is
        // safe — they are tap targets, so no gesture is ever in flight on them.
        if (showHeaderActions) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .graphicsLayer { alpha = collapse() },
            ) {
                headerActions(collapse)
            }
        }

        PageDots(
            progress = { state.currentPage + state.currentPageOffsetFraction },
            count = state.pageCount,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
    }
}

/** Mirrors the header's `graphicsLayer` scale so its hit area matches what is actually drawn. */
private fun headerScale(collapse: Float): Float = 1f - (1f - 0.34f) * collapse

/**
 * Measures the content unbounded but reports [height] of the measured height, so a
 * `graphicsLayer`-scaled child keeps its real measurement (no squeezed text) while the node's
 * touch target follows the visible size.
 */
private fun Modifier.collapsingHeight(height: (Int) -> Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
    )
    layout(placeable.width, height(placeable.height).coerceAtLeast(0)) {
        placeable.place(0, 0)
    }
}

/**
 * Forwards horizontal drags on this node to [state], so an element drawn above the pager still
 * swipes the pages 1:1 with the finger. Settles to the nearest page on release, honouring flings.
 *
 * `dispatchRawDelta` (not `scrollBy`) on purpose: it is synchronous, so a drag costs no coroutine
 * per frame — this runs on a Portal (API 28).
 */
@Composable
fun Modifier.pagerDragForward(state: PagerState, enabled: Boolean): Modifier {
    var dragged by remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragged += delta
        state.dispatchRawDelta(-delta)
    }
    return this.draggable(
        state = draggableState,
        orientation = Orientation.Horizontal,
        enabled = enabled,
        onDragStarted = { dragged = 0f },
        onDragStopped = { velocity ->
            val pageWidth = state.layoutInfo.pageSize.takeIf { it > 0 } ?: 1
            val threshold = pageWidth * 0.25f
            val from = state.settledPage
            val target = when {
                dragged < -threshold || velocity < -FLING_VELOCITY -> from + 1
                dragged > threshold || velocity > FLING_VELOCITY -> from - 1
                else -> from
            }
            dragged = 0f
            state.animateScrollToPage(target.coerceIn(0, state.pageCount - 1))
        },
    )
}

private const val FLING_VELOCITY = 600f

/** How close to a page edge a dragged icon must be held, and for how long, to flip the page. */
private val EDGE_FLIP_WIDTH = 44.dp
private const val EDGE_FLIP_DWELL_MS = 450L

/** Two small dots; the active one slides continuously with [progress]. */
@Composable
private fun PageDots(progress: () -> Float, count: Int, modifier: Modifier = Modifier) {
    val dotWidth = 12.dp
    val dotHeight = 6.dp
    val spacing = 6.dp
    Canvas(modifier.size(width = dotWidth * count + spacing * (count - 1).coerceAtLeast(0), height = dotHeight)) {
        val dotWidthPx = dotWidth.toPx()
        val minWidthPx = dotHeight.toPx()
        val spacingPx = spacing.toPx()
        val radius = size.height / 2f
        val currentProgress = progress()
        repeat(count) { index ->
            // Reserve the maximum width for every dot, then animate only pixels inside the Canvas.
            // The pager offset therefore invalidates draw, never composition or measurement.
            val weight = (1f - kotlin.math.abs(currentProgress - index)).coerceIn(0f, 1f)
            val width = minWidthPx + (dotWidthPx - minWidthPx) * weight
            val slotLeft = index * (dotWidthPx + spacingPx)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.25f + 0.55f * weight),
                topLeft = androidx.compose.ui.geometry.Offset(slotLeft + (dotWidthPx - width) / 2f, 0f),
                size = androidx.compose.ui.geometry.Size(width, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        }
    }
}
