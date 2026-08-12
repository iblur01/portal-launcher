package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.LastPage
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import kotlin.math.abs

/** A stable manual-group choice supplied by the store-owning caller. */
data class ManualGroupMenuOption(
    val groupId: String,
    val name: String,
    val alreadyContainsPill: Boolean = false,
    val memberEntityIds: Set<String> = emptySet(),
)

/** Keyboard/TalkBack alternatives to drag and drop. */
enum class HomePillMove {
    BEFORE,
    AFTER,
    FIRST,
    LAST,
}

data class HomePillMoveAvailability(
    val before: Boolean = true,
    val after: Boolean = true,
    val first: Boolean = true,
    val last: Boolean = true,
) {
    fun allows(move: HomePillMove): Boolean = when (move) {
        HomePillMove.BEFORE -> before
        HomePillMove.AFTER -> after
        HomePillMove.FIRST -> first
        HomePillMove.LAST -> last
    }
}

/**
 * Stateless integration contract for pill interactions. Persistent truth remains in the
 * repository/ViewModel; composables only report user intent.
 */
data class HomePillActions(
    val onOpen: (ResolvedPill) -> Unit = {},
    val onSetPinned: (ResolvedPill, Boolean) -> Unit = { _, _ -> },
    val onAddToManualGroup: (ResolvedPill, String) -> Unit = { _, _ -> },
    val onStartReorder: (ResolvedPill) -> Unit = {},
    val onMove: (ResolvedPill, HomePillMove) -> Unit = { _, _ -> },
    val onOpenCommands: (ResolvedPill) -> Unit = {},
    /** Disables an individual device rule; Settings remains the recovery path. */
    val onHideDevice: (ResolvedPill) -> Unit = {},
    val canReorder: (ResolvedPill) -> Boolean = { true },
    val moveAvailability: (ResolvedPill) -> HomePillMoveAvailability = { HomePillMoveAvailability() },
    /** True only after the user chose the explicit drag reorder action for this pill. */
    val isDragReordering: (ResolvedPill) -> Boolean = { false },
    /** Locks pager/vertical scrolling for the duration of a real reorder gesture. */
    val onDragActiveChange: (Boolean) -> Unit = {},
    /** Applies the staged relative offset once, on a valid drop only. */
    val onDragDrop: (ResolvedPill, Int) -> Unit = { _, _ -> },
    /** Clears the armed reorder mode after drop/cancel. */
    val onDragFinished: (ResolvedPill) -> Unit = {},
)

/**
 * Real drag surface shared by Accueil and Maison. Each horizontal cell-width crossed persists one
 * BEFORE/AFTER move immediately; the accessible first/last/before/after menu remains equivalent.
 */
@Composable
fun Modifier.homePillReorderDrag(
    pill: ResolvedPill,
    actions: HomePillActions,
): Modifier {
    if (!actions.isDragReordering(pill)) return this
    val thresholdPx = with(LocalDensity.current) { 52.dp.toPx() }
    return this
        .testTag("homePillReorder:${pill.ref.stableKey}")
        .pointerInput(pill.ref.stableKey, thresholdPx) {
            val accumulator = HomePillDragAccumulator()
            detectHorizontalDragGestures(
                onDragStart = {
                    accumulator.cancel()
                    actions.onDragActiveChange(true)
                },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    accumulator.dragBy(amount, thresholdPx)
                },
                onDragEnd = {
                    val steps = accumulator.drop()
                    if (steps != 0) actions.onDragDrop(pill, steps)
                    actions.onDragActiveChange(false)
                    actions.onDragFinished(pill)
                },
                onDragCancel = {
                    accumulator.cancel()
                    actions.onDragActiveChange(false)
                    actions.onDragFinished(pill)
                },
            )
        }
}

/** Transient gesture accumulator: drop commits staged steps; cancel always yields no mutation. */
internal class HomePillDragAccumulator {
    private var remainder = 0f
    private var stagedSteps = 0

    fun dragBy(amount: Float, threshold: Float) {
        require(threshold > 0f)
        remainder += amount
        while (abs(remainder) >= threshold) {
            val direction = if (remainder > 0f) 1 else -1
            stagedSteps += direction
            remainder -= threshold * direction
        }
    }

    fun drop(): Int = stagedSteps.also { reset() }

    fun cancel(): Int {
        reset()
        return 0
    }

    private fun reset() {
        remainder = 0f
        stagedSteps = 0
    }
}

/**
 * Reusable long-press menu for both the clock tray and Maison.
 *
 * The popup is focusable, so Android dismisses it on Back. Its full-screen backdrop owns outside
 * taps and the panel consumes pointer downs without creating one merged accessibility node.
 */
