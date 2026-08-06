package com.iblu01.portallauncher.ui.onboarding.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.onboarding.OnboardingChapter
import com.iblu01.portallauncher.ui.onboarding.OnboardingFlags
import com.iblu01.portallauncher.ui.onboarding.OnboardingStep
import com.iblu01.portallauncher.ui.onboarding.indexInChapter
import com.iblu01.portallauncher.ui.onboarding.visibleSteps
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * The frame every onboarding step sits in: chapter progress at the top, one short title and
 * explanation, a scrolling interactive area, and a navigation bar pinned to the bottom.
 *
 * The layout follows the width: on a panel or tablet in landscape the explanation stays on the left
 * and the interactive area takes the right; on a narrow screen they stack. The bottom bar never
 * scrolls away in either case.
 */
@Composable
fun OnboardingScaffold(
    step: OnboardingStep,
    flags: OnboardingFlags,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    showProgress: Boolean = true,
    /** A visual that belongs next to the text rather than inside the interactive column. */
    aside: (@Composable () -> Unit)? = null,
    navigation: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF14181D), AppleColors.background),
                    radius = 1800f,
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        val wide = maxWidth >= 720.dp

        Column(Modifier.fillMaxSize()) {
            if (showProgress) {
                OnboardingProgress(step = step, flags = flags)
                Spacer(Modifier.height(24.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (wide) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                        Column(
                            Modifier
                                .weight(0.42f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            OnboardingHeader(title, description, aside)
                        }
                        Column(
                            Modifier
                                .weight(0.58f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) { content() }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OnboardingHeader(title, description, aside)
                        content()
                    }
                }
            }

            if (navigation != null) {
                Spacer(Modifier.height(20.dp))
                navigation()
            }
        }
    }
}

@Composable
private fun OnboardingHeader(
    title: String,
    description: String?,
    aside: (@Composable () -> Unit)?,
) {
    Text(title, style = AppleTypography.headlineLarge, color = AppleColors.primary)
    if (description != null) {
        Spacer(Modifier.height(12.dp))
        Text(description, style = AppleTypography.bodyLarge, color = AppleColors.secondary)
    }
    if (aside != null) {
        Spacer(Modifier.height(24.dp))
        aside()
    }
}

/**
 * Progress by chapter — "Launcher / Home / Finish" plus one dot per remaining step — rather than a
 * "step 4 of 13" counter, which would only advertise how long the flow is.
 */
@Composable
fun OnboardingProgress(
    step: OnboardingStep,
    flags: OnboardingFlags,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OnboardingChapter.values().forEach { chapter ->
            val active = chapter == step.chapter
            val done = chapter.ordinal < step.chapter.ordinal
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(chapterLabel(chapter)),
                    style = AppleTypography.labelSmall,
                    color = when {
                        active -> AppleColors.primary
                        done -> AppleColors.secondary
                        else -> AppleColors.quaternary
                    },
                )
                if (active) {
                    Spacer(Modifier.width(10.dp))
                    ChapterDots(
                        count = visibleSteps(chapter, flags).size,
                        current = indexInChapter(step, flags),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterDots(count: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val diameter by animateDpAsState(
                targetValue = if (index == current) 7.dp else 5.dp,
                animationSpec = AppleMotion.spring(),
                label = "dot-size",
            )
            val opacity by animateFloatAsState(
                targetValue = if (index <= current) 1f else 0.25f,
                animationSpec = AppleMotion.spring(),
                label = "dot-alpha",
            )
            Box(
                Modifier
                    .size(diameter)
                    .background(AppleColors.primary.copy(alpha = opacity), CircleShape)
            )
        }
    }
}

private fun chapterLabel(chapter: OnboardingChapter): Int = when (chapter) {
    OnboardingChapter.LAUNCHER -> R.string.onb_common_chapter_launcher
    OnboardingChapter.HOME -> R.string.onb_common_chapter_home
    OnboardingChapter.FINISH -> R.string.onb_common_chapter_finish
}

/**
 * The bar every step after the welcome screen shares: back on the left, an optional skip, and the
 * step's own main action on the right.
 *
 * [skipLabel] stays null on the steps the launcher cannot work without, so "skip" never appears
 * where it would leave Portal half-configured. A null [onPrimary] renders the main action dimmed
 * and inert, which is how a form says "not yet" without the button disappearing.
 */
@Composable
fun OnboardingNavigationBar(
    onBack: (() -> Unit)?,
    primaryLabel: String,
    onPrimary: (() -> Unit)?,
    modifier: Modifier = Modifier,
    skipLabel: String? = null,
    onSkip: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.width(140.dp)) {
                PillButton(label = stringResource(R.string.onb_common_nav_back), onClick = onBack)
            }
        }
        if (skipLabel != null && onSkip != null) {
            Box(Modifier.width(170.dp)) {
                PillButton(label = skipLabel, onClick = onSkip)
            }
        }
        Spacer(Modifier.weight(1f))
        if (secondaryLabel != null && onSecondary != null) {
            Box(Modifier.widthIn(min = 170.dp)) {
                PillButton(label = secondaryLabel, onClick = onSecondary)
            }
        }
        Box(Modifier.widthIn(min = 200.dp).alpha(if (onPrimary == null) 0.35f else 1f)) {
            PillButton(label = primaryLabel, onClick = onPrimary ?: {}, primary = true)
        }
    }
}

/** Title + body, centred. Used by the loading, success and failure screens. */
@Composable
fun OnboardingCenteredMessage(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            title,
            style = AppleTypography.headlineLarge,
            color = AppleColors.primary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                body,
                style = AppleTypography.bodyLarge,
                color = AppleColors.secondary,
                textAlign = TextAlign.Center,
            )
        }
        content?.invoke()
    }
}
