package com.iblu01.portallauncher.ui.icons

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/** One icon extracted from a custom icon set: a single SVG path over a `width` × `height` box. */
data class PackIcon(val width: Float, val height: Float, val path: String)

/**
 * Disk cache for icons that come from third-party Home Assistant icon sets (`phu:`, `hue:`, …).
 *
 * Those sets are **frontend JavaScript modules**, not API data — they install themselves into
 * `window.customIconsets` — so there is no endpoint to query. Instead the module is streamed from
 * the user's own Home Assistant and the handful of icons their entities actually reference are
 * extracted line by line into `filesDir/haicons/<namespace>/<name>.path`.
 *
 * The point of streaming is memory: `custom-brand-icons.js` is 4.4 MB for ~1700 icons, of which a
 * given home uses maybe five. It is never held in memory, and never fetched again once its icons
 * are on disk. An icon that no installed pack provides is cached as an **empty file**, so a missing
 * reference does not re-download megabytes on every boot — but only once a module was actually
 * read end to end, so an offline boot never poisons the cache.
 *
 * Every pack ships single-path icons — a hard constraint of both packs' builders — so the renderer
 * only needs [androidx.compose.ui.graphics.vector.PathParser], not an SVG parser.
 */
class HaIconPackStore(context: Context, client: OkHttpClient) {

    private val root = File(context.filesDir, "haicons")
    private val routesFile = File(root, "routes.json")
    private val http = client.newBuilder().callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS).build()

    /** namespace -> the resource URL known to provide it; avoids rescanning every module on a miss. */
    private val namespaceUrls = mutableMapOf<String, String>()
    /**
     * `namespace|url` pairs already read once and found to hold nothing for that namespace. Keyed
     * per namespace on purpose: hass-hue-icons yields nothing when we are hunting `phu:` names, and
     * writing it off wholesale would make `hue:` unresolvable forever.
     */
    private val barrenPairs = mutableSetOf<String>()
    private var routesLoaded = false

    private fun file(ref: IconRef) = File(File(root, ref.namespace), "${ref.name}.path")

    /**
     * Reads a cached icon off disk. Returns null when it is not cached, or when it is cached as a
     * known miss (an empty file). Callers are expected to cache the parsed result rather than
     * re-read per frame.
     */
    fun cached(ref: IconRef): PackIcon? {
        val file = file(ref)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching {
            // "<width> <height>\n<path data>"
            val text = file.readText()
            val newline = text.indexOf('\n')
            val box = text.substring(0, newline).split(' ')
            PackIcon(box[0].toFloat(), box[1].toFloat(), text.substring(newline + 1))
        }.getOrElse {
            Log.w(TAG, "corrupt cache entry for $ref; dropping", it)
            file.delete()
            null
        }
    }

    /** True when [ref] still needs fetching — neither a cached icon nor a cached miss. */
    fun isPending(ref: IconRef): Boolean = !ref.isMdi && !file(ref).exists()

    /**
     * Downloads whatever [wanted] references are not on disk yet, from the icon-set modules listed
     * in [resourceUrls] (as reported by the `lovelace/resources` websocket command). Blocking; call
     * from a background dispatcher. Returns true when anything new landed on disk.
     */
    @Synchronized
    fun sync(baseUrl: String, token: String, resourceUrls: List<String>, wanted: Set<IconRef>): Boolean {
        val missing = wanted.filter { isPending(it) }
        if (missing.isEmpty() || resourceUrls.isEmpty()) return false
        loadRoutes()
        var changed = false
        for ((namespace, refs) in missing.groupBy { it.namespace }) {
            val names = refs.mapTo(mutableSetOf()) { it.name }
            var scannedAny = false
            for (url in candidates(namespace, resourceUrls)) {
                if (names.isEmpty()) break
                val result = harvest(baseUrl, token, url, namespace, names)
                if (!result.fetched) continue
                scannedAny = true
                if (result.written > 0) {
                    namespaceUrls[namespace] = url
                    changed = true
                } else if (namespaceUrls[namespace] != url) {
                    barrenPairs += "$namespace|$url"
                }
            }
            // Only remember a miss once a module was actually read: an unreachable HA must not
            // permanently cache "this icon does not exist".
            if (scannedAny) names.forEach { markMissing(IconRef(namespace, it)) }
        }
        saveRoutes()
        return changed
    }

    /** Wipes the cache — e.g. when a pack's resource URL changes, which is how these packs version. */
    @Synchronized
    fun clear() {
        runCatching { root.deleteRecursively() }
        namespaceUrls.clear()
        barrenPairs.clear()
        routesLoaded = false
    }

    /** Known provider first, then anything that looks like an icon pack, then the rest. */
    private fun candidates(namespace: String, resourceUrls: List<String>): List<String> {
        val known = namespaceUrls[namespace]?.takeIf { it in resourceUrls }
        val rest = resourceUrls.filter { it != known && "$namespace|$it" !in barrenPairs }
        val (iconish, others) = rest.partition { it.contains("icon", ignoreCase = true) }
        return listOfNotNull(known) + iconish + others
    }

    private class Harvest(val fetched: Boolean, val written: Int)

    /** Streams one icon-set module and writes out every icon in [names] it provides. */
    private fun harvest(baseUrl: String, token: String, url: String, namespace: String, names: MutableSet<String>): Harvest {
        val absolute = when {
            url.startsWith("http://", true) || url.startsWith("https://", true) -> url
            else -> baseUrl.trimEnd('/') + "/" + url.trimStart('/')
        }
        // The token only ever goes to Home Assistant itself. A Lovelace resource may point at an
        // external CDN, and a long-lived HA token must not be handed to a third-party host.
        val sameHost = runCatching {
            URI(absolute).host.equals(URI(baseUrl).host, ignoreCase = true)
        }.getOrDefault(false)
        val request = Request.Builder().url(absolute)
            .apply { if (sameHost && token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()

        var written = 0
        var fetched = false
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "icon pack fetch failed (${response.code}): $absolute")
                    return@use
                }
                val source = response.body?.source() ?: return@use
                fetched = true
                var read = 0L
                var overflowed = false
                val lines = generateSequence { source.readUtf8Line() }
                    .takeWhile { line ->
                        read += line.length + 1
                        overflowed = read > MAX_MODULE_BYTES
                        !overflowed
                    }
                written = IconPackParser.extract(lines, names) { name, width, height, path ->
                    write(namespace, name, width, height, path)
                }
                if (overflowed) {
                    Log.w(TAG, "icon pack exceeded $MAX_MODULE_BYTES bytes; giving up on $absolute")
                    fetched = false
                }
            }
        }.onFailure { Log.w(TAG, "icon pack fetch failed: $absolute", it); fetched = false }
        return Harvest(fetched, written)
    }

    private fun write(namespace: String, name: String, width: Float, height: Float, path: String) {
        val file = file(IconRef(namespace, name))
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText("$width $height\n$path")
        }.onFailure { Log.w(TAG, "cannot cache $namespace:$name", it) }
    }

    private fun markMissing(ref: IconRef) {
        val file = file(ref)
        runCatching { file.parentFile?.mkdirs(); file.writeBytes(ByteArray(0)) }
    }

    private fun loadRoutes() {
        if (routesLoaded) return
        routesLoaded = true
        val json = runCatching { JSONObject(routesFile.readText()) }.getOrNull() ?: return
        json.optJSONObject("namespaces")?.let { ns ->
            ns.keys().forEach { key -> namespaceUrls[key] = ns.optString(key) }
        }
        val barren = json.optJSONArray("barren") ?: JSONArray()
        for (i in 0 until barren.length()) barrenPairs += barren.optString(i)
    }

    private fun saveRoutes() {
        runCatching {
            root.mkdirs()
            val json = JSONObject()
                .put("namespaces", JSONObject(namespaceUrls.toMap()))
                .put("barren", JSONArray(barrenPairs.toList()))
            routesFile.writeText(json.toString())
        }.onFailure { Log.w(TAG, "cannot persist icon pack routes", it) }
    }

    private companion object {
        const val TAG = "HaIconPackStore"
        const val CALL_TIMEOUT_S = 60L
        /** Refuse to stream more than this from one module; a runaway resource is not an icon set. */
        const val MAX_MODULE_BYTES = 24L * 1024 * 1024
    }
}

