package com.iblu01.portallauncher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SensorDoor
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single source of truth mapping an icon/type key (from a widget, chip, or scene)
 * to an SF-Symbols-flavoured Material outlined icon.
 */
fun launcherIcon(key: String): ImageVector = when (key.lowercase()) {
    // Widgets / devices
    "washer", "timer" -> Icons.Outlined.CleaningServices
    "window", "opening", "cover", "shutter", "blind" -> Icons.Outlined.SensorDoor
    "door", "porte" -> Icons.Outlined.SensorDoor
    "air", "air_purifier", "purifier", "fan" -> Icons.Outlined.Air
    "lock" -> Icons.Outlined.Lock
    "unlock", "lock_open" -> Icons.Outlined.LockOpen
    "printer_3d", "3d_printer", "printer", "print" -> Icons.Outlined.Print
    "vacuum", "robot_vacuum", "aspirateur" -> Icons.Outlined.CleaningServices
    "climate", "hvac", "heating", "cooling", "heater", "ac", "thermostat" -> Icons.Outlined.Thermostat
    "switch", "outlet", "prise", "plug" -> Icons.Outlined.PowerSettingsNew
    "light", "lights", "bulb" -> Icons.Outlined.Lightbulb
    "sensor", "temperature", "humidity" -> Icons.Outlined.DeviceThermostat
    "energy", "power", "consumption" -> Icons.Outlined.Bolt
    "media", "tv", "speaker" -> Icons.Outlined.Tv
    "scene", "scenes", "script", "shortcut" -> Icons.Outlined.AutoAwesome
    "presence", "person", "people" -> Icons.Outlined.Group
    "security" -> Icons.Outlined.Security
    "shield" -> Icons.Outlined.Shield
    "music" -> Icons.Outlined.MusicNote
    // Scenes / shortcuts
    "arrive", "arriver", "come_home", "login" -> Icons.Outlined.Login
    "leave", "partir", "away", "logout" -> Icons.Outlined.Logout
    "wake", "reveil", "morning", "sun" -> Icons.Outlined.WbSunny
    "night", "nuit", "sleep", "bedtime" -> Icons.Outlined.Bedtime
    "movie", "cinema", "film" -> Icons.Outlined.Movie
    else -> Icons.Outlined.Home
}
