package com.iblu01.portallauncher

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val version: String,
    val title: String,
    val notes: String,
    val apkUrl: String,
)

object AppUpdateManager {
    const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1_000L
    const val REMINDER_DELAY_MS = 24L * 60L * 60L * 1_000L

    fun currentVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val left = parts(remote)
        val right = parts(current)
        repeat(maxOf(left.size, right.size)) { index ->
            val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison > 0
        }
        return false
    }

    fun parseLatestRelease(body: String): AppRelease {
        val json = JSONObject(body)
        val assets = json.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url").orEmpty()
        return AppRelease(
            version = json.optString("tag_name").removePrefix("v"),
            title = json.optString("name").ifBlank { json.optString("tag_name") },
            notes = json.optString("body"),
            apkUrl = apkUrl,
        )
    }

    fun fetchLatest(): AppRelease {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "Portal-Launcher")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { parseLatestRelease(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, release: AppRelease): File {
        require(release.apkUrl.isNotBlank()) { "Release has no APK asset" }
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(directory, "portal-launcher-${release.version}.apk")
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.inputStream.use { input -> target.outputStream().use(input::copyTo) }
        } finally {
            connection.disconnect()
        }
        return target
    }

    fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/iblur01/portal-launcher/releases/latest"
}
