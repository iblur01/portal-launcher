package com.iblu01.portallauncher.ui.apps

/**
 * A folder on the app grid: an ordered set of item keys, addressed by its own [GridItem] key.
 *
 * A folder carries no label of its own — it reuses the grid's rename mechanism (`Prefs.appLabels`
 * keyed by item key), so renaming a folder and renaming an app are literally the same code path.
 */
data class Folder(val id: String, val members: List<String>) {
    val key: String get() = GridItem.folderKey(id)
}

/**
 * The result of a folder mutation: the new folder list, plus any item keys that came back out onto
 * the grid because their folder dissolved. The caller has to place those somewhere.
 */
data class FolderEdit(val folders: List<Folder>, val released: List<String> = emptyList())

/** Below two members a folder is pointless, so it dissolves back into loose icons. */
private const val MIN_MEMBERS = 2

/** First `f<n>` not already taken. Stable and readable, unlike a random uuid. */
fun nextFolderId(folders: List<Folder>): String {
    val taken = folders.map { it.id }.toSet()
    var n = 1
    while ("f$n" in taken) n++
    return "f$n"
}

/** True for keys a folder can hold: apps and shortcuts, never widgets and never a folder itself. */
fun isFoldable(key: String): Boolean =
    key.startsWith("app:") || key.startsWith("sc:")

fun List<Folder>.holding(key: String): Folder? = firstOrNull { key in it.members }

fun List<Folder>.byKey(folderKey: String): Folder? = firstOrNull { it.key == folderKey }

/**
 * Drops [dragged] onto [target] — the gesture every launcher uses to make a folder.
 *
 * Three cases: onto a folder (join it), onto a loose icon (a new folder holding both), onto
 * something that cannot be foldered (returns the list unchanged, so the caller falls back to a
 * plain placement). [dragged] is detached from whatever folder it was in first, which is what makes
 * dragging an icon from one folder to another work.
 */
fun foldOnto(folders: List<Folder>, dragged: String, target: String): FolderEdit {
    if (dragged == target || !isFoldable(dragged)) return FolderEdit(folders)
    val detached = removeMember(folders, dragged)
    val base = detached.folders
    // A member released by the detach is unrelated to this drop and still needs a cell.
    val released = detached.released
    val targetFolder = base.byKey(target)
    if (targetFolder != null) {
        return FolderEdit(
            base.map { if (it.id == targetFolder.id) it.copy(members = it.members + dragged) else it },
            released.filterNot { it == dragged },
        )
    }
    if (!isFoldable(target)) return FolderEdit(folders)
    val id = nextFolderId(base)
    return FolderEdit(
        base + Folder(id, listOf(target, dragged)),
        released.filterNot { it == dragged || it == target },
    )
}

/**
 * Takes [key] out of whichever folder holds it. A folder left with a single member dissolves and
 * that member is [FolderEdit.released] — otherwise the grid would show a folder wrapping one icon.
 */
fun removeMember(folders: List<Folder>, key: String): FolderEdit {
    val owner = folders.holding(key) ?: return FolderEdit(folders)
    val remaining = owner.members - key
    return if (remaining.size < MIN_MEMBERS) {
        FolderEdit(folders.filterNot { it.id == owner.id }, remaining)
    } else {
        FolderEdit(folders.map { if (it.id == owner.id) it.copy(members = remaining) else it })
    }
}

/** Deletes a folder outright, spilling every member back onto the grid. */
fun dissolveFolder(folders: List<Folder>, folderKey: String): FolderEdit {
    val folder = folders.byKey(folderKey) ?: return FolderEdit(folders)
    return FolderEdit(folders.filterNot { it.id == folder.id }, folder.members)
}

/**
 * Drops members that no longer exist (app uninstalled) and folders that fell below the minimum as a
 * result. Called with the keys actually present on the device, so an uninstall cannot leave a
 * folder holding ghosts.
 */
fun pruneFolders(folders: List<Folder>, existingKeys: Set<String>): FolderEdit {
    val pruned = folders.map { it.copy(members = it.members.filter { key -> key in existingKeys }) }
    val kept = pruned.filter { it.members.size >= MIN_MEMBERS }
    val released = pruned.filter { it.members.size < MIN_MEMBERS }.flatMap { it.members }
    return FolderEdit(kept, released)
}
