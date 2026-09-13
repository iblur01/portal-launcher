package com.iblu01.portallauncher.domain

import com.iblu01.portallauncher.domain.scene.SceneActivationState
import com.iblu01.portallauncher.domain.scene.SceneActivationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneActivationStateTest {
    @Test fun `a first tap registers exactly one pending request`() {
        val (state, token) = requireNotNull(SceneActivationState().request("scene.evening"))

        assertEquals(SceneActivationStatus.PENDING, state.statusOf("scene.evening"))
        assertEquals(1L, token)
    }

    @Test fun `a second tap while in flight is refused instead of calling twice`() {
        val (state, _) = requireNotNull(SceneActivationState().request("scene.evening"))

        assertNull(state.request("scene.evening"))
    }

    @Test fun `another scene stays tappable while one is in flight`() {
        val (state, _) = requireNotNull(SceneActivationState().request("scene.evening"))

        assertNotNull(state.request("scene.morning"))
    }

    @Test fun `Home Assistant success and failure are both reported honestly`() {
        val (pending, token) = requireNotNull(SceneActivationState().request("scene.evening"))

        assertEquals(
            SceneActivationStatus.SUCCEEDED,
            pending.settle("scene.evening", token, success = true, nowMs = 10L).statusOf("scene.evening"),
        )
        assertEquals(
            SceneActivationStatus.FAILED,
            pending.settle("scene.evening", token, success = false, nowMs = 10L).statusOf("scene.evening"),
        )
    }

    @Test fun `a failed scene becomes tappable again immediately`() {
        val (pending, token) = requireNotNull(SceneActivationState().request("scene.evening"))
        val failed = pending.settle("scene.evening", token, success = false, nowMs = 10L)

        assertNotNull(failed.request("scene.evening"))
    }

    @Test fun `a late answer from a previous request cannot overwrite a fresher one`() {
        val (first, firstToken) = requireNotNull(SceneActivationState().request("scene.evening"))
        val settled = first.settle("scene.evening", firstToken, success = false, nowMs = 10L)
        val (second, _) = requireNotNull(settled.request("scene.evening"))

        val stale = second.settle("scene.evening", firstToken, success = true, nowMs = 20L)

        assertEquals(SceneActivationStatus.PENDING, stale.statusOf("scene.evening"))
    }

    @Test fun `a settled outcome disappears once its display window elapsed`() {
        val (pending, token) = requireNotNull(SceneActivationState().request("scene.evening"))
        val settled = pending.settle("scene.evening", token, success = true, nowMs = 0L)

        assertEquals(
            SceneActivationStatus.SUCCEEDED,
            settled.expire(nowMs = SceneActivationState.FEEDBACK_TTL_MS - 1).statusOf("scene.evening"),
        )
        assertNull(settled.expire(nowMs = SceneActivationState.FEEDBACK_TTL_MS).statusOf("scene.evening"))
    }

    @Test fun `an in-flight request is never expired away`() {
        val (pending, _) = requireNotNull(SceneActivationState().request("scene.evening"))

        assertTrue(pending.expire(nowMs = 10 * SceneActivationState.FEEDBACK_TTL_MS).isPending("scene.evening"))
    }
}
