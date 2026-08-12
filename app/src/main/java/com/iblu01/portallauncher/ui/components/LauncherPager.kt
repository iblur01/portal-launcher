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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Physical index used by Maison whenever it is enabled. */
const val PAGE_HOME = 0

/**
 * Legacy physical indices for the Maison-disabled layout.
 *
 * New code must use [LauncherPagerLayout.clockPage] and [LauncherPagerLayout.firstAppPage]: their
 * physical values move by one when Maison is enabled.
 */
const val PAGE_CLOCK = 0
const val PAGE_FIRST_APP = 1

/** Logical identity of a launcher page, independent of its current physical pager index. */
sealed interface PageIdentity {
    data object House : PageIdentity
    data object Clock : PageIdentity
    data class Apps(val index: Int) : PageIdentity {
        init {
            require(index >= 0) { "An application page index cannot be negative" }
        }
    }
}

/**
 * Vertical room the collapsed clock header really occupies, measured at runtime.
 *
 * A page that anchors content below the header must not guess: the clock is user-themed, so its
 * height follows the chosen font size and scale. [ClockHeaderCollapsedHeight] is only the default
 * used outside [LauncherPager].
 */
val LocalCollapsedHeaderHeight = compositionLocalOf { ClockHeaderCollapsedHeight }

/**
 * Pure mapping between logical pages and physical pager indices.
 *
 * Keeping this mapping in one value object prevents a Maison preference change from silently
 * turning application page N into N-1 or N+1.
 */
data class LauncherPagerLayout(
    val homePageEnabled: Boolean,
    val appPageCount: Int,
) {
    init {
        require(appPageCount >= 0) { "The application page count cannot be negative" }
    }

    val housePage: Int? = PAGE_HOME.takeIf { homePageEnabled }
    val clockPage: Int = if (homePageEnabled) 1 else 0
    val firstAppPage: Int = clockPage + 1
    val pageCount: Int = firstAppPage + appPageCount

    fun pageOf(identity: PageIdentity): Int? = when (identity) {
        PageIdentity.House -> housePage
        PageIdentity.Clock -> clockPage
        is PageIdentity.Apps ->
            (firstAppPage + identity.index).takeIf { identity.index < appPageCount }
    }

    fun identityOf(page: Int): PageIdentity? = when {
        page !in 0 until pageCount -> null
        homePageEnabled && page == housePage -> PageIdentity.House
        page == clockPage -> PageIdentity.Clock
        page >= firstAppPage -> PageIdentity.Apps(page - firstAppPage)
        else -> null
    }
}

/**
 * Maps [currentPage] through its logical identity when the pager layout changes at runtime.
 *
 * Maison itself has no destination after it is disabled, so it falls back to the main accueil.
 * An application page is preserved exactly when it still exists. If the application page count
 * also shrank, the last remaining application page is used, or the clock when none remains.
 */
fun remapPage(
    currentPage: Int,
    previous: LauncherPagerLayout,
    next: LauncherPagerLayout,
): Int {
    val identity = previous.identityOf(currentPage) ?: PageIdentity.Clock
    next.pageOf(identity)?.let { return it }
    return when (identity) {
        PageIdentity.House,
        PageIdentity.Clock -> next.clockPage
        is PageIdentity.Apps ->
            if (next.appPageCount > 0) next.firstAppPage + next.appPageCount - 1
            else next.clockPage
    }
}

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

/** Maison-aware pager state. Its initial page is always the main accueil, never Maison. */
@Composable
fun rememberLauncherPagerState(
    homePageEnabled: () -> Boolean,
    appPageCount: () -> Int,
): PagerState {
    val initialLayout = LauncherPagerLayout(homePageEnabled(), appPageCount())
    return rememberPagerState(initialPage = initialLayout.clockPage) {
        LauncherPagerLayout(homePageEnabled(), appPageCount()).pageCount
    }
}

/**
 * Continuous 0f..1f progress of the clock→apps swipe. Read this instead of `currentPage` so the
 * header shrink tracks the finger frame by frame, and stays collapsed across further app pages.
 */
fun PagerState.collapseFraction(): Float =
    (currentPage + currentPageOffsetFraction).coerceIn(0f, 1f)

