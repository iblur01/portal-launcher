package com.iblu01.portallauncher.ui.icons

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Covers what happens to the disk cache when the user's set of icon packs changes.
 *
 * This is the half of the pipeline with no network in it and the longest memory: a wrong verdict
 * here is permanent, because [HaIconPackStore.isPending] never looks at a reference twice.
 */
@RunWith(RobolectricTestRunner::class)
class HaIconPackStoreTest {

    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val root get() = File(context.filesDir, "haicons")

    private lateinit var store: HaIconPackStore

    private val brandIcons = "/hacsfiles/custom-brand-icons/custom-brand-icons.js?hacstag=1"
    private val hueIcons = "/hacsfiles/hass-hue-icons/hass-hue-icons.js?hacstag=2"
    private val someCard = "/hacsfiles/bubble-card/bubble-card.js?hacstag=3"

    @Before
    fun setUp() {
        root.deleteRecursively()
        store = HaIconPackStore(context, OkHttpClient())
    }

    private fun writeIcon(ref: IconRef, path: String = "M1 1Z") {
        File(root, ref.namespace).mkdirs()
        File(File(root, ref.namespace), "${ref.name}.path").writeText("24.0 24.0\n$path")
    }

    private fun writeMiss(ref: IconRef) {
        File(root, ref.namespace).mkdirs()
        File(File(root, ref.namespace), "${ref.name}.path").writeBytes(ByteArray(0))
    }

    /** Seeds the namespace -> provider map a real harvest would have learned, via the on-disk form. */
    private fun seedProvider(namespace: String, url: String) {
        root.mkdirs()
        File(root, "routes.json").writeText("""{"namespaces":{"$namespace":"$url"},"barren":[]}""")
        store = HaIconPackStore(context, OkHttpClient())
    }

    @Test
    fun `a cached miss stops the reference being fetched again`() {
        val ref = IconRef("hue", "bulb-group-classic")
        assertTrue(store.isPending(ref))
        writeMiss(ref)
        assertFalse(store.isPending(ref))
        assertNull(store.cached(ref))
    }

    @Test
    fun `installing a new pack re-opens every cached miss`() {
        val missing = IconRef("hue", "bulb-group-classic")
        val resolved = IconRef("phu", "sonos-arc")
        writeMiss(missing)
        writeIcon(resolved)

        // First sight of a module list establishes the baseline; nothing is stale yet.
        assertEquals(0, store.invalidateForModules(listOf(brandIcons, someCard)))
        assertFalse(store.isPending(missing))

        // The user installs hass-hue-icons. The old "nobody provides this" verdict is now wrong.
        assertEquals(1, store.invalidateForModules(listOf(brandIcons, someCard, hueIcons)))
        assertTrue("a cached miss must be retried once a new pack appears", store.isPending(missing))
        // Art from a pack that did not move is kept: a card update must not cost a re-download.
        assertFalse(store.isPending(resolved))
        assertEquals("M1 1Z", store.cached(resolved)?.path)
    }

    @Test
    fun `a pack that bumps its version drops the art it provided`() {
        val ref = IconRef("phu", "sonos-arc")
        writeIcon(ref)
        seedProvider("phu", brandIcons)
        assertEquals(0, store.invalidateForModules(listOf(brandIcons)))

        // HACS versions these packs through the URL, so a bump makes the cached art the old release.
        val bumped = "/hacsfiles/custom-brand-icons/custom-brand-icons.js?hacstag=999"
        assertEquals(1, store.invalidateForModules(listOf(bumped)))
        assertTrue(store.isPending(ref))
    }

    @Test
    fun `the module baseline survives a restart`() {
        // Without this the fingerprint is re-established on every boot, every run looks like the
        // first one, and a newly installed pack is never noticed at all.
        store.invalidateForModules(listOf(brandIcons, someCard))

        val afterRestart = HaIconPackStore(context, OkHttpClient())
        assertEquals(0, afterRestart.invalidateForModules(listOf(brandIcons, someCard)))
        writeMiss(IconRef("hue", "ensis"))
        assertEquals(1, afterRestart.invalidateForModules(listOf(brandIcons, someCard, hueIcons)))
    }

    @Test
    fun `the access token only travels to Home Assistant itself`() {
        val ha = "http://192.168.1.87:8123"
        assertTrue(HaIconPackStore.isSameOrigin("$ha/hacsfiles/custom-brand-icons.js?v=1", ha))
        assertTrue(HaIconPackStore.isSameOrigin("http://192.168.1.87:8123/local/x.js", "$ha/"))

        // A Lovelace resource is an arbitrary URL from the user's dashboard. None of these is the
        // same Home Assistant, and a long-lived token must not reach any of them.
        assertFalse("another host", HaIconPackStore.isSameOrigin("https://cdn.jsdelivr.net/x.js", ha))
        assertFalse("another port", HaIconPackStore.isSameOrigin("http://192.168.1.87:9999/x.js", ha))
        assertFalse("another scheme", HaIconPackStore.isSameOrigin("https://192.168.1.87:8123/x.js", ha))
        assertFalse("no host at all", HaIconPackStore.isSameOrigin("/hacsfiles/x.js", ha))
        assertFalse("unparseable", HaIconPackStore.isSameOrigin("http://[bad", ha))
    }

    @Test
    fun `an unchanged module list drops nothing`() {
        writeMiss(IconRef("hue", "ensis"))
        store.invalidateForModules(listOf(brandIcons, someCard))
        assertEquals(0, store.invalidateForModules(listOf(someCard, brandIcons)))
        assertEquals(0, store.invalidateForModules(listOf(brandIcons, someCard)))
        assertFalse(store.isPending(IconRef("hue", "ensis")))
    }
}
