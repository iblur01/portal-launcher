package com.iblu01.portallauncher.ui.onboarding.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.apps.toImageBitmap
import com.iblu01.portallauncher.ui.components.SettingsSearchField
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.onboarding.OnboardingApp
import com.iblu01.portallauncher.ui.onboarding.OnboardingUiState
import com.iblu01.portallauncher.ui.onboarding.components.Badge
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingNavigationBar
import com.iblu01.portallauncher.ui.onboarding.components.OnboardingScaffold
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The three shortcuts offered above the list; only a highlight, never an applied change. */
private enum class QuickAction { SHOW_ALL, HIDE_SYSTEM }

/**
 * "Keep only what matters": pick the apps that disappear from the launcher.
 *
 * The selection is local until the user applies it — the quick actions are proposals, so
 * "hide system apps" fills the selection but nothing is written before "apply". Apps the launcher
 * needs are rendered locked rather than silently ignored, so a tap on them is never a mystery.
 */
@Composable
fun HiddenAppsStep(
    state: OnboardingUiState,
    onLoadApps: () -> Unit,
    onApply: (Set<String>) -> Unit,
    onHideNothing: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadApps() }

    var selection by remember { mutableStateOf(state.hiddenPackages) }
    var quickAction by remember { mutableStateOf<QuickAction?>(null) }
    var query by remember { mutableStateOf("") }
    val icons = remember { mutableMapOf<String, ImageBitmap>() }

    val visible = remember(state.apps, query) {
        val needle = query.trim()
        if (needle.isEmpty()) state.apps
        else state.apps.filter { it.label.contains(needle, ignoreCase = true) }
    }

    OnboardingScaffold(
        step = state.step,
        flags = state.flags,
        title = stringResource(R.string.onb_hidden_title),
        description = stringResource(R.string.onb_hidden_body),
        modifier = modifier,
        aside = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(
                        R.plurals.onb_hidden_count_hidden,
                        selection.size,
                        selection.size,
                    ),
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                )
                Text(
                    stringResource(R.string.onb_hidden_hint_settings),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                )
            }
        },
        navigation = {
            OnboardingNavigationBar(
                onBack = onBack,
                primaryLabel = stringResource(R.string.onb_common_nav_apply),
                onPrimary = { onApply(selection) },
                secondaryLabel = stringResource(R.string.onb_hidden_action_hide_none),
                onSecondary = onHideNothing,
            )
        },
    ) {
        SettingsSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.onb_hidden_search_placeholder),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionChip(
                label = stringResource(R.string.onb_hidden_action_show_all),
                selected = quickAction == QuickAction.SHOW_ALL,
                onClick = {
                    selection = emptySet()
                    quickAction = QuickAction.SHOW_ALL
                },
            )
            QuickActionChip(
                label = stringResource(R.string.onb_hidden_action_hide_system),
                selected = quickAction == QuickAction.HIDE_SYSTEM,
                onClick = {
                    selection = state.apps
                        .filter { it.system && !it.protected }
                        .map { it.packageName }
                        .toSet()
                    quickAction = QuickAction.HIDE_SYSTEM
                },
            )
            QuickActionChip(
                label = stringResource(R.string.onb_hidden_action_select_manually),
                selected = quickAction == null,
                onClick = { quickAction = null },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.card)
                .background(AppleColors.frostedFill, AppleShapes.card)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
                .padding(vertical = 6.dp),
        ) {
            visible.forEach { app ->
                AppRow(
                    app = app,
                    hidden = app.packageName in selection,
                    icons = icons,
                    onToggle = {
                        selection = if (app.packageName in selection) selection - app.packageName
                        else selection + app.packageName
                        quickAction = null
                    },
                )
            }
        }
    }
}

@Composable
private fun QuickActionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppleTypography.labelSmall,
        color = if (selected) AppleColors.primary else AppleColors.secondary,
        modifier = Modifier
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) AppleColors.accent else AppleColors.frostedBorder,
                shape = AppleShapes.pill,
            )
            .appleClickable(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * One installed app. A protected app keeps its row but loses its clickable and shows a padlock:
 * the rule is visible instead of a tap that appears to do nothing.
 */
@Composable
private fun AppRow(
    app: OnboardingApp,
    hidden: Boolean,
    icons: MutableMap<String, ImageBitmap>,
    onToggle: () -> Unit,
) {
    val row = Modifier
        .fillMaxWidth()
        .then(if (app.protected) Modifier else Modifier.appleClickable(onToggle))
        .padding(horizontal = 16.dp, vertical = 11.dp)

    Row(modifier = row, verticalAlignment = Alignment.CenterVertically) {
        AppIcon(app.packageName, icons, Modifier.size(38.dp).alpha(if (hidden) 0.4f else 1f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = AppleTypography.titleMedium,
                color = if (hidden) AppleColors.secondary else AppleColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    if (hidden) R.string.onb_hidden_status_hidden
                    else R.string.onb_hidden_status_visible
                ),
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
            )
        }
        when {
            app.protected -> {
                Badge(stringResource(R.string.onb_hidden_badge_required))
                Spacer(Modifier.width(10.dp))
            }
            app.recommended -> {
                Badge(stringResource(R.string.onb_hidden_badge_recommended))
                Spacer(Modifier.width(10.dp))
            }
        }
        Icon(
            imageVector = when {
                app.protected -> Icons.Outlined.Lock
                hidden -> Icons.Outlined.VisibilityOff
                else -> Icons.Outlined.Visibility
            },
            contentDescription = null,
            tint = when {
                app.protected -> AppleColors.tertiary
                hidden -> AppleColors.secondary
                else -> AppleColors.active
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The real launcher icon, rasterized off the composition — `getApplicationIcon` reaches into the
 * PackageManager's resources, which is disk I/O. Results are kept in a caller-owned map so a
 * scrolled-away row does not pay for its icon twice.
 */
@Composable
private fun AppIcon(
    packageName: String,
    cache: MutableMap<String, ImageBitmap>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon by produceState(cache[packageName], packageName) {
        if (value != null) return@produceState
        val loaded = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toImageBitmap() }
                .getOrNull()
        }
        if (loaded != null) cache[packageName] = loaded
        value = loaded
    }

    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.clip(CircleShape))
    } else {
        Box(modifier.background(AppleColors.frostedFill, CircleShape))
    }
}
