package com.iblu01.portallauncher.ui.panel

import com.iblu01.portallauncher.ui.model.PanelKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure reducer tests (design §Tests). Covers precedence, toggle-close, media auto-open guards. */
class PanelStateTest {

    private fun chip(key: String, kind: PanelKind = PanelKind.GENERIC_DETAILS) =
        PanelRequest.Chip(key, kind)

    @Test fun `open chip from empty`() {
        val s = reduce(PanelState(), PanelEvent.OpenChip(chip("light.a", PanelKind.LIGHTS)))
        assertEquals(chip("light.a", PanelKind.LIGHTS), s.request)
        assertEquals(PanelSource.USER, s.source)
    }

    @Test fun `tap same key toggles closed`() {
        val open = PanelState(request = chip("light.a"), source = PanelSource.USER)
        val s = reduce(open, PanelEvent.OpenChip(chip("light.a")))
        assertNull(s.request)
    }

    @Test fun `tap different chip replaces`() {
        val open = PanelState(request = chip("light.a"))
        val s = reduce(open, PanelEvent.OpenChip(chip("lock.b", PanelKind.LOCK)))
        assertEquals(chip("lock.b", PanelKind.LOCK), s.request)
        assertEquals(PanelSource.USER, s.source)
    }

    @Test fun `long press always opens even on same key`() {
        val open = PanelState(request = chip("fan.a", PanelKind.FAN))
        val s = reduce(open, PanelEvent.LongPressChip(chip("fan.a", PanelKind.FAN)))
        assertEquals(chip("fan.a", PanelKind.FAN), s.request)
    }

    @Test fun `weather tap opens and toggles closed`() {
        val opened = reduce(PanelState(), PanelEvent.WeatherTap)
        assertTrue(opened.request is PanelRequest.Weather)
        val closed = reduce(opened, PanelEvent.WeatherTap)
        assertNull(closed.request)
    }

    @Test fun `weather replaces chip - last user intent wins`() {
        val chipOpen = PanelState(request = chip("light.a"))
        val s = reduce(chipOpen, PanelEvent.WeatherTap)
        assertTrue(s.request is PanelRequest.Weather)
    }

    @Test fun `media auto-open only when empty`() {
        val opened = reduce(PanelState(), PanelEvent.MediaAutoOpen("m1"))
        assertEquals(PanelRequest.Media("m1"), opened.request)
        assertEquals(PanelSource.AUTO, opened.source)
    }

    @Test fun `media auto-open does not clobber user panel`() {
        val userOpen = PanelState(request = chip("light.a"), source = PanelSource.USER)
        val s = reduce(userOpen, PanelEvent.MediaAutoOpen("m1"))
        assertEquals(chip("light.a"), s.request)
        assertEquals(PanelSource.USER, s.source)
    }

    @Test fun `auto media panel follows the primary on A to B swap`() {
        val autoA = PanelState(request = PanelRequest.Media("m.a"), source = PanelSource.AUTO)
        val s = reduce(autoA, PanelEvent.MediaAutoOpen("m.b"))
        assertEquals(PanelRequest.Media("m.b"), s.request)
        assertEquals(PanelSource.AUTO, s.source)
    }

    @Test fun `user media panel does not follow an auto primary swap`() {
        // A user who tapped the media chip pinned it; an auto A->B swap must not retarget it.
        val userMedia = PanelState(request = PanelRequest.Media("m.a"), source = PanelSource.USER)
        val s = reduce(userMedia, PanelEvent.MediaAutoOpen("m.b"))
        assertEquals(PanelRequest.Media("m.a"), s.request)
        assertEquals(PanelSource.USER, s.source)
    }

    @Test fun `dismiss clears request`() {
        val s = reduce(PanelState(request = chip("light.a")), PanelEvent.Dismiss)
        assertNull(s.request)
    }

    @Test fun `dismiss media suppresses reopen while playing`() {
        val mediaOpen = PanelState(request = PanelRequest.Media("m1"), source = PanelSource.AUTO)
        val dismissed = reduce(mediaOpen, PanelEvent.Dismiss)
        assertNull(dismissed.request)
        assertEquals("m1", dismissed.dismissedAutoKey)
        // same session tries to auto-open again -> suppressed
        val retry = reduce(dismissed, PanelEvent.MediaAutoOpen("m1"))
        assertNull(retry.request)
    }

