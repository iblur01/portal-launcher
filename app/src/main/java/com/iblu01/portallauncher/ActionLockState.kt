package com.iblu01.portallauncher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The panel's guest mode: everything is still drawn, nothing can be touched.
 *
 * Meant for an empty home — Home Assistant flips the switch, and the wall panel becomes a display.
 * Locking the *service call* rather than the controls is deliberate: the screen stays honest about
 * the state of the house, which is the point of a panel nobody is standing in front of.
 */
object ActionLockState {

    /**
     * Alarm calls are never blocked. The code is the authentication, and a locked panel that
     * cannot disarm is a panel that traps whoever comes home.
     */
    private const val ALWAYS_ALLOWED_DOMAIN = "alarm_control_panel"

    var locked by mutableStateOf(false)
        private set

    /** Shown when someone tries anyway. Null falls back to the built-in wording. */
    var reason by mutableStateOf<String?>(null)
        private set

    fun set(locked: Boolean, reason: String? = null) {
        this.locked = locked
        this.reason = reason?.takeIf { it.isNotBlank() }
    }

    /** True when this particular call must not reach Home Assistant. */
    fun blocks(domain: String): Boolean = locked && domain != ALWAYS_ALLOWED_DOMAIN
}
