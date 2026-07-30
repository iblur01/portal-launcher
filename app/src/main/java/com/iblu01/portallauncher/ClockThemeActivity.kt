package com.iblu01.portallauncher

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iblu01.portallauncher.ui.screens.ClockThemeScreen
import com.iblu01.portallauncher.ui.theme.PortalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen live editor for the clock theme (see [ClockThemeScreen]). Persists each change to
 * [Prefs.clockTheme]; [LauncherActivity] re-reads it on resume, so home restyles on return.
 */
@AndroidEntryPoint
class ClockThemeActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                ClockThemeScreen(
                    backgroundMode = prefs.backgroundMode,
                    overlayOpacity = prefs.bgOverlayOpacity,
                    initialTheme = prefs.clockTheme,
                    onThemeChange = { prefs.clockTheme = it },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersive()
    }

    @Suppress("DEPRECATION")
    private fun enableImmersive() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
