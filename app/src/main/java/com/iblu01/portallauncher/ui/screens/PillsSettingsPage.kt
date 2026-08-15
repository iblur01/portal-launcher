package com.iblu01.portallauncher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.PillFamily
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.HomeSectionIds
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.ui.components.IosSwitch
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.components.SettingsToggleSub
import com.iblu01.portallauncher.ui.settings.HomeSettingsAction
import com.iblu01.portallauncher.ui.settings.HomeSettingsPinPreview
import com.iblu01.portallauncher.ui.settings.HomeSettingsSectionOrder
import com.iblu01.portallauncher.ui.settings.MoveDirection
import com.iblu01.portallauncher.ui.settings.SettingsPillCatalog
import com.iblu01.portallauncher.ui.settings.SettingsPillTarget
import com.iblu01.portallauncher.ui.settings.labelForSettingsRef
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.roundToInt

private sealed interface PillsSettingsDestination {
    data object Root : PillsSettingsDestination
    data object LauncherHome : PillsSettingsDestination
    data object HousePage : PillsSettingsDestination
    data object AvailableDevices : PillsSettingsDestination
    data class ManualGroup(val id: String) : PillsSettingsDestination
}

/**
 * Pure state machine for settings pin drag-and-drop.
 *
 * It intentionally has no preference or Compose dependency: callers may draw [Session.stagedOrder]
 * while a pointer is held, then persist only [drop]. Cancelling always exposes the original order.
 */
internal object SettingsPinDragOrder {
    val rowHeight: Dp = 64.dp

    data class Session(
        val initialOrder: List<PillRef>,
        val stagedOrder: List<PillRef>,
        val draggedRef: PillRef,
        val totalDragPx: Float = 0f,
    )

    fun start(pinnedOrder: List<PillRef>, ref: PillRef): Session? {
        val stableOrder = pinnedOrder.distinct()
        return ref.takeIf(stableOrder::contains)?.let {
            Session(initialOrder = stableOrder, stagedOrder = stableOrder, draggedRef = ref)
        }
    }

    fun dragBy(session: Session, deltaPx: Float, rowHeightPx: Float): Session {
        require(rowHeightPx > 0f)
        val total = session.totalDragPx + deltaPx
        val startIndex = session.initialOrder.indexOf(session.draggedRef)
        if (startIndex < 0) return session
        val target = (startIndex + (total / rowHeightPx).roundToInt())
            .coerceIn(0, session.initialOrder.lastIndex)
        return session.copy(
            stagedOrder = moveToIndex(session.initialOrder, session.draggedRef, target),
            totalDragPx = total,
        )
    }

    fun drop(session: Session): List<PillRef> = session.stagedOrder

    fun cancel(session: Session): List<PillRef> = session.initialOrder

    private fun moveToIndex(order: List<PillRef>, ref: PillRef, target: Int): List<PillRef> {
        val from = order.indexOf(ref)
        if (from < 0 || from == target) return order
        return order.toMutableList().apply { add(target, removeAt(from)) }
    }
}

/**
 * Complete pill customisation surface. Persistent mutations are represented as typed actions and
 * reduced by the Activity before being written to Prefs; the composables only render immutable
 * state and keep transient dialog/text-field state.
 */