/**
 * Pulls named icons out of a Home Assistant icon-set module, one line at a time.
 *
 * Two shapes exist in the wild and both keep one icon per line, which is what makes a streaming
 * line scanner enough — no JS engine, no whole-file buffer:
 *
 *     custom-brand-icons   `"sonos-arc":[0,0,24,24,"M…"],`
 *     hass-hue-icons       `"adore":{` / `  path:"M…",` / `  keywords:[…]` / `},`
 */
internal object IconPackParser {

    private val ARRAY_ICON = Regex("\"([A-Za-z0-9_-]+)\"\\s*:\\s*\\[\\s*[-\\d.]+\\s*,\\s*[-\\d.]+\\s*,\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*,\\s*\"([^\"]+)\"")
    private val KEYED_PATH = Regex("\\bpath\\s*:\\s*\"([^\"]+)\"")
    private val OBJECT_KEY = Regex("\"([A-Za-z0-9_-]+)\"\\s*:\\s*\\{")

    /** Icons are drawn on a 24×24 box; only the array form states it explicitly. */
    private const val BOX = 24f

    /**
     * Scans [lines], calling [emit] with `(name, width, height, path)` for every icon whose name is
     * in [wanted]. Names are removed from [wanted] as they are found, so the caller can tell what is
     * still missing and stop early — scanning ends as soon as the set empties. Returns the emit count.
     */
    fun extract(
        lines: Sequence<String>,
        wanted: MutableSet<String>,
        emit: (name: String, width: Float, height: Float, path: String) -> Unit,
    ): Int {
        if (wanted.isEmpty()) return 0
        var written = 0
        var pendingName: String? = null
        for (line in lines) {
            val inline = ARRAY_ICON.find(line)
            if (inline != null) {
                val (name, width, height, path) = inline.destructured
                if (wanted.remove(name.lowercase())) {
                    emit(name.lowercase(), width.toFloatOrNull() ?: BOX, height.toFloatOrNull() ?: BOX, path)
                    written++
                }
                pendingName = null
            } else {
                val keyed = KEYED_PATH.find(line)
                if (keyed != null) {
                    val name = pendingName
                    if (name != null && wanted.remove(name)) {
                        emit(name, BOX, BOX, keyed.groupValues[1])
                        written++
                    }
                    pendingName = null
                } else {
                    OBJECT_KEY.find(line)?.let { pendingName = it.groupValues[1].lowercase() }
                }
            }
            if (wanted.isEmpty()) break
        }
        return written
    }
}
