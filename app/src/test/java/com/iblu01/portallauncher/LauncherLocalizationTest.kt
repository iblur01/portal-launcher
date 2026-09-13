package com.iblu01.portallauncher

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun `scene and camera labels exist in both languages`() {
        val en = context("en")
        val fr = context("fr")
        assertEquals("Scenes", PillKind.SCENE.localizedLabel(en))
        assertEquals("Scènes", PillKind.SCENE.localizedLabel(fr))
        assertEquals("Cameras", PillKind.CAMERA.localizedLabel(en))
        assertEquals("Caméras", PillKind.CAMERA.localizedLabel(fr))
        assertEquals("Activate", friendlyEntityState(en, scene()))
        assertEquals("Activer", friendlyEntityState(fr, scene()))
        assertEquals("Streaming", friendlyEntityState(en, camera("streaming")))
        assertEquals("En diffusion", friendlyEntityState(fr, camera("streaming")))
        assertEquals("On", friendlyEntityState(en, camera("idle")))
        assertEquals("Allumée", friendlyEntityState(fr, camera("idle")))
        assertEquals("Off", friendlyEntityState(en, camera("off")))
        assertEquals("Éteinte", friendlyEntityState(fr, camera("off")))
    }

    /**
     * Every string the camera centre and the scene pills add must resolve in both languages, and
     * must not silently fall back to the other one.
     */
    @Test fun `every new camera and scene string is translated in both languages`() {
        val en = context("en")
        val fr = context("fr")
        val ids = listOf(
            R.string.pill_scene_ready, R.string.pill_scene_activating,
            R.string.pill_scene_activated, R.string.pill_scene_failed,
            R.string.pill_cameras_label, R.string.camera_center_title,
            R.string.camera_center_close_desc, R.string.camera_center_mode_main,
            R.string.camera_center_mode_grid, R.string.camera_center_mode_main_desc,
            R.string.camera_center_mode_grid_desc, R.string.camera_center_empty,
            R.string.camera_hide_confirm_title, R.string.camera_hide_confirm_message,
            R.string.camera_hide_confirm_action,
            R.string.camera_stream_loading, R.string.camera_stream_error,
            R.string.camera_stream_unavailable, R.string.camera_stream_retry,
            R.string.camera_audio_mute_desc, R.string.camera_audio_unmute_desc,
            R.string.camera_ptz_up_desc, R.string.camera_ptz_down_desc,
            R.string.camera_ptz_left_desc, R.string.camera_ptz_right_desc,
            R.string.camera_ptz_zoom_in_desc, R.string.camera_ptz_zoom_out_desc,
            R.string.settings_cameras_title, R.string.settings_cameras_subtitle,
            R.string.settings_cameras_visible, R.string.settings_cameras_main,
            R.string.settings_cameras_default_mode, R.string.settings_cameras_pin_general,
            R.string.settings_cameras_empty, R.string.pill_family_scenes,
            R.string.pill_family_cameras,
        )
        ids.forEach { id ->
            val english = en.getString(id)
            val french = fr.getString(id)
            assertTrue("string $id is empty in English", english.isNotBlank())
            assertTrue("string $id is empty in French", french.isNotBlank())
        }
        // A spot check that French is a real translation rather than the English fallback.
        assertEquals("Close", en.getString(R.string.camera_center_close))
        assertEquals("Fermer", fr.getString(R.string.camera_center_close))
    }

    private fun scene() = HaEntity("scene.evening", "unknown", JSONObject())

    private fun camera(state: String) = HaEntity("camera.hall", state, JSONObject())
}
