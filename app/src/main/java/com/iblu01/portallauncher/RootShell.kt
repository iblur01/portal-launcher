package com.iblu01.portallauncher

import android.util.Log
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around `su`. On a rooted panel Portal can grant itself every capability the
 * onboarding otherwise walks the user through, so the whole system setup collapses into one tap.
 *
 * Everything here is blocking: call it off the main thread.
 */
object RootShell {

    private const val TAG = "PortalLauncher"
    private const val TIMEOUT_SECONDS = 20L

    /** Cached because probing spawns a process and the answer never changes while we run. */
    @Volatile private var available: Boolean? = null

    /** True when a `su` binary exists and hands us uid 0. */
    fun isAvailable(): Boolean = available ?: run(listOf("id")).let { result ->
        val ok = result != null && result.contains("uid=0")
        available = ok
        ok
    }

    /** Forgets the cached probe, e.g. after the user granted the root prompt on a retry. */
    fun forgetProbe() { available = null }

    /**
     * Runs [commands] in a single root shell, one per line. Returns the combined output, or null
     * when no root shell could be opened (no `su`, or the prompt was denied).
     *
     * A failing command does not abort the batch: each capability is independent, and one refused
     * grant should not cost the others.
     */
    fun run(commands: List<String>): String? = try {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        process.outputStream.bufferedWriter().use { writer ->
            commands.forEach { writer.write(it + "\n") }
            writer.write("exit\n")
        }
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            Log.w(TAG, "RootShell: timed out")
            null
        } else {
            output
        }
    } catch (e: Exception) {
        Log.i(TAG, "RootShell: no root shell (${e.message})")
        null
    }
}
