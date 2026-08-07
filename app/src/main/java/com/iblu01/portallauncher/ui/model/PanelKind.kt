package com.iblu01.portallauncher.ui.model

/**
 * Typed identity of a side panel (design §4). Replaces the string-id / `PillKind` routing
 * scattered across LauncherActivity + SidePanel. Populated by the chip mapper (step 7) and
 * consumed by the exhaustive `when (PanelKind)` router.
 */
enum class PanelKind {
    // ex-groupes string-id
    LIGHTS, AIR_QUALITY, PURIFIER, SCENES, PRESENCE, ENERGY,
    // ex-PillKind
    LOCK, COVER, THERMOSTAT, VACUUM, FAN, SWITCH, ALARM, WASHER,
    HUMIDIFIER, WATER_HEATER, VALVE, SIREN, LAWN_MOWER,
    // universels
    GENERIC_DETAILS, MEDIA, WEATHER,
}
