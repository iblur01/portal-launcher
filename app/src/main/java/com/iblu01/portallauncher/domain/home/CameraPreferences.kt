package com.iblu01.portallauncher.domain.home

/** How the camera center lays its cameras out. */
enum class CameraCenterMode { MAIN, GRID }

/**
 * User configuration of the camera center. Deliberately expressed as *exceptions* to Home
 * Assistant's own camera list: a camera nobody touched is visible, and an id that disappears from
 * HA simply stops resolving. Nothing here has to be migrated when a camera is added or removed.
 *
 * @param hidden camera entity ids explicitly removed from the center.
 * @param order camera entity ids in user order; ids absent from it sort last, alphabetically.
 * @param mainCameraId the camera opened by the general pill; falls back to the first visible one.
 */
data class CameraPreferences(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val hidden: Set<String> = emptySet(),
    val order: List<String> = emptyList(),
    val mainCameraId: String? = null,
    val defaultMode: CameraCenterMode = CameraCenterMode.MAIN,
) {
    /**
     * Projects [availableIds] (the camera entity ids currently known to HA) through the saved
     * visibility and order. Pure, so the ordering rule is unit-testable without preferences.
     */
    fun visibleCameras(availableIds: List<String>): List<String> {
        val ranked = order.withIndex().associate { (index, id) -> id to index }
        return availableIds.asSequence()
            .filterNot { it in hidden }
            .sortedWith(
                compareBy<String> { ranked[it] ?: Int.MAX_VALUE }.thenBy { it },
            )
            .toList()
    }

    /** The camera the general pill opens: the saved one while it is still visible, else the first. */
    fun resolveMainCamera(availableIds: List<String>): String? {
        val visible = visibleCameras(availableIds)
        return mainCameraId?.takeIf { it in visible } ?: visible.firstOrNull()
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