/**
 * Clock-header collapse measured as the distance from the logical accueil.
 *
 * Maison and the application grid are peer launcher pages on opposite sides of Accueil, so both
 * must pull the clock into the same compact top-bar state. Saturating the absolute distance also
 * keeps it collapsed across every later application page.
 */
fun PagerState.collapseFraction(layout: LauncherPagerLayout): Float =
    abs(currentPage + currentPageOffsetFraction - layout.clockPage).coerceIn(0f, 1f)

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

/** HOME, Back and auto-return all target the logical main accueil through this helper. */
fun returnToClockPage(
    scope: CoroutineScope,
    state: PagerState,
    layout: LauncherPagerLayout,
) {
    scope.launch { state.animateScrollToPage(layout.clockPage) }
}

/** Applies a hot layout change without animating through a different logical application page. */
fun remapPagerPage(
    scope: CoroutineScope,
    state: PagerState,
    currentPage: Int,
    previous: LauncherPagerLayout,
    next: LauncherPagerLayout,
) {
    scope.launch { state.scrollToPage(remapPage(currentPage, previous, next)) }
}

/** The app page index shown at pager index [pagerPage], or null for the clock. */
fun appPageOf(pagerPage: Int): Int? =
    (pagerPage - PAGE_FIRST_APP).takeIf { it >= 0 }