@Composable
fun PillsSettingsPage(
    uiState: SettingsUiState,
    homePreferences: HomePillPreferences,
    settingsCatalog: SettingsPillCatalog,
    onRefresh: () -> Unit,
    onSetEnabled: (List<com.iblu01.portallauncher.PillCandidate>, Boolean) -> Unit,
    onHomeAction: (HomeSettingsAction) -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    LaunchedEffect(Unit) { onRefresh() }
    var destination by remember { mutableStateOf<PillsSettingsDestination>(PillsSettingsDestination.Root) }

    AnimatedContent(
        targetState = destination,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "pillsSettingsDestination",
    ) { page ->
        when (page) {
            PillsSettingsDestination.Root -> PillsSettingsRoot(
                uiState = uiState,
                preferences = homePreferences,
                catalog = settingsCatalog,
                onNavigate = { destination = it },
                onRefresh = onRefresh,
                onBack = onBack,
                showBack = showBack,
            )
            PillsSettingsDestination.LauncherHome -> LauncherHomeSettings(
                preferences = homePreferences,
                catalog = settingsCatalog,
                onAction = onHomeAction,
                onBack = { destination = PillsSettingsDestination.Root },
            )
            PillsSettingsDestination.HousePage -> HousePageSettings(
                preferences = homePreferences,
                catalog = settingsCatalog,
                onAction = onHomeAction,
                onEditGroup = { destination = PillsSettingsDestination.ManualGroup(it) },
                onBack = { destination = PillsSettingsDestination.Root },
            )
            PillsSettingsDestination.AvailableDevices -> AvailableDevicesSettings(
                uiState = uiState,
                preferences = homePreferences,
                catalog = settingsCatalog,
                onSetEnabled = onSetEnabled,
                onAction = onHomeAction,
                onBack = { destination = PillsSettingsDestination.Root },
            )
            is PillsSettingsDestination.ManualGroup -> {
                val group = homePreferences.manualGroups.firstOrNull { it.id == page.id }
                if (group == null) {
                    LaunchedEffect(page.id) { destination = PillsSettingsDestination.HousePage }
                } else {
                    ManualGroupSettings(
                        group = group,
                        preferences = homePreferences,
                        catalog = settingsCatalog,
                        onAction = onHomeAction,
                        onDeleted = { destination = PillsSettingsDestination.HousePage },
                        onBack = { destination = PillsSettingsDestination.HousePage },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillsSettingsRoot(
    uiState: SettingsUiState,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onNavigate: (PillsSettingsDestination) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean,
) {
    SettingsPageColumn {
        SettingsSubPageHeader(
            title = stringResource(R.string.pills_page_title),
            onBack = onBack,
            showBack = showBack,
            breadcrumb = "${stringResource(R.string.settings_main_title)}  ›  ${stringResource(R.string.settings_tile_pills_title)}",
        )
        Text(
            stringResource(R.string.pills_page_description),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
        )
        SettingsSection(title = stringResource(R.string.pills_settings_sections_title)) {
            SettingsRow(
                label = stringResource(R.string.pills_settings_launcher_home),
                value = stringResource(R.string.pills_settings_pinned_count, preferences.pinnedOrder.size),
                onClick = { onNavigate(PillsSettingsDestination.LauncherHome) },
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.pills_settings_house_page),
                value = stringResource(if (preferences.homePageEnabled) R.string.pills_enabled else R.string.pills_disabled),
                onClick = { onNavigate(PillsSettingsDestination.HousePage) },
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.pills_settings_available_devices),
                value = catalog.devices.size.toString(),
                onClick = { onNavigate(PillsSettingsDestination.AvailableDevices) },
            )
        }
        when {
            uiState.pillLoading -> Text(stringResource(R.string.pills_loading), color = AppleColors.secondary)
            uiState.pillError != null -> SettingsSection(title = stringResource(R.string.pills_section_connection)) {
                SettingsRow(
                    label = uiState.pillError ?: stringResource(R.string.pills_error_fallback),
                    value = stringResource(R.string.pills_retry),
                    onClick = onRefresh,
                )
            }
            catalog.devices.isEmpty() -> Text(stringResource(R.string.pills_no_devices), color = AppleColors.secondary)
            else -> PillButton(label = stringResource(R.string.pills_button_refresh), onClick = onRefresh)
        }
    }
}

@Composable
private fun LauncherHomeSettings(
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onAction: (HomeSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    var dragSession by remember(preferences.pinnedOrder) { mutableStateOf<SettingsPinDragOrder.Session?>(null) }
    // Do not move composable rows while their pointer-input node is actively dragging: a pin can
    // cross the primary/secondary/overflow section boundary, where Compose would otherwise dispose
    // that node and turn the gesture into a cancellation. The target order is still staged in the
    // pure session and becomes visible atomically after the drop action is reduced.
    val preview = HomeSettingsPinPreview.build(preferences, catalog)
    val visiblePins = preview.visible
    val overflow = preview.overflow
    val startDrag: (PillRef) -> Unit = { ref ->
        dragSession = SettingsPinDragOrder.start(preferences.pinnedOrder, ref)
    }
    val dragBy: (PillRef, Float, Float) -> Unit = { ref, deltaPx, rowHeightPx ->
        dragSession?.takeIf { it.draggedRef == ref }?.let { session ->
            dragSession = SettingsPinDragOrder.dragBy(session, deltaPx, rowHeightPx)
        }
    }
    val cancelDrag: (PillRef) -> Unit = { ref ->
        if (dragSession?.draggedRef == ref) dragSession = null
    }
    val dropDrag: (PillRef) -> Unit = { ref ->
        dragSession?.takeIf { it.draggedRef == ref }?.let { session ->
            if (session.stagedOrder != preferences.pinnedOrder) {
                onAction(HomeSettingsAction.SetPinnedOrder(session.stagedOrder))
            }
        }
        dragSession = null
    }
    SettingsPageColumn {
        SettingsSubPageHeader(stringResource(R.string.pills_settings_launcher_home), onBack)
        Text(stringResource(R.string.pills_settings_launcher_home_description), style = AppleTypography.bodyLarge, color = AppleColors.secondary)
        SettingsSection(title = stringResource(R.string.pills_settings_primary_slots)) {
            repeat(3) { index ->
                HomeSlotRow(
                    index,
                    visiblePins.getOrNull(index),
                    preferences,
                    catalog,
                    onAction,
                    dragSession?.draggedRef,
                    startDrag,
                    dragBy,
                    dropDrag,
                    cancelDrag,
                )
                if (index != 2) SettingsDivider()
            }
        }
        SettingsSection(title = stringResource(R.string.pills_settings_secondary_slots)) {
            repeat(6) { relativeIndex ->
                val index = relativeIndex + 3
                HomeSlotRow(
                    index,
                    visiblePins.getOrNull(index),
                    preferences,
                    catalog,
                    onAction,
                    dragSession?.draggedRef,
                    startDrag,
                    dragBy,
                    dropDrag,
                    cancelDrag,
                )
                if (relativeIndex != 5) SettingsDivider()
            }
        }
        if (overflow.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.pills_settings_overflow)) {
                overflow.forEachIndexed { index, ref ->
                    OrderedReferenceRow(
                        ref,
                        preferences,
                        catalog,
                        onAction,
                        draggingRef = dragSession?.draggedRef,
                        onStartDrag = startDrag,
                        onDragBy = dragBy,
                        onDropDrag = dropDrag,
                        onCancelDrag = cancelDrag,
                    )
                    if (index != overflow.lastIndex) SettingsDivider()
                }
            }
        }
        if (preview.unavailable.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.pills_unavailable)) {
                preview.unavailable.forEachIndexed { index, ref ->
                    OrderedReferenceRow(
                        ref,
                        preferences,
                        catalog,
                        onAction,
                        draggingRef = dragSession?.draggedRef,
                        onStartDrag = startDrag,
                        onDragBy = dragBy,
                        onDropDrag = dropDrag,
                        onCancelDrag = cancelDrag,
                    )
                    if (index != preview.unavailable.lastIndex) SettingsDivider()
                }
            }
        }
    }
}

@Composable
private fun HomeSlotRow(
    index: Int,
    ref: PillRef?,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onAction: (HomeSettingsAction) -> Unit,
    draggingRef: PillRef?,
    onStartDrag: (PillRef) -> Unit,
    onDragBy: (PillRef, Float, Float) -> Unit,
    onDropDrag: (PillRef) -> Unit,
    onCancelDrag: (PillRef) -> Unit,
) {
    if (ref == null) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${index + 1}", style = AppleTypography.bodySmall, color = AppleColors.tertiary)
            Text(
                stringResource(R.string.pills_settings_automatic_slot),
                modifier = Modifier.padding(start = 12.dp),
                style = AppleTypography.titleMedium,
                color = AppleColors.secondary,
            )
        }
    } else {
        OrderedReferenceRow(
            ref = ref,
            preferences = preferences,
            catalog = catalog,
            onAction = onAction,
            prefix = "${index + 1}",
            draggingRef = draggingRef,
            onStartDrag = onStartDrag,
            onDragBy = onDragBy,
            onDropDrag = onDropDrag,
            onCancelDrag = onCancelDrag,
        )
    }
}

