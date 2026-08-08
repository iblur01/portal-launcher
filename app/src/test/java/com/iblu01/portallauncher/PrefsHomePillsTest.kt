package com.iblu01.portallauncher

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsHomePillsTest {
    private lateinit var context: Context

    @Before fun clearPreferences() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `first read migrates Home defaults without changing legacy pill rules`() {
        val legacyRules = listOf(
            PillRule("light.salon", PillKind.LIGHTS, "Salon", enabled = false, priorityBoost = 4),
        )
        val rawLegacyRules = PillRuleCodec.encode(legacyRules)
        val store = context.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE)
        store.edit().putString("pill_rules", rawLegacyRules).commit()

        val migrated = Prefs(context).homePillPreferences

        assertTrue(migrated.homePageEnabled)
        assertTrue(migrated.pinnedOrder.isEmpty())
        assertTrue(migrated.manualGroups.isEmpty())
        assertEquals(rawLegacyRules, store.getString("pill_rules", null))
        assertEquals(legacyRules, Prefs(context).pillRules)
        assertTrue(store.contains("home_pill_preferences"))
    }

    @Test fun `corrupt JSON falls back without overwriting its source`() {
        val corrupt = "{definitely-not-json"
        val store = context.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE)
        store.edit().putString("home_pill_preferences", corrupt).commit()

        val fallback = Prefs(context).homePillPreferences

        assertTrue(fallback.homePageEnabled)
        assertEquals(corrupt, store.getString("home_pill_preferences", null))
    }

    @Test fun `updates preserve unknown pins and are deduplicated`() = runTest {
        val prefs = Prefs(context)
        val missingNow = PillRef.Device("light.temporarily_missing")
        val bus = SettingsChangeBus.get()

        bus.changes.test {
            val updated = prefs.updateHomePillPreferences {
                it.copy(pinnedOrder = listOf(missingNow))
            }
            assertEquals(listOf(missingNow), updated.pinnedOrder)
            assertEquals(Prefs.HOME_PILL_PREFERENCES_CHANGE_KEY, awaitItem())

            assertFalse(prefs.writeHomePillPreferences(updated))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(missingNow), Prefs(context).homePillPreferences.pinnedOrder)
        assertTrue(context.getSharedPreferences("portal_launcher", Context.MODE_PRIVATE)
            .getString("home_pill_preferences", null)?.contains("light.temporarily_missing") == true)
    }

    @Test fun `hide action disables the settings rule without deleting the device identity`() {
        val prefs = Prefs(context)
        val ref = PillRef.Device("light.kitchen")
        val pill = ResolvedPill(
            ref = ref,
            chip = LauncherChip(
                id = ref.entityId,
                icon = "light",
                label = "Cuisine",
                value = "Allumée",
                entityId = ref.entityId,
                kind = PillKind.LIGHTS,
            ),
        )
        val repository = PillRepository(context)

        assertTrue(repository.setPillEnabled(prefs, pill, enabled = false))
        assertEquals(false, prefs.pillRules.single { it.entityId == ref.entityId }.enabled)
        assertTrue(repository.setPillEnabled(prefs, pill, enabled = true))
        assertEquals(true, prefs.pillRules.single { it.entityId == ref.entityId }.enabled)
    }
}
