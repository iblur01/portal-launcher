package com.iblu01.portallauncher.ui.components.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** iOS lock-screen letter groups shown under each digit. */
private val KeypadLetters = mapOf(
    '2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL",
    '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
)

/**
 * Apple-style numeric keypad. A row of dots tracks the entry; the whole cluster shakes rapidly
 * (passcode-style) whenever [error] flips true, then clears and calls [onErrorConsumed].
 *
 * Two modes:
 *  - **fixed length** ([codeLength] > 0): dots equal the length and [onSubmit] fires automatically
 *    the moment the last digit lands — the parent then either dismisses (correct) or flips [error]
 *    (wrong). No confirm key.
 *  - **variable length** ([codeLength] == 0): dots grow with input and a ✓ key submits.
 *
 * Everything tints from [accent]; [dotColor] fills the entry dots (white by default, à la iOS).
 */
@Composable
fun PinKeypad(
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    codeLength: Int = 4,
    title: String? = null,
    subtitle: String? = null,
    accent: Color = AppleColors.accent,
    dotColor: Color = AppleColors.primary,
    error: Boolean = false,
    onErrorConsumed: () -> Unit = {},
    enabled: Boolean = true,
    loading: Boolean = false,
    showLetters: Boolean = true,
    haptics: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    val fixed = codeLength > 0
    val haptic = LocalHapticFeedback.current
    var code by remember { mutableStateOf("") }
    var errored by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val inputEnabled = enabled && !loading

    fun tick() { if (haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }

    // Parent rejected the entry → shake hard, clear, notify.
    LaunchedEffect(error) {
        if (error) {
            errored = true
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // Decaying left/right oscillation — the classic iOS passcode "no".
            listOf(-16f, 16f, -13f, 13f, -9f, 9f, -5f, 5f, 0f).forEach {
                shake.animateTo(it, tween(45, easing = LinearEasing))
            }
            code = ""
            errored = false
            onErrorConsumed()
        }
    }

    val submit: () -> Unit = {
        if (code.isNotEmpty()) {
            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onSubmit(code)
        }
    }
    val press: (String) -> Unit = press@{ key ->
        if (!inputEnabled) return@press
        when (key) {
            "back" -> if (code.isNotEmpty()) { tick(); code = code.dropLast(1) }
            "ok" -> submit()
            "cancel" -> onCancel?.invoke()
            "" -> Unit
            else -> if (!fixed || code.length < codeLength) {
                tick()
                code += key
                if (fixed && code.length == codeLength) {
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSubmit(code)
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.35f) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                title,
                style = AppleTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AppleColors.primary,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (subtitle != null) {
            Text(subtitle, style = AppleTypography.bodySmall, color = AppleColors.secondary)
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(16.dp))

        // Entry dots, riding the shake.
        val dotCount = if (fixed) codeLength else code.length.coerceAtLeast(1)
        val filledColor by animateColorAsState(
            if (errored) AppleColors.error else dotColor, tween(120), label = "dotFill",
        )
        val loadingTransition = rememberInfiniteTransition(label = "keypadLoading")
        val loadingPhase by loadingTransition.animateFloat(
            initialValue = 0f,
            targetValue = dotCount.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(dotCount * 180, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "keypadLoadingPhase",
        )
        Row(
            modifier = Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            repeat(dotCount) { i ->
                val on = i < code.length
                val directDistance = abs(loadingPhase - i)
                val pulseDistance = min(directDistance, dotCount - directDistance)
                val pulse = if (loading) (1f - pulseDistance).coerceIn(0f, 1f) else 0f
                Box(
                    Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = 1f + pulse * 0.28f
                            scaleY = 1f + pulse * 0.28f
                            alpha = if (loading) 0.55f + pulse * 0.45f else 1f
                        }
                        .clip(CircleShape)
                        .then(
                            if (on) Modifier.background(filledColor, CircleShape)
                            else Modifier.border(1.5.dp, AppleColors.quaternary, CircleShape),
                        ),
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        // Keys. Bottom row depends on the mode.
        val bottomRow = when {
            !fixed -> listOf("back", "0", "ok")
            else -> listOf(if (onCancel != null) "cancel" else "", "0", if (code.isNotEmpty()) "back" else "")
        }
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            bottomRow,
        )
        rows.forEachIndexed { index, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        accent = accent,
                        showLetters = showLetters,
                        enabled = inputEnabled && (key != "ok" || code.isNotEmpty()),
                        onClick = { press(key) },
                    )
                }
            }
            if (index < rows.lastIndex) Spacer(Modifier.height(16.dp))
        }

        // Variable length spends its bottom row on ⌫ / ✓, so cancelling gets its own line.
        if (!fixed && onCancel != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.keypad_cancel),
                style = AppleTypography.bodyLarge,
                color = AppleColors.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = inputEnabled,
                        onClick = onCancel,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun KeypadKey(
    key: String,
    accent: Color,
    showLetters: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val diameter = 74.dp

    // "cancel" / empty slots stay the grid's size but carry no circle.
    if (key.isEmpty()) {
        Spacer(Modifier.size(diameter))
        return
    }
    if (key == "cancel") {
        Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.keypad_cancel),
                style = AppleTypography.bodyLarge,
                color = AppleColors.secondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                    .padding(8.dp),
            )
        }
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val backDesc = stringResource(R.string.keypad_back_desc)
    val okDesc = stringResource(R.string.keypad_confirm_desc)
    val keyDescFormat = stringResource(R.string.keypad_key_desc_format)
    val background by animateColorAsState(
        if (pressed && enabled) Color.White.copy(alpha = 0.24f) else AppleColors.frostedFill,
        tween(if (pressed) 40 else 260), label = "keyPress",
    )

    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background, CircleShape)
            .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { contentDescription = when (key) {
                "back" -> backDesc
                "ok" -> okDesc
                else -> java.lang.String.format(keyDescFormat, key)
            } },
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "back" -> Icon(Icons.Outlined.Backspace, null, tint = AppleColors.secondary, modifier = Modifier.size(26.dp))
            "ok" -> Icon(Icons.Outlined.Check, null, tint = if (enabled) accent else AppleColors.tertiary, modifier = Modifier.size(28.dp))
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(key, style = AppleTypography.titleLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.Normal), color = AppleColors.primary)
                val letters = KeypadLetters[key.firstOrNull()]
                if (showLetters && letters != null) {
                    Text(
                        letters,
                        style = AppleTypography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp),
                        color = AppleColors.tertiary,
                    )
                }
            }
        }
    }
}
