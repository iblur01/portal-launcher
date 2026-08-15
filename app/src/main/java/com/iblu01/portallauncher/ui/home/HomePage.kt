package com.iblu01.portallauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.R
import androidx.compose.ui.res.stringResource
import com.iblu01.portallauncher.domain.home.HomeGridLayoutPolicy
import com.iblu01.portallauncher.domain.home.HomePageModel
import com.iblu01.portallauncher.domain.home.HomeSectionModel
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.components.HomePillActions
import com.iblu01.portallauncher.ui.components.HomePillContextMenu
import com.iblu01.portallauncher.ui.components.HomePillMove
import com.iblu01.portallauncher.ui.components.LocalCollapsedHeaderHeight
import com.iblu01.portallauncher.ui.components.ManualGroupMenuOption
import com.iblu01.portallauncher.ui.components.StatusChip
import com.iblu01.portallauncher.ui.components.groupDescription
import com.iblu01.portallauncher.ui.components.homePillReorderDrag
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

data class HomePageEditActions(
    val onMoveSection: (sectionId: String, move: HomePillMove) -> Unit = { _, _ -> },
    val onHideSection: (sectionId: String) -> Unit = {},
    val onCreateManualGroup: () -> Unit = {},
    val onEditManualGroups: () -> Unit = {},
)

/** Breathing room between the compact clock header and the first line of Maison content. */
private val HomeContentTopGap = 14.dp
private val HomeContentSideGutter = 24.dp
private val HomePillGap = 10.dp

/**
 * Frosted-glass slab drawn under each Maison pill.
 *
 * The tray's frosted fill (white 8 %) assumes the darkened bottom of the clock page. Maison covers
 * the whole wallpaper, and a translucent scrim was not enough: a bright photo still read through
 * the pills. The Portal is API 28, where [androidx.compose.ui.draw.blur] is a no-op, so the frost
 * has to be an opaque elevated surface rather than a real blur. It is drawn *before* the chip's own
 * fill, so the white sheen, the border and the accent/selected states are untouched.
 */
private val HomePillSurface = AppleColors.elevated

/**
 * Maison's catalog surface. It is a pure renderer of [HomePageModel]: ordering, availability and
 * business priority are resolved upstream, while all mutations are emitted as callbacks.
 *
 * The page owns a single vertical gesture. Sections wrap their pills across full-width lines
 * instead of hiding them in horizontal rails, so nothing needs a second scroll axis to be reached.
 */