/** Maison-aware application page mapping. */
fun appPageOf(pagerPage: Int, layout: LauncherPagerLayout): Int? =
    (layout.identityOf(pagerPage) as? PageIdentity.Apps)?.index

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
    /** Application-page chrome pinned to the top bar; fades in to the right of Accueil. */
    headerActions: @Composable (collapse: () -> Float) -> Unit = {},
    /** Maison title pinned to the left side of the same compact clock header. */
    houseHeader: @Composable () -> Unit = {},
    /** Maison-only chrome; replaces application actions to the left of Accueil. */
    houseHeaderActions: @Composable () -> Unit = {},
    /** Drawn above every page so an icon drag can cross them. */
    dragOverlay: @Composable () -> Unit = {},
    /** The drag in flight, if any. Drives the edge-held page flip. */
    drag: GridDragState? = null,
    /** Tap on the (expanded) clock — same contract as before the pager: opens Home Assistant. */
    onHeaderTap: () -> Unit = {},
    /** Long-press on the (expanded) clock — the quick-actions menu. */
    onHeaderLongPress: () -> Unit = {},
    /** Logical mapping for the current Maison preference and application page count. */
    pageLayout: LauncherPagerLayout = LauncherPagerLayout(
        homePageEnabled = false,
        appPageCount = (state.pageCount - PAGE_FIRST_APP).coerceAtLeast(0),
    ),
    /** Maison page content. Required by callers whenever [pageLayout] enables Maison. */
    housePage: @Composable (() -> Unit)? = null,
) {
    require(!pageLayout.homePageEnabled || housePage != null) {
        "housePage content is required when Maison is enabled"
    }
    // Keep the rapidly changing pager offset out of composition. Consumers invoke this only from
    // layout or draw/layer blocks, which Compose can invalidate without rebuilding the subtree.
    val collapse = remember(state, pageLayout) { { state.collapseFraction(pageLayout) } }
    val houseChromeAlpha = remember(state, pageLayout) {
        {
            (pageLayout.clockPage - (state.currentPage + state.currentPageOffsetFraction))
                .coerceIn(0f, 1f)
        }
    }
    val appChromeAlpha = remember(state, pageLayout) {
        {
            (state.currentPage + state.currentPageOffsetFraction - pageLayout.clockPage)
                .coerceIn(0f, 1f)
        }
    }
    // Transparent controls must not intercept clock taps. These booleans change only when a swipe
    // leaves or returns to Accueil; the frame-by-frame fade itself remains in the layer phase.
    val showHouseChrome by remember(state, pageLayout) {
        derivedStateOf { houseChromeAlpha() > 0.01f }
    }
    val showAppChrome by remember(state, pageLayout) {
        derivedStateOf { appChromeAlpha() > 0.01f }
    }
    val density = LocalDensity.current
    val edgePx = with(density) { EDGE_FLIP_WIDTH.toPx() }
    // The header is measured at full size and only drawn scaled, so this stays stable during a
    // swipe: it changes when the clock theme changes, not on every frame.
    var headerFullHeightPx by remember { mutableIntStateOf(0) }
    val collapsedHeaderHeight = with(density) {
        (headerFullHeightPx * ClockCollapsedScale).toDp()
    }.takeIf { headerFullHeightPx > 0 } ?: ClockHeaderCollapsedHeight

    // Preserve the settled logical page across a live Maison toggle. PagerState owns only a
    // physical Int and its page count can shrink before the next frame; retaining the identity is
    // what prevents app page N from silently becoming N-1.
    var previousLayout by remember(state) { mutableStateOf(pageLayout) }
    var settledIdentity by remember(state) {
        mutableStateOf(previousLayout.identityOf(state.settledPage) ?: PageIdentity.Clock)
    }
    LaunchedEffect(state, pageLayout) {
        if (previousLayout != pageLayout) {
            val previousPage = previousLayout.pageOf(settledIdentity) ?: previousLayout.clockPage
            state.scrollToPage(remapPage(previousPage, previousLayout, pageLayout))
            previousLayout = pageLayout
        }
        snapshotFlow { state.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                settledIdentity = pageLayout.identityOf(page) ?: PageIdentity.Clock
            }
    }

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
                        .coerceIn(
                            pageLayout.firstAppPage,
                            (state.pageCount - 1).coerceAtLeast(pageLayout.firstAppPage),
                        )
                    if (target == state.currentPage) break
                    state.animateScrollToPage(target)
                }
            }
    }

    CompositionLocalProvider(LocalCollapsedHeaderHeight provides collapsedHeaderHeight) {
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
            val identity = pageLayout.identityOf(page)
            // The fade is passed as a lambda so each page reads it in its own draw phase: as a
            // value it would recompose every page on every frame of the swipe.
            when (identity) {
                PageIdentity.House -> Box(
                    Modifier
                        .fillMaxSize()
                        // Match the app grid reveal: Maison stays invisible while the clock is
                        // expanded, so its first rail cannot show through the moving header.
                        .graphicsLayer { alpha = houseChromeAlpha() },
                ) {
                    housePage?.invoke()
                }
                PageIdentity.Clock -> clockPage()
                is PageIdentity.Apps ->
                    appPage(identity.index) { state.collapseFraction(pageLayout) }
                null -> Unit
            }
        }

        dragOverlay()

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Both pointer nodes sit OUTSIDE collapsingHeight on purpose. A pointer modifier
                // placed after it would wrap the content, which is still measured at full height —
                // and Compose does not clip hit-testing to the parent's bounds, so the invisible
                // full-size clock band would swallow taps on page content.
                .pagerDragForward(state, enabled = userScrollEnabled)
                // Tap detection never consumes drags, so the drag forwarder above still wins past
                // the touch slop. The guard is read at gesture time (not via the modifier chain) so
                // nothing is swapped mid-drag.
                .pointerInput(pageLayout) {
                    detectTapGestures(
                        onTap = {
                            if (state.collapseFraction(pageLayout) < 0.5f) onHeaderTap()
                        },
                        onLongPress = {
                            if (state.collapseFraction(pageLayout) < 0.5f) onHeaderLongPress()
                        },
                    )
                }
                .collapsingHeight { headerHeight ->
                    (headerHeight * headerScale(collapse())).roundToInt()
                },
            // The wrapper fills the width (so the whole clock band forwards drags), so the clock
            // itself has to be centred here — it wraps its own width.
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.onSizeChanged { headerFullHeightPx = it.height }) {
                header(collapse)
            }
        }

        if (showHouseChrome) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 14.dp, start = 28.dp)
                    .graphicsLayer { alpha = houseChromeAlpha() },
            ) {
                houseHeader()
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .graphicsLayer { alpha = houseChromeAlpha() },
            ) {
                houseHeaderActions()
            }
        }

        // Application chrome is direction-aware: it never leaks into Maison during the first half
        // of the swipe, even though both sides share the same absolute clock-collapse progress.
        if (showAppChrome) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .graphicsLayer { alpha = appChromeAlpha() },
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
}

/** Mirrors the header's `graphicsLayer` scale so its hit area matches what is actually drawn. */
private fun headerScale(collapse: Float): Float = 1f - (1f - ClockCollapsedScale) * collapse

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
