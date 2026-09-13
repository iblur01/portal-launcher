package com.iblu01.portallauncher.voice

import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.PillRepository
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.domain.home.HomeGroupingMode
import com.iblu01.portallauncher.domain.home.PillRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PortalSettingsToolTest {
    @Test
    fun `pinning puts the device first without duplicating it`() {
        val kitchen = PillRef.Device("light.kitchen")
        val hall = PillRef.Device("light.hall")

        assertEquals(listOf(kitchen, hall), pinFirst(listOf(hall), kitchen))
        assertEquals(listOf(kitchen, hall), pinFirst(listOf(kitchen, hall), kitchen))
    }

    @Test
    fun `installed app resolves by friendly name typo or exact package`() {
        val youtube = PortalLaunchableApp("YouTube", "com.google.android.youtube", "Main")
        val home = PortalLaunchableApp("Home Assistant", "io.homeassistant", "Main")
        val apps = listOf(youtube, home)

        assertEquals(youtube, selectPortalApp(apps, "Youtub"))
        assertEquals(home, selectPortalApp(apps, "io.homeassistant"))
    }

    @Test
    fun `portal settings update existing preferences through validated actions`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        val tool = PortalSettingsTool(context, prefs, PillRepository(context))

        assertTrue(tool.execute(JSONObject().put("action", "set_device_grouping").put("grouping", "room"))["success"] == true)
        assertEquals(HomeGroupingMode.BY_ROOM, prefs.homePillPreferences.groupingMode)

        tool.execute(JSONObject().put("action", "set_home_page").put("enabled", false))
        assertFalse(prefs.homePillPreferences.homePageEnabled)

        tool.execute(JSONObject().put("action", "set_clock_format").put("clock_format", "12h"))
        assertFalse(prefs.clockFormat24h)

        tool.execute(JSONObject().put("action", "set_grid_scale").put("grid_scale", 0.8))
        assertEquals(0.8f, prefs.gridScale, 0.001f)
    }

    @Test
    fun `invalid multi-value voice action changes nothing`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        prefs.voiceBargeIn = false
        prefs.voiceAssistantIdleSeconds = 20
        val tool = PortalSettingsTool(context, prefs, PillRepository(context))

        val result = tool.execute(
            JSONObject()
                .put("action", "set_voice_behavior")
                .put("barge_in", true)
                .put("idle_seconds", 2),
        )

        assertTrue(result.containsKey("error"))
        assertFalse(prefs.voiceBargeIn)
        assertEquals(20, prefs.voiceAssistantIdleSeconds)
    }
}
