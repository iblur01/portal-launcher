package com.iblu01.portallauncher.ui.onboarding.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * How much room the assistant has, and what that implies.
 *
 * Derived from the actual window rather than from the device type: the same 8" panel is [COMPACT]
 * in portrait and [MEDIUM] in landscape, and a phone in landscape is short before it is narrow.
 */
enum class OnboardingSize {
    /** Phone, or any window under ~600dp wide: one column, tight margins. */
    COMPACT,

    /** Small tablet / large phone in landscape: one column, comfortable margins. */
    MEDIUM,

    /** 10"+ panel: explanation on the left, interactive area on the right. */
    EXPANDED,
}

/** Metrics the steps read to size their own previews without re-measuring the window. */
@Immutable
data class OnboardingLayout(
    val size: OnboardingSize,
    /** True when the window is short (phone in landscape, split screen): trim vertical spacing. */
    val short: Boolean,
    /** Whether the explanation and the interactive area sit side by side. */
    val twoColumn: Boolean,
    /**
     * False on a window too short to show an illustration and its step's controls at once. The
     * previews are explanatory, not load-bearing: dropping them beats scrolling past them.
     */
    val showPreview: Boolean,
    /** Widest a preview/illustration may be, so a 16:10 card can never outgrow its column. */
    val previewMaxWidth: Dp,
    val spacing: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
)

val LocalOnboardingLayout = staticCompositionLocalOf {
    OnboardingLayout(
        size = OnboardingSize.MEDIUM,
        short = false,
        twoColumn = false,
        showPreview = true,
        previewMaxWidth = 420.dp,
        spacing = 16.dp,
        horizontalPadding = 28.dp,
        verticalPadding = 24.dp,
    )
}

/**
 * Measures the window once and publishes the result through [LocalOnboardingLayout].
 *
 * Wrapped around the whole flow rather than around each step: a step needs its layout *before* it
 * calls [OnboardingScaffold] — to decide how many cards it can show at once, for instance — and a
 * local provided inside the scaffold would only reach the scaffold's own slots.
 */
@Composable
fun ProvideOnboardingLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val size = when {
            maxWidth >= 840.dp -> OnboardingSize.EXPANDED
            maxWidth >= 600.dp -> OnboardingSize.MEDIUM
            else -> OnboardingSize.COMPACT
        }
        val short = maxHeight < 480.dp
        val horizontalPadding = when (size) {
            OnboardingSize.COMPACT -> 18.dp
            OnboardingSize.MEDIUM -> 28.dp
            OnboardingSize.EXPANDED -> 40.dp
        }
        // Two columns are a large-display composition, not a fallback for missing height. A short
        // phone / small panel gets a focused, paged flow instead of a squeezed tablet layout.
        val twoColumn = size == OnboardingSize.EXPANDED
        val widthBudget = when {
            twoColumn -> minOf(maxWidth * 0.42f, 460.dp)
            size == OnboardingSize.COMPACT -> maxWidth - horizontalPadding * 2
            else -> minOf(maxWidth - horizontalPadding * 2, 460.dp)
        }
        // Previews are roughly 16:10, so their width is what decides their height. Bound it by the
        // height budget as well, or a card sized purely from the width overflows a short window.
        val heightBudget = maxHeight * if (short) 0.45f else 0.52f

        CompositionLocalProvider(
            LocalOnboardingLayout provides OnboardingLayout(
                size = size,
                short = short,
                twoColumn = twoColumn,
                // Under ~340dp of height there is only room for the words and the actions.
                showPreview = maxHeight >= 340.dp,
                previewMaxWidth = minOf(widthBudget, heightBudget * 1.6f),
                spacing = if (short || size == OnboardingSize.COMPACT) 12.dp else 16.dp,
                horizontalPadding = horizontalPadding,
                verticalPadding = if (short) 10.dp else 24.dp,
            )
        ) { content() }
    }
}

/**
 * The frame every onboarding step sits in: chapter progress at the top, one short title and
 * explanation, a scrolling interactive area, and a navigation bar pinned to the bottom.
 *
 * The layout follows the window, not the device (see [ProvideOnboardingLayout]): a 10" panel gets
 * two columns, everything smaller stacks into one. Margins, spacing and the title's size step down
 * with the window so a 5" screen spends its pixels on the content instead of on padding. The bottom
 * bar never scrolls away, and the content area scrolls on its own when it does not fit.
 */
@Composable
fun OnboardingScaffold(
    step: OnboardingStep,
    flags: OnboardingFlags,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    showProgress: Boolean = true,
    /** Compact sub-pages can spend the whole content area on the current decision. */
    showHeader: Boolean = true,
    /** A visual that belongs next to the text rather than inside the interactive column. */
    aside: (@Composable () -> Unit)? = null,
    navigation: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val layout = LocalOnboardingLayout.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF14181D), AppleColors.background),
                    radius = 1800f,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                // Very wide wall panels: keep the flow at a readable measure, centred.
                .widthIn(max = 1280.dp)
                .align(Alignment.TopCenter)
                .padding(
                    horizontal = layout.horizontalPadding,
                    vertical = layout.verticalPadding,
                )
        ) {
            if (showProgress) {
                OnboardingProgress(step = step, flags = flags)
                Spacer(Modifier.height(if (layout.short) 14.dp else 24.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (layout.twoColumn) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(if (layout.short) 24.dp else 36.dp),
                    ) {
                        ScrollingColumn(Modifier.weight(0.42f), layout.spacing) {
                            if (showHeader) OnboardingHeader(title, description, aside)
                        }
                        ScrollingColumn(Modifier.weight(0.58f), layout.spacing) { content() }
                    }
                } else {
                    ScrollingColumn(Modifier.fillMaxWidth(), layout.spacing) {
                        if (showHeader) OnboardingHeader(title, description, aside)
                        content()
                    }
                }
            }

            if (navigation != null) {
                Spacer(Modifier.height(if (layout.short) 12.dp else 20.dp))
                navigation()
            }
        }
    }
}

