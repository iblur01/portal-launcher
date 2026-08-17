package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * An open folder, as a popup over the grid.
 *
 * A popup rather than the expanding in-place container other launchers animate: on a wall panel the
 * grid is read from a distance, and a centred dialog puts the folder's contents where the eye
 * already is instead of wherever the folder happened to sit. It also means the folder needs no
 * geometry of its own — no reserved cells, no reflow.
 *
 * Renaming lives here rather than only in the long-press menu because this is where the user is
 * when the folder's name turns out to be wrong.
 */
@Composable
fun FolderDialog(
    folder: GridItem?,
    onDismiss: () -> Unit,
    onLaunch: (GridItem) -> Unit,
    onRename: (String) -> Unit,
    onRemoveMember: (GridItem) -> Unit,
    onDelete: () -> Unit,
    /** True when the member has a pending notification, same rule as on the grid. */
    hasDot: (GridItem) -> Boolean = { false },
) {
    if (folder == null) return
    var renaming by remember(folder.key) { mutableStateOf(false) }
    var draft by remember(folder.key) { mutableStateOf(folder.label) }
    // Long-press arms removal on one member at a time: a tile that can always be removed with one
    // tap would make opening a folder a hazard.
    var armedForRemoval by remember(folder.key) { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics { dialog() }
            .testTag("folderDialog"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(AppleShapes.panel)
                .background(AppleColors.elevated.copy(alpha = 0.97f), AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                // Taps inside the folder must not reach the dismissing scrim.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 14.dp),
        ) {
            if (renaming) {
                SettingsTextField(
                    label = stringResource(R.string.folder_rename_label),
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(R.string.folder_rename_placeholder),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        stringResource(R.string.context_menu_confirm),
                        style = AppleTypography.titleMedium,
                        color = AppleColors.accent,
                        modifier = Modifier
                            .clip(AppleShapes.pill)
                            .appleClickable { onRename(draft); renaming = false }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = folder.label,
                        style = AppleTypography.titleMedium,
                        color = AppleColors.primary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(AppleShapes.pill)
                            .appleClickable { draft = folder.label; renaming = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("folderTitle"),
                    )
                }
                Text(
                    text = stringResource(R.string.folder_hint),
                    style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                    color = AppleColors.tertiary,
                    modifier = Modifier.padding(horizontal = 26.dp).padding(bottom = 8.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 84.dp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 12.dp),
            ) {
                items(folder.folderMembers, key = { it.key }) { member ->
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.pointerInput(member.key) {
                                detectTapGestures(
                                    onTap = { onLaunch(member) },
                                    onLongPress = { armedForRemoval = member.key },
                                )
                            }
                        ) {
                            AppTile(
                                label = member.label,
                                icon = member.icon,
                                // The tap is owned by the gesture above so the long-press can win.
                                onClick = {},
                                dot = hasDot(member),
                            )
                        }
                        if (armedForRemoval == member.key) {
                            Text(
                                stringResource(R.string.folder_take_out),
                                style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                                color = AppleColors.accent,
                                modifier = Modifier
                                    .clip(AppleShapes.pill)
                                    .background(AppleColors.elevated, AppleShapes.pill)
                                    .appleClickable {
                                        armedForRemoval = null
                                        onRemoveMember(member)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    stringResource(R.string.folder_delete),
                    style = AppleTypography.titleMedium,
                    color = AppleColors.error,
                    modifier = Modifier
                        .clip(AppleShapes.pill)
                        .appleClickable { onDelete(); onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
