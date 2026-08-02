package com.iblu01.portallauncher.session

/** Strict local persistence codec for the classified package allowlist. */
object SessionAllowlistCodec {
    const val MAX_ENTRIES = 32
    private val packageRegex = Regex("^[a-zA-Z][a-zA-Z0-9._-]{0,127}$")

    fun encode(allowlist: SessionAllowlist): Set<String> = allowlist.toMap()
        .entries
        .asSequence()
        .filter { packageRegex.matches(it.key) }
        .sortedBy { it.key }
        .take(MAX_ENTRIES)
        .map { "${it.key}|${it.value.name}" }
        .toSet()

    fun decode(values: Set<String>?): SessionAllowlist {
        if (values.isNullOrEmpty()) return SessionAllowlist(emptyMap())
        val entries = linkedMapOf<String, AppClassification>()
        values.asSequence().sorted().take(MAX_ENTRIES).forEach { encoded ->
            val separator = encoded.lastIndexOf('|')
            if (separator <= 0 || separator == encoded.lastIndex) return@forEach
            val packageName = encoded.substring(0, separator)
            if (!packageRegex.matches(packageName)) return@forEach
            val classification = runCatching {
                AppClassification.valueOf(encoded.substring(separator + 1))
            }.getOrNull() ?: return@forEach
            entries[packageName] = classification
        }
        return SessionAllowlist(entries)
    }
}
