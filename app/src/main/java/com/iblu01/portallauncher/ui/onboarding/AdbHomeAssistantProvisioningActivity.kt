package com.iblu01.portallauncher.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.iblu01.portallauncher.LauncherActivity
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.SettingsChangeBus
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Receives Home Assistant credentials from the privileged ADB shell without exposing a form.
 * Nothing is persisted until the same end-to-end check used by onboarding has succeeded.
 */
@AndroidEntryPoint
class AdbHomeAssistantProvisioningActivity : ComponentActivity() {

    @Inject lateinit var prefs: Prefs
    @Inject lateinit var tester: HaOnboardingTester

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent?.getStringExtra(EXTRA_HA_URL).orEmpty()
        val token = intent?.getStringExtra(EXTRA_HA_TOKEN).orEmpty()
        // Do not retain credentials in this Activity's saved instance state or local Intent.
        setIntent(Intent())

        lifecycleScope.launch {
            when (val result = tester.test(url, token) { }) {
                is TestState.Success -> {
                    prefs.haUrl = OnboardingUrls.normalizeHaUrl(url)
                    prefs.haToken = token
                    prefs.homeAssistantOnboardingSkipped = false
                    SettingsChangeBus.get().emit("haUrl")
                    SettingsChangeBus.get().emit("haToken")
                    Log.i(TAG, "Home Assistant credentials provisioned")
                    Toast.makeText(
                        this@AdbHomeAssistantProvisioningActivity,
                        R.string.onb_ha_test_success_title,
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is TestState.Failure -> {
                    Log.w(TAG, "Home Assistant provisioning failed: ${result.error}")
                    Toast.makeText(
                        this@AdbHomeAssistantProvisioningActivity,
                        R.string.onb_ha_test_error_generic_title,
                        Toast.LENGTH_LONG,
                    ).show()
                }
                else -> error("Home Assistant tester returned a non-terminal state")
            }
            openPortalWithoutCredentials()
        }
    }

    private fun openPortalWithoutCredentials() {
        val destination = if (prefs.onboardingCompleted) {
            LauncherActivity::class.java
        } else {
            OnboardingActivity::class.java
        }
        startActivity(
            Intent(this, destination).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            )
        )
        finish()
    }

    companion object {
        private const val TAG = "PortalProvisioning"
        private const val EXTRA_HA_URL = "ha_url"
        private const val EXTRA_HA_TOKEN = "ha_token"
    }
}
