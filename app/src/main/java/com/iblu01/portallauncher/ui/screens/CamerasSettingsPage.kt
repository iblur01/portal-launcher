package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.components.SettingsToggleSub
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** One camera as the settings page sees it: identity, display name and live reachability. */
data class CameraSettingsEntry(
    val entityId: String,
    val label: String,
    val available: Boolean,
)

/**
 * Camera centre configuration: which cameras it shows, in which order, which one is the main one,
 * which mode it opens in, and whether the general pill is pinned.
 *
 * Every choice is stored as an exception to Home Assistant's own list, so a camera added or
 * removed in Home Assistant needs no migration here — and a camera that vanished simply stops
 * being listed, without blocking the page or the remaining cameras.
 */
@Composable
fun CamerasSettingsPage(
    cameras: List<CameraSettingsEntry>,
    preferences: CameraPreferences,
    generalPillPinned: Boolean,
    onPreferences: ((CameraPreferences) -> CameraPreferences) -> Unit,
    onGeneralPillPinned: (Boolean) -> Unit,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val ordered = remember(cameras, preferences) {
        val visible = preferences.visibleCameras(cameras.map { it.entityId })
        val byId = cameras.associateBy { it.entityId }
        visible.mapNotNull(byId::get) + cameras.filter { it.entityId !in visible }
    }
    val mainCameraId = preferences.resolveMainCamera(cameras.map { it.entityId })

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSubPageHeader(stringResource(R.string.settings_cameras_title), onBack, showBack = showBack)

        if (cameras.isEmpty()) {
            Text(
                stringResource(R.string.settings_cameras_empty),
                style = AppleTypography.bodyLarge,
                color = AppleColors.secondary,
            )
            return@Column
        }

        SettingsSection(title = stringResource(R.string.settings_cameras_visible)) {
            Text(
                stringResource(R.string.settings_cameras_order_hint),
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ordered.forEachIndexed { index, camera ->
                val visible = camera.entityId !in preferences.hidden
                CameraVisibilityRow(
                    camera = camera,
                    visible = visible,
                    canMoveUp = index > 0,
                    canMoveDown = index < ordered.lastIndex,
                    onVisible = { wanted ->
                        onPreferences { current ->
                            current.copy(
                                hidden = if (wanted) current.hidden - camera.entityId
                                else current.hidden + camera.entityId,
                            )
                        }
                    },
                    onMove = { offset ->
                        onPreferences { current ->
                            current.copy(order = moved(ordered.map { it.entityId }, index, offset))
                        }
                    },
                )
                if (index != ordered.lastIndex) SettingsDivider()
            }
        }

        SettingsSection(title = stringResource(R.string.settings_cameras_main)) {
            val selectable = ordered.filter { it.entityId !in preferences.hidden }
            selectable.forEachIndexed { index, camera ->
                val description = stringResource(R.string.settings_camera_main_desc, camera.label)
                SettingsRow(
                    label = camera.label,
                    value = if (camera.entityId == mainCameraId) "✓" else "",
                    onClick = {
                        onPreferences { current -> current.copy(mainCameraId = camera.entityId) }
                    },
                    modifier = Modifier.semantics { contentDescription = description },
                )
                if (index != selectable.lastIndex) SettingsDivider()
            }
        }

        SettingsSection(title = stringResource(R.string.settings_cameras_default_mode)) {
            listOf(
                CameraCenterMode.MAIN to R.string.camera_center_mode_main,
                CameraCenterMode.GRID to R.string.camera_center_mode_grid,
            ).forEachIndexed { index, (mode, label) ->
                SettingsRow(
                    label = stringResource(label),
                    value = if (preferences.defaultMode == mode) "✓" else "",
                    onClick = { onPreferences { current -> current.copy(defaultMode = mode) } },
                )
                if (index == 0) SettingsDivider()
            }
        }

        SettingsSection(title = stringResource(R.string.pill_cameras_label)) {
            SettingsToggle(
                label = stringResource(R.string.settings_cameras_pin_general),
                checked = generalPillPinned,
                onCheckedChange = onGeneralPillPinned,
            )
        }
    }
}

@Composable
private fun CameraVisibilityRow(
    camera: CameraSettingsEntry,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisible: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val toggleDescription = stringResource(R.string.settings_camera_toggle_desc, camera.label)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).semantics { contentDescription = toggleDescription }) {
            SettingsToggleSub(
                label = camera.label,
                // A camera Home Assistant reports as unavailable stays listed and configurable:
                // it must not disappear from the settings just because it is momentarily down.
                sublabel = if (camera.available) null
                else stringResource(R.string.camera_stream_unavailable),
                checked = visible,
                onCheckedChange = onVisible,
            )
        }
        MoveButton(
            icon = Icons.Outlined.KeyboardArrowUp,
            description = stringResource(R.string.settings_camera_move_up_desc, camera.label),
            enabled = canMoveUp,
            onClick = { onMove(-1) },
        )
        MoveButton(
            icon = Icons.Outlined.KeyboardArrowDown,
            description = stringResource(R.string.settings_camera_move_down_desc, camera.label),
            enabled = canMoveDown,
            onClick = { onMove(1) },
        )
    }
}

@Composable
private fun MoveButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(36.dp)
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .then(if (enabled) Modifier.appleClickable(onClick) else Modifier)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AppleColors.primary else AppleColors.quaternary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Moves the entry at [index] by [offset], clamped to the list. Returned as the complete explicit
 * order so the saved preference no longer depends on Home Assistant's own iteration order.
 */
internal fun moved(ids: List<String>, index: Int, offset: Int): List<String> {
    val target = (index + offset).coerceIn(0, ids.lastIndex)
    if (target == index || index !in ids.indices) return ids
    val mutable = ids.toMutableList()
    mutable.add(target, mutable.removeAt(index))
    return mutable
}
