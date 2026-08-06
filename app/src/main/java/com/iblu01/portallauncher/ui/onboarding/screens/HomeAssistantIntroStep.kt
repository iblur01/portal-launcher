package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.onboarding.DiscoveryState
import com.iblu01.portallauncher.ui.onboarding.HomeCandidate
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.ChoiceTile
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

/** How long the mDNS sweep is given before the step admits it found nothing. */
private const val DISCOVERY_TIMEOUT_MS = 8_000L

/**
 * Offers — never demands — a Home Assistant connection.
 *
 * The step starts an mDNS sweep on its own and turns whatever comes back into the shortest possible
 * path: one detected home becomes a single "continue with this home" action, several become a list,
 * and nothing found simply falls back to the manual form. Skipping is always one tap away, because
 * Portal is a complete launcher without a home behind it.
 */
@Composable
fun HomeAssistantIntroStep(
    state: OnboardingUiState,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onDiscoveryTimeout: () -> Unit,
    onSelectHome: (HomeCandidate) -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onStartDiscovery()
        onDispose { onStopDiscovery() }
    }

    // A sweep that never answers would otherwise spin forever: after the timeout the step says so.
    val searching = state.haDiscovery is DiscoveryState.Searching
    LaunchedEffect(searching) {
        if (searching) {
            delay(DISCOVERY_TIMEOUT_MS)
            onDiscoveryTimeout()
        }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_ha_intro_title),
        modifier = modifier,
        description = stringResource(R.string.onb_ha_intro_body),
        aside = { ConnectedHomeGlimpse() },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_ha_intro_action_manual),
                onPrimary = onManual,
                skipLabel = stringResource(R.string.onb_common_nav_skip),
                onSkip = onSkip,
            )
        },
    ) {
        Text(
            stringResource(R.string.onb_ha_intro_question),
            style = AppleTypography.titleLarge,
            color = AppleColors.primary,
        )

        AnimatedContent(
            targetState = state.haDiscovery,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "ha-discovery",
        ) { discovery ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (discovery) {
                    is DiscoveryState.Searching -> SearchingIndicator()

                    is DiscoveryState.Found -> DiscoveredHomes(
                        homes = state.discoveredHomes,
                        onSelectHome = onSelectHome,
                    )

                    DiscoveryState.NothingFound -> Text(
                        stringResource(R.string.onb_ha_intro_discovery_found_none),
                        style = AppleTypography.bodyLarge,
                        color = AppleColors.secondary,
                    )

                    DiscoveryState.Idle -> Unit
                }
            }
        }
    }
}

/** Discreet "we're looking" line: a small indicator next to the sentence, not a full-screen spinner. */
@Composable
private fun SearchingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = AppleColors.secondary,
            strokeWidth = 1.5.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.onb_ha_intro_discovery_searching),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
    }
}

/**
 * The homes mDNS answered with. A single result is the common case and gets its own large action, so
 * the user confirms rather than chooses from a list of one.
 */
@Composable
private fun DiscoveredHomes(
    homes: List<HomeCandidate>,
    onSelectHome: (HomeCandidate) -> Unit,
) {
    if (homes.isEmpty()) {
        Text(
            stringResource(R.string.onb_ha_intro_discovery_found_none),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
        return
    }

    Text(
        pluralStringResource(R.plurals.onb_ha_intro_discovery_found_count, homes.size, homes.size),
        style = AppleTypography.bodySmall,
        color = AppleColors.secondary,
    )

    homes.forEach { home ->
        ChoiceTile(
            title = home.name,
            subtitle = home.url,
            selected = false,
            onClick = { onSelectHome(home) },
        )
    }

    if (homes.size == 1) {
        val only = homes.first()
        PillButton(
            label = stringResource(R.string.onb_ha_intro_action_continue),
            onClick = { onSelectHome(only) },
            primary = true,
        )
    }
}

/**
 * A still picture of what a connected home adds to the screen: a lit lamp, an opening, the weather,
 * an alarm. Deliberately motionless — the onboarding explains the calm launcher, it should not
 * animate at the user while doing so.
 */
@Composable
private fun ConnectedHomeGlimpse(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlimpsePill(Icons.Outlined.Lightbulb, AppleColors.warning, 0.62f)
        GlimpsePill(Icons.Outlined.MeetingRoom, AppleColors.accent, 0.48f)
        GlimpsePill(Icons.Outlined.WbSunny, AppleColors.secondary, 0.55f)
        GlimpsePill(Icons.Outlined.NotificationsActive, AppleColors.active, 0.40f)
    }
}

/**
 * One capsule of the illustration: an icon and an abstract label bar.
 *
 * The bar stands in for the entity's name on purpose — inventing "Kitchen light" here would put an
 * untranslated, made-up device name in front of the user before their home is even connected.
 */
@Composable
private fun GlimpsePill(icon: ImageVector, tint: Color, labelWidth: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppleShapes.pill)
            .background(AppleColors.elevated, AppleShapes.pill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .fillMaxWidth(labelWidth)
                .height(6.dp)
                .background(AppleColors.quaternary, CircleShape)
        )
    }
}
