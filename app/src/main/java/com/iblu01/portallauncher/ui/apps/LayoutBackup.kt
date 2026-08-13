package com.iblu01.portallauncher.ui.apps

import com.iblu01.portallauncher.AppPlacement
import com.iblu01.portallauncher.FolderRecord
import com.iblu01.portallauncher.PinnedShortcut
import com.iblu01.portallauncher.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Export and restore of the launcher arrangement — the one part of this app that cannot be
 * reconstructed from Home Assistant or from the device: where every icon sits, what is hidden, what
 * is renamed, and how the grid is scaled.
 *
 * The format is a plain JSON document, versioned, meant to be readable and hand-editable. It is
 * deliberately *not* a dump of `SharedPreferences`: nothing here touches credentials, the HA token,
 * or the MQTT password, so a backup file can be copied around without leaking a secret.
 */
object LayoutBackup {

    const val VERSION = 1

    /** Suggested file name for the export document. */
    fun fileName(deviceName: String): String {
        val slug = deviceName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "portal" }
        return "portal-launcher-layout-$slug.json"
    }

    fun export(prefs: Prefs): String {
        val placements = JSONArray()
        prefs.appPlacements.forEach {
            placements.put(
                JSONObject()
                    .put("key", it.key).put("page", it.page).put("col", it.col).put("row", it.row)
                    .put("spanX", it.spanX).put("spanY", it.spanY)
            )
        }
        val folders = JSONArray()
        prefs.appFolders.forEach { folder ->
            val members = JSONArray()
            folder.members.forEach { members.put(it) }
            folders.put(JSONObject().put("id", folder.id).put("members", members))
        }
        val labels = JSONObject()
        prefs.appLabels.forEach { (key, label) -> labels.put(key, label) }
        val hidden = JSONArray()
        prefs.hiddenApps.forEach { hidden.put(it) }
        val shortcuts = JSONArray()
        prefs.pinnedShortcuts.forEach {
            shortcuts.put(
                JSONObject()
                    .put("package", it.packageName).put("id", it.shortcutId).put("label", it.label)
            )
        }
        return JSONObject()
            .put("version", VERSION)
            .put("device", prefs.deviceName)
            .put("gridScale", prefs.gridScale.toDouble())
            .put("iconPack", prefs.iconPack)
            .put("placements", placements)
            .put("folders", folders)
            .put("labels", labels)
            .put("hidden", hidden)
            .put("shortcuts", shortcuts)
            .toString(2)
    }

    /** What a restore did, so the UI can say something more useful than "done". */
    data class Result(val placements: Int, val folders: Int, val hidden: Int)

    /**
     * Applies a backup, replacing the current arrangement.
     *
     * Widget placements (`wg:` keys) are dropped on purpose: a widget id is allocated by *this*
     * device's `AppWidgetHost` and means nothing anywhere else, so restoring one would either point
     * at another widget or at nothing. The widgets the device already has keep their own cells.
     *
     * Throws [IllegalArgumentException] on a document this version cannot read, so the caller can
     * tell the user instead of silently wiping the grid.
     */
    fun import(prefs: Prefs, json: String): Result {
        val root = runCatching { JSONObject(json) }
            .getOrElse { throw IllegalArgumentException("not a layout backup", it) }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "unsupported backup version $version" }

        val placements = root.optJSONArray("placements") ?: JSONArray()
        val restored = (0 until placements.length()).mapNotNull { i ->
            val o = placements.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("key")
            if (key.isBlank() || key.startsWith("wg:")) null
            else AppPlacement(
                key = key,
                page = o.optInt("page").coerceAtLeast(0),
                col = o.optInt("col").coerceAtLeast(0),
                row = o.optInt("row").coerceAtLeast(0),
                spanX = o.optInt("spanX", 1).coerceAtLeast(1),
                spanY = o.optInt("spanY", 1).coerceAtLeast(1),
            )
        }
        // Widgets bound on this device keep their cells; everything else comes from the file.
        val keptWidgets = prefs.appPlacements.filter { it.key.startsWith("wg:") }
        prefs.appPlacements = keptWidgets + restored
        // The legacy seeding must not replay over a restored arrangement.
        prefs.appPlacementsSeeded = true

        val folders = root.optJSONArray("folders") ?: JSONArray()
        prefs.appFolders = (0 until folders.length()).mapNotNull { i ->
            val o = folders.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            val members = o.optJSONArray("members") ?: JSONArray()
            val keys = (0 until members.length()).mapNotNull {
                members.optString(it).takeIf(String::isNotBlank)
            }
            if (id.isBlank() || keys.size < 2) null else FolderRecord(id, keys)
        }

        val labels = root.optJSONObject("labels") ?: JSONObject()
        prefs.appLabels = labels.keys().asSequence().mapNotNull { key ->
            labels.optString(key).takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()

        val hidden = root.optJSONArray("hidden") ?: JSONArray()
        prefs.hiddenApps = (0 until hidden.length())
            .mapNotNull { hidden.optString(it).takeIf(String::isNotBlank) }
            .toSet()

        val shortcuts = root.optJSONArray("shortcuts") ?: JSONArray()
        val restoredShortcuts = (0 until shortcuts.length()).mapNotNull { i ->
            val o = shortcuts.optJSONObject(i) ?: return@mapNotNull null
            val pkg = o.optString("package")
            val id = o.optString("id")
            if (pkg.isBlank() || id.isBlank()) null else PinnedShortcut(pkg, id, o.optString("label"))
        }
        // Shortcut icons live in ShortcutIconStore and are not in the backup: a shortcut restored
        // onto a device that never pinned it shows the grid's placeholder tile, but still launches.
        prefs.pinnedShortcuts = restoredShortcuts

        if (root.has("gridScale")) prefs.gridScale = root.optDouble("gridScale", 1.0).toFloat()
        if (root.has("iconPack")) prefs.iconPack = root.optString("iconPack")

        return Result(
            placements = restored.size,
            folders = prefs.appFolders.size,
            hidden = prefs.hiddenApps.size,
        )
    }
}
