package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * iOS picker-wheel — a rotating "cylinder" of options. Items curl away from the centre with
 * a 3D tilt and fade; scrolling snaps to the nearest one, which becomes the selection. Tap an
 * item to roll it to the middle. Ideal for a thermostat mode picker (Off / Cool / Heat / Auto).
 *
 * @param visibleCount odd number of rows shown at once (the middle one is the selection).
 */
@Composable
fun <T> WheelPicker(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    visibleCount: Int = 5,
    itemHeight: Dp = 40.dp,
    accent: Color = AppleColors.primary,
) {
    val contentScale = (itemHeight / 40.dp).coerceIn(0.7f, 1.5f)
    val rows = (visibleCount.coerceIn(3, 9)).let { if (it % 2 == 0) it + 1 else it }
    val edge = rows / 2
    val initialIndex = options.indexOf(selected).coerceAtLeast(0)
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()

    // The row closest to the vertical centre is the current selection.
    val centeredIndex by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2f) - mid) }?.index ?: initialIndex
        }
    }
    LaunchedEffect(centeredIndex, state.isScrollInProgress) {
        // Commit only once the wheel settles; scrolling across several rows must not emit one
        // backend action per transiently centered option.
        if (!state.isScrollInProgress) options.getOrNull(centeredIndex)?.let(onSelect)
    }

    LazyColumn(
        modifier = modifier.height(itemHeight * rows),
        state = state,
        flingBehavior = fling,
        contentPadding = PaddingValues(vertical = itemHeight * edge),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(options) { index, option ->
            val isCentered = index == centeredIndex
            val textColor by animateColorAsState(
                if (isCentered) accent else AppleColors.secondary, tween(180), label = "wheelText",
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .graphicsLayer {
                        // Distance of this row's centre from the viewport centre, in item units.
                        val info = state.layoutInfo
                        val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                        val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                        val off = if (item != null) (item.offset + item.size / 2f - mid) / item.size else 0f
                        val clamped = off.coerceIn(-edge.toFloat(), edge.toFloat())
                        rotationX = -clamped * 24f
                        alpha = (1f - abs(clamped) * 0.30f).coerceIn(0.2f, 1f)
                        val s = 1f - abs(clamped) * 0.06f
                        scaleX = s; scaleY = s
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(percent = 30))
                    .then(if (isCentered) Modifier.background(Color.White.copy(alpha = 0.08f)) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = AppleTypography.titleLarge.copy(
                        fontSize = AppleTypography.titleLarge.fontSize * contentScale,
                        fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
