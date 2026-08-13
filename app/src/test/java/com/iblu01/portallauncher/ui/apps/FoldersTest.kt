package com.iblu01.portallauncher.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder rules, as pure functions: what a drop means, and when a folder stops existing. */
class FoldersTest {

    private val a = GridItem.appKey("com.a", "com.a.Main")
    private val b = GridItem.appKey("com.b", "com.b.Main")
    private val c = GridItem.appKey("com.c", "com.c.Main")
    private val widget = GridItem.widgetKey(7)

    @Test
    fun `dropping an icon onto another makes a folder of the two`() {
        val edit = foldOnto(emptyList(), dragged = b, target = a)

        val folder = edit.folders.single()
        // Target first: the folder took the target's place, so its icon leads.
        assertEquals(listOf(a, b), folder.members)
        assertEquals("fd:f1", folder.key)
    }

    @Test
    fun `dropping onto an existing folder joins it`() {
        val first = foldOnto(emptyList(), dragged = b, target = a).folders

        val edit = foldOnto(first, dragged = c, target = "fd:f1")

        assertEquals(listOf(a, b, c), edit.folders.single().members)
    }

    @Test
    fun `a widget can neither be foldered nor swallow an icon`() {
        assertFalse(isFoldable(widget))
        assertTrue(foldOnto(emptyList(), dragged = widget, target = a).folders.isEmpty())
        assertTrue(foldOnto(emptyList(), dragged = a, target = widget).folders.isEmpty())
    }

    @Test
    fun `dragging an icon out of one folder and into another moves it`() {
        val d = GridItem.appKey("com.d", "com.d.Main")
        // f1 = [a, b, d], f2 = [c, e].
        val f1 = foldOnto(foldOnto(emptyList(), b, a).folders, d, "fd:f1").folders
        val both = foldOnto(f1, GridItem.appKey("com.e", "com.e.Main"), c).folders

        val edit = foldOnto(both, dragged = b, target = both.first { c in it.members }.key)

        assertEquals(listOf(a, d), edit.folders.first { it.id == "f1" }.members)
        assertTrue(edit.folders.first { c in it.members }.members.contains(b))
        assertTrue(edit.released.isEmpty())
    }

    @Test
    fun `moving an icon out of a two-member folder dissolves the one it left`() {
        val f1 = foldOnto(emptyList(), b, a).folders
        val both = foldOnto(f1, GridItem.appKey("com.d", "com.d.Main"), c).folders

        val edit = foldOnto(both, dragged = b, target = both.first { c in it.members }.key)

        // f1 is gone and its survivor needs a cell again; b is now in the other folder.
        assertTrue(edit.folders.none { it.id == "f1" })
        assertEquals(listOf(a), edit.released)
        assertTrue(edit.folders.single().members.contains(b))
    }

    @Test
    fun `a folder down to one member dissolves and releases it`() {
        val folders = foldOnto(emptyList(), dragged = b, target = a).folders

        val edit = removeMember(folders, b)

        assertTrue(edit.folders.isEmpty())
        // The survivor is handed back so the caller can find it a cell.
        assertEquals(listOf(a), edit.released)
    }

    @Test
    fun `removing one of three members keeps the folder`() {
        val folders = foldOnto(foldOnto(emptyList(), b, a).folders, c, "fd:f1").folders

        val edit = removeMember(folders, c)

        assertEquals(listOf(a, b), edit.folders.single().members)
        assertTrue(edit.released.isEmpty())
    }

    @Test
    fun `deleting a folder spills every member back onto the grid`() {
        val folders = foldOnto(foldOnto(emptyList(), b, a).folders, c, "fd:f1").folders

        val edit = dissolveFolder(folders, "fd:f1")

        assertTrue(edit.folders.isEmpty())
        assertEquals(listOf(a, b, c), edit.released)
    }

    @Test
    fun `an uninstalled member is pruned, and a folder that drops below two dissolves`() {
        val folders = foldOnto(foldOnto(emptyList(), b, a).folders, c, "fd:f1").folders

        val stillThere = pruneFolders(folders, setOf(a, b))
        assertEquals(listOf(a, b), stillThere.folders.single().members)

        val gutted = pruneFolders(folders, setOf(a))
        assertTrue(gutted.folders.isEmpty())
        assertEquals(listOf(a), gutted.released)
    }

    @Test
    fun `folder ids do not collide with one already in use`() {
        val folders = listOf(Folder("f1", listOf(a, b)), Folder("f2", listOf(c, widget)))

        assertEquals("f3", nextFolderId(folders))
        assertEquals("f2", nextFolderId(listOf(Folder("f1", listOf(a, b)))))
    }
}
