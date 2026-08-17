package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.iblu01.portallauncher.ui.theme.AppleMotion

/**
 * iOS-style tap feedback: scales to [AppleMotion.PRESS_SCALE] on press and springs
 * back on release. No Material ripple. Runs on the GPU via [graphicsLayer] so it
 * never triggers recomposition of the wrapped content.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) AppleMotion.PRESS_SCALE else 1f,
        animationSpec = AppleMotion.spring(),
        label = "pressScale"
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Rippleless clickable that also drives [pressScale]. Use for every tappable
 * Apple-style surface (rows, buttons, cards).
 */
@Composable
fun Modifier.appleClickable(onClick: () -> Unit): Modifier = appleClickable(onClick, null)

/**
 * Rippleless clickable that leaves the gesture **unconsumed**, so an ancestor's long-press and drag
 * detector still works over it.
 *
 * [appleClickable] cannot be used inside a container that owns a long-press gesture:
 * `detectTapGestures` consumes the `down`, and a consumed event cancels the ancestor's pending
 * long-press — which silently killed the app grid's item menu. Here the down is only observed, and
 * a tap counts only if the finger lifts before the long-press timeout, so a hold or a drag falls
 * through to the container instead of also firing a click.
 */
@Composable
fun Modifier.nonConsumingClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .accessibleActivation(onClick)
        .pointerInput(onClick) {
            val longPressTimeout = viewConfiguration.longPressTimeoutMillis
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val press = PressInteraction.Press(down.position)
                interaction.tryEmit(press)
                // Returns null as soon as anything else consumes the gesture (i.e. a drag started).
                val up = waitForUpOrCancellation()
                if (up == null) {
                    interaction.tryEmit(PressInteraction.Cancel(press))
                    return@awaitEachGesture
                }
                interaction.tryEmit(PressInteraction.Release(press))
                if (up.uptimeMillis - down.uptimeMillis < longPressTimeout) onClick()
            }
        }
}

/**
 * Rippleless clickable with optional long-press. When [onLongPress] is set the long-press
 * fires (and its gesture is consumed, so a parent long-press handler does not also fire).
 */
@Composable
fun Modifier.appleClickable(onClick: () -> Unit, onLongPress: (() -> Unit)?): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .pressScale(interaction)
        .accessibleActivation(onClick, onLongPress)
        .pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onPress = { offset ->
                    val press = androidx.compose.foundation.interaction.PressInteraction.Press(offset)
                    interaction.emit(press)
                    val released = tryAwaitRelease()
                    interaction.emit(
                        if (released) androidx.compose.foundation.interaction.PressInteraction.Release(press)
                        else androidx.compose.foundation.interaction.PressInteraction.Cancel(press)
                    )
                },
                onLongPress = onLongPress?.let { { _ -> it() } },
                onTap = { onClick() }
            )
        }
}

/** Adds accessibility-service and hardware-key activation without changing pointer consumption. */
private fun Modifier.accessibleActivation(
    onClickAction: () -> Unit,
    onLongClickAction: (() -> Unit)? = null,
): Modifier = this
    .semantics(mergeDescendants = true) {
        role = Role.Button
        onClick {
            onClickAction()
            true
        }
        onLongClickAction?.let { action ->
            onLongClick {
                action()
                true
            }
        }
    }
    .onKeyEvent { event ->
        val activationKey = event.key == Key.Enter ||
            event.key == Key.NumPadEnter ||
            event.key == Key.DirectionCenter ||
            event.key == Key.Spacebar
        if (activationKey && event.type == KeyEventType.KeyUp) {
            onClickAction()
            true
        } else {
            false
        }
    }
    .focusable()
