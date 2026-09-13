package com.iblu01.portallauncher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerStateTest {
    @Test fun `active timer counts down from finishes at`() {
        val entity = HaEntity(
            "timer.pasta", "active",
            JSONObject().put("duration", "00:10:00").put("remaining", "00:09:59")
                .put("finishes_at", "2026-08-28T10:10:00Z"),
        )
        val timer = timerStateOf(entity, nowMs = 1_787_911_500_000L)
        assertTrue(timer.running)
        assertEquals(300L, timer.remainingSeconds)
        assertEquals(600L, timer.durationSeconds)
        assertEquals(0.5f, timer.progress)
    }

    @Test fun `paused timer keeps reported remaining duration`() {
        val timer = timerStateOf(HaEntity(
            "timer.tea", "paused", JSONObject().put("duration", "00:05:00").put("remaining", "00:02:07"),
        ))
        assertFalse(timer.running)
        assertTrue(timer.paused)
        assertEquals(127L, timer.remainingSeconds)
        assertEquals("02:07", formatTimerDuration(timer.remainingSeconds))
    }

    @Test fun `long timer uses hours without changing digit width`() {
        assertEquals("1:01:01", formatTimerDuration(3_661))
        assertEquals(3723L, parseTimerDuration("01:02:03"))
    }
}
