package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme

/** iOS Settings.app inset-grouped section: caption above, rounded card below.
 *  Optionally shows a tappable [action] at the end of the caption row (e.g. "Tout afficher"). */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = AppleTypography.labelSmall,
                color = AppleColors.secondary,
                modifier = Modifier.weight(1f)
            )
            if (action != null && onAction != null) {
                Text(
                    text = action,
                    style = AppleTypography.labelSmall,
                    color = AppleColors.accent,
                    modifier = Modifier
                        .clip(AppleShapes.section)
                        .appleClickable(onAction)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.section)
                .background(AppleColors.elevated, AppleShapes.section)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section),
            content = content
        )
    }
}

/** Hairline divider between rows inside a section. */
@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(AppleColors.quaternary)
    )
}

/** Tappable row: label left, optional value + chevron right. */
@Composable
fun SettingsRow(
    label: String,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Box(Modifier.weight(1f))
        if (value != null) {
            Text(value, style = AppleTypography.titleMedium, color = AppleColors.secondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AppleColors.tertiary,
            modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp)
        )
    }
}

/** Label left, iOS switch right. */
@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Box(Modifier.weight(1f))
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Two-line toggle row: label on top, small [sublabel] (e.g. live state) below, switch right. */
@Composable
fun SettingsToggleSub(
    label: String,
    sublabel: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
            if (!sublabel.isNullOrBlank()) {
                Text(
                    sublabel,
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Outcome of a connection test shown inline in settings. */
enum class ConnStatus { IDLE, TESTING, OK, ERROR }

/** Row showing a live connection status; tap re-runs the test. */
@Composable
fun SettingsStatusRow(
    label: String,
    status: ConnStatus,
    detail: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .appleClickable(onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Box(Modifier.weight(1f))
        when (status) {
            ConnStatus.IDLE -> Text("Vérifier", style = AppleTypography.titleMedium, color = AppleColors.accent)
            ConnStatus.TESTING -> Text("Vérification…", style = AppleTypography.titleMedium, color = AppleColors.secondary)
            ConnStatus.OK -> {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = AppleColors.active,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Connecté", style = AppleTypography.titleMedium, color = AppleColors.active)
            }
            ConnStatus.ERROR -> {
                Text(
                    detail ?: "Échec",
                    style = AppleTypography.titleMedium,
                    color = AppleColors.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Rounded search field used to filter long settings lists. */
@Composable
fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Rechercher…",
    container: Color = AppleColors.elevated,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.section)
            .background(container, AppleShapes.section)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Search,
                null,
                tint = AppleColors.tertiary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppleTypography.titleMedium.copy(color = AppleColors.primary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppleColors.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, style = AppleTypography.titleMedium, color = AppleColors.tertiary)
                    }
                    inner()
                }
            )
        }
    }
}

/** Simple modal with a title and a list of plain-text lines (help, explanations). */
@Composable
fun SettingsInfoDialog(
    title: String,
    lines: List<String>,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
            color = AppleColors.elevated,
            shape = AppleShapes.panel
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                lines.forEach { line ->
                    Text(
                        line,
                        style = AppleTypography.bodyMedium,
                        color = AppleColors.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                PillButton(label = "OK", primary = true, onClick = onDismiss)
            }
        }
    }
}

/**
 * iOS-style input: no underline, filled rounded rect. Label sits above the field.
 */
@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppleShapes.section)
                .background(AppleColors.background.copy(alpha = 0.5f), AppleShapes.section)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = AppleTypography.titleMedium.copy(color = AppleColors.primary),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppleColors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation =
                    if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            placeholder,
                            style = AppleTypography.titleMedium,
                            color = AppleColors.tertiary
                        )
                    }
                    inner()
                }
            )
        }
    }
}

/** Label + integer value + slider (used for the screen-timeout minutes). */
@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueSuffix: String = "",
    /** Overrides the auto-formatted "{value}{valueSuffix}" label, e.g. for a "5 × 4" grid readout. */
    valueText: String? = null,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = AppleTypography.titleMedium, color = AppleColors.primary)
            Box(Modifier.weight(1f))
            Text(
                valueText ?: "${value.toInt()}$valueSuffix",
                style = AppleTypography.titleMedium,
                color = AppleColors.secondary
            )
        }
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = AppleColors.primary,
                activeTrackColor = AppleColors.accent,
                inactiveTrackColor = AppleColors.quaternary
            ),
            modifier = Modifier.wrapContentHeight()
        )
    }
}

