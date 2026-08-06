package com.iblu01.portallauncher.ui.onboarding.screens

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsSearchField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleMotion
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.delay

/** The three situations the demo walks through, in order. */
private enum class DemoSituation { NORMAL, DOOR, ALARM }

/**
 * Explains what makes Portal's home screen different: pills are ranked by what matters, not by the
 * order the devices happen to be in.
 *
 * The demo is the explanation — three situations, each re-ordering the same pills — and it can be
 * driven by hand as well as on its own.
 */
@Composable
fun PillsIntroStep(
    state: OnboardingUiState,
    onAcceptRecommended: () -> Unit,
    onLoadPillOptions: () -> Unit,
    onSetPillEnabled: (entityId: String, enabled: Boolean) -> Unit,
    onSetAllPillsEnabled: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var choosing by remember { mutableStateOf(false) }

    if (choosing) {
        PillSelectionSubPage(
            state = state,
            onLoadPillOptions = onLoadPillOptions,
            onSetPillEnabled = onSetPillEnabled,
            onSetAllPillsEnabled = onSetAllPillsEnabled,
            onClose = { choosing = false },
            onContinue = onContinue,
            modifier = modifier,
        )
        return
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_pills_title),
        description = stringResource(R.string.onb_pills_body),
        modifier = modifier,
        aside = { PillDemo() },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_pills_choice_recommended),
                onPrimary = onAcceptRecommended,
                secondaryLabel = stringResource(R.string.onb_pills_choice_custom),
                onSecondary = { choosing = true },
            )
        },
    ) {
        Text(
            stringResource(R.string.onb_pills_explanation_priority),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
        Text(
            stringResource(R.string.onb_pills_explanation_more),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
    }
}

/**
 * Three situations, cycling slowly, or held on one frame when the system asks for no animation.
 * Tapping a caption jumps to that situation, so the point can be read at the user's own pace.
 */
@Composable
private fun PillDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animated = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    var situationIndex by remember { mutableIntStateOf(0) }
    val situation = DemoSituation.values()[situationIndex]

    if (animated) {
        LaunchedEffect(situationIndex) {
            delay(4_000)
            situationIndex = (situationIndex + 1) % DemoSituation.values().size
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pillsFor(situation).forEach { pill ->
                DemoPill(label = stringResource(pill.labelRes), tone = pill.tone)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoSituation.values().forEachIndexed { index, entry ->
                val selected = index == situationIndex
                val color by animateColorAsState(
                    targetValue = if (selected) AppleColors.primary else AppleColors.quaternary,
                    animationSpec = AppleMotion.spring(),
                    label = "demo-dot",
                )
                Box(
                    Modifier
                        .size(if (selected) 7.dp else 5.dp)
                        .background(color, CircleShape)
                        .appleClickable { situationIndex = index }
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                stringResource(captionFor(situation)),
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
                modifier = Modifier.appleClickable {
                    situationIndex = (situationIndex + 1) % DemoSituation.values().size
                },
            )
        }
    }
}

/** One capsule of the demo. [tone] is what carries the "this one matters more" signal. */
@Composable
private fun DemoPill(label: String, tone: PillTone) {
    val emphasis by animateFloatAsState(
        targetValue = if (tone == PillTone.QUIET) 0.08f else 0.18f,
        animationSpec = AppleMotion.spring(),
        label = "pill-emphasis",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (tone) {
                    PillTone.QUIET -> AppleColors.primary.copy(alpha = emphasis)
                    PillTone.ATTENTION -> AppleColors.warning.copy(alpha = emphasis)
                    PillTone.URGENT -> AppleColors.error.copy(alpha = emphasis)
                },
                AppleShapes.pill,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    when (tone) {
                        PillTone.QUIET -> AppleColors.secondary
                        PillTone.ATTENTION -> AppleColors.warning
                        PillTone.URGENT -> AppleColors.error
                    },
                    CircleShape,
                )
        )
        Spacer(Modifier.size(10.dp))
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
    }
}

private enum class PillTone { QUIET, ATTENTION, URGENT }

private data class DemoPillSpec(val labelRes: Int, val tone: PillTone)

/**
 * The same home, three moments. The list order *is* the message: the door and then the alarm move
 * to the top as they start mattering.
 */
private fun pillsFor(situation: DemoSituation): List<DemoPillSpec> {
    val quiet = listOf(
        DemoPillSpec(R.string.onb_pills_demo_pill_light, PillTone.QUIET),
        DemoPillSpec(R.string.onb_pills_demo_pill_weather, PillTone.QUIET),
        DemoPillSpec(R.string.onb_pills_demo_pill_temperature, PillTone.QUIET),
    )
    return when (situation) {
        DemoSituation.NORMAL -> quiet
        DemoSituation.DOOR -> listOf(
            DemoPillSpec(R.string.onb_pills_demo_pill_door, PillTone.ATTENTION)
        ) + quiet
        DemoSituation.ALARM -> listOf(
            DemoPillSpec(R.string.onb_pills_demo_pill_alarm, PillTone.URGENT),
            DemoPillSpec(R.string.onb_pills_demo_pill_door, PillTone.ATTENTION),
        ) + quiet
    }
}

private fun captionFor(situation: DemoSituation): Int = when (situation) {
    DemoSituation.NORMAL -> R.string.onb_pills_demo_caption_normal
    DemoSituation.DOOR -> R.string.onb_pills_demo_caption_door
    DemoSituation.ALARM -> R.string.onb_pills_demo_caption_alarm
}

/**
 * The optional per-entity selection. Deliberately light: a search field and a switch per entity,
 * closable at any point — nobody should have to walk a whole house's worth of devices to finish
 * setting up a launcher.
 */
@Composable
private fun PillSelectionSubPage(
    state: OnboardingUiState,
    onLoadPillOptions: () -> Unit,
    onSetPillEnabled: (String, Boolean) -> Unit,
    onSetAllPillsEnabled: (Boolean) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadPillOptions() }
    var query by remember { mutableStateOf("") }
    val visible = remember(state.pillOptions, query) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) state.pillOptions
        else state.pillOptions.filter { it.label.lowercase().contains(needle) }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_pills_choice_custom),
        modifier = modifier,
        navigation = {
            OnboardingNavigationBar(
                onBack = onClose,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = onContinue,
            )
        },
    ) {
        SettingsSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.onb_pills_search_placeholder),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(R.string.onb_pills_action_enable_all),
                    onClick = { onSetAllPillsEnabled(true) },
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(R.string.onb_pills_action_disable_all),
                    onClick = { onSetAllPillsEnabled(false) },
                )
            }
        }
        if (visible.isEmpty() && !state.pillOptionsLoading) {
            Text(
                stringResource(R.string.onb_pills_empty_state),
                style = AppleTypography.bodyLarge,
                color = AppleColors.secondary,
            )
        } else {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(visible, key = { it.entityId }) { option ->
                    SettingsToggle(
                        label = option.label,
                        checked = option.enabled,
                        onCheckedChange = { onSetPillEnabled(option.entityId, it) },
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
