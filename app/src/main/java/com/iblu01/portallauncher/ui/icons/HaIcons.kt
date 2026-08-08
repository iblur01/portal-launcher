package com.iblu01.portallauncher.ui.icons

import android.content.Context
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import okhttp3.OkHttpClient

/**
 * App-wide entry point for Home Assistant entity icons, in the same shape as
 * [com.iblu01.portallauncher.ui.ConnectionStatus]: a process-scoped holder rather than a
 * `CompositionLocal`, because every panel down the tree draws entity icons and threading a
 * provider through all of them buys nothing.
 */
object HaIcons {

    val resolver = HaIconResolver()

    @Volatile
    var packs: HaIconPackStore? = null
        private set

    /**
     * Bumped whenever the pack cache changes on disk. Composables read it so an icon that was still
     * downloading redraws once it arrives, without any per-icon state plumbing.
     */
    var revision by mutableIntStateOf(0)
        private set

    /**
     * Parsed pack icons, keyed by reference. Bounded because a `PathParser` run per frame is
     * wasteful and an unbounded map of vectors is what hurts on a wall panel.
     */
    internal val packVectors = LruCache<String, ImageVector>(64)

    fun init(context: Context, client: OkHttpClient) {
        if (packs == null) packs = HaIconPackStore(context.applicationContext, client)
    }

    /**
     * Announces that the pack cache changed. Evicts the parsed vectors first: after a pack update
     * the cached [ImageVector] is the *old* art, and redrawing without dropping it would show the
     * previous icon forever. Safe from any thread, like the rest of this holder.
     */
    fun onPackCacheChanged() {
        packVectors.evictAll()
        revision++
    }
}
