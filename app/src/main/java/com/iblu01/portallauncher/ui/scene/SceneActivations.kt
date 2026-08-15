package com.iblu01.portallauncher.ui.scene

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.iblu01.portallauncher.domain.scene.SceneActivationState
import com.iblu01.portallauncher.domain.scene.SceneActivationStatus
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay

/**
 * Compose-side owner of [SceneActivationState]: routes a tap to `scene.turn_on` and turns Home
 * Assistant's own answer into the pill's transient feedback.
 *
 * The guard against a double activation lives in the pure state, not here — this holder only
 * forwards the request when the state accepted it.
 */
@Stable
class SceneActivations internal constructor(
    private val callService: (entityId: String, onResult: (Boolean) -> Unit) -> Unit,
    private val nowMs: () -> Long,
) {
    var state: SceneActivationState by mutableStateOf(SceneActivationState())
        private set

    fun statusOf(entityId: String): SceneActivationStatus? = state.statusOf(entityId)

    /** Activates [entityId] unless one of its activations is still in flight. */
    fun activate(entityId: String) {
        if (entityId.isBlank()) return
        val (next, token) = state.request(entityId) ?: return
        state = next
        callService(entityId) { success ->
            // The answer arrives on the socket thread; Compose state is snapshot-safe to write.
            state = state.settle(entityId, token, success, nowMs())
        }
    }

    internal fun expire() {
        state = state.expire(nowMs())
    }
}

/**
 * Remembers the scene activations for this composition and clears settled outcomes once their
 * display window elapsed. The ticker only runs while an outcome is actually displayed.
 */
@Composable
fun rememberSceneActivations(
    callService: (domain: String, service: String, entityId: String?, data: Map<String, Any>?, onResult: ((Boolean) -> Unit)?) -> Unit,
): SceneActivations {
    val activations = remember(callService) {
        SceneActivations(
            callService = { entityId, onResult ->
                callService("scene", "turn_on", entityId, null, onResult)
            },
            nowMs = System::currentTimeMillis,
        )
    }
    val hasSettled = activations.state.entries.values.any { it.status != SceneActivationStatus.PENDING }
    LaunchedEffect(hasSettled) {
        if (!hasSettled) return@LaunchedEffect
        while (true) {
            delay(SceneActivationState.FEEDBACK_TTL_MS / 4)
            activations.expire()
            if (activations.state.entries.values.none { it.status != SceneActivationStatus.PENDING }) break
        }
    }
    return activations
}
