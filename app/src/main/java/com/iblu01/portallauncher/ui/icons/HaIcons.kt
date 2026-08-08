package com.iblu01.portallauncher.ui.icons

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
     * Bumped whenever new pack icons land on disk. Composables read it so an icon that was still
     * downloading redraws once it arrives, without any per-icon state plumbing.
     */
    var revision by mutableIntStateOf(0)
        internal set

    fun init(context: Context, client: OkHttpClient) {
        if (packs == null) packs = HaIconPackStore(context.applicationContext, client)
    }
}