@Composable
private fun OrderedReferenceRow(
    ref: PillRef,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onAction: (HomeSettingsAction) -> Unit,
    prefix: String? = null,
    draggingRef: PillRef? = null,
    onStartDrag: (PillRef) -> Unit = {},
    onDragBy: (PillRef, Float, Float) -> Unit = { _, _, _ -> },
    onDropDrag: (PillRef) -> Unit = {},
    onCancelDrag: (PillRef) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (draggingRef == ref) 0.62f else 1f)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefix != null) Text(prefix, style = AppleTypography.bodySmall, color = AppleColors.tertiary, modifier = Modifier.padding(end = 12.dp))
            Text(labelForSettingsRef(androidx.compose.ui.platform.LocalContext.current, ref, catalog, preferences), style = AppleTypography.titleMedium, color = AppleColors.primary, modifier = Modifier.weight(1f))
            SettingsPinDragHandle(
                ref = ref,
                onStart = onStartDrag,
                onDragBy = onDragBy,
                onDrop = onDropDrag,
                onCancel = onCancelDrag,
            )
            TextButton(onClick = { onAction(HomeSettingsAction.TogglePin(ref, canPin = false)) }) {
                Text(stringResource(R.string.pills_unpin))
            }
        }
        MoveControls { direction -> onAction(HomeSettingsAction.MovePin(ref, direction)) }
    }
}

