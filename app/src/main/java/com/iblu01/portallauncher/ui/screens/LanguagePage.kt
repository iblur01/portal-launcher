package com.iblu01.portallauncher.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.AppLanguage
import com.iblu01.portallauncher.LauncherActivity
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Language picker (Settings > Application > Language). Plain `ComponentActivity`s don't get
 * per-app locale switching for free, so [Prefs.appLanguage] is applied via `attachBaseContext`
 * ([com.iblu01.portallauncher.LocaleHelper]) — changing it here restarts the app to take effect.
 */
@Composable
fun LanguagePage(prefs: Prefs, onBack: () -> Unit, showBack: Boolean = true) {
    val context = LocalContext.current
    val current = AppLanguage.from(prefs.appLanguage)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        SettingsSubPageHeader(title = stringResource(R.string.language_page_title), onBack = onBack, showBack = showBack)

        SettingsSection(title = stringResource(R.string.settings_app_section_language)) {
            AppLanguage.values().forEachIndexed { index, language ->
                val isSelected = language == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 44.dp)
                        .appleClickable {
                            if (!isSelected) {
                                prefs.appLanguage = language.code
                                restartApp(context)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(language.flag, style = AppleTypography.titleLarge)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(language.nameRes),
                        style = AppleTypography.titleMedium,
                        color = AppleColors.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = AppleColors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (index != AppLanguage.values().lastIndex) SettingsDivider()
            }
        }
    }
}

/** Full process restart so every Activity re-reads the new locale from a fresh `attachBaseContext`. */
private fun restartApp(context: android.content.Context) {
    val intent = Intent(context, LauncherActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
