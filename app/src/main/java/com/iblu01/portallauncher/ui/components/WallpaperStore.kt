package com.iblu01.portallauncher.ui.components

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * The single custom wallpaper the launcher renders in "custom" mode.
 *
 * Shared by the onboarding and the settings so both write the same file the same way — through a
 * staging copy, off the main thread, so a revoked or half-read URI can never replace a good photo.
 */
internal fun wallpaperFile(context: Context): File = File(context.filesDir, "wallpaper.jpg")

/** Copies [uri] over the wallpaper. Returns false and leaves the previous photo intact on failure. */
internal fun copyWallpaper(context: Context, uri: Uri): Boolean {
    val target = wallpaperFile(context)
    val staging = File(context.filesDir, "wallpaper.jpg.tmp")
    return runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            staging.outputStream().use { output -> input.copyTo(output) }
        }
        check(staging.length() > 0L)
        check(staging.renameTo(target) || (target.delete() && staging.renameTo(target)))
    }.onFailure { staging.delete() }.isSuccess
}
