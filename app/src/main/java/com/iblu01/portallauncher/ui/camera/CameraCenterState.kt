package com.iblu01.portallauncher.ui.camera

import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences

/**
 * What the camera center is showing. `null` [open] means closed — and closed is the only state in
 * which no player exists, which is what makes "closing stops every stream" a structural property
 * rather than a call the UI has to remember to make.
 */
data class CameraCenterState(val open: Open? = null) {
    data class Open(
        /** Cameras to render, already filtered and ordered by the user's preferences. */
        val cameras: List<String>,
        val selected: String,
        val mode: CameraCenterMode,
    )

    val isOpen: Boolean get() = open != null

    /**
     * Opens the center. [target] is the camera an individual pill asked for; `null` comes from the
     * general pill and uses the configured main camera and default mode.
     *
     * An individual pill always shows *its* camera, even when another one is configured as main,
     * and it forces the single-camera mode: the user asked for that camera, not for a wall of them.
     * Returns the unchanged state when no camera is available, so an empty configuration can never
     * open a center with nothing in it.
     */
    fun opened(
        target: String?,
        availableIds: List<String>,
        preferences: CameraPreferences,
    ): CameraCenterState {
        val cameras = preferences.visibleCameras(availableIds)
        val selected = when {
            // An explicitly requested camera opens even when the user hid it from the centre:
            // tapping its own pill is a clearer intent than the centre's visibility list.
            target != null && target in availableIds -> target
            else -> preferences.resolveMainCamera(availableIds)
        } ?: return this
        val mode = if (target != null) CameraCenterMode.MAIN else preferences.defaultMode
        return copy(
            open = Open(
                cameras = if (selected in cameras) cameras else cameras + selected,
                selected = selected,
                mode = mode,
            ),
        )
    }

    fun closed(): CameraCenterState = CameraCenterState()

    /** Grid → main: picking a thumbnail promotes it to the single large stream. */
    fun selected(entityId: String): CameraCenterState {
        val current = open ?: return this
        if (entityId !in current.cameras) return this
        return copy(open = current.copy(selected = entityId, mode = CameraCenterMode.MAIN))
    }

    fun withMode(mode: CameraCenterMode): CameraCenterState {
        val current = open ?: return this
        return copy(open = current.copy(mode = mode))
    }

    /**
     * Reconciles an open center with a fresh Home Assistant snapshot. A camera that disappears
     * leaves the list; if it was the selected one, the center falls back to another rather than
     * closing, and only an empty list closes it. Preference edits are picked up the same way.
     */
    fun reconciled(availableIds: List<String>, preferences: CameraPreferences): CameraCenterState {
        val current = open ?: return this
        val cameras = preferences.visibleCameras(availableIds).let { visible ->
            // Keep an explicitly opened but hidden camera as long as HA still exposes it.
            if (current.selected in visible || current.selected !in availableIds) visible
            else visible + current.selected
        }
        if (cameras.isEmpty()) return closed()
        val selected = current.selected.takeIf { it in cameras } ?: cameras.first()
        return copy(open = current.copy(cameras = cameras, selected = selected))
    }
}
