package com.iblu01.portallauncher.session

/**
 * Classification assigned to an allowed package.
 *
 * Each class carries a local default duration and a hard maximum duration. The default is used when
 * a `start` command omits `duration_s`; the max is enforced regardless of whether the duration came
 * from the command or the default.
 */
enum class AppClassification(
    val defaultDurationSeconds: Int,
    val maxDurationSeconds: Int,
) {
    HOME(defaultDurationSeconds = 60, maxDurationSeconds = 300),
    MEDIA(defaultDurationSeconds = 30, maxDurationSeconds = 120),
    UTILITY(defaultDurationSeconds = 30, maxDurationSeconds = 60),
    COMMUNICATION(defaultDurationSeconds = 30, maxDurationSeconds = 60),
}

/**
 * Local package allowlist. Each entry maps a package name to a classification that supplies the
 * default and maximum durations. The parser rejects unknown packages, so the system fails closed.
 *
 * Runtime configuration starts from [EMPTY] and is populated only through local device settings.
 */
class SessionAllowlist(private val entries: Map<String, AppClassification>) {

    fun classificationFor(packageName: String): AppClassification? = entries[packageName]

    /** Exposes a read-only view of the configured entries for persistence and UI editing. */
    fun toMap(): Map<String, AppClassification> = entries

    override fun equals(other: Any?): Boolean =
        other is SessionAllowlist && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    companion object {
        val EMPTY = SessionAllowlist(emptyMap())
    }
}