@Composable
fun HomePillContextMenu(
    target: ResolvedPill?,
    isPinned: Boolean,
    manualGroups: List<ManualGroupMenuOption>,
    actions: HomePillActions,
    onDismiss: () -> Unit,
) {
    target ?: return
    var showGroups by remember(target.ref.stableKey) { mutableStateOf(false) }
    var showMoveActions by remember(target.ref.stableKey) { mutableStateOf(false) }
    val canPin = target.availability == Availability.AVAILABLE || isPinned
    val canOpenCommands = target.availability == Availability.AVAILABLE
    val canReorder = actions.canReorder(target)
    val moveAvailability = actions.moveAvailability(target)
    val targetDeviceId = (target.ref as? PillRef.Device)?.entityId
    fun ManualGroupMenuOption.containsTarget(): Boolean =
        alreadyContainsPill || targetDeviceId in memberEntityIds

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(onClickLabel = "Fermer le menu") { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(min = 280.dp, max = 360.dp)
                    .heightIn(max = 560.dp)
                    .clip(AppleShapes.panel)
                    .background(AppleColors.elevated.copy(alpha = 0.98f), AppleShapes.panel)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                    // Swallow taps without merging the children into one semantics node.
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                MenuHeader(target = target, isPinned = isPinned)
                MenuDivider()

                HomeMenuRow(
                    icon = Icons.Outlined.PushPin,
                    label = if (isPinned) "Désépingler" else "Épingler",
                    enabled = canPin,
                ) {
                    actions.onSetPinned(target, !isPinned)
                    onDismiss()
                }
                HomeMenuRow(
                    icon = Icons.Outlined.PlaylistAdd,
                    label = "Ajouter à un groupe manuel",
                    enabled = target.ref is PillRef.Device && manualGroups.any { !it.containsTarget() },
                ) { showGroups = !showGroups }
                if (showGroups) {
                    if (manualGroups.isEmpty()) {
                        MenuHint("Aucun groupe manuel. Créez-en un depuis Modifier.")
                    } else {
                        manualGroups.forEach { group ->
                            val containsTarget = group.containsTarget()
                            HomeMenuRow(
                                icon = Icons.Outlined.PlaylistAdd,
                                label = if (containsTarget) "${group.name} · déjà ajouté" else group.name,
                                enabled = !containsTarget,
                                nested = true,
                            ) {
                                actions.onAddToManualGroup(target, group.groupId)
                                onDismiss()
                            }
                        }
                    }
                }

                HomeMenuRow(
                    icon = Icons.Outlined.DragIndicator,
                    label = "Réorganiser",
                    enabled = canReorder,
                ) {
                    showMoveActions = !showMoveActions
                }
                if (showMoveActions) {
                    MoveRows(target, moveAvailability, actions, onDismiss)
                }

                MenuDivider()
                HomeMenuRow(
                    icon = Icons.Outlined.Tune,
                    label = "Ouvrir les commandes",
                    enabled = canOpenCommands,
                ) {
                    actions.onOpenCommands(target)
                    onDismiss()
                }
                if (!canOpenCommands) {
                    MenuHint("Données figées : les commandes sont temporairement indisponibles.")
                }
                if (target.ref is PillRef.Device) {
                    HomeMenuRow(
                        icon = Icons.Outlined.VisibilityOff,
                        label = "Ne plus afficher cet appareil",
                        enabled = true,
                    ) {
                        actions.onHideDevice(target)
                        onDismiss()
                    }
                    MenuHint("L’appareil pourra être réactivé depuis les réglages des pills.")
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(target: ResolvedPill, isPinned: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(target.chip.label, style = AppleTypography.titleLarge, color = AppleColors.primary)
        Text(
            buildString {
                append(target.chip.value)
                append(" · ")
                append(target.ref.groupDescription())
                if (isPinned) append(" · Épinglé")
                if (target.availability == Availability.STALE) append(" · Données figées")
            },
            style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
            color = AppleColors.secondary,
        )
    }
}

@Composable
private fun MoveRows(
    target: ResolvedPill,
    availability: HomePillMoveAvailability,
    actions: HomePillActions,
    onDismiss: () -> Unit,
) {
    HomeMenuRow(
        icon = Icons.Outlined.DragIndicator,
        label = "Déplacer par glisser-déposer",
        enabled = true,
        nested = true,
    ) {
        actions.onStartReorder(target)
        onDismiss()
    }
    listOf(
        Triple(Icons.Outlined.FirstPage, "Placer en premier", HomePillMove.FIRST),
        Triple(Icons.Outlined.ArrowBack, "Déplacer avant", HomePillMove.BEFORE),
        Triple(Icons.Outlined.ArrowForward, "Déplacer après", HomePillMove.AFTER),
        Triple(Icons.Outlined.LastPage, "Placer en dernier", HomePillMove.LAST),
    ).forEach { (icon, label, move) ->
        HomeMenuRow(icon, label, availability.allows(move), nested = true) {
            actions.onMove(target, move)
            onDismiss()
        }
    }
}

@Composable
private fun HomeMenuRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    nested: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                else Modifier,
            )
            .padding(start = if (nested) 34.dp else 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (nested) 30.dp else 34.dp)
                .background(AppleColors.frostedFill, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) AppleColors.accent else AppleColors.tertiary,
                modifier = Modifier.size(if (nested) 17.dp else 19.dp),
            )
        }
        Text(
            label,
            style = AppleTypography.titleMedium,
            color = if (enabled) AppleColors.primary else AppleColors.tertiary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MenuHint(text: String) {
    Text(
        text,
        style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
        color = AppleColors.tertiary,
        modifier = Modifier.padding(start = 66.dp, end = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun MenuDivider() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 5.dp)
            .height(0.5.dp)
            .background(AppleColors.frostedBorder),
    )
}

internal fun PillRef.groupDescription(): String = when (this) {
    is PillRef.Device -> "Appareil"
    is PillRef.AreaGroup -> "Groupe de pièce"
    is PillRef.KindGroup -> "Groupe de type"
    is PillRef.ManualGroup -> "Groupe manuel"
}
