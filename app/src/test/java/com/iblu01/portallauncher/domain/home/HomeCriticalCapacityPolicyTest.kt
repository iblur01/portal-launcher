package com.iblu01.portallauncher.domain.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCriticalCapacityPolicyTest {
    @Test
    fun `wide window admits one exceptional alert while compact or large text does not`() {
        assertEquals(1, HomeCriticalCapacityPolicy.capacity(800f, 1f).extraCriticalPrimarySlots)
        assertEquals(0, HomeCriticalCapacityPolicy.capacity(640f, 1f).extraCriticalPrimarySlots)
        assertEquals(0, HomeCriticalCapacityPolicy.capacity(800f, 1.5f).extraCriticalPrimarySlots)
    }
}
