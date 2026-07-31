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
 * [DEFAULT] is only an optional seed for convenience. Production configurations should build their
 * own [SessionAllowlist] from local storage or configuration so the set of launchable packages is
 * not hard-coded and can differ per device.
 */
class SessionAllowlist(private val entries: Map<String, AppClassification>) {

    fun classificationFor(packageName: String): AppClassification? = entries[packageName]

    companion object {
        val DEFAULT = SessionAllowlist(
            mapOf(
                "io.homeassistant.companion.android" to AppClassification.HOME,
                "com.google.android.youtube" to AppClassification.MEDIA,
                "com.android.chrome" to AppClassification.UTILITY,
                "com.whatsapp" to AppClassification.COMMUNICATION,
            )
        )
    }
}
