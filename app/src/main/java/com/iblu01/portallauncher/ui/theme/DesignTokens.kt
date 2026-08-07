package com.iblu01.portallauncher.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R

/**
 * The whole palette. Nothing outside this + the state palette should appear in composables.
 */
object AppleColors {
    // Surfaces
    val background = Color(0xFF000000)   // true OLED black
    val elevated = Color(0xFF1C1C1E)     // cards, panels
    val groupedBg = Color(0xFF000000)    // settings grouped background

    // Frosted
    val frostedFill = Color.White.copy(alpha = 0.08f)   // fill on top of blur
    val frostedBorder = Color.White.copy(alpha = 0.10f) // subtle outline

    // Text
    val primary = Color(0xFFFFFFFF)
    val secondary = Color.White.copy(alpha = 0.60f)
    val tertiary = Color.White.copy(alpha = 0.35f)
    val quaternary = Color.White.copy(alpha = 0.18f)

    // Accent
    val accent = Color(0xFF0A84FF)       // iOS blue
    val lockAccent = Color(0xFF00C8BE)
    val fanAccent = Color(0xFF64D1FC)
    val thermostatCool = Color(0xFF33AEE0)
    val thermostatHeat = Color(0xFFFE9709)

    // State indicators
    val active = Color(0xFF30D158)       // iOS green
    val warning = Color(0xFFFFD60A)      // iOS yellow
    val error = Color(0xFFFF453A)        // iOS red
    val inactive = Color(0xFF636366)     // muted gray
    val iosSwitchGreen = Color(0xFF34C759)
}

/**
 * Maps the launcher's widget state strings onto the Apple state palette.
 * State strings come from MQTT JSON (see [com.iblu01.portallauncher.LauncherWidget]).
 */
fun stateColor(state: String): Color = when (state.lowercase()) {
    "active" -> AppleColors.active            // green
    "warning" -> AppleColors.warning          // yellow
    "critical", "error" -> AppleColors.error  // red
    "ok" -> AppleColors.accent                // blue (accent, per spec)
    "inactive", "muted" -> AppleColors.inactive
    else -> AppleColors.secondary             // info → white @ 60%
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun spaceGrotesk(weight: FontWeight) = Font(
    R.font.space_grotesk_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

// Variable font: without explicit weight variation settings the entries render at the default
// axis (400) with *synthetic* bolding, which looks thin. Pin each entry to its true wght axis.
val SpaceGrotesk = FontFamily(
    spaceGrotesk(FontWeight.Thin),
    spaceGrotesk(FontWeight.Light),
    spaceGrotesk(FontWeight.Normal),
    spaceGrotesk(FontWeight.Medium),
    spaceGrotesk(FontWeight.SemiBold),
    spaceGrotesk(FontWeight.Bold),
)

/** Font resource backing each [ClockFont] (all variable-`wght` .ttf in res/font). */
private fun clockFontRes(font: ClockFont): Int = when (font) {
    ClockFont.SPACE_GROTESK -> R.font.space_grotesk_variable
    ClockFont.OSWALD -> R.font.oswald
    ClockFont.PLAYFAIR -> R.font.playfair_display
    ClockFont.MONTSERRAT -> R.font.montserrat
    ClockFont.JETBRAINS_MONO -> R.font.jetbrains_mono
    ClockFont.ORBITRON -> R.font.orbitron
    ClockFont.TEKO -> R.font.teko
    ClockFont.ROBOTO_SLAB -> R.font.roboto_slab
    ClockFont.EXO2 -> R.font.exo2
    ClockFont.INTER -> R.font.inter
}

/**
 * A single-entry [FontFamily] for [font], pinned to [weight] on its variable `wght` axis. The
 * caller must render text at the same [weight] so the pinned variation is matched exactly (this
 * is how variable fonts avoid synthetic bolding — see [spaceGrotesk]).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
fun clockFontFamily(font: ClockFont, weight: FontWeight): FontFamily = FontFamily(
    Font(
        clockFontRes(font),
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
)

/**
 * SF Pro Display-flavoured type scale.
 */
val AppleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Thin,
        fontSize = 120.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        letterSpacing = (-0.3).sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = (-0.1).sp
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * Continuous-corner ("squircle") radii, expressed as percentages of the shorter side —
 * the closest Compose offers to Apple's continuous curvature.
 */
object AppleShapes {
    val card = RoundedCornerShape(28.dp)     // widget cards
    val panel = RoundedCornerShape(28.dp)    // panels / overlays
    val tray = RoundedCornerShape(40.dp)     // widget tray
    val pill = RoundedCornerShape(50)        // buttons / switches (percent = full pill)
    val section = RoundedCornerShape(16.dp)  // settings groups
}

/** Core iOS-style spring. Everything animated should route through these. */
object AppleMotion {
    const val STAGGER_DELAY = 50L   // ms between staggered cards
    const val FADE_DURATION = 250   // ms
    const val SLIDE_DURATION = 300  // ms
    const val PRESS_SCALE = 0.96f
    const val PRESS_DURATION = 50    // ms scale-down
    const val RELEASE_DURATION = 300 // ms spring-back

    fun <T> spring() = spring<T>(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Frosted-glass blur that degrades gracefully.
 *
 * [Modifier.blur] only renders on API 31+. On the Portal (Android 9 / API 28) it is a
 * no-op, so callers must lean on translucent scrims for the frosted look there, and
 * pre-blur large wallpaper bitmaps with [blurBitmapCompat] instead.
 */
fun Modifier.blurCompat(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) this.blur(radius) else this

/** True when live composable blur is available. */
val supportsLiveBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun rememberBlurredWallpaper(source: Bitmap?, radius: Int = 32): Bitmap? =
    remember(source, radius) {
        if (source == null) null else blurBitmapCompat(source, radius)
    }
