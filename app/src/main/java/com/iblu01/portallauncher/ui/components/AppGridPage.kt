package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import com.iblu01.portallauncher.ui.apps.GridCell
import com.iblu01.portallauncher.ui.apps.GridPlacement
import com.iblu01.portallauncher.ui.apps.coveringCell
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.apps.GridSpec
import com.iblu01.portallauncher.ui.apps.PlacedItem
import com.iblu01.portallauncher.ui.apps.cellAt
import com.iblu01.portallauncher.ui.apps.cellOrigin
import com.iblu01.portallauncher.ui.apps.gridSpecFor
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

/** Space around the cell area of every app page. The top inset is the pinned, collapsed clock. */
object AppGridInsets {
    val horizontal = 12.dp
    val bottom = 28.dp
}

/** How far the finger must travel after a long-press before the icon is picked up. */
private val DRAG_SLOP = 14.dp

/**
 * One page of the app grid: items at the exact cells they were dropped in.
 *
 * Free placement, so this is an absolutely-positioned grid rather than a lazy list — holes are the
 * point, and there is nothing to scroll (pages replace scrolling). Cell geometry is derived from the
 * page size, so hit-testing needs no reported rects: a point maps to a cell arithmetically.
 *
 * The drag itself is owned by [drag], above the pager, because it can cross pages.
 */
