package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.AppRelease
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

@Composable
fun AppUpdateOverlay(
    release: AppRelease?,
    downloading: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onIgnore: () -> Unit,
) {
    release ?: return
    Box(
        modifier = Modifier.fillMaxSize().background(AppleColors.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = AppleColors.accent)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.update_popup_title),
                style = AppleTypography.headlineLarge,
                color = AppleColors.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.update_popup_version, release.version),
                style = AppleTypography.titleLarge,
                color = AppleColors.secondary,
            )
            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                MarkdownText(
                    release.notes,
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                )
            } else {
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(24.dp))
            PillButton(
                label = if (downloading) stringResource(R.string.settings_info_downloading)
                else stringResource(R.string.update_popup_install),
                primary = true,
                enabled = !downloading,
                onClick = onInstall,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton(
                    label = stringResource(R.string.update_popup_later),
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                    onClick = onLater,
                )
                PillButton(
                    label = stringResource(R.string.update_popup_ignore),
                    modifier = Modifier.weight(1f),
                    enabled = !downloading,
                    onClick = onIgnore,
                )
            }
        }
    }
}
