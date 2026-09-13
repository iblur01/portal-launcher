package com.iblu01.portallauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.AlertItem
import com.iblu01.portallauncher.AlertLevel
import com.iblu01.portallauncher.AlertPayload
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.icons.HaIcon
import com.iblu01.portallauncher.ui.icons.IconRef
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * MQTT notifications, drawn as the voice assistant's sibling: same [EdgeGlow] backdrop, but amber
 * and hanging from the top edge instead of blue rising from the bottom. Across a room the edge and
 * the hue say which of the two is on screen before a single word is read.
 *
 * Like the voice layer it owns every touch while visible, so a notification cannot be dismissed by
 * accidentally hitting a control behind it; tapping anywhere dismisses.
 */
@Composable
fun AlertOverlay(
    alert: AlertPayload?,
    onDismiss: () -> Unit,
    timerEndsAt: Long? = null,
) {
    // The payload is cleared the instant it is dismissed, but the fade-out still has to draw it.
    var shown by remember { mutableStateOf<AlertPayload?>(null) }
    if (alert != null) shown = alert
    val drawn = shown

    AnimatedVisibility(
        visible = alert != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(150)),
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (drawn?.dismissible != false) {
                        Modifier.pointerInput(Unit) { detectTapGestures { onDismiss() } }
                    } else {
                        // Still owns every touch — it just refuses to be one tap away from
                        // silencing an alarm. Only the code, or Home Assistant, takes it down.
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
                ),
        ) {
            val compact = maxWidth < 600.dp
            val accent = drawn?.accentArgb()?.let { Color(it) } ?: drawn?.level.color()

            if (drawn?.blackout == true) {
                // Opaque, not translucent: a clock showing through the alarm screen reads as a
                // glitch, and the point is that nothing else is worth looking at.
                Box(Modifier.fillMaxSize().background(AppleColors.groupedBg))
            }

            EdgeGlow(
                color = accent,
                edge = GlowEdge.TOP,
                // Quicker than the assistant's: a notification announces itself and leaves, it
                // does not sit there waiting to be talked to.
                shimmerMillis = 2000,
                breatheMillis = 1100,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (compact) 150.dp else 200.dp)
                    .align(Alignment.TopCenter),
            )

            if (drawn?.blackout == true) {
                AlarmScreen(
                    alert = drawn,
                    accent = accent,
                    compact = compact,
                    // A wall panel is wide and short; stacking the keypad under the text there
                    // pushes its last row off the screen.
                    short = maxHeight < 560.dp,
                    // A panel stood on its end has room to breathe: the keypad grows and the
                    // incident takes the upper third instead of huddling in the middle.
                    tall = maxHeight > maxWidth * 1.2f,
                    timerEndsAt = timerEndsAt,
                )
                return@BoxWithConstraints
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 720.dp)
                    .padding(horizontal = if (compact) 24.dp else 48.dp)
                    .padding(top = if (compact) 26.dp else 38.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingingIcon(icon = drawn?.icon, accent = accent, compact = compact)
                Spacer(Modifier.width(if (compact) 14.dp else 18.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = drawn?.title ?: stringResource(R.string.alert_overlay_title),
                            style = AppleTypography.labelSmall,
                            color = accent,
                        )
                        drawn?.badge?.let { badge ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = badge.uppercase(),
                                style = AppleTypography.labelSmall,
                                color = AppleColors.groupedBg,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accent)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = drawn?.message.orEmpty(),
                        style = AppleTypography.titleLarge.copy(
                            fontSize = if (compact) 22.sp else 30.sp,
                            lineHeight = if (compact) 30.sp else 40.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                        color = AppleColors.primary,
                        textAlign = TextAlign.Start,
                        maxLines = if (compact) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    drawn?.items.orEmpty().let { items ->
                        // Three lines is what stays readable from across the room; the rest is
                        // counted rather than listed.
                        val shown = items.take(3)
                        if (shown.isNotEmpty()) Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                        shown.forEach { item -> DetailLine(item, accent, compact) }
                        if (items.size > shown.size) {
                            Text(
                                text = stringResource(R.string.alert_overlay_more_items, items.size - shown.size),
                                style = AppleTypography.labelSmall,
                                color = AppleColors.tertiary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                if (timerEndsAt != null) {
                    Spacer(Modifier.width(if (compact) 16.dp else 24.dp))
                    Countdown(endsAt = timerEndsAt, accent = accent, compact = compact)
                }
            }
        }
    }
}

/**
 * The alarm's own screen: the wallpaper is gone, the incident is the whole page, and the keypad
 * sits in the middle. This is what a wall panel looks like when it is counting down or howling,
 * and it is also what keeps a passer-by from silencing it — the only way out is the code.
 */
@Composable
private fun BoxScope.AlarmScreen(
    alert: AlertPayload,
    accent: Color,
    compact: Boolean,
    short: Boolean,
    tall: Boolean,
    timerEndsAt: Long?,
) {
    // Une entité que Home Assistant n'a jamais poussée ne peut rien désarmer : mieux vaut pas de
    // clavier du tout qu'un clavier dont le code part dans le vide.
    val entityId = alert.keypad?.takeIf { rememberEntity(it) != null }
    val entity = entityId?.let { rememberEntity(it) }

    val incident: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HaIcon(
                ref = IconRef.parse(alert.icon),
                contentDescription = null,
                tint = accent,
                size = if (compact) 20.dp else 26.dp,
                fallback = Icons.Outlined.NotificationsActive,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = alert.title ?: stringResource(R.string.alert_overlay_title),
                style = AppleTypography.labelSmall.copy(fontSize = if (tall) 17.sp else 13.sp),
                color = accent,
            )
        }
        alert.badge?.let { badge ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = badge.uppercase(),
                style = AppleTypography.labelSmall,
                color = AppleColors.groupedBg,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
        Text(
            text = alert.message,
            style = AppleTypography.titleLarge.copy(
                fontSize = when {
                    tall -> 40.sp
                    compact -> 20.sp
                    else -> 30.sp
                },
                lineHeight = if (tall) 48.sp else 36.sp,
                fontWeight = FontWeight.Normal,
            ),
            color = AppleColors.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (tall) Spacer(Modifier.height(10.dp))
        alert.items.take(if (short) 2 else 3).forEach { item ->
            DetailLine(item, accent, compact, big = tall)
        }
        if (timerEndsAt != null) {
            if (tall) Spacer(Modifier.height(12.dp))
            Countdown(endsAt = timerEndsAt, accent = accent, compact = compact, big = tall)
        }
    }

    val keypad: @Composable () -> Unit = keypad@{
        if (entityId == null) return@keypad
        Box(
            Modifier.widthIn(
                max = when {
                    short -> 300.dp
                    tall -> 600.dp
                    compact -> 300.dp
                    else -> 360.dp
                }
            )
        ) {
            AlarmKeypad(
                entityId = entityId,
                service = "alarm_disarm",
                currentState = entity?.state.orEmpty(),
                prompt = stringResource(R.string.alarm_disarm_prompt),
                accent = accent,
                maxKeyDiameter = if (tall) 128.dp else 74.dp,
                // No way out but the code: an escape hatch here would be the tap we just removed.
                onCancel = null,
            )
        }
    }

    if (short && entityId != null) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = if (compact) 16.dp else 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = incident,
            )
            keypad()
        }
        return
    }

    Column(
        modifier = Modifier
            .then(
                if (tall && entityId != null) Modifier.fillMaxSize()
                else Modifier.align(Alignment.Center).fillMaxWidth()
            )
            .padding(horizontal = if (compact) 20.dp else 40.dp)
            .padding(vertical = if (tall && entityId != null) 36.dp else 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = incident)
        if (tall && entityId != null) {
            // The incident stays at the top and the keypad takes the middle of what is left —
            // an even split would leave a hole between the two.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { keypad() }
        } else {
            Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
            keypad()
        }
    }
}

