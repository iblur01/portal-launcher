package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.apps.WidgetOffer
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Picks a widget to add.
 *
 * Our own list rather than the system's `ACTION_APPWIDGET_PICK`: that picker is styled by the
 * platform, offers no size information, and on this device it looks nothing like the rest of the
 * launcher. Each entry says how many cells it will take, because that is what decides whether it
 * fits on a page at all.
 */
@Composable
fun WidgetPickerDialog(
    offers: List<WidgetOffer>?,
    onPick: (WidgetOffer) -> Unit,
    onDismiss: () -> Unit,
) {
    if (offers == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .semantics { dialog() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(AppleShapes.panel)
                .background(AppleColors.elevated.copy(alpha = 0.97f), AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                .testTag("widgetPicker")
                .padding(vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.widget_picker_title),
                style = AppleTypography.titleMedium,
                color = AppleColors.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                if (offers.isEmpty()) stringResource(R.string.widget_picker_empty) else stringResource(R.string.widget_picker_size_hint),
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(offers, key = { it.key }) { offer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appleClickable { onPick(offer) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val preview = offer.preview
                        if (preview != null) {
                            Image(
                                bitmap = preview,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(44.dp),
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(AppleShapes.panel)
                                    .background(AppleColors.frostedFill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Widgets,
                                    contentDescription = null,
                                    tint = AppleColors.secondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                offer.label,
                                style = AppleTypography.titleMedium,
                                color = AppleColors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${offer.appLabel} · ${offer.minSpan.width}×${offer.minSpan.height}",
                                style = AppleTypography.bodySmall.copy(fontSize = 12.sp),
                                color = AppleColors.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
