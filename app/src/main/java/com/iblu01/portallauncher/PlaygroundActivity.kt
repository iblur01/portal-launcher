package com.iblu01.portallauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iblu01.portallauncher.ui.screens.PlaygroundScreen
import com.iblu01.portallauncher.ui.theme.PortalTheme

/**
 * Dev-only playground hosting the reusable control gallery ([PlaygroundScreen]).
 * Launched from the home-screen long-press menu.
 */
class PlaygroundActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalTheme {
                PlaygroundScreen(onBack = { finish() })
            }
        }
    }
}
