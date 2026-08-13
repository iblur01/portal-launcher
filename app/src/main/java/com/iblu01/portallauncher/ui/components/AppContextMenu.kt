package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.apps.AppShortcut
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.apps.GridSpan
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Which item the long-press menu belongs to, its footprint, and where it sits in the root, in px. */
data class AppMenuTarget(
    val item: GridItem,
    val anchor: IntRect,
    val span: GridSpan = GridSpan(),
)

/**
 * The long-press menu of a real launcher: the app's own shortcuts first, then the launcher actions.
 *
 * App shortcuts only exist when Portal Launcher is the device's selected home app
 * (`hasShortcutHostPermission()`), so when it is not, the menu says so instead of silently
 * showing a shorter list.
 */
@Composable
fun AppContextMenu(
    target: AppMenuTarget?,
    shortcuts: List<AppShortcut>,
    canUninstall: Boolean,
    isDefaultHome: Boolean,
    onDismiss: () -> Unit,
    onShortcut: (AppShortcut) -> Unit,
    onRename: (String) -> Unit,
    onHide: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onRemoveShortcut: () -> Unit,
    onDeleteFolder: () -> Unit = {},
    onOpenHomeSettings: () -> Unit = {},
    onResize: (GridSpan) -> Unit = {},
    onRemoveWidget: () -> Unit = {},
    /** Upper bound for the size steppers: a widget cannot be wider than the page. */
    maxSpan: GridSpan = GridSpan(4, 3),
) {
    if (target == null) return
    val item = target.item
    var renaming by remember(target.item.key) { mutableStateOf(false) }
    var draft by remember(target.item.key) { mutableStateOf(item.label) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Scrim: dismisses, and swallows taps so nothing behind the menu reacts.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() }
        )

        val density = LocalDensity.current
        val menuWidth = 268.dp
        val margin = 12.dp
        val anchorCentreX = with(density) { ((target.anchor.left + target.anchor.right) / 2).toDp() }
        val anchorBottom = with(density) { target.anchor.bottom.toDp() }
        val anchorTop = with(density) { target.anchor.top.toDp() }
        // Positioned from the *measured* height, not an estimate: with shortcuts the menu is tall
        // enough to run off a 470 dp-high panel, and an estimate that is wrong either overlaps the
        // tile or hangs off the screen. Costs one extra frame at the first, still-invisible layout.
        var menuHeight by remember(target.item.key) { mutableStateOf(0.dp) }
        val maxMenuHeight = maxHeight - margin * 2
        val below = anchorBottom + 8.dp
        val y = when {
            menuHeight == 0.dp -> below
            below + menuHeight <= maxHeight - margin -> below
            // Not enough room underneath: flip above the tile, then clamp into the screen.
            else -> (anchorTop - 8.dp - menuHeight).coerceIn(margin, (maxHeight - margin - menuHeight).coerceAtLeast(margin))
        }
        val x = (anchorCentreX - menuWidth / 2)
            .coerceIn(margin, (maxWidth - menuWidth - margin).coerceAtLeast(margin))

        Column(
            modifier = Modifier
                .offset(x = x, y = y)
                .width(menuWidth)
                .heightIn(max = maxMenuHeight)
                .clip(AppleShapes.panel)
                .background(AppleColors.elevated.copy(alpha = 0.97f), AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                .onSizeChanged { menuHeight = with(density) { it.height.toDp() } }
                // Taps inside the panel must not reach the dismissing scrim. Deliberately NOT
                // `clickable`: that merges the panel into a single semantics node, which hides the
                // individual rows from accessibility (and from tests).
                .pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false).consume() }
                }
                // A long list of shortcuts on a short screen has to scroll rather than overflow.
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp),
        ) {
            Text(
                text = item.label,
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            if (renaming) {
                RenameField(
                    value = draft,
                    onValueChange = { draft = it },
                    onConfirm = { onRename(draft); onDismiss() },
                )
                return@Column
            }

            if (shortcuts.isNotEmpty()) {
                MenuSeparator()
                shortcuts.forEach { shortcut ->
                    ShortcutRow(shortcut) { onShortcut(shortcut); onDismiss() }
                }
            } else if (!isDefaultHome && !item.isShortcut && !item.isFolder) {
                MenuSeparator()
                Text(
                    text = stringResource(R.string.context_menu_shortcuts_unavailable),
                    style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                    color = AppleColors.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // Actionable, not just an explanation: this is where the user notices the problem.
                ActionRow(Icons.Outlined.Home, stringResource(R.string.context_menu_set_default_home)) {
                    onOpenHomeSettings(); onDismiss()
                }
            }

            MenuSeparator()
            if (item.isWidget) {
                // A widget has no label of its own to rename and no app-level action: what it does
                // have is a size.
                SizeStepper(
                    label = stringResource(R.string.context_menu_width_stepper),
                    value = target.span.width,
                    max = maxSpan.width,
                    onChange = { onResize(target.span.copy(width = it)) },
                )
                SizeStepper(
                    label = stringResource(R.string.context_menu_height_stepper),
                    value = target.span.height,
                    max = maxSpan.height,
                    onChange = { onResize(target.span.copy(height = it)) },
                )
                MenuSeparator()
                ActionRow(Icons.Outlined.Delete, stringResource(R.string.context_menu_remove_widget), danger = true) {
                    onRemoveWidget(); onDismiss()
                }
                return@Column
            }
            ActionRow(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.context_menu_rename)) {
                draft = item.label
                renaming = true
            }
            if (item.isFolder) {
                // A folder has no app behind it: no app info, no uninstall, and hiding it would
                // hide its members with it. Deleting spills them back onto the grid instead.
                ActionRow(Icons.Outlined.Delete, stringResource(R.string.folder_delete), danger = true) {
                    onDeleteFolder(); onDismiss()
                }
            } else if (item.isShortcut) {
                ActionRow(Icons.Outlined.Delete, stringResource(R.string.context_menu_remove_shortcut)) {
                    onRemoveShortcut(); onDismiss()
                }
            } else {
                ActionRow(Icons.Outlined.VisibilityOff, stringResource(R.string.context_menu_hide)) { onHide(); onDismiss() }
                ActionRow(Icons.Outlined.Info, stringResource(R.string.context_menu_app_info)) { onAppInfo(); onDismiss() }
                if (canUninstall) {
                    ActionRow(Icons.Outlined.Delete, stringResource(R.string.context_menu_uninstall), danger = true) {
                        onUninstall(); onDismiss()
                    }
                }
            }
        }
    }
}