@Composable
fun HomePage(
    model: HomePageModel,
    pinnedRefs: Set<PillRef>,
    actions: HomePillActions,
    manualGroups: List<ManualGroupMenuOption> = emptyList(),
    editing: Boolean = false,
    reordering: Boolean = false,
    stale: Boolean = false,
    onEditingChange: (Boolean) -> Unit = {},
    editActions: HomePageEditActions = HomePageEditActions(),
    onOpenHomeAssistantSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuTargetRef by rememberSaveable { mutableStateOf<String?>(null) }
    val pillsByKey = model.sections
        .flatMap { it.items }
        .associateBy { it.ref.stableKey }
    val menuTarget = menuTargetRef?.let(pillsByKey::get)
    val isStale = stale || pillsByKey.values.any { it.availability == Availability.STALE }

    LaunchedEffect(menuTargetRef, pillsByKey.keys) {
        if (menuTargetRef != null && menuTarget == null) menuTargetRef = null
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("homePage"),
    ) {
        // The header is user-themed, so its collapsed height is measured by the pager rather than
        // assumed here. Guessing is what pushed the first line of pills under the clock.
        val topInset = LocalCollapsedHeaderHeight.current + HomeContentTopGap
        val scrollViewportHeight = (maxHeight - topInset).coerceAtLeast(0.dp)
        val density = LocalDensity.current
        val contentWidthDp = (maxWidth - HomeContentSideGutter * 2).value
        val columns = remember(contentWidthDp, density.fontScale) {
            HomeGridLayoutPolicy.columns(
                availableWidthDp = contentWidthDp,
                fontScale = density.fontScale,
            )
        }

        LazyColumn(
            // A smaller viewport anchored below the header, not a full-screen list with top
            // padding. Clipping makes it impossible for content to travel under the chrome.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(scrollViewportHeight)
                .clipToBounds()
                .testTag("homeScrollableContent"),
            contentPadding = PaddingValues(
                start = HomeContentSideGutter,
                end = HomeContentSideGutter,
                top = 0.dp,
                bottom = 42.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(30.dp),
            userScrollEnabled = !reordering,
        ) {
            if (editing || isStale) {
                item(key = "home-header") {
                    HomePageHeader(
                        editing = editing,
                        isStale = isStale,
                        onCreateManualGroup = editActions.onCreateManualGroup,
                        onEditManualGroups = editActions.onEditManualGroups,
                    )
                }
            }

            if (model.sections.isEmpty()) {
                item(key = "home-empty") {
                    HomeEmptyState(
                        hasCompatibleDevices = model.hasCompatibleDevices,
                        onOpenHomeAssistantSettings = onOpenHomeAssistantSettings,
                        onEdit = { onEditingChange(true) },
                    )
                }
            } else {
                items(model.sections, key = { it.sectionId }) { section ->
                    HomeSection(
                        section = section,
                        columns = columns,
                        pinnedRefs = pinnedRefs,
                        actions = actions,
                        editing = editing,
                        onLongPress = { menuTargetRef = it.ref.stableKey },
                        editActions = editActions,
                    )
                }
            }
        }

        HomePillContextMenu(
            target = menuTarget,
            isPinned = menuTarget?.ref in pinnedRefs,
            manualGroups = manualGroups,
            actions = actions,
            onDismiss = { menuTargetRef = null },
        )
    }
}

@Composable
private fun HomePageHeader(
    editing: Boolean,
    isStale: Boolean,
    onCreateManualGroup: () -> Unit,
    onEditManualGroups: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (isStale) {
            Text(
                stringResource(R.string.home_stale_state),
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.warning,
                modifier = Modifier.testTag("homeStaleState"),
            )
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeCompactButton(Icons.Outlined.Add, stringResource(R.string.home_create_group), onCreateManualGroup)
                HomeCompactButton(Icons.Outlined.HomeWork, stringResource(R.string.home_manage_groups), onEditManualGroups)
            }
            Text(
                stringResource(R.string.home_ha_membership_hint),
                style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                color = AppleColors.tertiary,
            )
        }
    }
}