@Composable
fun AppGridPage(
    page: Int,
    items: List<PlacedItem>,
    spec: GridSpec,
    drag: GridDragState,
    onLaunch: (GridItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (item: GridItem, span: GridSpan, anchor: IntRect) -> Unit = { _, _, _ -> },
    onPickUp: () -> Unit = {},
    /** Long-press on a cell holding nothing: the surface's own menu (wallpaper, settings). */
    onLongPressEmpty: () -> Unit = {},
    onDrop: (key: String, placement: GridPlacement?) -> Unit = { _, _ -> },
    /** Builds a bound widget's view. Null for anything that is not a widget, or a dead id. */
    widgetView: (widgetId: Int) -> android.view.View? = { null },
    topInset: Dp = ClockHeaderCollapsedHeight,
    /** Reports the cell grid this page's size affords, so placement can use the real numbers. */
    onSpec: (GridSpec) -> Unit = {},
    /** Reports one cell's size in dp — the only way a widget's declared minimum becomes cells. */
    onCellSize: (widthDp: Float, heightDp: Float) -> Unit = { _, _ -> },
    /** User-configurable multiplier on cell size (icon size / grid density), from Prefs.gridScale. */
    cellScale: Float = 1f,
    /**
     * 0 on the clock page, 1 once this page is in view. A lambda, not a value: read in the draw
     * phase it costs no recomposition, whereas a parameter would recompose every page every frame
     * of the swipe.
     */
    appear: () -> Float = { 1f },
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var contentRect by remember { mutableStateOf(Rect.Zero) }
    // Page contents change only when placement changes, not while the pager moves. Avoid scanning
    // and allocating a new list if an ancestor happens to recompose during a gesture.
    val onPage = remember(items, page) { items.filter { it.cell.page == page } }
    // Read inside the gesture rather than captured by it: the pointer-input block must survive the
    // whole drag, so it cannot be keyed on anything that changes while dragging.
    val currentOnPage = rememberUpdatedState(onPage)

    DisposableEffect(page) { onDispose { drag.unregisterPage(page) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear() }
            .padding(
                start = AppGridInsets.horizontal,
                end = AppGridInsets.horizontal,
                top = topInset,
                bottom = AppGridInsets.bottom,
            )
            .testTag("appGridPage$page")
            .onGloballyPositioned { coordinates ->
                contentRect = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
                drag.registerPage(page, PageGeometry(contentRect, spec))
                val widthDp = with(density) { contentRect.width.toDp().value }
                val heightDp = with(density) { contentRect.height.toDp().value }
                onSpec(gridSpecFor(widthDp = widthDp, heightDp = heightDp, cellWidthDp = 112f * cellScale, cellHeightDp = 116f * cellScale))
                onCellSize(widthDp / spec.columns, heightDp / spec.rows)
            }
            // Outlines the footprint the dragged item would land on — a widget covers several
            // cells, so a single-cell hint would be a lie. Read in the draw phase, so following the
            // finger costs no recomposition.
            .drawBehind {
                if (!drag.isDragging) return@drawBehind
                val target = drag.hoveredPlacement()?.takeIf { it.cell.page == page } ?: return@drawBehind
                val (x, y) = cellOrigin(target.cell, size.width, size.height, spec)
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(x, y),
                    size = Size(
                        width = size.width / spec.columns * target.span.width,
                        height = size.height / spec.rows * target.span.height,
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            // Keyed on `page` and `spec` only. Keying it on `contentRect` (or on the item list)
            // recreated the node whenever the page slid under the finger, which is exactly what a
            // page flip does — the drag was cancelled at the very moment it needed to continue.
            .pointerInput(page, spec) {
                val slopPx = DRAG_SLOP.toPx()
                var armed: PlacedItem? = null
                var travelled = 0f
                var pointerRoot = Offset.Zero
                // Where the finger went down. The grab offset must be measured from *here*, not
                // from the position after the first delta, or that delta is silently absorbed and
                // the icon never leaves its cell.
                var downPointer = Offset.Zero

                fun finish() {
                    val key = drag.draggedKey
                    if (key != null) {
                        // Cancel is treated as a drop on purpose: a page flip can dispose the page
                        // that owns the gesture, and losing the icon mid-air would be worse than
                        // committing it where the user last held it.
                        onDrop(key, drag.hoveredPlacement())
                        drag.stop()
                    }
                    armed = null
                    travelled = 0f
                }

                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        travelled = 0f
                        armed = null
                        downPointer = contentRect.topLeft + offset
                        pointerRoot = downPointer
                        val cell = cellAt(
                            x = offset.x,
                            y = offset.y,
                            pageWidth = contentRect.width,
                            pageHeight = contentRect.height,
                            spec = spec,
                            page = page,
                        )
                        val hit = currentOnPage.value.coveringCell(cell)
                        if (hit == null) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressEmpty()
                            return@detectDragGesturesAfterLongPress
                        }
                        armed = hit
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress(
                            hit.item,
                            hit.span,
                            itemRootRect(hit, contentRect, spec).roundToIntRect(),
                        )
                    },
                    onDrag = { _, dragAmount ->
                        val hit = armed ?: return@detectDragGesturesAfterLongPress
                        travelled += dragAmount.getDistance()
                        if (!drag.isDragging) {
                            if (travelled < slopPx) return@detectDragGesturesAfterLongPress
                            // Past the slop: this is a move, not a menu.
                            drag.start(
                                key = hit.item.key,
                                icon = hit.item.icon,
                                label = hit.item.label,
                                tileRect = itemRootRect(hit, contentRect, spec),
                                pointerRoot = downPointer,
                                span = hit.span,
                            )
                            onPickUp()
                        }
                        pointerRoot += dragAmount
                        drag.pointer = pointerRoot
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            },
    ) {
        onPage.forEach { placed ->
            // The dragged item is drawn by the overlay above the pager, not by its page.
            if (placed.item.key == drag.draggedKey) return@forEach
            val cellWidth = contentRect.width / spec.columns
            val cellHeight = contentRect.height / spec.rows
            key(placed.item.key) {
                Box(
                    modifier = Modifier
                        .offset {
                            val (x, y) = cellOrigin(placed.cell, contentRect.width, contentRect.height, spec)
                            IntOffset(x.roundToInt(), y.roundToInt())
                        }
                        .size(
                            width = with(density) { (cellWidth * placed.span.width).toDp() },
                            height = with(density) { (cellHeight * placed.span.height).toDp() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (placed.item.isWidget) {
                        WidgetTile(widgetId = placed.item.widgetId, widgetView = widgetView)
                    } else {
                        AppTile(
                            label = placed.item.label,
                            icon = placed.item.icon,
                            onClick = { onLaunch(placed.item) },
                            iconSize = 56.dp * cellScale,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A bound widget, hosted as a real Android view.
 *
 * `AndroidView`'s factory runs once per node, which is what the host needs: the view is a live
 * connection to another process, not something to rebuild on recomposition. A dead id (provider
 * uninstalled between layouts) draws a placeholder rather than crashing.
 */
@Composable
private fun WidgetTile(widgetId: Int, widgetView: (Int) -> android.view.View?) {
    val view = remember(widgetId) { widgetView(widgetId) }
    if (view == null) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .clip(AppleShapes.panel)
                .background(AppleColors.frostedFill)
        )
        return
    }
    AndroidView(
        factory = { view },
        modifier = Modifier.fillMaxSize().padding(4.dp),
    )
}

/** An item's rectangle in root coordinates, covering its whole footprint. */
private fun itemRootRect(placed: PlacedItem, contentRect: Rect, spec: GridSpec): Rect {
    val (x, y) = cellOrigin(placed.cell, contentRect.width, contentRect.height, spec)
    return Rect(
        offset = contentRect.topLeft + Offset(x, y),
        size = Size(
            width = contentRect.width / spec.columns * placed.span.width,
            height = contentRect.height / spec.rows * placed.span.height,
        ),
    )
}

private fun Rect.roundToIntRect(): IntRect =
    IntRect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/** The floating icon under the finger, drawn above every page so a drag can cross them. */
@Composable
fun DraggedIconOverlay(drag: GridDragState, iconSize: Dp = 56.dp) {
    if (!drag.isDragging) return
    val icon = drag.draggedIcon
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
    ) {
        Box(
            modifier = Modifier
                .offset {
                    val topLeft = drag.tileTopLeft()
                    IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                }
                .graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    alpha = 0.92f
                },
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
internal fun AppTile(label: String, icon: ImageBitmap?, onClick: () -> Unit, iconSize: Dp = 56.dp) {
    val scale = iconSize / 56.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(AppleShapes.panel)
            .nonConsumingClickable(onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(iconSize),
            )
        } else {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(AppleShapes.panel)
                    .background(AppleColors.frostedFill)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = AppleTypography.bodySmall.copy(fontSize = (13.sp.value * scale).sp),
            color = AppleColors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