/**
 * Pointer-only drag handle for pin ordering. The surrounding scrollable column remains usable
 * because the gesture is deliberately confined to this 48dp target; keyboard and TalkBack users
 * keep the explicit [MoveControls] immediately below every pin.
 */
@Composable
private fun SettingsPinDragHandle(
    ref: PillRef,
    onStart: (PillRef) -> Unit,
    onDragBy: (PillRef, Float, Float) -> Unit,
    onDrop: (PillRef) -> Unit,
    onCancel: (PillRef) -> Unit,
) {
    val rowHeightPx = with(LocalDensity.current) { SettingsPinDragOrder.rowHeight.toPx() }
    val latestStart = rememberUpdatedState(onStart)
    val latestDragBy = rememberUpdatedState(onDragBy)
    val latestDrop = rememberUpdatedState(onDrop)
    val latestCancel = rememberUpdatedState(onCancel)
    Icon(
        imageVector = Icons.Outlined.DragIndicator,
        contentDescription = null,
        tint = AppleColors.tertiary,
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(12.dp)
            .pointerInput(ref, rowHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { latestStart.value(ref) },
                    onVerticalDrag = { _, delta -> latestDragBy.value(ref, delta, rowHeightPx) },
                    onDragEnd = { latestDrop.value(ref) },
                    onDragCancel = { latestCancel.value(ref) },
                )
            },
    )
}