@Composable
private fun HomeSection(
    section: HomeSectionModel,
    columns: Int,
    pinnedRefs: Set<PillRef>,
    actions: HomePillActions,
    editing: Boolean,
    onLongPress: (ResolvedPill) -> Unit,
    editActions: HomePageEditActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup()
            .testTag("homeSection:${section.sectionId}"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    section.title,
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                    modifier = Modifier.semantics { heading() },
                )
                if (editing) {
                    Text(
                        androidx.compose.ui.res.pluralStringResource(R.plurals.home_section_item_count, section.items.size, section.items.size),
                        style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                        color = AppleColors.tertiary,
                    )
                }
            }
            if (editing) {
                HomeIconButton(Icons.Outlined.ArrowUpward, stringResource(R.string.home_move_section_before, section.title)) {
                    editActions.onMoveSection(section.sectionId, HomePillMove.BEFORE)
                }
                HomeIconButton(Icons.Outlined.ArrowDownward, stringResource(R.string.home_move_section_after, section.title)) {
                    editActions.onMoveSection(section.sectionId, HomePillMove.AFTER)
                }
                HomeIconButton(Icons.Outlined.VisibilityOff, stringResource(R.string.home_hide_section, section.title)) {
                    editActions.onHideSection(section.sectionId)
                }
            }
        }

        // Reported for tests and for anyone debugging a density issue: the column count is a pure
        // function of the available width, never of how many tiles the section happens to hold.
        Box(
            Modifier
                .size(0.dp)
                .testTag("homeGridColumns:${section.sectionId}:$columns"),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("homeGrid:${section.sectionId}"),
            verticalArrangement = Arrangement.spacedBy(HomePillGap),
        ) {
            section.items.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(HomePillGap)) {
                    row.forEach { pill ->
                        key(pill.ref.stableKey) {
                            HomePill(
                                pill = pill,
                                sectionTitle = section.title,
                                pinned = pill.ref in pinnedRefs,
                                editing = editing,
                                actions = actions,
                                onLongPress = { onLongPress(pill) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // Keep the last line's pills the same width as every other line's.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * One device pill — the exact chip Accueil renders, laid out on the section's grid so every line
 * keeps the same widths.
 */
@Composable
private fun HomePill(
    pill: ResolvedPill,
    sectionTitle: String,
    pinned: Boolean,
    editing: Boolean,
    actions: HomePillActions,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = pill.availability == Availability.AVAILABLE
    val context = LocalContext.current
    val description = homePillAccessibilityLabel(context, pill, pinned, sectionTitle)
    val openLabel = stringResource(R.string.home_open_item, pill.chip.label)
    val actionsLabel = stringResource(R.string.home_item_actions, pill.chip.label)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusChip(
            chip = pill.chip,
            // Keep the touch long-press detector installed on stale pills; tap remains a guarded
            // no-op, while the menu still permits safe actions such as Désépingler.
            onClick = { if (available) actions.onOpen(pill) },
            onLongPress = onLongPress,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(HomePillSurface, AppleShapes.pill)
                .homePillReorderDrag(pill, actions)
                .alpha(if (available) 1f else 0.72f)
                .testTag("homePill:${pill.ref.stableKey}")
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    if (available) {
                        onClick(label = openLabel) {
                            actions.onOpen(pill)
                            true
                        }
                    }
                    onLongClick(label = actionsLabel) {
                        onLongPress()
                        true
                    }
                }
                .onKeyEvent { event ->
                    if (available && event.type == KeyEventType.KeyUp &&
                        event.key in setOf(Key.Enter, Key.DirectionCenter, Key.Spacebar)
                    ) {
                        actions.onOpen(pill)
                        true
                    } else false
                }
                .focusable(),
        )
        if (editing && pinned) {
            Text(
                stringResource(R.string.home_pinned),
                style = AppleTypography.labelSmall.copy(fontSize = 11.sp),
                color = AppleColors.secondary,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun HomeEmptyState(
    hasCompatibleDevices: Boolean,
    onOpenHomeAssistantSettings: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .testTag("homeEmptyState"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            if (hasCompatibleDevices) Icons.Outlined.Settings else Icons.Outlined.HomeWork,
            contentDescription = null,
            tint = AppleColors.secondary,
            modifier = Modifier.size(42.dp),
        )
        Text(
            if (hasCompatibleDevices) stringResource(R.string.home_empty_sections) else stringResource(R.string.home_empty_devices),
            style = AppleTypography.titleLarge,
            color = AppleColors.primary,
        )
        Text(
            if (hasCompatibleDevices) {
                stringResource(R.string.home_empty_sections_hint)
            } else {
                stringResource(R.string.home_empty_devices_hint)
            },
            style = AppleTypography.bodyMedium,
            color = AppleColors.secondary,
        )
        HomeTextButton(
            label = if (hasCompatibleDevices) stringResource(R.string.home_edit_sections) else stringResource(R.string.home_open_ha_settings),
            onClick = if (hasCompatibleDevices) onEdit else onOpenHomeAssistantSettings,
        )
    }
}

@Composable
private fun HomeTextButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.accent)
    }
}

@Composable
private fun HomeCompactButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .background(AppleColors.frostedFill, CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppleColors.accent, modifier = Modifier.size(18.dp))
        Text(label, style = AppleTypography.bodySmall, color = AppleColors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppleColors.secondary, modifier = Modifier.size(20.dp))
    }
}

internal fun homePillAccessibilityLabel(
    context: android.content.Context,
    pill: ResolvedPill,
    pinned: Boolean,
    sectionTitle: String,
): String = buildString {
    append(pill.chip.label)
    if (pill.chip.value.isNotBlank()) append(", ${pill.chip.value}")
    append(", ${pill.ref.groupDescription(context)}")
    append(", ${context.getString(R.string.home_accessibility_section, sectionTitle)}")
    if (pinned) append(", ${context.getString(R.string.home_pinned).lowercase()}")
    if (pill.alert != null || pill.chip.state.equals("critical", ignoreCase = true)) {
        append(", ${context.getString(R.string.home_accessibility_critical)}")
    }
    if (pill.availability == Availability.STALE) append(", ${context.getString(R.string.home_accessibility_stale)}")
}