/** Tile on the main settings screen that navigates to a sub-page. */
@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = AppleColors.accent,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppleShapes.card)
            .background(AppleColors.elevated, AppleShapes.card)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.card)
            .appleClickable(onClick)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppleShapes.section)
                    .background(iconTint.copy(alpha = 0.15f))
                    .then(
                        Modifier.wrapContentHeight(Alignment.CenterVertically)
                            .then(Modifier.size(40.dp))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = AppleTypography.titleMedium,
                    color = AppleColors.primary
                )
                Text(
                    subtitle,
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AppleColors.tertiary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Back button + title bar for settings sub-pages.
 *  [showBack] is false in the two-pane tablet layout, where the sidebar is the navigation
 *  and a back chevron in the detail pane would be redundant. */
@Composable
fun SettingsSubPageHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(AppleShapes.section)
                    .background(AppleColors.elevated, AppleShapes.section)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.section)
                    .appleClickable(onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "Retour",
                    tint = AppleColors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(
            title,
            style = AppleTypography.headlineLarge,
            color = AppleColors.primary
        )
    }
}

/** Fixed-width navigation rail for the two-pane tablet settings layout (width >= 840dp).
 *  One row per section; the selected section is highlighted, mirroring Android's own
 *  Settings app list-detail pattern. */
@Composable
fun <T> SettingsSidebar(
    items: List<Triple<T, ImageVector, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    header: String = "Portal Launcher",
) {
    Column(
        modifier = modifier
            .width(300.dp)
            .fillMaxWidth()
            .padding(end = 16.dp)
    ) {
        Text(
            header,
            style = AppleTypography.headlineLarge,
            color = AppleColors.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { (value, icon, title) ->
                val isSelected = value == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppleShapes.section)
                        .background(if (isSelected) AppleColors.accent.copy(alpha = 0.15f) else Color.Transparent, AppleShapes.section)
                        .appleClickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) AppleColors.accent else AppleColors.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        title,
                        style = AppleTypography.titleMedium,
                        color = if (isSelected) AppleColors.accent else AppleColors.primary
                    )
                }
            }
        }
    }
}

/** Full-screen dialog to pick a launcher app from the installed list. */
@Composable
fun AppPickerDialog(
    apps: List<AppEntry>,
    selectedPackage: String,
    onDismiss: () -> Unit,
    onAppSelected: (AppEntry) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    val filtered = remember(apps, filter) {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) apps
        else apps.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
            color = AppleColors.elevated,
            shape = AppleShapes.panel
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Choisir une application",
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                SettingsSearchField(
                    value = filter,
                    onValueChange = { filter = it },
                    container = AppleColors.background.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "Aucune application trouvée",
                        style = AppleTypography.bodySmall,
                        color = AppleColors.secondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val isSelected = app.packageName == selectedPackage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppleShapes.section)
                                    .appleClickable { onAppSelected(app) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        app.label,
                                        style = AppleTypography.titleMedium,
                                        color = if (isSelected) AppleColors.accent else AppleColors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        app.packageName,
                                        style = AppleTypography.bodySmall,
                                        color = AppleColors.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Outlined.Check,
                                        null,
                                        tint = AppleColors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PillButton(
                    label = "Annuler",
                    onClick = onDismiss
                )
            }
        }
    }
}

/** Dialog listing HA instances discovered on the LAN. Tap one to select it. */
@Composable
fun HaDiscoveryDialog(
    instances: List<HaInstance>,
    onDismiss: () -> Unit,
    onSelect: (HaInstance) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
            color = AppleColors.elevated,
            shape = AppleShapes.panel
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Home Assistant sur le réseau",
                    style = AppleTypography.titleLarge,
                    color = AppleColors.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (instances.isEmpty()) {
                    Text(
                        "Recherche en cours…",
                        style = AppleTypography.bodySmall,
                        color = AppleColors.secondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(instances, key = { it.url }) { instance ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppleShapes.section)
                                    .appleClickable { onSelect(instance) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        instance.name,
                                        style = AppleTypography.titleMedium,
                                        color = AppleColors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        instance.url,
                                        style = AppleTypography.bodySmall,
                                        color = AppleColors.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                PillButton(label = "Annuler", onClick = onDismiss)
            }
        }
    }
}


@Preview(backgroundColor = 0xFF000000, showBackground = true, widthDp = 380)
@Composable
private fun SettingsPreview() {
    PortalTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "DISPLAY & SLEEP") {
                SettingsToggle(label = "Always-on display", checked = true, onCheckedChange = {})
                SettingsDivider()
                SettingsToggle(label = "Screen timeout", checked = false, onCheckedChange = {})
            }
            SettingsSection(title = "MQTT") {
                SettingsTextField(label = "Host", value = "homeassistant.local", onValueChange = {})
            }
        }
    }
}