@Composable
private fun HousePageSettings(
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onAction: (HomeSettingsAction) -> Unit,
    onEditGroup: (String) -> Unit,
    onBack: () -> Unit,
) {
    var creatingGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    if (creatingGroup) {
        NameDialog(
            title = stringResource(R.string.pills_manual_create),
            value = newGroupName,
            onValueChange = { newGroupName = it },
            onDismiss = { creatingGroup = false; newGroupName = "" },
            onConfirm = {
                onAction(HomeSettingsAction.CreateManualGroup(newGroupName))
                creatingGroup = false
                newGroupName = ""
            },
        )
    }
    SettingsPageColumn {
        SettingsSubPageHeader(stringResource(R.string.pills_settings_house_page), onBack)
        SettingsSection(title = stringResource(R.string.pills_house_activation)) {
            SettingsToggle(
                label = stringResource(R.string.pills_house_enable),
                checked = preferences.homePageEnabled,
                onCheckedChange = { onAction(HomeSettingsAction.SetHomePageEnabled(it)) },
            )
        }
        SettingsSection(title = stringResource(R.string.pills_house_sections)) {
            val orderedSections = preferences.homeSections.sortedWith(compareBy({ it.order }, { it.sectionId }))
            orderedSections.forEachIndexed { index, section ->
                val sectionItems = HomeSettingsSectionOrder.items(section.sectionId, preferences, catalog)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SettingsToggleSub(
                        label = sectionLabel(section.sectionId),
                        sublabel = stringResource(R.string.pills_house_section_position, index + 1),
                        checked = section.visible,
                        onCheckedChange = { onAction(HomeSettingsAction.SetSectionVisible(section.sectionId, it)) },
                    )
                    MoveControls { direction -> onAction(HomeSettingsAction.MoveSection(section.sectionId, direction)) }
                    if (sectionItems.size > 1) {
                        sectionItems.forEach { ref ->
                            SectionItemOrderRow(
                                ref = ref,
                                preferences = preferences,
                                catalog = catalog,
                                onMove = { direction ->
                                    onAction(
                                        HomeSettingsAction.MoveSectionItem(
                                            sectionId = section.sectionId,
                                            ref = ref,
                                            visibleOrder = sectionItems,
                                            direction = direction,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                if (index != orderedSections.lastIndex) SettingsDivider()
            }
        }
        if (catalog.automaticGroups.isNotEmpty()) {
            SettingsSection(title = stringResource(R.string.pills_house_automatic_groups)) {
                catalog.automaticGroups.forEachIndexed { index, target ->
                    PinnableTargetRow(target, target.ref in preferences.pinnedOrder, onAction)
                    if (index != catalog.automaticGroups.lastIndex) SettingsDivider()
                }
            }
            Text(stringResource(R.string.pills_house_automatic_groups_hint), style = AppleTypography.bodySmall, color = AppleColors.secondary)
        }
        SettingsSection(
            title = stringResource(R.string.pills_manual_groups),
            action = stringResource(R.string.pills_manual_create),
            onAction = { creatingGroup = true },
        ) {
            if (preferences.manualGroups.isEmpty()) {
                Text(
                    stringResource(R.string.pills_manual_empty),
                    modifier = Modifier.padding(16.dp),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.secondary,
                )
            } else preferences.manualGroups.forEachIndexed { index, group ->
                Column {
                    SettingsRow(
                        label = group.name,
                        value = stringResource(R.string.pills_manual_member_count, group.members.size),
                        onClick = { onEditGroup(group.id) },
                    )
                    MoveControls { direction -> onAction(HomeSettingsAction.MoveManualGroup(group.id, direction)) }
                }
                if (index != preferences.manualGroups.lastIndex) SettingsDivider()
            }
        }
    }
}

@Composable
private fun SectionItemOrderRow(
    ref: PillRef,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onMove: (MoveDirection) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 28.dp, top = 4.dp)) {
        Text(
            text = labelForSettingsRef(androidx.compose.ui.platform.LocalContext.current, ref, catalog, preferences),
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
        )
        MoveControls(onMove)
    }
}

@Composable
private fun AvailableDevicesSettings(
    uiState: SettingsUiState,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onSetEnabled: (List<com.iblu01.portallauncher.PillCandidate>, Boolean) -> Unit,
    onAction: (HomeSettingsAction) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageColumn {
        SettingsSubPageHeader(stringResource(R.string.pills_settings_available_devices), onBack)
        Text(stringResource(R.string.pills_devices_description), style = AppleTypography.bodyLarge, color = AppleColors.secondary)
        if (uiState.pillCandidates.isEmpty()) {
            Text(stringResource(R.string.pills_no_devices), color = AppleColors.secondary)
        } else {
            PillFamily.entries.forEach { family ->
                val candidates = uiState.pillCandidates.filter { PillFamily.of(it.kind) == family }
                if (candidates.isNotEmpty()) {
                    SettingsSection(title = stringResource(family.labelRes)) {
                        candidates.forEachIndexed { index, candidate ->
                            val ref = PillRef.Device(candidate.primary.entityId)
                            val target = catalog.byRef[ref] ?: return@forEachIndexed
                            DeviceTargetRow(
                                target = target,
                                pinned = ref in preferences.pinnedOrder,
                                onEnabled = { onSetEnabled(listOf(candidate), it) },
                                onAction = onAction,
                            )
                            if (index != candidates.lastIndex) SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualGroupSettings(
    group: ManualPillGroup,
    preferences: HomePillPreferences,
    catalog: SettingsPillCatalog,
    onAction: (HomeSettingsAction) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var confirmDelete by remember { mutableStateOf(false) }
    var memberToMove by remember { mutableStateOf<PillRef.Device?>(null) }
    val otherGroups = preferences.manualGroups.filterNot { it.id == group.id }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.pills_manual_delete_title, group.name)) },
            text = { Text(stringResource(R.string.pills_manual_delete_description)) },
            confirmButton = {
                TextButton(onClick = {
                    onAction(HomeSettingsAction.DeleteManualGroup(group.id))
                    confirmDelete = false
                    onDeleted()
                }) { Text(stringResource(R.string.pills_manual_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    memberToMove?.let { device ->
        AlertDialog(
            onDismissRequest = { memberToMove = null },
            title = { Text(stringResource(R.string.pills_manual_move_to_group)) },
            text = {
                Column {
                    otherGroups.forEach { destination ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onAction(HomeSettingsAction.MoveManualGroupMemberToGroup(group.id, destination.id, device))
                                memberToMove = null
                            },
                        ) { Text(destination.name, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { memberToMove = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    val ref = PillRef.ManualGroup(group.id)
    val groupAvailable = group.members.any { catalog.byRef[it]?.canPin == true }
    SettingsPageColumn {
        SettingsSubPageHeader(group.name, onBack)
        SettingsSection(title = stringResource(R.string.pills_manual_identity)) {
            SettingsTextField(
                label = stringResource(R.string.pills_manual_name),
                value = name,
                onValueChange = {
                    name = it
                    onAction(HomeSettingsAction.RenameManualGroup(group.id, it))
                },
            )
            SettingsDivider()
            SettingsToggleSub(
                label = stringResource(R.string.pills_pin_group),
                sublabel = if (groupAvailable) null else stringResource(R.string.pills_not_available_to_pin),
                checked = ref in preferences.pinnedOrder,
                onCheckedChange = { onAction(HomeSettingsAction.TogglePin(ref, groupAvailable)) },
            )
        }
        SettingsSection(title = stringResource(R.string.pills_manual_members)) {
            if (catalog.devices.isEmpty()) {
                Text(stringResource(R.string.pills_no_devices), modifier = Modifier.padding(16.dp), color = AppleColors.secondary)
            } else catalog.devices.forEachIndexed { index, target ->
                val device = target.ref as PillRef.Device
                val included = device in group.members
                Column {
                    SettingsToggleSub(
                        label = target.label,
                        sublabel = if (target.available) target.stateLabel else stringResource(R.string.pills_unavailable),
                        checked = included,
                        onCheckedChange = { onAction(HomeSettingsAction.SetManualGroupMember(group.id, device, it)) },
                    )
                    if (included) {
                        MoveControls { direction ->
                            onAction(HomeSettingsAction.MoveManualGroupMember(group.id, device, direction))
                        }
                        if (otherGroups.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { memberToMove = device }) {
                                    Text(stringResource(R.string.pills_manual_move_to_group))
                                }
                            }
                        }
                    }
                }
                if (index != catalog.devices.lastIndex) SettingsDivider()
            }
        }
        PillButton(label = stringResource(R.string.pills_manual_delete), onClick = { confirmDelete = true })
    }
}

@Composable
private fun DeviceTargetRow(
    target: SettingsPillTarget,
    pinned: Boolean,
    onEnabled: (Boolean) -> Unit,
    onAction: (HomeSettingsAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(target.label, style = AppleTypography.titleMedium, color = AppleColors.primary)
                Text(
                    when {
                        !target.available -> stringResource(R.string.pills_unavailable)
                        target.stale -> stringResource(R.string.pills_stale_state, target.stateLabel)
                        else -> target.stateLabel
                    },
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                )
            }
            IosSwitch(checked = target.enabled, onCheckedChange = onEnabled)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = pinned || target.canPin,
                onClick = { onAction(HomeSettingsAction.TogglePin(target.ref, target.canPin)) },
            ) {
                Text(
                    when {
                        pinned -> stringResource(R.string.pills_unpin)
                        !target.available -> stringResource(R.string.pills_unavailable)
                        target.stale -> stringResource(R.string.pills_stale)
                        else -> stringResource(R.string.pills_pin)
                    },
                )
            }
        }
    }
}

@Composable
private fun PinnableTargetRow(
    target: SettingsPillTarget,
    pinned: Boolean,
    onAction: (HomeSettingsAction) -> Unit,
) {
    SettingsToggleSub(
        label = target.label,
        sublabel = if (target.stale) {
            stringResource(R.string.pills_stale_state, stringResource(R.string.pills_group_member_count, target.stateLabel))
        } else {
            stringResource(R.string.pills_group_member_count, target.stateLabel)
        },
        checked = pinned,
        onCheckedChange = { onAction(HomeSettingsAction.TogglePin(target.ref, target.canPin)) },
    )
}

@Composable
private fun MoveControls(onMove: (MoveDirection) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        MoveButton(Icons.Outlined.FirstPage, stringResource(R.string.pills_move_first)) { onMove(MoveDirection.FIRST) }
        MoveButton(Icons.Outlined.NavigateBefore, stringResource(R.string.pills_move_before)) { onMove(MoveDirection.PREVIOUS) }
        MoveButton(Icons.Outlined.NavigateNext, stringResource(R.string.pills_move_after)) { onMove(MoveDirection.NEXT) }
        MoveButton(Icons.Outlined.LastPage, stringResource(R.string.pills_move_last)) { onMove(MoveDirection.LAST) }
    }
}

@Composable
private fun MoveButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = AppleColors.accent) }
}

@Composable
private fun NameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { SettingsTextField(stringResource(R.string.pills_manual_name), value, onValueChange) },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = onConfirm) { Text(stringResource(R.string.pills_manual_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun sectionLabel(sectionId: String): String = when {
    sectionId == HomeSectionIds.FAVORITES -> stringResource(R.string.pills_section_favorites)
    sectionId == HomeSectionIds.AREAS -> stringResource(R.string.pills_section_areas)
    sectionId == HomeSectionIds.MANUAL_GROUPS -> stringResource(R.string.pills_manual_groups)
    sectionId.startsWith("kind:") -> runCatching {
        stringResource(com.iblu01.portallauncher.PillKind.valueOf(sectionId.substringAfter(':')).labelRes)
    }.getOrDefault(sectionId)
    else -> sectionId
}

@Composable
private fun SettingsPageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}
