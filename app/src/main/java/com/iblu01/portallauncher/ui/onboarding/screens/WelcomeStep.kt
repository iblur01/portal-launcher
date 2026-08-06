package com.iblu01.portallauncher.ui.onboarding.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iblu01.portallauncher.AppLanguage
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.LocalOnboardingLayout
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

/**
 * First screen of the assistant: what Portal is, and the two ways out of this screen.
 *
 * No progress bar and no back button — the flow has not started yet. Skipping is deliberately
 * gated behind a confirmation: the alternative is a launcher silently left on its defaults.
 */
@Composable
fun WelcomeStep(
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onSkipEverything: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) { Prefs(context) }
    var languageSelected by remember { mutableStateOf(prefs.onboardingLanguageSelected) }
    var askingToSkip by remember { mutableStateOf(false) }

    if (!languageSelected) {
        InitialLanguageStep(
            state = state,
            onSelect = { language ->
                val changed = prefs.appLanguage != language.code
                prefs.appLanguage = language.code
                prefs.onboardingLanguageSelected = true
                if (changed) {
                    restartOnboarding(context)
                } else {
                    languageSelected = true
                }
            },
            modifier = modifier,
        )
        return
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_welcome_title),
        modifier = modifier,
        description = stringResource(R.string.onb_welcome_body),
        showProgress = false,
        navigation = {
            OnboardingNavigationBar(
                onBack = null,
                primaryLabel = stringResource(R.string.onb_welcome_action_primary),
                onPrimary = onContinue,
                secondaryLabel = stringResource(R.string.onb_welcome_action_secondary),
                onSecondary = { askingToSkip = true },
            )
        },
    ) {
        if (LocalOnboardingLayout.current.showPreview) {
            HomeScreenDemo(Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (askingToSkip) {
        SkipConfirmationDialog(
            onDismiss = { askingToSkip = false },
            onConfirm = {
                askingToSkip = false
                onSkipEverything()
            },
        )
    }

}

/** First-run language choice, intentionally before any product copy is presented. */
@Composable
private fun InitialLanguageStep(
    state: OnboardingUiState,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_language_title),
        description = stringResource(R.string.onb_language_body),
        modifier = modifier,
        showProgress = false,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppLanguage.values().forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppleShapes.section)
                        .background(AppleColors.frostedFill, AppleShapes.section)
                        .appleClickable { onSelect(language) }
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(language.flag, style = AppleTypography.titleLarge)
                    Text(
                        stringResource(language.nameRes),
                        style = AppleTypography.titleMedium,
                        color = AppleColors.primary,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The looping demo of the real home screen
// ---------------------------------------------------------------------------------------------

private const val DEMO_CLOCK = 0
private const val DEMO_PILLS = 1
private const val DEMO_APPS = 2

/** How long each frame of the demo is held. Long on purpose: the launcher it shows is a calm one. */
private const val DEMO_FRAME_MILLIS = 2600L

/**
 * A slow, silent re-enactment of the home screen: the clock alone, then the pills that matter, then
 * a swipe onto the app grid, then back to the clock.
 *
 * Built out of plain shapes rather than the real `ClockScreen`, which needs a live ViewModel and a
 * configured home — neither of which exists yet on this screen.
 */
@Composable
private fun HomeScreenDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) { animatorScale(context) != 0f }

    var phase by remember { mutableIntStateOf(DEMO_CLOCK) }
    LaunchedEffect(animationsEnabled) {
        if (!animationsEnabled) {
            // Reduced animations: hold one representative frame instead of looping.
            phase = DEMO_PILLS
            return@LaunchedEffect
        }
        while (true) {
            delay(DEMO_FRAME_MILLIS)
            phase = (phase + 1) % 3
        }
    }

    val pillsPresence by animateFloatAsState(
        targetValue = if (phase == DEMO_PILLS) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "demo-pills",
    )
    val swipe by animateFloatAsState(
        targetValue = if (phase == DEMO_APPS) 1f else 0f,
        animationSpec = tween(durationMillis = 1100),
        label = "demo-swipe",
    )

    val layout = LocalOnboardingLayout.current
    BoxWithConstraints(
        modifier = modifier
            .widthIn(max = layout.previewMaxWidth)
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(AppleShapes.card)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF11151A), AppleColors.background)),
                AppleShapes.card,
            )
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card),
    ) {
        val pageWidth = maxWidth

        DemoClockPage(
            pillsPresence = pillsPresence,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -swipe * pageWidth.toPx()
                    alpha = 1f - swipe * 0.4f
                },
        )
        DemoAppsPage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (1f - swipe) * pageWidth.toPx()
                    alpha = swipe
                },
        )
    }
}

