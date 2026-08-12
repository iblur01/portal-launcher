package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.model.MediaPlayerVolume
import com.iblu01.portallauncher.domain.model.PlayingMedia
import com.iblu01.portallauncher.ui.components.controls.PortalThreeWayControl
import com.iblu01.portallauncher.ui.components.controls.ThreeWayControlSize
import com.iblu01.portallauncher.ui.components.controls.VerticalFillSlider
import com.iblu01.portallauncher.ui.components.controls.controlSize
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import okhttp3.OkHttpClient
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

internal data class MediaDisclosure(val showAlbum: Boolean, val secondaryPlayerCount: Int)

/** Keeps primary playback controls while shedding secondary detail as usable height shrinks. */
internal fun mediaDisclosureFor(widthDp: Float, heightDp: Float): MediaDisclosure = when {
    heightDp <= 420f -> MediaDisclosure(showAlbum = false, secondaryPlayerCount = 1)
    heightDp <= 560f || widthDp <= 480f -> MediaDisclosure(showAlbum = false, secondaryPlayerCount = 2)
    else -> MediaDisclosure(showAlbum = true, secondaryPlayerCount = 2)
}

@Composable
fun MediaPlayerView(
    media: PlayingMedia,
    secondaryMedia: List<PlayingMedia>,
    haToken: String,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVolumeChange: (String, Float) -> Unit,
    onSecondaryPlayPause: (PlayingMedia) -> Unit,
    onSecondaryPrevious: (PlayingMedia) -> Unit,
    onSecondaryNext: (PlayingMedia) -> Unit,
    onSelectSecondary: (PlayingMedia) -> Unit,
    onSwipePlayer: (Int) -> Unit,
    onJoinPlayer: (String) -> Unit,
    onUnjoinPlayer: (String) -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context.applicationContext)
            .okHttpClient {
                OkHttpClient.Builder()
                    .proxy(Proxy.NO_PROXY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(1_000)
            .build()
    }
    val imageRequest = remember(media.coverUrl, haToken) {
        media.coverUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .addHeader("Authorization", "Bearer $haToken")
                .crossfade(true)
                .build()
        }
    }
    val isPlaying = media.state == "playing" || media.state == "buffering"
    var volumePanelVisible by remember { mutableStateOf(false) }
    var groupDialogVisible by remember { mutableStateOf(false) }
    var selectedGroupMembers by remember(media.entityId) { mutableStateOf(media.groupMemberIds.toSet()) }
    LaunchedEffect(media.groupMemberIds) {
        selectedGroupMembers = media.groupMemberIds.toSet()
    }
    var horizontalDrag by remember(media.entityId) { mutableFloatStateOf(0f) }
    var swipeDirection by remember { mutableIntStateOf(0) }
    val swipeOffset = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    LaunchedEffect(secondaryMedia.isEmpty()) {
        if (secondaryMedia.isEmpty()) {
            horizontalDrag = 0f
            swipeDirection = 0
            swipeOffset.snapTo(0f)
        }
    }
    val fallbackSourceName = media.entityId.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
    val sourceName = when (media.playerNames.size) {
        0 -> fallbackSourceName
        1 -> media.playerNames.first()
        2 -> "${media.playerNames[0]} + ${media.playerNames[1]}"
        else -> stringResource(R.string.media_source_many_format, media.playerNames.first(), media.playerNames.size - 1)
    }

    if (volumePanelVisible) {
        MediaVolumePanel(
            players = media.players.ifEmpty { listOf(MediaPlayerVolume(media.entityId, sourceName, media.volumePercent, media.isMuted)) },
            onVolumeChange = onVolumeChange,
            onBack = { volumePanelVisible = false },
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val panelInset = if (fullScreen) 0.dp else {
            (minOf(availableWidth, availableHeight) * 0.03f).coerceIn(10.dp, 20.dp)
        }
        val availableWidthPx = with(LocalDensity.current) { availableWidth.toPx() }
        val wide = availableWidth > availableHeight
        val mediaDisclosure = mediaDisclosureFor(availableWidth.value, availableHeight.value)
        val mainPlayer: @Composable () -> Unit = {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
        val incomingMedia = when {
            swipeDirection > 0 -> secondaryMedia.firstOrNull()
            swipeDirection < 0 -> secondaryMedia.lastOrNull()
            else -> null
        }
        if (incomingMedia != null) {
            SwipeIncomingCard(
                media = incomingMedia,
                haToken = haToken,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = if (swipeDirection > 0) {
                        availableWidthPx + swipeOffset.value
                    } else {
                        -availableWidthPx + swipeOffset.value
                    }
                    rotationZ = if (swipeDirection > 0) 2.2f else -2.2f
                },
            )
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .graphicsLayer {
                    translationX = swipeOffset.value
                    rotationZ = (swipeOffset.value / availableWidthPx) * 2.2f
                    alpha = 1f - (kotlin.math.abs(swipeOffset.value) / availableWidthPx * 0.18f).coerceIn(0f, 0.18f)
                }
                .pointerInput(media.entityId, secondaryMedia.size) {
                    if (secondaryMedia.isEmpty()) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { horizontalDrag = 0f; swipeDirection = 0 },
                        onHorizontalDrag = { _, amount ->
                            horizontalDrag += amount
                            swipeDirection = if (horizontalDrag < 0f) 1 else -1
                            swipeScope.launch { swipeOffset.snapTo(horizontalDrag.coerceIn(-availableWidthPx, availableWidthPx)) }
                        },
                        onDragEnd = {
                            if (kotlin.math.abs(horizontalDrag) > 72f) {
                                val direction = if (horizontalDrag < 0f) 1 else -1
                                val exit = if (direction > 0) -availableWidthPx else availableWidthPx
                                swipeScope.launch {
                                    swipeOffset.animateTo(exit, tween(180))
                                    onSwipePlayer(direction)
                                    swipeOffset.snapTo(-exit)
                                    swipeOffset.animateTo(0f, tween(240))
                                    swipeDirection = 0
                                }
                            } else {
                                swipeScope.launch {
                                    swipeOffset.animateTo(0f, tween(180))
                                    swipeDirection = 0
                                }
                            }
                            horizontalDrag = 0f
                        },
                        onDragCancel = {
                            horizontalDrag = 0f
                            swipeScope.launch {
                                swipeOffset.animateTo(0f, tween(180))
                                swipeDirection = 0
                            }
                        },
                    )
                }
                .clip(AppleShapes.panel)
                .background(Color.Black.copy(alpha = 0.72f))
                .then(
                    if (fullScreen) Modifier
                    else Modifier.border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel)
                )
        ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(42.dp).alpha(0.34f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.16f),
                    0.48f to Color.Black.copy(alpha = 0.54f),
                    1f to Color.Black.copy(alpha = 0.94f),
                )
            )
        )

        if (wide) {
            Box(Modifier.fillMaxSize()) {
                val shortEdge = minOf(availableWidth, availableHeight)
                val outerInset = (shortEdge * 0.035f).coerceIn(10.dp, 24.dp)
                val contentGap = (shortEdge * 0.045f).coerceIn(14.dp, 30.dp)
                val artworkSize = minOf(
                    availableHeight - outerInset * 2,
                    availableWidth * 0.36f,
                )
                val artworkCorner = (artworkSize * 0.085f).coerceIn(14.dp, 28.dp)
                val sectionGap = (shortEdge * 0.025f).coerceIn(8.dp, 16.dp)
                val sourceFontSize = (shortEdge.value * 0.042f).coerceIn(17f, 23f).sp
                val titleFontSize = (shortEdge.value * 0.052f).coerceIn(20f, 29f).sp
                val artistFontSize = (shortEdge.value * 0.036f).coerceIn(14f, 19f).sp
                Row(
                    modifier = Modifier.fillMaxSize().padding(outerInset),
                    horizontalArrangement = Arrangement.spacedBy(contentGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .clip(RoundedCornerShape(artworkCorner))
                            .background(Color.White.copy(alpha = 0.07f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(artworkCorner)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                imageLoader = imageLoader,
            contentDescription = stringResource(R.string.media_cover_desc_format, media.title),
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else if (!media.hasMedia) {
                                            HaEntityIcon(
                                                entityId = media.entityId,
                                                contentDescription = stringResource(R.string.media_player_icon_desc_format, sourceName),
                                                tint = AppleColors.secondary,
                                                size = 64.dp,
                                                fallback = Icons.Outlined.MusicNote,
                                            )
                                        } else {
                                            Icon(Icons.Outlined.MusicNote, stringResource(R.string.media_no_cover_desc), tint = AppleColors.secondary, modifier = Modifier.size(64.dp))
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(sectionGap),
                    ) {
                        Row(
                            modifier = Modifier
                                .then(if (media.groupablePlayers.size > 1) Modifier.clickable { groupDialogVisible = true } else Modifier)
                                .padding(horizontal = 8.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(Modifier.size(7.dp).background(if (isPlaying) AppleColors.active else AppleColors.warning, CircleShape))
                            HaEntityIcon(
                                entityId = media.entityId,
                                contentDescription = null,
                                tint = if (isPlaying) AppleColors.active else AppleColors.warning,
                                size = 23.dp,
                                fallback = Icons.Outlined.MusicNote,
                            )
                            Text(
                                sourceName,
                                style = AppleTypography.titleLarge.copy(fontSize = sourceFontSize, fontWeight = FontWeight.SemiBold),
                                color = AppleColors.primary,
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        ) {
                            Text(media.title, style = AppleTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = titleFontSize), color = AppleColors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text(media.artist, style = AppleTypography.titleMedium.copy(fontSize = artistFontSize), color = AppleColors.primary.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            media.album?.takeIf { mediaDisclosure.showAlbum && it.isNotBlank() }?.let { album ->
                                Text(album, style = AppleTypography.bodySmall.copy(fontSize = 13.sp), color = AppleColors.secondary.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }

                        PortalThreeWayControl(
                            leadingIcon = Icons.Filled.SkipPrevious,
                            leadingContentDescription = stringResource(R.string.media_previous_track_desc),
                            onLeadingClick = onPrevious,
                            centerIcon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            centerContentDescription = if (isPlaying) stringResource(R.string.media_pause_desc) else stringResource(R.string.media_play_desc),
                            onCenterClick = onPlayPause,
                            trailingIcon = Icons.Filled.SkipNext,
                            trailingContentDescription = stringResource(R.string.media_next_track_desc),
                            onTrailingClick = onNext,
                            size = if (shortEdge < 560.dp) ThreeWayControlSize.Compact else ThreeWayControlSize.Regular,
                        )

                        Row(
                            modifier = Modifier
                                .clip(AppleShapes.pill)
                                .background(AppleColors.frostedFill, AppleShapes.pill)
                                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                                .clickable { volumePanelVisible = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(if (media.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, stringResource(R.string.media_volume_desc), tint = AppleColors.secondary, modifier = Modifier.size(18.dp))
                            Text(if (media.players.size > 1) stringResource(R.string.media_volume_multi_format, media.players.size) else stringResource(R.string.media_volume_single_format, media.volumePercent), style = AppleTypography.bodySmall.copy(fontSize = 12.sp), color = AppleColors.primary)
                        }
                    }
                }
                if (onDismiss != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AppleColors.frostedFill)
                            .border(0.5.dp, AppleColors.frostedBorder, CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.media_close_player_desc), tint = AppleColors.primary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else {
            val artworkRatio = if (secondaryMedia.isEmpty()) 0.43f else 0.34f
            val artworkSize = minOf(availableWidth - 56.dp, availableHeight * artworkRatio, 310.dp)
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (onDismiss != null) {
                    PanelHeader(
                        title = sourceName,
                        onNavigation = onDismiss,
                        navigationIcon = Icons.Filled.Close,
                        navigationContentDescription = stringResource(R.string.media_close_player_desc),
                        titleIcon = Icons.Outlined.MusicNote,
                        titleEntityId = media.entityId,
                        accent = if (isPlaying) AppleColors.active else AppleColors.warning,
                        onTitleClick = if (media.groupablePlayers.size > 1) ({ groupDialogVisible = true }) else null,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        HaEntityIcon(
                            entityId = media.entityId,
                            contentDescription = null,
                            tint = if (isPlaying) AppleColors.active else AppleColors.warning,
                            size = 23.dp,
                            fallback = Icons.Outlined.MusicNote,
                        )
                        Text(
                            sourceName,
                            style = AppleTypography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                            color = AppleColors.primary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(artworkSize)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                        if (imageRequest != null) {
                            AsyncImage(model = imageRequest, imageLoader = imageLoader, contentDescription = stringResource(R.string.media_cover_desc_format, media.title), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else if (!media.hasMedia) {
                            HaEntityIcon(
                                entityId = media.entityId,
                                contentDescription = stringResource(R.string.media_player_icon_desc_format, sourceName),
                                tint = AppleColors.secondary,
                                size = 82.dp,
                                fallback = Icons.Outlined.MusicNote,
                            )
                        } else {
                            Icon(Icons.Outlined.MusicNote, stringResource(R.string.media_no_cover_desc), tint = AppleColors.secondary, modifier = Modifier.size(82.dp))
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Text(media.title, style = AppleTypography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp), color = AppleColors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(media.artist, style = AppleTypography.titleMedium.copy(fontSize = 17.sp), color = AppleColors.primary.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    media.album?.takeIf { mediaDisclosure.showAlbum && it.isNotBlank() }?.let { album ->
                        Text(album, style = AppleTypography.bodySmall, color = AppleColors.secondary.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                PortalThreeWayControl(
                    leadingIcon = Icons.Filled.SkipPrevious,
                    leadingContentDescription = stringResource(R.string.media_previous_track_desc),
                    onLeadingClick = onPrevious,
                    centerIcon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    centerContentDescription = if (isPlaying) stringResource(R.string.media_pause_desc) else stringResource(R.string.media_play_desc),
                    onCenterClick = onPlayPause,
                    trailingIcon = Icons.Filled.SkipNext,
                    trailingContentDescription = stringResource(R.string.media_next_track_desc),
                    onTrailingClick = onNext,
                )

                Row(
                    modifier = Modifier
                        .clip(AppleShapes.pill)
                        .background(AppleColors.frostedFill, AppleShapes.pill)
                        .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                        .clickable { volumePanelVisible = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(if (media.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, stringResource(R.string.media_volume_desc), tint = AppleColors.secondary, modifier = Modifier.size(20.dp))
                    Text(if (media.players.size > 1) stringResource(R.string.media_volume_multi_format, media.players.size) else stringResource(R.string.media_volume_single_format, media.volumePercent), style = AppleTypography.bodySmall.copy(fontSize = 13.sp), color = AppleColors.primary)
                }
            }
        }
        }
        }
        }

        if (wide && secondaryMedia.isNotEmpty()) {
            // Strip mode: main player 66% wide, secondary sessions stacked in the last third.
            Row(modifier = Modifier.fillMaxSize().padding(panelInset), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(2f).fillMaxHeight()) { mainPlayer() }
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    secondaryMedia.take(mediaDisclosure.secondaryPlayerCount).forEach { session ->
                        MiniMediaPlayerVertical(
                            media = session,
                            haToken = haToken,
                            imageLoader = imageLoader,
                            onPlayPause = { onSecondaryPlayPause(session) },
                            onPrevious = { onSecondaryPrevious(session) },
                            onNext = { onSecondaryNext(session) },
                            onSelect = { onSelectSecondary(session) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (secondaryMedia.size > mediaDisclosure.secondaryPlayerCount) {
                        Text(
                            stringResource(R.string.media_secondary_more_format, secondaryMedia.size - mediaDisclosure.secondaryPlayerCount),
                            style = AppleTypography.bodySmall,
                            color = AppleColors.secondary,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(panelInset), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { mainPlayer() }
                secondaryMedia.take(mediaDisclosure.secondaryPlayerCount).forEach { session ->
                    MiniMediaPlayer(
                        media = session,
                        haToken = haToken,
                        imageLoader = imageLoader,
                        onPlayPause = { onSecondaryPlayPause(session) },
                        onPrevious = { onSecondaryPrevious(session) },
                        onNext = { onSecondaryNext(session) },
                        onSelect = { onSelectSecondary(session) },
                    )
                }
                if (secondaryMedia.size > mediaDisclosure.secondaryPlayerCount) {
                    Text(
                        stringResource(R.string.media_secondary_more_active_format, secondaryMedia.size - mediaDisclosure.secondaryPlayerCount),
                        style = AppleTypography.bodySmall,
                        color = AppleColors.secondary,
                    )
                }
            }
        }

        if (groupDialogVisible) {
            Dialog(onDismissRequest = { groupDialogVisible = false }) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xF21B1D20),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppleColors.frostedBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(stringResource(R.string.media_group_dialog_title), style = AppleTypography.headlineLarge.copy(fontSize = 24.sp), color = AppleColors.primary)
                        Text(stringResource(R.string.media_group_dialog_subtitle_format, sourceName), style = AppleTypography.bodySmall, color = AppleColors.secondary)
                        media.groupablePlayers.forEach { player ->
                            val isLeader = player.entityId == media.entityId
                            val isMember = isLeader || player.entityId in selectedGroupMembers
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable(enabled = !isLeader, role = Role.Checkbox) {
                                        if (isMember) {
                                            selectedGroupMembers = selectedGroupMembers - player.entityId
                                            onUnjoinPlayer(player.entityId)
                                        } else {
                                            selectedGroupMembers = selectedGroupMembers + player.entityId
                                            onJoinPlayer(player.entityId)
                                        }
                                    }
                                    .background(if (isMember) AppleColors.active.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.04f))
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier.size(20.dp).clip(CircleShape)
                                        .border(1.dp, if (isMember) AppleColors.active else AppleColors.secondary, CircleShape)
                                        .background(if (isMember) AppleColors.active else Color.Transparent),
                                )
                                Text(player.name, modifier = Modifier.weight(1f), style = AppleTypography.titleMedium, color = AppleColors.primary)
                                if (isLeader) Text(stringResource(R.string.media_group_leader_label), style = AppleTypography.bodySmall, color = AppleColors.secondary)
                            }
                        }
                        Text(
                            stringResource(R.string.media_group_dialog_close),
                            modifier = Modifier.align(Alignment.End).clip(AppleShapes.pill).clickable { groupDialogVisible = false }
                                .background(AppleColors.frostedFill).padding(horizontal = 18.dp, vertical = 10.dp),
                            style = AppleTypography.titleMedium,
                            color = AppleColors.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaVolumePanel(
    players: List<MediaPlayerVolume>,
    onVolumeChange: (String, Float) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember(players) { mutableStateOf(players.first().entityId) }
    val selected = players.firstOrNull { it.entityId == selectedId } ?: players.first()
    var position by remember(selected.entityId, selected.volumePercent) {
        mutableFloatStateOf(selected.volumePercent.toFloat())
    }
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 16.dp)
            .clip(AppleShapes.panel).background(Color.Black.copy(alpha = 0.76f))
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.14f), Color.Black.copy(alpha = 0.94f)))))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PanelHeader(
                title = stringResource(R.string.media_volume_dialog_title),
                onNavigation = onBack,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.playground_back_desc),
                titleIcon = if (selected.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                accent = if (position > 0f) AppleColors.accent else AppleColors.inactive,
            )
            if (players.size > 1) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    players.forEach { player ->
                        val active = player.entityId == selected.entityId
                        Row(
                            Modifier.clip(AppleShapes.pill)
                                .background(if (active) AppleColors.accent.copy(alpha = 0.20f) else AppleColors.frostedFill, AppleShapes.pill)
                                .border(0.5.dp, if (active) AppleColors.accent.copy(alpha = 0.65f) else AppleColors.frostedBorder, AppleShapes.pill)
                                .clickable { selectedId = player.entityId }
                                .padding(horizontal = 13.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Box(Modifier.size(7.dp).background(if (player.volumePercent > 0) AppleColors.accent else AppleColors.inactive, CircleShape))
                            Text(player.name, style = AppleTypography.bodySmall, color = if (active) AppleColors.primary else AppleColors.secondary, maxLines = 1)
                            Text("${player.volumePercent} %", style = AppleTypography.labelSmall, color = AppleColors.tertiary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(selected.name, style = AppleTypography.titleLarge, color = AppleColors.primary)
            Text("${position.toInt()} %", style = AppleTypography.headlineLarge.copy(fontSize = 34.sp), color = AppleColors.primary)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                VerticalFillSlider(
                    value = position,
                    onValueChange = { position = it },
                    valueRange = 0f..100f,
                    accent = AppleColors.accent,
                    icon = if (position <= 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                    label = { "${it.toInt()} %" },
                    hapticSteps = 20,
                    onValueChangeFinished = { onVolumeChange(selected.entityId, it / 100f) },
                    modifier = Modifier.controlSize(104.dp),
                )
            }
        }
    }
}

@Composable
private fun SwipeIncomingCard(
    media: PlayingMedia,
    haToken: String,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(media.coverUrl, haToken) {
        media.coverUrl?.let {
            ImageRequest.Builder(context).data(it).addHeader("Authorization", "Bearer $haToken").build()
        }
    }
    val rooms = when (media.playerNames.size) {
        0 -> media.entityId.substringAfter('.').replace('_', ' ')
        1 -> media.playerNames.first()
        2 -> "${media.playerNames[0]} + ${media.playerNames[1]}"
        else -> stringResource(R.string.media_source_many_format, media.playerNames.first(), media.playerNames.size - 1)
    }
    Box(
        modifier = modifier.clip(AppleShapes.panel).background(Color.Black.copy(alpha = 0.86f))
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.panel),
    ) {
        if (request != null) AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(42.dp).alpha(0.3f),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.92f)))))
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(rooms, style = AppleTypography.bodySmall, color = AppleColors.secondary)
            Box(
                modifier = Modifier.padding(vertical = 18.dp).fillMaxWidth(0.86f).weight(1f, fill = false)
                    .clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (request != null) AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) else Icon(Icons.Outlined.MusicNote, null, tint = AppleColors.secondary, modifier = Modifier.size(72.dp))
            }
            Text(media.title, style = AppleTypography.headlineLarge.copy(fontSize = 25.sp), color = AppleColors.primary, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(media.artist, style = AppleTypography.titleMedium, color = AppleColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MiniMediaPlayer(
    media: PlayingMedia,
    haToken: String,
    imageLoader: ImageLoader,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(media.coverUrl, haToken) {
        media.coverUrl?.let {
            ImageRequest.Builder(context).data(it).addHeader("Authorization", "Bearer $haToken").crossfade(true).build()
        }
    }
    val rooms = when (media.playerNames.size) {
        0 -> media.entityId.substringAfter('.').replace('_', ' ')
        1 -> media.playerNames.first()
        2 -> "${media.playerNames[0]} + ${media.playerNames[1]}"
        else -> "${media.playerNames.first()} + ${media.playerNames.size - 1} autres"
    }
    val playing = media.state == "playing" || media.state == "buffering"
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp))
            .background(AppleColors.frostedFill).border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (request != null) AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) else Icon(Icons.Outlined.MusicNote, null, tint = AppleColors.secondary, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(rooms, style = AppleTypography.bodySmall.copy(fontSize = 11.sp), color = AppleColors.secondary, maxLines = 1)
            Text(media.title, style = AppleTypography.titleMedium.copy(fontSize = 14.sp), color = AppleColors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.artist, style = AppleTypography.bodySmall.copy(fontSize = 11.sp), color = AppleColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.media_mini_previous_desc_format, rooms), tint = AppleColors.primary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp).background(Color.White, CircleShape)) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (playing) stringResource(R.string.media_mini_pause_desc_format, rooms) else stringResource(R.string.media_mini_play_desc_format, rooms),
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
            Icon(Icons.Filled.SkipNext, stringResource(R.string.media_mini_next_desc_format, rooms), tint = AppleColors.primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Compact vertical card for a secondary session, used in the 33% column of strip mode. */
@Composable
private fun MiniMediaPlayerVertical(
    media: PlayingMedia,
    haToken: String,
    imageLoader: ImageLoader,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(media.coverUrl, haToken) {
        media.coverUrl?.let {
            ImageRequest.Builder(context).data(it).addHeader("Authorization", "Bearer $haToken").crossfade(true).build()
        }
    }
    val rooms = when (media.playerNames.size) {
        0 -> media.entityId.substringAfter('.').replace('_', ' ')
        1 -> media.playerNames.first()
        2 -> "${media.playerNames[0]} + ${media.playerNames[1]}"
        else -> "${media.playerNames.first()} + ${media.playerNames.size - 1} autres"
    }
    val playing = media.state == "playing" || media.state == "buffering"
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(AppleColors.frostedFill).border(0.5.dp, AppleColors.frostedBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Square cover bounded by both card width and remaining height, centered.
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (request != null) AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                ) else Icon(Icons.Outlined.MusicNote, null, tint = AppleColors.secondary, modifier = Modifier.size(28.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rooms, style = AppleTypography.bodySmall.copy(fontSize = 11.sp), color = AppleColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(media.title, style = AppleTypography.titleMedium.copy(fontSize = 14.sp), color = AppleColors.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(media.artist, style = AppleTypography.bodySmall.copy(fontSize = 11.sp), color = AppleColors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.SkipPrevious, stringResource(R.string.media_vertical_previous_desc_format, rooms), tint = AppleColors.primary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(38.dp).background(Color.White, CircleShape)) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) stringResource(R.string.media_vertical_pause_desc_format, rooms) else stringResource(R.string.media_vertical_play_desc_format, rooms),
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Filled.SkipNext, stringResource(R.string.media_vertical_next_desc_format, rooms), tint = AppleColors.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}
