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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.apps.GridItem
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/**
 * Restores apps hidden from the grid.
 *
 * The counterpart of the item menu's "Masquer": without it, hiding an app would put it out of reach
 * for good. A restored app comes back at the end of the grid, since its old slot has probably been
 * taken in the meantime.
 */
@Composable
fun HiddenAppsDialog(
    items: List<GridItem>?,
    onRestore: (GridItem) -> Unit,
    onDismiss: () -> Unit,
) {
    if (items == null) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(AppleShapes.panel)
                .background(AppleColors.elevated.copy(alpha = 0.97f), AppleShapes.panel)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                .padding(vertical = 12.dp),
        ) {
            Text(
                stringResource(R.string.hidden_apps_title),
                style = AppleTypography.titleMedium,
                color = AppleColors.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                if (items.isEmpty()) stringResource(R.string.hidden_apps_empty) else stringResource(R.string.hidden_apps_tap_to_restore),
                style = AppleTypography.bodySmall.copy(fontSize = 13.sp),
                color = AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp),
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(items, key = { it.key }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .appleClickable { onRestore(item) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        val icon = item.icon
                        if (icon != null) {
                            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(28.dp))
                        } else {
                            Box(Modifier.size(28.dp).clip(AppleShapes.panel).background(AppleColors.frostedFill))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(item.label, style = AppleTypography.titleMedium, color = AppleColors.primary, maxLines = 1)
                    }
                }
            }
        }
    }
}
