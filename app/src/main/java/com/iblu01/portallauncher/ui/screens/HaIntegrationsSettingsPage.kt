package com.iblu01.portallauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.IosSwitch
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography

data class HaIntegrationSettingsEntry(
    val domain: String,
    val label: String,
    val entityCount: Int,
    val deviceCount: Int,
)

internal fun buildHaIntegrationEntries(
    platformByEntity: Map<String, String>,
    deviceIdByEntity: Map<String, String>,
): List<HaIntegrationSettingsEntry> = platformByEntity.entries
    .filter { (_, platform) -> platform.isNotBlank() }
    .groupBy({ it.value.lowercase() }, { it.key })
    .map { (domain, entityIds) ->
        HaIntegrationSettingsEntry(
            domain = domain,
            label = integrationLabel(domain),
            entityCount = entityIds.size,
            deviceCount = entityIds.mapNotNull(deviceIdByEntity::get).distinct().size,
        )
    }
    .sortedWith(compareByDescending<HaIntegrationSettingsEntry> { it.deviceCount }.thenBy { it.label.lowercase() })

private fun integrationLabel(domain: String): String = when (domain) {
    "samsungtv" -> "Samsung Smart TV"
    "mobile_app" -> "Home Assistant Mobile"
    "homeassistant" -> "Home Assistant"
    "google_cast" -> "Google Cast"
    "androidtv_remote" -> "Android TV Remote"
    "met" -> "Météo MET"
    else -> domain.split('_').joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}

@Composable
fun HaIntegrationsSettingsPage(
    prefs: Prefs,
    platformByEntity: Map<String, String>,
    deviceIdByEntity: Map<String, String>,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val entries = remember(platformByEntity, deviceIdByEntity) {
        buildHaIntegrationEntries(platformByEntity, deviceIdByEntity)
    }
    var disabled by remember { mutableStateOf(prefs.disabledHaIntegrations) }
    val initialDisabled = remember { prefs.disabledHaIntegrations }
    val latestDisabled by rememberUpdatedState(disabled)

    // This page is deliberately transactional: toggles update the mosaic instantly, while the
    // rest of Portal keeps its stable snapshot. Leaving the page commits once and triggers one
    // catalog rebuild, instead of tearing through the whole launcher for every tap.
    DisposableEffect(Unit) {
        onDispose {
            if (latestDisabled != initialDisabled) {
                prefs.disabledHaIntegrations = latestDisabled
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SettingsSubPageHeader(stringResource(R.string.settings_integrations_title), onBack, showBack = showBack)
        Text(
            text = stringResource(R.string.settings_integrations_intro),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        )
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.settings_integrations_empty),
                style = AppleTypography.bodyLarge,
                color = AppleColors.tertiary,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(190.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(entries, key = { it.domain }) { integration ->
                    val enabled = integration.domain !in disabled
                    IntegrationCard(
                        integration = integration,
                        enabled = enabled,
                        haUrl = prefs.haUrl,
                        haToken = prefs.haToken,
                        onEnabled = { wanted ->
                            disabled = if (wanted) disabled - integration.domain else disabled + integration.domain
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntegrationCard(
    integration: HaIntegrationSettingsEntry,
    enabled: Boolean,
    haUrl: String,
    haToken: String,
    onEnabled: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val accent = Color(0xFF5AC8FA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) Color.White.copy(alpha = 0.105f) else Color.White.copy(alpha = 0.045f))
            .border(1.dp, if (enabled) accent.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.08f), shape)
            .clickable { onEnabled(!enabled) }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            BrandIcon(integration, haUrl, haToken, enabled)
            Box(Modifier.weight(1f))
            IosSwitch(checked = enabled, onCheckedChange = onEnabled)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = integration.label,
                style = AppleTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (enabled) AppleColors.primary else AppleColors.tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.settings_integrations_counts,
                    integration.deviceCount,
                    integration.entityCount,
                ),
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
            )
        }
    }
}

@Composable
private fun BrandIcon(entry: HaIntegrationSettingsEntry, haUrl: String, haToken: String, enabled: Boolean) {
    val context = LocalContext.current
    val iconShape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier.size(62.dp).clip(iconShape).background(Color.White.copy(alpha = if (enabled) 0.96f else 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.label.take(2).uppercase(),
            color = Color(0xFF303036),
            style = AppleTypography.bodySmall.copy(fontWeight = FontWeight.Bold),
        )
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data("${haUrl.trimEnd('/')}/api/brands/integration/${entry.domain}/icon.png?placeholder=no")
                .addHeader("Authorization", "Bearer $haToken")
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}
