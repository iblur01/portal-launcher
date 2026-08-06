package com.iblu01.portallauncher.ui.onboarding.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.CapabilityStatus
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * A large, illustrated choice — the background modes, the grid presets, a discovered home.
 *
 * Selection is shown by a quiet accent border and a check, never by a filled colour block: the
 * onboarding stays as dark and calm as the launcher it is configuring.
 */
@Composable
fun ChoiceTile(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    preview: (@Composable () -> Unit)? = null,
) {
    val borderAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.10f,
        animationSpec = AppleMotion.spring(),
        label = "tile-border",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) AppleColors.accent.copy(alpha = borderAlpha)
                else AppleColors.frostedBorder,
                shape = AppleShapes.card,
            )
            .appleClickable { onClick() }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (preview != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(AppleShapes.section)
            ) { preview() }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) AppleColors.accent else AppleColors.secondary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = AppleTypography.titleMedium, color = AppleColors.primary)
                if (subtitle != null) {
                    Text(subtitle, style = AppleTypography.bodySmall, color = AppleColors.secondary)
                }
            }
            SelectedCheck(visible = selected)
        }
    }
}

/** The check that appears when something becomes valid: a soft scale-in, no bounce. */
@Composable
fun SelectedCheck(visible: Boolean, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onb_bg_selected_a11y)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(AppleMotion.spring()) + scaleIn(AppleMotion.spring(), initialScale = 0.8f),
        exit = fadeOut(AppleMotion.spring()),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(24.dp)
                .background(AppleColors.active, CircleShape)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/**
 * One system capability: what it does, whether it is already usable, and the single action that
 * grants it. Status text is passed in rather than derived here so each card can name its own
 * states ("enabled" reads better than "configured" for the screen control).
 */
@Composable
fun CapabilityCard(
    title: String,
    description: String,
    status: CapabilityStatus,
    statusLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = AppleTypography.titleMedium, color = AppleColors.primary)
                    if (badge != null) {
                        Spacer(Modifier.width(10.dp))
                        Badge(badge)
                    }
                }
                Text(
                    statusLabel,
                    style = AppleTypography.bodySmall,
                    color = when (status) {
                        CapabilityStatus.GRANTED -> AppleColors.active
                        CapabilityStatus.MISSING -> AppleColors.secondary
                        CapabilityStatus.UNAVAILABLE -> AppleColors.tertiary
                    },
                )
            }
            SelectedCheck(visible = status == CapabilityStatus.GRANTED)
        }
        Text(description, style = AppleTypography.bodySmall, color = AppleColors.secondary)

        if (status != CapabilityStatus.GRANTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (status == CapabilityStatus.MISSING) {
                    Box(Modifier.weight(1f)) {
                        PillButton(label = actionLabel, onClick = onAction)
                    }
                }
                if (secondaryLabel != null && onSecondary != null) {
                    Box(Modifier.weight(1f)) {
                        PillButton(label = secondaryLabel, onClick = onSecondary)
                    }
                }
            }
        }
        footer?.invoke()
    }
}

/** Small pill of text next to a title ("recommended", "optional"). */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppleTypography.labelSmall,
        color = AppleColors.secondary,
        modifier = modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The phases of a running test, so the wait is explained instead of spun through: past phases are
 * checked off, the current one is highlighted, later ones stay dim.
 */
@Composable
fun TestPhaseList(
    phases: List<String>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        phases.forEachIndexed { index, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (index < currentIndex) {
                        SelectedCheck(visible = true)
                    } else {
                        Box(
                            Modifier
                                .size(if (index == currentIndex) 9.dp else 6.dp)
                                .background(
                                    if (index == currentIndex) AppleColors.accent
                                    else AppleColors.quaternary,
                                    CircleShape,
                                )
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    label,
                    style = AppleTypography.titleMedium,
                    color = when {
                        index < currentIndex -> AppleColors.secondary
                        index == currentIndex -> AppleColors.primary
                        else -> AppleColors.tertiary
                    },
                )
            }
        }
    }
}

/** A quiet horizontal rule between blocks of a step. */
@Composable
fun OnboardingSpacerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(AppleColors.frostedBorder)
    )
}