/**
 * One detail line: what is at fault, and what the panel did about it.
 */
@Composable
private fun DetailLine(item: AlertItem, accent: Color, compact: Boolean, big: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        HaIcon(
            ref = IconRef.parse(item.icon),
            contentDescription = null,
            tint = accent,
            size = if (big) 24.dp else if (compact) 15.dp else 18.dp,
            fallback = Icons.Outlined.Circle,
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Text(
            text = item.text,
            style = AppleTypography.bodyMedium.copy(
                fontSize = if (big) 22.sp else if (compact) 14.sp else 17.sp,
            ),
            color = AppleColors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        item.note?.let { note ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = note,
                style = AppleTypography.labelSmall.copy(fontSize = if (big) 17.sp else 13.sp),
                color = accent,
                maxLines = 1,
            )
        }
    }
}

/**
 * The time left, ticking once a second beside the message: across a room the number is the whole
 * notification — a glance says how long the oven, the wash or the alarm still has.
 */
@Composable
private fun Countdown(endsAt: Long, accent: Color, compact: Boolean, big: Boolean = false) {
    var remaining by remember(endsAt) {
        mutableStateOf((endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(endsAt) {
        while (remaining > 0) {
            // Realign on the wall clock every tick: a drifting counter is worse than a coarse one.
            delay(((endsAt - System.currentTimeMillis()) % 1000L).takeIf { it > 0 } ?: 1000L)
            remaining = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
        }
    }
    Text(
        text = formatRemaining(remaining),
        style = AppleTypography.titleLarge.copy(
            fontSize = if (big) 84.sp else if (compact) 34.sp else 46.sp,
            fontWeight = FontWeight.Medium,
            fontFeatureSettings = "tnum",
        ),
        color = accent,
        maxLines = 1,
    )
}

/** `m:ss` under an hour, `h:mm:ss` above it — the shortest form that still reads unambiguously. */
internal fun formatRemaining(remainingMs: Long): String {
    val total = (remainingMs + 999) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Amber stays the notification colour at its ordinary level — that is the hue the panel has
 * always used to mean "something wants you" — and only the levels above it break away.
 */
private fun AlertLevel?.color(): Color = when (this) {
    AlertLevel.CRITICAL -> AppleColors.error
    AlertLevel.WARNING -> AppleColors.thermostatHeat
    else -> AppleColors.warning
}

/**
 * The sender's own icon keeps pulsing for as long as the notification stands: a glance is enough
 * to catch it, and the glyph says what it is about before the text is read.
 */
@Composable
private fun RingingIcon(icon: String?, accent: Color, compact: Boolean) {
    val pulse by rememberInfiniteTransition(label = "alertBell").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700), repeatMode = RepeatMode.Reverse),
        label = "alertBellPulse",
    )
    Box(
        modifier = Modifier
            .size(if (compact) 44.dp else 54.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        HaIcon(
            ref = IconRef.parse(icon),
            contentDescription = null,
            tint = accent,
            size = if (compact) 24.dp else 30.dp,
            fallback = Icons.Outlined.NotificationsActive,
        )
    }
}
