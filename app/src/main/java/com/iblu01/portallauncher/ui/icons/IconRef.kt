package com.iblu01.portallauncher.ui.icons

/**
 * A Home Assistant icon reference such as `mdi:speaker` or `phu:sonos-arc`.
 *
 * [namespace] is the prefix an icon set registers itself under in the HA frontend. Only `mdi` is
 * bundled with the app; every other prefix comes from a third-party icon-set module installed on
 * the user's Home Assistant (`phu:` = custom-brand-icons, `hue:` = hass-hue-icons, …) and is
 * resolved through [HaIconPackStore].
 */
data class IconRef(val namespace: String, val name: String) {
    val isMdi: Boolean get() = namespace == MDI

    override fun toString(): String = "$namespace:$name"

    companion object {
        const val MDI = "mdi"

        /** Namespace + name characters we accept, which is also what makes a safe cache filename. */
        private fun safe(value: String) = value.isNotEmpty() &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

        /**
         * Parses a HA icon string. Returns null for anything unusable — a blank value, a bare name
         * with no prefix, or characters that have no business in a namespace or a cache filename.
         */
        fun parse(raw: String?): IconRef? {
            val value = raw?.trim().orEmpty()
            val colon = value.indexOf(':')
            if (colon <= 0 || colon == value.length - 1) return null
            val namespace = value.substring(0, colon).lowercase()
            val name = value.substring(colon + 1).lowercase()
            if (!safe(namespace) || !safe(name)) return null
            return IconRef(namespace, name)
        }
    }
}
