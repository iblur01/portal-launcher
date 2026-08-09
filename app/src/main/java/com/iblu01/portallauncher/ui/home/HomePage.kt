package com.iblu01.portallauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.iblu01.portallauncher.domain.home.HomePageModel
import com.iblu01.portallauncher.domain.home.HomeRailLayoutPolicy
import com.iblu01.portallauncher.domain.home.HomeSectionModel
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.components.HomePillActions
import com.iblu01.portallauncher.ui.components.HomePillContextMenu
import com.iblu01.portallauncher.ui.components.HomePillMove
import com.iblu01.portallauncher.ui.components.ManualGroupMenuOption
import com.iblu01.portallauncher.ui.components.LocalLauncherPagerGestureLock
import com.iblu01.portallauncher.ui.components.StatusChip
import com.iblu01.portallauncher.ui.components.groupDescription
import com.iblu01.portallauncher.ui.components.homePillReorderDrag
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.min

data class HomePageEditActions(
    val onMoveSection: (sectionId: String, move: HomePillMove) -> Unit = { _, _ -> },
    val onHideSection: (sectionId: String) -> Unit = {},
    val onCreateManualGroup: () -> Unit = {},
    val onEditManualGroups: () -> Unit = {},
)

/**
 * Maison's catalog surface. It is a pure renderer of [HomePageModel]: ordering, availability and
 * business priority are resolved upstream, while all mutations are emitted as callbacks.
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
    /** Lets the parent pager yield the complete gesture to a rail that received the down event. */
    onRailGestureActiveChange: (Boolean) -> Unit = {},
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
    val lockParentPager = LocalLauncherPagerGestureLock.current
    val railGestureChanged: (Boolean) -> Unit = { active ->
        lockParentPager(active)
        onRailGestureActiveChange(active)
    }

    LaunchedEffect(menuTargetRef, pillsByKey.keys) {
        if (menuTargetRef != null && menuTarget == null) menuTargetRef = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A steady scrim keeps rails readable on user-selected photography, including API 28
            // where blurCompat intentionally degrades to a flat translucent surface.
            .background(Color.Black.copy(alpha = 0.34f))
            .testTag("homePage"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 34.dp, bottom = 42.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
            userScrollEnabled = !reordering,
        ) {
            item(key = "home-header") {
                HomePageHeader(
                    editing = editing,
                    isStale = isStale,
                    onEditingChange = onEditingChange,
                    onCreateManualGroup = editActions.onCreateManualGroup,
                    onEditManualGroups = editActions.onEditManualGroups,
                )
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
                        pinnedRefs = pinnedRefs,
                        actions = actions,
                        editing = editing,
                        reordering = reordering,
                        onLongPress = { menuTargetRef = it.ref.stableKey },
                        onRailGestureActiveChange = railGestureChanged,
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
    onEditingChange: (Boolean) -> Unit,
    onCreateManualGroup: () -> Unit,
    onEditManualGroups: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Maison",
                    style = AppleTypography.displaySmall,
                    color = AppleColors.primary,
                    modifier = Modifier.semantics { heading() },
                )
                if (isStale) {
                    Text(
                        "Hors ligne · données figées, commandes suspendues",
                        style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                        color = AppleColors.warning,
                        modifier = Modifier.testTag("homeStaleState"),
                    )
                }
            }
            HomeTextButton(
                label = if (editing) "Terminer" else "Modifier",
                onClick = { onEditingChange(!editing) },
            )
        }
        if (editing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeCompactButton(Icons.Outlined.Add, "Créer un groupe", onCreateManualGroup)
                HomeCompactButton(Icons.Outlined.HomeWork, "Gérer mes groupes", onEditManualGroups)
            }
            Text(
                "L’appartenance aux pièces et aux types se gère dans Home Assistant.",
                style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                color = AppleColors.tertiary,
            )
        }
    }
}

@Composable
private fun HomeSection(
    section: HomeSectionModel,
    pinnedRefs: Set<PillRef>,
    actions: HomePillActions,
    editing: Boolean,
    reordering: Boolean,
    onLongPress: (ResolvedPill) -> Unit,
    onRailGestureActiveChange: (Boolean) -> Unit,
    editActions: HomePageEditActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
                        "${section.items.size} élément${if (section.items.size > 1) "s" else ""}",
                        style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                        color = AppleColors.tertiary,
                    )
                }
            }
            if (editing) {
                HomeIconButton(Icons.Outlined.ArrowUpward, "Déplacer ${section.title} avant") {
                    editActions.onMoveSection(section.sectionId, HomePillMove.BEFORE)
                }
                HomeIconButton(Icons.Outlined.ArrowDownward, "Déplacer ${section.title} après") {
                    editActions.onMoveSection(section.sectionId, HomePillMove.AFTER)
                }
                HomeIconButton(Icons.Outlined.VisibilityOff, "Masquer ${section.title}") {
                    editActions.onHideSection(section.sectionId)
                }
            }
        }
        HomeRail(
            section = section,
            pinnedRefs = pinnedRefs,
            actions = actions,
            editing = editing,
            userScrollEnabled = !reordering,
            onLongPress = onLongPress,
            onRailGestureActiveChange = onRailGestureActiveChange,
        )
    }
}