/** Clock page: the time, its date line, and the pills fading in underneath. */
@Composable
private fun DemoClockPage(pillsPresence: Float, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            // Numerals only: the demo carries no copy to translate.
            text = "9:41",
            style = AppleTypography.displayLarge.copy(fontSize = 52.sp),
            color = AppleColors.primary,
        )
        Spacer(Modifier.height(10.dp))
        DemoBar(width = 108.dp, height = 7.dp, alpha = 0.28f)
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.alpha(pillsPresence),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DemoPill(dot = AppleColors.active, width = 84.dp)
            DemoPill(dot = AppleColors.warning, width = 66.dp)
            DemoPill(dot = AppleColors.accent, width = 74.dp)
        }
    }
}

/** App page: the grid the swipe reveals, as placeholder icons. */
@Composable
private fun DemoAppsPage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(5) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .background(
                                    AppleColors.primary.copy(alpha = 0.10f),
                                    RoundedCornerShape(10.dp),
                                )
                        )
                        Spacer(Modifier.height(6.dp))
                        DemoBar(width = 24.dp, height = 4.dp, alpha = 0.18f)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoPill(dot: Color, width: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .width(width),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot.copy(alpha = 0.85f), CircleShape))
        DemoBar(width = width / 2, height = 5.dp, alpha = 0.30f)
    }
}

@Composable
private fun DemoBar(width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp, alpha: Float) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(AppleColors.primary.copy(alpha = alpha), AppleShapes.pill)
    )
}

/** `ANIMATOR_DURATION_SCALE`, which is 0 when the user asked the system to stop animating. */
private fun animatorScale(context: Context): Float = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
}.getOrDefault(1f)

// ---------------------------------------------------------------------------------------------
// Language
// ---------------------------------------------------------------------------------------------

/**
 * Recreate the assistant task so [LocaleHelper] wraps the new activity with the chosen locale.
 * Killing the process after launching the replacement raced with that activity and could leave a
 * black screen on slower devices.
 */
private fun restartOnboarding(context: Context) {
    val intent = Intent(context, com.iblu01.portallauncher.ui.onboarding.OnboardingActivity::class.java)
        .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
    context.startActivity(intent)
}

// ---------------------------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------------------------

/** "Skip setup?" — the only way out of the flow, and never without this step. */
@Composable
private fun SkipConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    OnboardingDialog(
        title = stringResource(R.string.onb_welcome_skip_dialog_title),
        onDismiss = onDismiss,
    ) {
        Text(
            stringResource(R.string.onb_welcome_skip_dialog_body),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
        Spacer(Modifier.height(4.dp))
        PillButton(
            label = stringResource(R.string.onb_welcome_skip_dialog_continue),
            onClick = onDismiss,
            primary = true,
        )
        PillButton(
            label = stringResource(R.string.onb_welcome_skip_dialog_confirm),
            onClick = onConfirm,
        )
    }
}

/** Shared shell for this step's two modals, in the same frosted language as the rest of the flow. */
@Composable
private fun OnboardingDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .clip(AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
            color = AppleColors.elevated,
            shape = AppleShapes.panel,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = AppleTypography.titleLarge, color = AppleColors.primary)
                content()
            }
        }
    }
}
