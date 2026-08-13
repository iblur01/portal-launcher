package com.iblu01.portallauncher.ui.apps

import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Folders as the grid produces them: a drop onto an occupied cell, and what it does to placement. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LauncherLayoutFolderTest {

    private lateinit var prefs: Prefs
    private lateinit var store: LauncherLayoutStore

    private val a = GridItem.appKey("com.a", "com.a.Main")
    private val b = GridItem.appKey("com.b", "com.b.Main")
    private val widget = GridItem.widgetKey(4)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("portal_launcher", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs(context)
        store = LauncherLayoutStore(
            prefs,
            ShortcutIconStore(context),
            CoroutineScope(Dispatchers.Unconfined),
            folderLabel = "Dossier",
        )
    }

    @Test
    fun `dropping an icon onto another creates a folder in the target's cell`() {
        store.place(a, GridCell(0, 1, 1))
        store.place(b, GridCell(0, 3, 2))

        store.dropAt(b, GridCell(0, 1, 1))

        val folder = store.folderList.value.single()
        assertEquals(listOf(a, b), folder.members)
        // The folder inherits the cell the swallowed icon was in, and both members lose theirs.
        assertEquals(GridPlacement(GridCell(0, 1, 1)), store.storedCells.value[folder.key])
        assertNull(store.storedCells.value[a])
        assertNull(store.storedCells.value[b])
        // Written through, like every other mutation.
        assertEquals(listOf(a, b), prefs.appFolders.single().members)
    }

    @Test
    fun `dropping onto a widget still swaps rather than foldering`() {
        store.place(widget, GridCell(0, 0, 0), GridSpan(2, 1))
        store.place(a, GridCell(0, 2, 0))

        store.dropAt(a, GridCell(0, 0, 0))

        assertTrue(store.folderList.value.isEmpty())
        assertEquals(GridPlacement(GridCell(0, 0, 0)), store.storedCells.value[a])
    }

    @Test
    fun `dropping onto an empty cell is a plain move`() {
        store.place(a, GridCell(0, 0, 0))

        store.dropAt(a, GridCell(0, 2, 2))

        assertTrue(store.folderList.value.isEmpty())
        assertEquals(GridPlacement(GridCell(0, 2, 2)), store.storedCells.value[a])
    }

    @Test
    fun `taking the second-to-last member out dissolves the folder and frees its cell`() {
        store.place(a, GridCell(0, 1, 1))
        store.place(b, GridCell(0, 2, 1))
        store.dropAt(b, GridCell(0, 1, 1))
        val folderKey = store.folderList.value.single().key

        store.removeFromFolder(b)

        assertTrue(store.folderList.value.isEmpty())
        assertTrue(prefs.appFolders.isEmpty())
        // Nothing is left pointing at a folder that no longer exists.
        assertNull(store.storedCells.value[folderKey])
        assertNull(store.storedCells.value[a])
    }

    @Test
    fun `deleting a folder forgets its cell and its name`() {
        store.place(a, GridCell(0, 0, 0))
        store.place(b, GridCell(0, 1, 0))
        store.dropAt(b, GridCell(0, 0, 0))
        val folderKey = store.folderList.value.single().key
        store.rename(folderKey, "Jeux", defaultLabel = "Dossier")

        store.deleteFolder(folderKey)

        assertTrue(store.folderList.value.isEmpty())
        assertNull(store.storedCells.value[folderKey])
        assertNull(prefs.appLabels[folderKey])
    }

    @Test
    fun `folders survive a reload from disk`() {
        store.place(a, GridCell(0, 0, 0))
        store.place(b, GridCell(0, 1, 0))
        store.dropAt(b, GridCell(0, 0, 0))

        store.reload()

        assertEquals(listOf(a, b), store.folderList.value.single().members)
    }
}
