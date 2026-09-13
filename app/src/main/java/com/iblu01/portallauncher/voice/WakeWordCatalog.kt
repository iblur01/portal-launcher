package com.iblu01.portallauncher.voice

import android.content.Context
import android.util.Log
import java.io.File

/**
 * What wake words this panel can actually use: the models bundled in `assets/wakeword`, plus any
 * `.onnx` dropped into the app's own `wakeword` directory.
 *
 * Listed rather than hardcoded so adding a model is a file copy, not a release — the settings
 * page used to name its two bundled models in a Kotlin list, which meant a custom openWakeWord
 * model could be pushed to the device and still be unreachable.
 */
object WakeWordCatalog {
    private const val TAG = "WakeWordCatalog"
    const val ASSET_DIR = "wakeword"

    /** Where a custom model is dropped: `adb push model.onnx /sdcard/…` then copied here. */
    fun customDir(context: Context): File = File(context.filesDir, ASSET_DIR)

    /**
     * Asset-relative paths for bundled models, absolute paths for custom ones — the same shape
     * [PortalWakeWordEngine] already accepts for `modelPath`.
     */
    fun available(context: Context): List<String> {
        val bundled = runCatching {
            context.assets.list(ASSET_DIR)
                ?.filter { it.endsWith(".onnx", ignoreCase = true) }
                ?.map { "$ASSET_DIR/$it" }
                .orEmpty()
        }.onFailure { Log.w(TAG, "asset list failed: ${it.message}") }.getOrDefault(emptyList())

        val custom = runCatching {
            customDir(context).listFiles { file -> file.extension.equals("onnx", ignoreCase = true) }
                ?.map { it.absolutePath }
                .orEmpty()
        }.getOrDefault(emptyList())

        return (bundled + custom).sortedBy { wakeWordLabel(it) }
    }
}
