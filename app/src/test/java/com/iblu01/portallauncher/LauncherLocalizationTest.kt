package com.iblu01.portallauncher

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LauncherLocalizationTest {
    private val base: Context = ApplicationProvider.getApplicationContext()

    private fun context(language: String): Context = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply { setLocale(Locale(language)) },
    )

    private fun door(state: String) = HaEntity(
        "binary_sensor.front_door",
        state,
        JSONObject().put("device_class", "door"),
    )

    @Test fun `English launcher labels and HA states never fall back to French`() {
        val en = context("en")
        assertEquals("Security", PillKind.SAFETY.localizedLabel(en))
        assertEquals("Doors & windows", PillKind.OPENING.localizedLabel(en))
        assertEquals("Open", friendlyEntityState(en, door("on")))
        assertEquals("Closed", friendlyEntityState(en, door("off")))
        assertEquals("Disarmed", en.getString(R.string.alarm_state_disarmed))
    }

    @Test fun `French launcher labels and HA states remain translated`() {
        val fr = context("fr")
        assertEquals("Sécurité", PillKind.SAFETY.localizedLabel(fr))
        assertEquals("Portes et fenêtres", PillKind.OPENING.localizedLabel(fr))
        assertEquals("Ouverte", friendlyEntityState(fr, door("on")))
        assertEquals("Fermée", friendlyEntityState(fr, door("off")))
        assertEquals("Désarmée", fr.getString(R.string.alarm_state_disarmed))
    }
}
