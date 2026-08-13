package com.iblu01.portallauncher.ui.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** An installed icon pack, as offered in the settings picker. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * The intents every icon pack on Android declares. There is no official API for this — the ADW
 * action is the de-facto standard and the others are the two forks that stuck.
 */
private val ICON_PACK_ACTIONS = listOf(
    "org.adw.launcher.THEMES",
    "com.gau.go.launcherex.theme",
    "com.novalauncher.THEME",
)

/** Icon packs installed on the device, alphabetically. Touches the PackageManager: call off-main. */
fun installedIconPacks(context: Context): List<IconPackInfo> {
    val pm = context.packageManager
    return ICON_PACK_ACTIONS
        .flatMap { action -> pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .mapNotNull { pkg ->
            runCatching {
                IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString())
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

/**
 * One icon pack's `appfilter.xml`, resolved to drawables.
 *
 * Only the `<item component=… drawable=…>` mapping is honoured: an app the pack themes gets the
 * pack's icon, anything else keeps its own. The `iconback` / `iconmask` / `iconupon` compositing
 * that packs use to restyle *unthemed* icons is deliberately not implemented — see the note in
 * README; it is a rendering pipeline, not a lookup, and the fallback here is never wrong, only
 * inconsistent.
 */
class IconPack private constructor(
    private val resources: Resources,
    private val packageName: String,
    private val mapping: Map<String, String>,
) {
    /** Drawable this pack declares for a launcher component, or null when it themes nothing. */
    fun drawableFor(component: String): Drawable? {
        val name = mapping[component] ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        @Suppress("DEPRECATION")
        return runCatching { resources.getDrawable(id) }.getOrNull()
    }

    val isEmpty: Boolean get() = mapping.isEmpty()

    companion object {
        private const val TAG = "IconPack"

        /** Key format used inside `appfilter.xml`: `ComponentInfo{pkg/activity}`. */
        fun componentKey(packageName: String, activityName: String) =
            "ComponentInfo{$packageName/$activityName}"

        /**
         * Loads [packageName]'s appfilter. Returns null when the package is gone or declares no
         * usable mapping, so the caller falls straight back to system icons.
         */
        fun load(context: Context, packageName: String): IconPack? {
            if (packageName.isBlank()) return null
            val resources = runCatching {
                context.packageManager.getResourcesForApplication(packageName)
            }.getOrElse {
                Log.w(TAG, "icon pack $packageName is not installed", it)
                return null
            }
            val mapping = readAppfilter(resources, packageName)
            return if (mapping.isEmpty()) null else IconPack(resources, packageName, mapping)
        }

        /**
         * `appfilter.xml` lives either as a compiled XML resource or as a raw asset, depending on
         * how the pack was built. Both are tried, resource first.
         */
        private fun readAppfilter(resources: Resources, packageName: String): Map<String, String> {
            val id = resources.getIdentifier("appfilter", "xml", packageName)
            if (id != 0) {
                runCatching { resources.getXml(id).use { return parseAppfilter(it) } }
                    .onFailure { Log.w(TAG, "appfilter resource unreadable in $packageName", it) }
            }
            return runCatching {
                resources.assets.open("appfilter.xml").use { stream ->
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(stream, null)
                    parseAppfilter(parser)
                }
            }.getOrElse {
                Log.w(TAG, "no appfilter in $packageName", it)
                emptyMap()
            }
        }

        private inline fun <T> android.content.res.XmlResourceParser.use(block: (XmlPullParser) -> T): T {
            try {
                return block(this)
            } finally {
                close()
            }
        }
    }
}

/**
 * Pulls the `component -> drawable` pairs out of an appfilter document.
 *
 * Separate from [IconPack] and taking a bare parser so it can be tested against a string: appfilter
 * files in the wild are full of surprises (entries with no drawable, duplicate components, the
 * `iconback`/`iconmask` elements that are not mappings at all) and every one of them must be
 * skipped rather than crash the app list.
 */
fun parseAppfilter(parser: XmlPullParser): Map<String, String> {
    val mapping = LinkedHashMap<String, String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "item") {
            val component = parser.getAttributeValue(null, "component")
            val drawable = parser.getAttributeValue(null, "drawable")
            if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                // First entry wins: packs that list a component twice mean the first one.
                mapping.putIfAbsent(component, drawable)
            }
        }
        event = parser.next()
    }
    return mapping
}
