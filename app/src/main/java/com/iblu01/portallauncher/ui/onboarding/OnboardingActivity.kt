package com.iblu01.portallauncher.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iblu01.portallauncher.LauncherActivity
import com.iblu01.portallauncher.LocaleHelper
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.WebConfigActivity
import com.iblu01.portallauncher.ui.onboarding.components.ProvideOnboardingLayout
import com.iblu01.portallauncher.ui.theme.PortalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the first-run assistant.
 *
 * Its own activity rather than a page of the settings: the flow owns the whole screen, survives
 * trips into Android's settings, and must be able to run before anything else is configured.
 */
@AndroidEntryPoint
class OnboardingActivity : ComponentActivity() {

    @Inject lateinit var prefs: Prefs

    private val viewModel: OnboardingViewModel by viewModels()
    private val webConfigLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.refreshExternalConfiguration()
            viewModel.testHomeAssistant()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // A dev trigger may ask for a clean run (see the debug manifest / ADB commands in README).
        if (intent?.getBooleanExtra(EXTRA_RESET, false) == true) {
            prefs.resetOnboarding()
        }

        setContent {
            PortalTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // Measured once for the whole flow: a step reads its layout before it lays anything
                // out, so the measurement cannot live inside the scaffold it is about to call.
                ProvideOnboardingLayout {
                    OnboardingScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenSystemSetting = ::openSystemSetting,
                        onFinish = ::finishOnboarding,
                        onConfigureWithPhone = {
                            webConfigLauncher.launch(Intent(this, WebConfigActivity::class.java))
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
        // Every capability is re-read here, so a trip into Android's settings shows up as soon as
        // the user comes back — with its own check animation, without advancing the flow.
        viewModel.refreshCapabilities()
        viewModel.refreshExternalConfiguration()
    }

    private fun openSystemSetting(capability: Capability) {
        if (capability == Capability.SCREEN_CONTROL && viewModel.tryEnableScreenControlDirectly()) {
            viewModel.refreshCapabilities()
            return
        }
        val intent = viewModel.settingsIntentFor(capability) ?: return
        runCatching { startActivity(intent) }
    }

    /** Leaves for the home screen; the flow is already marked complete by the ViewModel. */
    private fun finishOnboarding(openSettings: Boolean) {
        val target = if (openSettings) {
            Intent(this, com.iblu01.portallauncher.SettingsActivity::class.java)
        } else {
            Intent(this, LauncherActivity::class.java)
        }
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    companion object {
        /** Wipes the stored progress before showing the flow. Used by the dev trigger. */
        const val EXTRA_RESET = "reset"

        /** Opens the assistant, optionally from scratch. */
        fun intent(context: Context, reset: Boolean = false): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_RESET, reset)
    }
}
