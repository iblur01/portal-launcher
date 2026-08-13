package com.iblu01.portallauncher.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Injected Home Assistant service caller (design §8). Composables read it from the composition
 * instead of touching the `PillHub` singleton directly — the only path from UI to the data layer.
 * The default `data` arg keeps the ~40 call sites textually unchanged (3-arg calls stay valid).
 */
interface CallService {
    operator fun invoke(domain: String, service: String, entityId: String?, data: Map<String, Any>? = null)
}

/** Provided once at the launcher root as `vm::callService`; `error` guards accidental use outside. */
val LocalCallService = staticCompositionLocalOf<CallService> {
    error("LocalCallService not provided")
}

/**
 * Live per-entity HA state store provided to the panel subtree (design §8). The instance is
 * stable for the whole life of the screen (safe under `staticCompositionLocalOf`): a HA push
 * rewrites individual `MutableState` slots, so only the composables reading the touched entity
 * recompose — providing a fresh `Map` here used to invalidate the entire launcher on every push.
 * The default empty store keeps previews and unprovided trees rendering (entities resolve null).
 */
val LocalHaStates = staticCompositionLocalOf { HaStates() }

/** entity_id -> area display name, provided at the launcher root (from the VM's snapshot).
 *  Read by the light-rooms grouping instead of touching the repository singleton. */
val LocalAreas = staticCompositionLocalOf<Map<String, String>> { emptyMap() }
