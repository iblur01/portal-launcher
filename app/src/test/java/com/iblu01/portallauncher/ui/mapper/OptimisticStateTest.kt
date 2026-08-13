package com.iblu01.portallauncher.ui.mapper

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OptimisticStateTest {

    @Test
    fun `predictState couvre les services a etat previsible et refuse le reste`() {
        assertEquals("on", predictState("turn_on", "off"))
        assertEquals("off", predictState("turn_off", "on"))
        assertEquals("on", predictState("toggle", "off"))
        assertEquals("off", predictState("toggle", "on"))
        assertEquals("paused", predictState("media_play_pause", "playing"))
        assertEquals("playing", predictState("media_play_pause", "paused"))
        assertEquals("locking", predictState("lock", "unlocked"))
        assertEquals("unlocking", predictState("unlock", "locked"))
        assertEquals("opening", predictState("open_cover", "closed"))
        assertNull(predictState("volume_set", "playing"))
        assertNull(predictState("set_temperature", "heat"))
    }

    private val chip = LauncherChip(
        id = "light.salon", icon = "light", label = "Salon", value = "Éteint",
        state = "ok", entityId = "light.salon", deviceState = "off",
    )

    @Test
    fun `withLiveState reprojette etat valeur et accent depuis l'entite live`() {
        val live = HaEntity("light.salon", "on", JSONObject())
        val shown = chip.withLiveState(live)
        assertEquals("on", shown.deviceState)
        assertEquals("active", shown.state)
        assertEquals("Allumé", shown.value)
    }

    @Test
    fun `withLiveState ne touche a rien quand l'etat live est identique`() {
        val live = HaEntity("light.salon", "off", JSONObject())
        assertSame(chip, chip.withLiveState(live))
        assertSame(chip, chip.withLiveState(null))
    }

    @Test
    fun `withLiveState preserve une alerte critique du pipeline`() {
        val critical = chip.copy(state = "critical", deviceState = "off")
        val live = HaEntity("light.salon", "on", JSONObject())
        assertEquals("critical", critical.withLiveState(live).state)
    }
}