/**
 * A column that scrolls only when it has to, and centres its content otherwise — which is what
 * keeps a short step from floating at the top of a tall panel with a hole underneath it.
 */
@Composable
private fun ScrollingColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun OnboardingHeader(
    title: String,
    description: String?,
    aside: (@Composable () -> Unit)?,
) {
    val layout = LocalOnboardingLayout.current
    // The type steps down on a small or short window: a headline that takes four lines is what
    // pushes the rest of the step off the screen.
    val cramped = layout.size == OnboardingSize.COMPACT || layout.short
    Column {
        Text(
            title,
            style = if (cramped) AppleTypography.titleLarge else AppleTypography.headlineLarge,
            color = AppleColors.primary,
        )
        if (description != null) {
            Spacer(Modifier.height(if (cramped) 6.dp else 12.dp))
            Text(
                description,
                style = if (cramped) AppleTypography.bodySmall else AppleTypography.bodyLarge,
                color = AppleColors.secondary,
            )
        }
        if (aside != null && layout.showPreview) {
            Spacer(Modifier.height(if (layout.short) 12.dp else 24.dp))
            Box(Modifier.widthIn(max = layout.previewMaxWidth)) { aside() }
        }
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
    val layout = LocalOnboardingLayout.current
    if (layout.size != OnboardingSize.EXPANDED) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(chapterLabel(step.chapter)),
                style = AppleTypography.labelSmall,
                color = AppleColors.primary,
            )
            StepDots(
                count = visibleSteps(step.chapter, flags).size,
                current = indexInChapter(step, flags),
            )
        }
        return
    }

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
                    StepDots(
                        count = visibleSteps(chapter, flags).size,
                        current = indexInChapter(step, flags),
                    )
                }
            }
        }
    }
}

/**
 * A dot per item, the current one slightly larger. Used for the chapter's steps, and by a step that
 * splits itself into pages on a small screen.
 */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
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
 * Every button is weighted rather than sized: [PillButton] fills its slot, so a fixed-width slot
 * next to a flexible one lets one button eat the row and squeeze the other to nothing. On a compact
 * or short window the row becomes a stack, main action first — a 5" screen in landscape cannot fit
 * four capsules side by side and still be tappable.
 *
 * [skipLabel] stays null on the steps the launcher cannot work without, so "skip" never appears
 * where it would leave Portal half-configured. A null [onPrimary] renders the main action dimmed
 * and inert, which is how a form says "not yet" without the button moving.
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
    val layout = LocalOnboardingLayout.current
    val primary: @Composable (Modifier) -> Unit = { slot ->
        Box(slot.alpha(if (onPrimary == null) 0.35f else 1f)) {
            PillButton(label = primaryLabel, onClick = onPrimary ?: {}, primary = true)
        }
    }
    val back = stringResource(R.string.onb_common_nav_back)

    // One column of buttons: the main action is reachable with a thumb, the rest sits under it.
    // Not when the window is also short — there, stacking would eat the little height left for the
    // step itself, and a narrow row still fits two capsules.
    if (layout.size == OnboardingSize.COMPACT && !layout.short) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            primary(Modifier.fillMaxWidth())
            if (secondaryLabel != null && onSecondary != null) {
                PillButton(label = secondaryLabel, onClick = onSecondary)
            }
            if (onBack != null || (skipLabel != null && onSkip != null)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onBack != null) {
                        Box(Modifier.weight(1f)) { PillButton(label = back, onClick = onBack) }
                    }
                    if (skipLabel != null && onSkip != null) {
                        Box(Modifier.weight(1f)) { PillButton(label = skipLabel, onClick = onSkip) }
                    }
                }
            }
        }
        return
    }

    if (layout.short) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onBack != null) {
                Box(Modifier.weight(1f)) { PillButton(label = back, onClick = onBack) }
            }
            if (skipLabel != null && onSkip != null) {
                Box(Modifier.weight(1f)) { PillButton(label = skipLabel, onClick = onSkip) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                Box(Modifier.weight(1f)) { PillButton(label = secondaryLabel, onClick = onSecondary) }
            }
            primary(Modifier.weight(1.6f))
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.weight(1f)) { PillButton(label = back, onClick = onBack) }
        }
        if (skipLabel != null && onSkip != null) {
            Box(Modifier.weight(1f)) { PillButton(label = skipLabel, onClick = onSkip) }
        }
        // Pushes the actions to the trailing edge — but only when something holds the leading one,
        // otherwise the row would hug the right with a hole beside it.
        if (onBack != null || (skipLabel != null && onSkip != null)) {
            Spacer(Modifier.weight(0.6f))
        }
        if (secondaryLabel != null && onSecondary != null) {
            Box(Modifier.weight(1.4f)) { PillButton(label = secondaryLabel, onClick = onSecondary) }
        }
        primary(Modifier.weight(1.8f))
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
