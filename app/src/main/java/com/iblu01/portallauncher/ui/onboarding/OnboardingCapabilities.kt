package com.iblu01.portallauncher.ui.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.iblu01.portallauncher.RootProvisioning
import com.iblu01.portallauncher.ScreenControl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the real system state behind each capability card, and knows which system screen to open
 * for it. Nothing is inferred from a stored flag: everything is re-read on every `onResume`, so a
 * permission granted (or revoked) outside the app shows up immediately.
 */
@Singleton
class OnboardingCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun read(): SystemCapabilities = SystemCapabilities(
        defaultLauncher = defaultLauncherStatus(),
        screenControl = if (ScreenControl.isAccessibilityEnabled(context)) CapabilityStatus.GRANTED
        else CapabilityStatus.MISSING,
        brightness = if (Settings.System.canWrite(context)) CapabilityStatus.GRANTED
        else CapabilityStatus.MISSING,
    )

    /** True when Portal is the activity Android launches for HOME. */
    fun isDefaultLauncher(): Boolean {
        val resolved = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    private fun defaultLauncherStatus(): CapabilityStatus = when {
        isDefaultLauncher() -> CapabilityStatus.GRANTED
        canOpenHomeSettings() -> CapabilityStatus.MISSING
        // No system screen offers the choice on this device: only the ADB command can set it.
        else -> CapabilityStatus.UNAVAILABLE
    }

    private fun canOpenHomeSettings(): Boolean =
        Intent(Settings.ACTION_HOME_SETTINGS).resolveActivity(context.packageManager) != null

    /**
     * The system screen to send the user to for [capability].
     *
     * `RoleManager.ROLE_HOME` is the modern way to ask; it only exists from API 29, and this app
     * still runs on API 28 panels, hence the fallback to the home-settings screen.
     */
    fun settingsIntentFor(capability: Capability): Intent? = when (capability) {
        Capability.DEFAULT_LAUNCHER -> homeRoleIntent()
        Capability.SCREEN_CONTROL -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        Capability.BRIGHTNESS -> Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
    }

    private fun homeRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return if (canOpenHomeSettings()) Intent(Settings.ACTION_HOME_SETTINGS) else null
    }

    /**
     * Tries the shortcut first: with `WRITE_SECURE_SETTINGS` granted over ADB the accessibility
     * service can be enabled without leaving the app. Returns true when that worked.
     */
    fun tryEnableScreenControlDirectly(): Boolean = ScreenControl.enableAccessibility(context)

    /** True when a root shell answers, i.e. Portal can grant itself everything. */
    fun isRootAvailable(): Boolean = RootProvisioning.isAvailable()

    /** Grants every capability from a root shell. Blocking: call it off the main thread. */
    fun provisionWithRoot(): Boolean = RootProvisioning.provision(context)

    /** The command shown when no system screen can set the launcher role. */
    fun adbSetHomeCommand(): String =
        "adb shell cmd package set-home-activity ${context.packageName}/.LauncherActivity"
}