    @Test fun `media stopped rearms auto-open`() {
        val dismissed = PanelState(dismissedAutoKey = "m1")
        val rearmed = reduce(dismissed, PanelEvent.MediaStopped)
        assertNull(rearmed.dismissedAutoKey)
        val reopened = reduce(rearmed, PanelEvent.MediaAutoOpen("m1"))
        assertEquals(PanelRequest.Media("m1"), reopened.request)
    }

    @Test fun `media stopped closes media panel`() {
        val mediaOpen = PanelState(request = PanelRequest.Media("m1"), source = PanelSource.AUTO)
        val s = reduce(mediaOpen, PanelEvent.MediaStopped)
        assertNull(s.request)
    }

    @Test fun `media stopped leaves user panel untouched`() {
        val userOpen = PanelState(request = chip("light.a"), source = PanelSource.USER)
        val s = reduce(userOpen, PanelEvent.MediaStopped)
        assertEquals(chip("light.a"), s.request)
    }

    private fun alarm(key: String = "alarm") = PanelRequest.Chip(key, PanelKind.ALARM)

    @Test fun `alarm alert opens over a user panel`() {
        val userOpen = PanelState(request = chip("light.a"), source = PanelSource.USER)
        val s = reduce(userOpen, PanelEvent.AlarmAlert(alarm()))
        assertEquals(alarm(), s.request)
        assertEquals(PanelSource.ALERT, s.source)
    }

    @Test fun `media auto-open never clobbers an alert panel`() {
        val alerting = PanelState(request = alarm(), source = PanelSource.ALERT)
        val s = reduce(alerting, PanelEvent.MediaAutoOpen("m1"))
        assertEquals(alarm(), s.request)
    }

    @Test fun `alarm cleared closes the alert panel`() {
        val alerting = PanelState(request = alarm(), source = PanelSource.ALERT)
        val s = reduce(alerting, PanelEvent.AlarmCleared)
        assertNull(s.request)
    }

    @Test fun `alarm cleared leaves a user panel untouched`() {
        val userOpen = PanelState(request = chip("light.a"), source = PanelSource.USER)
        val s = reduce(userOpen, PanelEvent.AlarmCleared)
        assertEquals(chip("light.a"), s.request)
    }

    @Test fun `dismissed alert does not reopen while the alarm keeps alerting`() {
        var s = reduce(PanelState(), PanelEvent.AlarmAlert(alarm()))
        s = reduce(s, PanelEvent.Dismiss)
        assertNull(s.request)
        assertEquals("alarm", s.dismissedAlertKey)
        s = reduce(s, PanelEvent.AlarmAlert(alarm()))
        assertNull(s.request)
    }

    @Test fun `alarm cleared rearms the alert`() {
        var s = PanelState(dismissedAlertKey = "alarm")
        s = reduce(s, PanelEvent.AlarmCleared)
        assertNull(s.dismissedAlertKey)
        s = reduce(s, PanelEvent.AlarmAlert(alarm()))
        assertEquals(alarm(), s.request)
    }

    @Test fun `alert panel is reopened after the user navigates away and closes`() {
        var s = reduce(PanelState(), PanelEvent.AlarmAlert(alarm()))
        s = reduce(s, PanelEvent.OpenChip(chip("lock.b", PanelKind.LOCK)))
        assertEquals(PanelSource.USER, s.source)
        s = reduce(s, PanelEvent.Dismiss)                 // closing a chip, not the alert
        assertNull(s.dismissedAlertKey)
        s = reduce(s, PanelEvent.AlarmAlert(alarm()))
        assertEquals(alarm(), s.request)
    }

    @Test fun `interleave - user opens over auto media then dismiss`() {
        var s = reduce(PanelState(), PanelEvent.MediaAutoOpen("m1"))       // AUTO media
        s = reduce(s, PanelEvent.OpenChip(chip("lock.b", PanelKind.LOCK))) // USER replaces
        assertEquals(PanelSource.USER, s.source)
        s = reduce(s, PanelEvent.Dismiss)                                   // dismiss chip
        assertNull(s.request)
        assertNull(s.dismissedAutoKey)                                      // chip dismiss, not media
    }
}
