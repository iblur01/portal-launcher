package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.ui.HaStates
import com.iblu01.portallauncher.ui.LocalHaStates
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Headless Android/Compose validation (Robolectric — no emulator) of the alarm-keypad anti-storm
 * guarantee, now structural (P1): with the per-entity store, an unrelated HA push must not
 * recompose a [rememberEntity] reader AT ALL, and a real change on the observed entity must.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RememberEntityBehaviorTest {

    @get:Rule val rule = createComposeRule()

    private fun entity(id: String, state: String) = HaEntity(id, state, JSONObject())

    @Test
    fun `rememberEntity ignore un push sans rapport, suit un vrai changement`() {
        val seen = mutableListOf<HaEntity?>()
        val store = HaStates()

        rule.setContent {
            CompositionLocalProvider(LocalHaStates provides store) {
                seen.add(rememberEntity("light.a"))
            }
        }
        rule.runOnIdle { store.apply(mapOf("light.a" to entity("light.a", "on"))) }
        rule.waitForIdle()

        // Unrelated entity changes → the reader must not even recompose.
        rule.runOnIdle {
            store.apply(mapOf("light.a" to entity("light.a", "on"), "light.b" to entity("light.b", "on")))
        }
        rule.waitForIdle()

        // Observed entity changes → one recomposition with the new value.
        rule.runOnIdle {
            store.apply(mapOf("light.a" to entity("light.a", "off"), "light.b" to entity("light.b", "on")))
        }
        rule.waitForIdle()

        // Initial composition (store empty), then "on", then "off" — and nothing else: the
        // unrelated push produced no recomposition at all.
        assertEquals(listOf(null, "on", "off"), seen.map { it?.state })
    }
}
