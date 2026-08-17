package com.iblu01.portallauncher.ui.onboarding

import java.net.URI

/**
 * URL helpers for the onboarding forms. Kept free of Android types so the validation rules and the
 * MQTT host suggestion stay unit-testable.
 */
object OnboardingUrls {

    /**
     * Normalises what the user typed into something [com.iblu01.portallauncher.HaApiClient] accepts:
     * trimmed, trailing slash removed, and `http://` added when the scheme is missing (typing
     * "homeassistant.local:8123" is the common case).
     */
    fun normalizeHaUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()
        val withScheme = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> trimmed
            // Some other protocol entirely: not something the API client can talk to, and not
            // something to silently rewrite into http.
            lower.contains("://") -> return ""
            else -> "http://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    /** True when [raw] normalises to an http(s) URL with a host — the bar to allow a test run. */
    fun isValidHaUrl(raw: String): Boolean {
        val normalized = normalizeHaUrl(raw)
        if (normalized.isEmpty()) return false
        val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (host.isBlank() || host.contains(' ')) return false
        if (uri.port != -1 && uri.port !in 1..65535) return false
        return true
    }

    /** Host part of a Home Assistant URL, or "" — the suggested MQTT broker address. */
    fun hostOf(raw: String): String =
        runCatching { URI(normalizeHaUrl(raw)).host }.getOrNull().orEmpty()

    /** `.local` relies on mDNS, which is unavailable on some Android-based panels. */
    fun usesMdnsHostname(raw: String): Boolean = hostOf(raw).lowercase().endsWith(".local")

    /**
     * The broker address to pre-fill.
     *
     * Only ever a *suggestion*: Home Assistant exposes no API to read its broker's address or
     * credentials, so nothing here is discovered — the host is reused because the add-on broker
     * usually runs on the same machine.
     */
    fun suggestedMqttHost(haUrl: String, fallback: String = "homeassistant.local"): String =
        hostOf(haUrl).ifBlank { fallback }

    /** Whether a port string is usable as typed. */
    fun isValidPort(raw: String): Boolean = raw.trim().toIntOrNull() in 1..65535
}
