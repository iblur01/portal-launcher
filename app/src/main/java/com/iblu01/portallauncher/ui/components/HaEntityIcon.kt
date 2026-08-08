package com.iblu01.portallauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.iblu01.portallauncher.ui.icons.HaIcon
import com.iblu01.portallauncher.ui.icons.HaIcons

/**
 * Draws the icon Home Assistant shows for [entityId] — the user's own `mdi:`/`phu:`/`hue:` choice
 * when they set one, HA's component default otherwise — falling back to [fallback] when the entity
 * is unknown or its icon cannot be resolved.
 *
 * Goes through [rememberEntity] rather than reading `LocalHaStates` directly: that map is a fresh
 * instance on every HA push, and an icon has no business recomposing on unrelated updates.
 */
@Composable
fun HaEntityIcon(
    entityId: String,
    contentDescription: String?,
    tint: Color,
    size: Dp,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
) {
    val entity = rememberEntity(entityId)
    val ref = entity?.let { HaIcons.resolver.refFor(it) }
    HaIcon(ref, contentDescription, tint, size, fallback, modifier)
}
