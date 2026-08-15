package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.SettingsSearchField
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.Badge
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Optional step: which app a tap on the empty part of the home screen opens.
 *
 * The gesture is off until an app is chosen here — "no app" is the default selection and a real
 * answer, not a postponed one, so a tap in the void simply does nothing.
 */
@Composable
fun TapAppStep(
    state: OnboardingUiState,
    onLoadApps: () -> Unit,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadApps() }

    var selected by remember { mutableStateOf(state.tapAppPackage) }
    var query by remember { mutableStateOf("") }

    val visible = remember(state.apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) state.apps
        else state.apps.filter { it.label.contains(needle, ignoreCase = true) }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_tap_app_title),
        description = stringResource(R.string.onb_tap_app_body),
        modifier = modifier,
        aside = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (selected.isBlank()) stringResource(R.string.onb_tap_app_summary_none)
                    else state.apps.firstOrNull { it.packageName == selected }?.label ?: selected,
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                )
                Text(
                    stringResource(R.string.onb_tap_app_hint_settings),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                )
            }
        },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_continue),
                onPrimary = { onConfirm(selected) },
                secondaryLabel = stringResource(R.string.onb_tap_app_action_none),
                onSecondary = { onConfirm("") },
            )
        },
    ) {
        SettingsSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.onb_hidden_search_placeholder),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.card)
                .background(AppleColors.frostedFill, AppleShapes.card)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                .padding(vertical = 6.dp),
        ) {
            ChoiceRow(
                label = stringResource(R.string.onb_tap_app_action_none),
                selected = selected.isBlank(),
                onClick = { selected = "" },
            )
            visible.forEach { app ->
                ChoiceRow(
                    label = app.label,
                    selected = app.packageName == selected,
                    recommended = app.recommended,
                    onClick = { selected = app.packageName },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    recommended: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppleTypography.titleMedium,
            color = if (selected) AppleColors.accent else AppleColors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (recommended) {
            Badge(stringResource(R.string.onb_hidden_badge_recommended))
            Spacer(Modifier.width(10.dp))
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = AppleColors.accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
