package com.iblu01.portallauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iblu01.portallauncher.ui.onboarding.OnboardingCoordinator
import com.iblu01.portallauncher.ui.screens.OpacityPreviewScreen
import com.iblu01.portallauncher.ui.theme.PortalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen preview for tuning [Prefs.bgOverlayOpacity]: a replica of the launcher home over
 * the real wallpaper with a live opacity slider (see [OpacityPreviewScreen]). Persists on slider
 * release; [LauncherActivity] re-reads the value on resume, so home updates on return.
 */
@AndroidEntryPoint
class OpacityPreviewActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                val fromOnboarding = intent?.getBooleanExtra(EXTRA_ONBOARDING, false) == true
                OpacityPreviewScreen(
                    backgroundMode = prefs.backgroundMode,
                    initialOpacity = prefs.bgOverlayOpacity,
                    onOpacityCommit = { opacity ->
                        if (fromOnboarding) {
                            OnboardingCoordinator(prefs).updateLocalConfiguration { stored ->
                                stored.bgOverlayOpacity = opacity
                            }
                        } else {
                            prefs.bgOverlayOpacity = opacity
                        }
                    },
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

    companion object {
        private const val EXTRA_ONBOARDING = "onboarding"

        fun onboardingIntent(context: Context): Intent =
            Intent(context, OpacityPreviewActivity::class.java).putExtra(EXTRA_ONBOARDING, true)
    }
}
