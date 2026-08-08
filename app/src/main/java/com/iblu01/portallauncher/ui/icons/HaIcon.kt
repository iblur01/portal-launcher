package com.iblu01.portallauncher.ui.icons

import android.util.LruCache
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R

/** The bundled Material Design Icons webfont (MDI 7.4.47). */
private val MdiFontFamily = FontFamily(Font(R.font.mdi))

/**
 * Glyph size relative to the requested box. MDI glyphs fill their em square, where Material's own
 * `Icon` set leaves a little optical padding inside 24×24 — leave the knob here rather than
 * chasing it at every call site if the two ever need to look the same weight side by side.
 */
private const val MDI_GLYPH_SCALE = 1f

/**
 * Parsed custom-pack icons, keyed by reference. Bounded because a `PathParser` run per frame is
 * wasteful and an unbounded map of vectors is exactly the kind of thing that hurts on a wall panel.
 */
private val packVectors = LruCache<String, ImageVector>(64)

/**
 * Draws the icon Home Assistant would draw for an entity.
 *
 * `mdi:` names render as glyphs from the bundled webfont — no heap-resident icon table, the font
 * cache lives in Skia. Any other namespace (`phu:`, `hue:`, …) is served from
 * [HaIconPackStore]'s disk cache. Anything unresolved falls back to [fallback], which is what keeps
 * the launcher usable offline, on a fresh install, or for an icon no installed pack provides.
 */
@Composable
fun HaIcon(
    ref: IconRef?,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    if (ref != null && ref.isMdi) {
        val glyph = remember(ref) { MdiCodepoints.glyph(context, ref.name) }
        if (glyph != null) {
            // sp is scaled by the user's font setting; an icon is not text, so divide it back out
            // to land on exactly `size` pixels.
            val fontSize = (size.value * MDI_GLYPH_SCALE / density.fontScale).sp
            Box(modifier.size(size), contentAlignment = Alignment.Center) {
                Text(
                    text = glyph,
                    color = tint,
                    style = TextStyle(
                        fontFamily = MdiFontFamily,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
            return
        }
    }

    if (ref != null && !ref.isMdi) {
        // Reading HaIcons.revision here is what redraws the icon once a pack download completes.
        val vector = remember(ref, HaIcons.revision) { packVector(ref) }
        if (vector != null) {
            Icon(vector, contentDescription, modifier.size(size), tint = tint)
            return
        }
    }

    Icon(fallback, contentDescription, modifier.size(size), tint = tint)
}

private fun packVector(ref: IconRef): ImageVector? {
    val key = ref.toString()
    packVectors.get(key)?.let { return it }
    val icon = HaIcons.packs?.cached(ref) ?: return null
    val vector = runCatching {
        ImageVector.Builder(
            name = key,
            defaultWidth = icon.width.dp,
            defaultHeight = icon.height.dp,
            viewportWidth = icon.width,
            viewportHeight = icon.height,
        ).addPath(
            pathData = PathParser().parsePathString(icon.path).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }.getOrNull() ?: return null
    packVectors.put(key, vector)
    return vector
}