/** Independent, saveable rail state per stable section id. */
@Composable
private fun HomeRail(
    section: HomeSectionModel,
    pinnedRefs: Set<PillRef>,
    actions: HomePillActions,
    editing: Boolean,
    userScrollEnabled: Boolean,
    onLongPress: (ResolvedPill) -> Unit,
    onRailGestureActiveChange: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val railState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthDp = with(density) { maxWidth.toPx() / density.density }
        val availableRailHeight = min(configuration.screenHeightDp * 0.28f, 156f)
        val layout = remember(section.items.size, widthDp, availableRailHeight, density.fontScale) {
            HomeRailLayoutPolicy.calculate(
                itemCount = section.items.size,
                availableWidthDp = widthDp,
                availableHeightDp = availableRailHeight,
                fontScale = density.fontScale,
            )
        }
        val rows = remember(section.items, layout.rowCount) {
            distributeHomeRailRows(section.items, layout.rowCount)
        }

        Box(
            Modifier
                .size(0.dp)
                .testTag("homeRailRows:${section.sectionId}:${layout.rowCount}"),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .horizontalScroll(railState, enabled = userScrollEnabled)
                .pointerInput(section.sectionId) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        onRailGestureActiveChange(true)
                        try {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onRailGestureActiveChange(false)
                        }
                    }
                }
                .padding(end = 28.dp)
                .testTag("homeRail:${section.sectionId}"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pill ->
                        key(pill.ref.stableKey) {
                        HomeRailPill(
                            pill = pill,
                            sectionTitle = section.title,
                            pinned = pill.ref in pinnedRefs,
                            editing = editing,
                            actions = actions,
                            onLongPress = { onLongPress(pill) },
                        )
                        }
                    }
                }
            }
        }
    }
}

/** Keeps reading order column-major while each visual row packs its pills independently. */
internal fun <T> distributeHomeRailRows(items: List<T>, rowCount: Int): List<List<T>> {
    val safeRows = rowCount.coerceAtLeast(1)
    return List(safeRows) { row -> items.filterIndexed { index, _ -> index % safeRows == row } }
        .filter { it.isNotEmpty() }
}

@Composable
private fun HomeRailPill(
    pill: ResolvedPill,
    sectionTitle: String,
    pinned: Boolean,
    editing: Boolean,
    actions: HomePillActions,
    onLongPress: () -> Unit,
) {
    val available = pill.availability == Availability.AVAILABLE
    val description = homePillAccessibilityLabel(pill, pinned, sectionTitle)
    Column(
        modifier = Modifier.widthIn(min = 132.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StatusChip(
            chip = pill.chip,
            // Keep the touch long-press detector installed on stale pills; tap remains a guarded
            // no-op, while the menu still permits safe actions such as Désépingler.
            onClick = { if (available) actions.onOpen(pill) },
            onLongPress = onLongPress,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .homePillReorderDrag(pill, actions)
                .alpha(if (available) 1f else 0.72f)
                .testTag("homePill:${pill.ref.stableKey}")
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    if (available) {
                        onClick(label = "Ouvrir ${pill.chip.label}") {
                            actions.onOpen(pill)
                            true
                        }
                    }
                    onLongClick(label = "Actions pour ${pill.chip.label}") {
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
                "Épinglé",
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
            if (hasCompatibleDevices) "Aucune section visible" else "Aucun appareil compatible",
            style = AppleTypography.titleLarge,
            color = AppleColors.primary,
        )
        Text(
            if (hasCompatibleDevices) {
                "Affichez une section pour retrouver vos appareils et groupes."
            } else {
                "Ajoutez ou activez un appareil pilotable dans Home Assistant."
            },
            style = AppleTypography.bodyMedium,
            color = AppleColors.secondary,
        )
        HomeTextButton(
            label = if (hasCompatibleDevices) "Modifier les sections" else "Ouvrir les réglages Home Assistant",
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
    pill: ResolvedPill,
    pinned: Boolean,
    sectionTitle: String,
): String = buildString {
    append(pill.chip.label)
    if (pill.chip.value.isNotBlank()) append(", ${pill.chip.value}")
    append(", ${pill.ref.groupDescription()}")
    append(", section $sectionTitle")
    if (pinned) append(", épinglé")
    if (pill.alert != null || pill.chip.state.equals("critical", ignoreCase = true)) {
        append(", alerte critique")
    }
    if (pill.availability == Availability.STALE) append(", données figées, commandes indisponibles")
}
