package com.iblu01.portallauncher.ui.icons

import android.content.Context
import android.util.LruCache
import android.util.Log
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Name → glyph lookup for the bundled Material Design Icons webfont (MDI 7.4.47, 7447 icons).
 *
 * The index is a sorted fixed-width table read **by binary search straight out of the APK**, not a
 * `HashMap`: a screen uses a few dozen icons, and holding all 7447 names on the heap for that would
 * cost roughly a megabyte on wall panels that do not have one to spare. A lookup is ~13 positional
 * reads against a page-cached file, and only the names actually drawn are memoised — misses
 * included, so an unknown name is not searched again.
 *
 * The asset must stay uncompressed in the APK (`noCompress += "mdi"` in build.gradle.kts);
 * `openFd` is what makes seeking possible, and it throws on a deflated entry.
 */
object MdiCodepoints {
    private const val TAG = "MdiCodepoints"
    private const val ASSET = "mdi_index.mdi"

    /** Longest MDI icon name; every record pads to this with NUL, which sorts below every ASCII byte. */
    private const val NAME_BYTES = 42
    /** Name field + a 3-byte big-endian codepoint (MDI lives in plane 15, up to U+F1D17). */
    private const val RECORD_BYTES = NAME_BYTES + 3

    /**
     * Recently resolved glyphs, misses included — [MISS] stands in for "MDI has no such icon", which
     * an `LruCache` cannot hold as null. Bounded, because an unbounded memo would drift back into
     * exactly the resident table this whole design exists to avoid.
     */
    private val memo = LruCache<String, String>(256)
    private const val MISS = ""
    private var channel: FileChannel? = null
    private var baseOffset = 0L
    private var recordCount = 0
    private var unavailable = false

    @Synchronized
    private fun open(context: Context): FileChannel? {
        channel?.let { return it }
        if (unavailable) return null
        return runCatching {
            val fd = context.assets.openFd(ASSET)
            baseOffset = fd.startOffset
            recordCount = (fd.length / RECORD_BYTES).toInt()
            FileInputStream(fd.fileDescriptor).channel.also { channel = it }
        }.getOrElse {
            Log.e(TAG, "cannot open $ASSET (compressed asset?); MDI icons unavailable", it)
            unavailable = true
            null
        }
    }

    /** The font glyph for `name` (a surrogate pair), or null when MDI has no icon by that name. */
    @Synchronized
    fun glyph(context: Context, name: String): String? {
        memo.get(name)?.let { return it.takeIf { cached -> cached != MISS } }
        val glyph = lookup(context, name)
        memo.put(name, glyph ?: MISS)
        return glyph
    }

    private fun lookup(context: Context, name: String): String? {
        val channel = open(context) ?: return null
        val needle = name.toByteArray(Charsets.US_ASCII)
        if (needle.size > NAME_BYTES) return null
        val buffer = ByteBuffer.allocate(RECORD_BYTES)
        var low = 0
        var high = recordCount - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            buffer.clear()
            val read = runCatching { channel.read(buffer, baseOffset + mid.toLong() * RECORD_BYTES) }
                .getOrElse { Log.w(TAG, "index read failed", it); return null }
            if (read != RECORD_BYTES) return null
            val record = buffer.array()
            val order = compareName(record, needle)
            when {
                order < 0 -> low = mid + 1
                order > 0 -> high = mid - 1
                else -> return String(Character.toChars(codepoint(record)))
            }
        }
        return null
    }

    /** Unsigned byte compare of a NUL-padded record name against the (unpadded) needle. */
    private fun compareName(record: ByteArray, needle: ByteArray): Int {
        for (i in 0 until NAME_BYTES) {
            val a = record[i].toInt() and 0xFF
            val b = if (i < needle.size) needle[i].toInt() and 0xFF else 0
            if (a != b) return a - b
        }
        return 0
    }

    private fun codepoint(record: ByteArray): Int =
        ((record[NAME_BYTES].toInt() and 0xFF) shl 16) or
            ((record[NAME_BYTES + 1].toInt() and 0xFF) shl 8) or
            (record[NAME_BYTES + 2].toInt() and 0xFF)
}
