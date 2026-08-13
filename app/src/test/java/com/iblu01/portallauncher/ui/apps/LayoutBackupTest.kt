package com.iblu01.portallauncher.ui.apps

import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.AppPlacement
import com.iblu01.portallauncher.FolderRecord
import com.iblu01.portallauncher.PinnedShortcut
import com.iblu01.portallauncher.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Export/restore of the arrangement: what survives a round trip, and what deliberately does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LayoutBackupTest {

    private lateinit var prefs: Prefs

    private val a = GridItem.appKey("com.a", "com.a.Main")
    private val b = GridItem.appKey("com.b", "com.b.Main")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("portal_launcher", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs(context)
    }

    private fun seed() {
        prefs.appPlacements = listOf(
            AppPlacement(a, page = 1, col = 2, row = 0),
            AppPlacement(b, page = 0, col = 0, row = 1),
            AppPlacement(GridItem.widgetKey(9), page = 0, col = 2, row = 2, spanX = 2, spanY = 1),
        )
        prefs.appFolders = listOf(FolderRecord("f1", listOf(a, b)))
        prefs.appLabels = mapOf(a to "Alpha")
        prefs.hiddenApps = setOf("app:com.hidden/com.hidden.Main")
        prefs.pinnedShortcuts = listOf(PinnedShortcut("com.a", "sc1", "Nouveau"))
        prefs.gridScale = 1.2f
        prefs.iconPack = "com.pack"
    }

    @Test
    fun `a round trip restores the whole arrangement`() {
        seed()
        val json = LayoutBackup.export(prefs)
        prefs.appPlacements = emptyList()
        prefs.appFolders = emptyList()
        prefs.appLabels = emptyMap()
        prefs.hiddenApps = emptySet()
        prefs.pinnedShortcuts = emptyList()
        prefs.gridScale = 1f
        prefs.iconPack = ""

        val result = LayoutBackup.import(prefs, json)

        assertEquals(2, result.placements)
        assertEquals(AppPlacement(a, 1, 2, 0), prefs.appPlacements.first { it.key == a })
        assertEquals(listOf(a, b), prefs.appFolders.single().members)
        assertEquals("Alpha", prefs.appLabels[a])
        assertEquals(setOf("app:com.hidden/com.hidden.Main"), prefs.hiddenApps)
        assertEquals("sc1", prefs.pinnedShortcuts.single().shortcutId)
        assertEquals(1.2f, prefs.gridScale, 0.001f)
        assertEquals("com.pack", prefs.iconPack)
    }

    @Test
    fun `widget placements are not carried across devices`() {
        seed()
        val json = LayoutBackup.export(prefs)

        // A widget id belongs to this device's AppWidgetHost, so the file's copy is ignored and the
        // widget already bound here keeps its cell.
        prefs.appPlacements = listOf(AppPlacement(GridItem.widgetKey(9), page = 2, col = 1, row = 1))
        LayoutBackup.import(prefs, json)

        val widgets = prefs.appPlacements.filter { it.key.startsWith("wg:") }
        assertEquals(1, widgets.size)
        assertEquals(2, widgets.single().page)
    }

    @Test
    fun `restoring marks the legacy seeding done so it cannot replay over the arrangement`() {
        seed()
        val json = LayoutBackup.export(prefs)
        prefs.appPlacementsSeeded = false

        LayoutBackup.import(prefs, json)

        assertTrue(prefs.appPlacementsSeeded)
    }

    @Test
    fun `the export carries no secret`() {
        seed()
        prefs.haToken = "super-secret-token"
        prefs.password = "mqtt-password"

        val json = LayoutBackup.export(prefs)

        assertFalse(json.contains("super-secret-token"))
        assertFalse(json.contains("mqtt-password"))
    }

    @Test
    fun `a file that is not a backup is rejected instead of wiping the grid`() {
        seed()

        assertThrows(IllegalArgumentException::class.java) {
            LayoutBackup.import(prefs, "definitely not json")
        }
        assertThrows(IllegalArgumentException::class.java) {
            LayoutBackup.import(prefs, """{"version":99,"placements":[]}""")
        }
        assertEquals(3, prefs.appPlacements.size)
    }

    @Test
    fun `a folder left with fewer than two members is dropped on restore`() {
        seed()
        val json = """
            {"version":1,"placements":[],"folders":[{"id":"f1","members":["$a"]}]}
        """.trimIndent()

        LayoutBackup.import(prefs, json)

        assertTrue(prefs.appFolders.isEmpty())
    }

    @Test
    fun `the suggested file name is safe to write anywhere`() {
        assertEquals("portal-launcher-layout-salon-cuisine.json", LayoutBackup.fileName("Salon / Cuisine"))
        assertEquals("portal-launcher-layout-portal.json", LayoutBackup.fileName("  "))
    }
}