/** − / + on a whole number of cells. Resizing a widget is not a free-form gesture here. */
@Composable
private fun SizeStepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            "$value",
            style = AppleTypography.titleMedium,
            color = AppleColors.secondary,
            modifier = Modifier.width(24.dp),
        )
        Spacer(Modifier.weight(1f))
        StepperButton("−", enabled = value > 1) { onChange(value - 1) }
        Spacer(Modifier.width(8.dp))
        StepperButton("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepperButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(AppleShapes.pill)
            .background(Color.White.copy(alpha = if (enabled) 0.12f else 0.04f), AppleShapes.pill)
            .then(if (enabled) Modifier.appleClickable(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = AppleTypography.titleMedium,
            color = if (enabled) AppleColors.primary else AppleColors.quaternary,
        )
    }
}

@Composable
private fun RenameField(value: String, onValueChange: (String) -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SettingsTextField(
            label = stringResource(R.string.context_menu_rename_label),
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.context_menu_rename_placeholder),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                stringResource(R.string.context_menu_confirm),
                style = AppleTypography.titleMedium,
                color = AppleColors.accent,
                modifier = Modifier
                    .clip(AppleShapes.pill)
                    .appleClickable(onConfirm)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: AppShortcut, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = shortcut.icon
        if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(22.dp))
        } else {
            Box(Modifier.size(22.dp).clip(AppleShapes.pill).background(AppleColors.frostedFill))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            shortcut.label,
            style = AppleTypography.titleMedium,
            color = AppleColors.primary,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (danger) AppleColors.error else AppleColors.accent
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = AppleTypography.titleMedium,
            color = if (danger) AppleColors.error else AppleColors.primary,
        )
    }
}

@Composable
private fun MenuSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .heightIn(min = 0.5.dp, max = 0.5.dp)
            .background(AppleColors.quaternary)
    )
}
