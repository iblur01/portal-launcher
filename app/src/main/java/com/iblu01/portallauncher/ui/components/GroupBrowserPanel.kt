package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.GroupCollectiveAction
import com.iblu01.portallauncher.domain.home.PillGroupSnapshot
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.scaled

/** One explicit Home Assistant service call derived from a typed collective action. */
data class GroupServiceCall(
    val domain: String,
    val service: String,
    val entityId: String,
)

/**
 * Pure collective-action router. It intentionally never looks at a label, group name or localized
 * state. A group action can only execute the exact services admitted by the catalog policy.
 */
fun collectiveServiceCalls(group: PillGroupSnapshot): List<GroupServiceCall> {
    val availableMembers = group.resolvedMembers
        .filter { it.availability == Availability.AVAILABLE }
        .distinctBy { it.chip.entityId }
    return when (group.collectiveAction) {
        GroupCollectiveAction.TURN_OFF -> availableMembers.mapNotNull { member ->
            val domain = when (member.chip.kind) {
                PillKind.LIGHTS -> "light"
                PillKind.SWITCH -> if (member.chip.entityId.startsWith("input_boolean.")) {
                    "input_boolean"
                } else {
                    "switch"
                }
                PillKind.FAN, PillKind.PURIFIER -> "fan"
                else -> null
            }
            domain?.let { GroupServiceCall(it, "turn_off", member.chip.entityId) }
        }
        GroupCollectiveAction.CLOSE -> availableMembers.mapNotNull { member ->
            member.takeIf { it.chip.kind == PillKind.COVER }
                ?.let { GroupServiceCall("cover", "close_cover", it.chip.entityId) }
        }
        GroupCollectiveAction.LOCK -> availableMembers.mapNotNull { member ->
            member.takeIf { it.chip.kind == PillKind.LOCK }
                ?.let { GroupServiceCall("lock", "lock", it.chip.entityId) }
        }
        null -> emptyList()
    }
}

/**
 * Typed group browser. Group/device navigation lives in [PanelRequest.Group]'s reducer; this
 * composable only renders the current level and emits navigation/service intents.
 */
@Composable
fun GroupBrowserPanel(
    group: PillGroupSnapshot,
    selectedDevice: LauncherChip?,
    deviceRequested: Boolean,
    onSelectMember: (ResolvedPill) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onCollectiveAction: (List<GroupServiceCall>) -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    if (deviceRequested) {
        if (selectedDevice != null) {
            ChipActionsPanel(
                chip = selectedDevice,
                onDismiss = onBack,
                navigationIcon = Icons.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.group_back, group.chip.label),
                onClose = onDismiss,
                modifier = modifier.testTag("groupDevicePanel"),
                fullScreen = fullScreen,
            )
        } else {
            UnavailableGroupDevice(
                groupName = group.chip.label,
                onBack = onBack,
                onDismiss = onDismiss,
                modifier = modifier,
            )
        }
        return
    }

    val calls = collectiveServiceCalls(group)
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (fullScreen) Modifier
                else Modifier.padding(horizontal = 14.dp.scaled(), vertical = 16.dp.scaled())
            )
            .testTag("groupBrowserPanel"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f), AppleShapes.panel)
                .then(
                    if (fullScreen) Modifier
                    else Modifier.border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                )
                .padding(horizontal = 22.dp.scaled(), vertical = 20.dp.scaled()),
        ) {
            PanelHeader(
                title = group.chip.label,
                titleIcon = launcherIcon(group.chip.icon),
                accent = launcherChipAccent(group.chip),
                onClose = onDismiss,
                closeContentDescription = stringResource(R.string.group_close, group.chip.label),
            )
            Spacer(Modifier.height(12.dp.scaled()))
            Text(
                group.chip.value,
                style = AppleTypography.titleMedium,
                color = AppleColors.secondary,
                modifier = Modifier.testTag("groupSummary"),
            )
            if (group.availability == Availability.STALE) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.group_stale_state),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.warning,
                    modifier = Modifier.testTag("groupStaleState"),
                )
            }
            if (calls.isNotEmpty()) {
                Spacer(Modifier.height(14.dp.scaled()))
                CollectiveActionButton(group.collectiveAction!!, calls) {
                    onCollectiveAction(calls)
                }
            }
            Spacer(Modifier.height(14.dp.scaled()))
            if (group.resolvedMembers.isEmpty()) {
                GroupEmptyState(Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp.scaled()),
                ) {
                    items(group.resolvedMembers, key = { it.ref.stableKey }) { member ->
                        GroupMemberRow(member = member, onSelectMember = onSelectMember)
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectiveActionButton(
    action: GroupCollectiveAction,
    calls: List<GroupServiceCall>,
    onClick: () -> Unit,
) {
    val label = when (action) {
        GroupCollectiveAction.TURN_OFF -> stringResource(R.string.group_action_turn_off)
        GroupCollectiveAction.CLOSE -> stringResource(R.string.group_action_close)
        GroupCollectiveAction.LOCK -> stringResource(R.string.group_action_lock)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("groupCollectiveAction"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, tint = AppleColors.accent)
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary, modifier = Modifier.weight(1f))
        Text("${calls.size}", style = AppleTypography.bodySmall, color = AppleColors.secondary)
    }
}

@Composable
private fun GroupMemberRow(
    member: ResolvedPill,
    onSelectMember: (ResolvedPill) -> Unit,
) {
    val available = member.availability == Availability.AVAILABLE
    val openLabel = stringResource(R.string.home_open_item, member.chip.label)
    val staleLabel = stringResource(R.string.group_stale_state)
    val unavailableLabel = stringResource(R.string.group_device_unavailable)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .alpha(if (available) 1f else 0.68f)
            .background(AppleColors.frostedFill, AppleShapes.card)
            .then(
                if (available) Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = openLabel,
                ) { onSelectMember(member) } else Modifier,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = buildString {
                    append(member.chip.label)
                    if (member.chip.value.isNotBlank()) append(", ${member.chip.value}")
                    when (member.availability) {
                        Availability.AVAILABLE -> Unit
                        Availability.STALE -> append(", $staleLabel")
                        Availability.UNAVAILABLE -> append(", $unavailableLabel")
                    }
                }
                if (!available) disabled()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("groupMember:${member.ref.stableKey}"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(chip = member.chip, onClick = null, onLongPress = null)
        Column(Modifier.weight(1f)) {
            Text(
                member.chip.label,
                style = AppleTypography.titleMedium,
                color = AppleColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (member.availability) {
                    Availability.AVAILABLE -> member.chip.value
                    Availability.STALE -> staleLabel
                    Availability.UNAVAILABLE -> unavailableLabel
                },
                style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                color = AppleColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GroupEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().testTag("groupEmptyState"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.group_devices_unavailable), style = AppleTypography.titleLarge, color = AppleColors.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.group_devices_unavailable_hint),
                style = AppleTypography.bodySmall,
                color = AppleColors.secondary,
            )
        }
    }
}

@Composable
private fun UnavailableGroupDevice(
    groupName: String,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(22.dp)
            .background(Color.Black.copy(alpha = 0.82f), AppleShapes.panel)
            .padding(20.dp)
            .testTag("groupUnavailableDevice"),
    ) {
        PanelHeader(
            title = stringResource(R.string.panel_device_unavailable),
            onNavigation = onBack,
            navigationIcon = Icons.Filled.ArrowBack,
            navigationContentDescription = stringResource(R.string.group_back, groupName),
            onClose = onDismiss,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.group_unavailable_detail),
            style = AppleTypography.bodyMedium,
            color = AppleColors.secondary,
        )
    }
}
