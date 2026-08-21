package com.iblu01.portallauncher.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.home.CameraCapabilities
import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraStreamFormat
import com.iblu01.portallauncher.domain.home.CameraSupport
import com.iblu01.portallauncher.domain.home.PtzAction
import com.iblu01.portallauncher.ui.components.appleClickable
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography

/** Everything the centre needs from the rest of the launcher, as plain values and callbacks. */
class CameraCenterEnvironment(
    val entityOf: (String) -> HaEntity?,
    val labelOf: (String) -> String,
    val resolver: CameraStreamResolver,
    val token: String,
    val capabilitiesOf: (HaEntity) -> CameraCapabilities,
    val onPtz: (CameraCapabilities, String, PtzAction) -> Unit,
)

/**
 * The camera centre: a full-surface page drawn over the launcher.
 *
 * It occupies the whole window on every supported size — phone, tablet and wall panel — because
 * the video is the content, not a card inside a page. Its layout adapts to the container it is
 * given rather than to a device name: the controls sit beside the picture when the surface is
 * wide, and under it when it is tall.
 *
 * Closing it removes the whole subtree, and every player is scoped to that subtree, so no stream
 * and no connection can outlive the centre.
 */
@Composable
fun CameraCenter(
    state: CameraCenterState,
    environment: CameraCenterEnvironment,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onHide: (String) -> Unit,
    onMode: (CameraCenterMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var cameraPendingHide by remember { mutableStateOf<String?>(null) }
    AnimatedVisibility(
        visible = state.isOpen,
        modifier = modifier,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        val open = state.open
        Box(
            Modifier
                .fillMaxSize()
                .background(AppleColors.background)
                // The centre is opaque and full-surface: nothing behind it may receive a tap.
                .appleClickable {},
        ) {
            if (open == null) return@Box
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val wide = maxWidth > maxHeight
                Column(Modifier.fillMaxSize().padding(12.dp)) {
                    CameraCenterHeader(
                        mode = open.mode,
                        onMode = onMode,
                        onClose = onClose,
                        canSwitchMode = open.cameras.size > 1,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (open.mode) {
                            CameraCenterMode.MAIN -> MainCameraView(
                                open = open,
                                environment = environment,
                                wide = wide,
                                onSelect = onSelect,
                                onHide = { cameraPendingHide = it },
                            )
                            CameraCenterMode.GRID -> CameraGrid(
                                cameras = open.cameras,
                                environment = environment,
                                columns = if (wide) 3 else 2,
                                onSelect = onSelect,
                            )
                        }
                    }
                }
            }
            cameraPendingHide?.let { entityId ->
                val label = environment.labelOf(entityId)
                AlertDialog(
                    onDismissRequest = { cameraPendingHide = null },
                    title = { Text(stringResource(R.string.camera_hide_confirm_title)) },
                    text = { Text(stringResource(R.string.camera_hide_confirm_message, label)) },
                    confirmButton = {
                        TextButton(onClick = {
                            cameraPendingHide = null
                            onHide(entityId)
                        }) {
                            Text(stringResource(R.string.camera_hide_confirm_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { cameraPendingHide = null }) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraCenterHeader(
    mode: CameraCenterMode,
    onMode: (CameraCenterMode) -> Unit,
    onClose: () -> Unit,
    canSwitchMode: Boolean,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.camera_center_title),
            style = AppleTypography.titleMedium,
            color = AppleColors.primary,
            modifier = Modifier.weight(1f),
        )
        if (canSwitchMode) {
            IconAction(
                icon = Icons.Outlined.Videocam,
                description = stringResource(R.string.camera_center_mode_main_desc),
                active = mode == CameraCenterMode.MAIN,
                onClick = { onMode(CameraCenterMode.MAIN) },
            )
            IconAction(
                icon = Icons.Outlined.GridView,
                description = stringResource(R.string.camera_center_mode_grid_desc),
                active = mode == CameraCenterMode.GRID,
                onClick = { onMode(CameraCenterMode.GRID) },
            )
        }
        IconAction(
            icon = Icons.Outlined.Close,
            description = stringResource(R.string.camera_center_close_desc),
            onClick = onClose,
        )
    }
}

@Composable
private fun MainCameraView(
    open: CameraCenterState.Open,
    environment: CameraCenterEnvironment,
    wide: Boolean,
    onSelect: (String) -> Unit,
    onHide: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraTile(
                entityId = open.selected,
                environment = environment,
                showControls = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Only one camera: no picker, and nothing spends a slot on it.
        if (open.cameras.size > 1) {
            Spacer(Modifier.height(8.dp))
            CameraPicker(
                cameras = open.cameras,
                selected = open.selected,
                environment = environment,
                onSelect = onSelect,
                onHide = onHide,
                compact = !wide,
            )
        }
    }
}

/**
 * Name-only chips rather than live thumbnails: a picker that previewed every camera would keep as
 * many players alive as the grid does, which is exactly what the main mode exists to avoid.
 */
@Composable
private fun CameraPicker(
    cameras: List<String>,
    selected: String,
    environment: CameraCenterEnvironment,
    onSelect: (String) -> Unit,
    onHide: (String) -> Unit,
    compact: Boolean,
) {
    val scroll = rememberScrollState()
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cameras.forEach { entityId ->
            val label = environment.labelOf(entityId)
            val active = entityId == selected
            val hideDescription = stringResource(R.string.camera_center_hide_desc, label)
            Text(
                text = label,
                style = if (compact) AppleTypography.bodySmall else AppleTypography.bodyMedium,
                color = if (active) AppleColors.primary else AppleColors.secondary,
                modifier = Modifier
                    .clip(AppleShapes.pill)
                    .background(
                        if (active) AppleColors.frostedFill else Color.Transparent,
                        AppleShapes.pill,
                    )
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                    .appleClickable(
                        onClick = { onSelect(entityId) },
                        onLongPress = { onHide(entityId) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics {
                        contentDescription = label
                        onLongClick(label = hideDescription) {
                            onHide(entityId)
                            true
                        }
                    },
            )
        }
    }
}

/**
 * Only the tiles the grid actually composes hold a player, and a tile leaving the composition
 * releases it — so scrolling a camera off screen stops its stream instead of leaving it decoding.
 */
@Composable
private fun CameraGrid(
    cameras: List<String>,
    environment: CameraCenterEnvironment,
    columns: Int,
    onSelect: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cameras, key = { it }) { entityId ->
            val label = environment.labelOf(entityId)
            val description = stringResource(R.string.camera_center_select_desc, label)
            Box(
                Modifier
                    .aspectRatio(16f / 9f)
                    .clip(AppleShapes.section)
                    .appleClickable { onSelect(entityId) }
                    .semantics { contentDescription = description },
            ) {
                CameraTile(
                    entityId = entityId,
                    environment = environment,
                    showControls = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * One camera, with its own lifecycle: loading, playing, unavailable and error are distinct states
 * and each says something the user can act on.
 */
@Composable
private fun CameraTile(
    entityId: String,
    environment: CameraCenterEnvironment,
    showControls: Boolean,
    modifier: Modifier = Modifier,
) {
    val entity = environment.entityOf(entityId)
    val label = environment.labelOf(entityId)
    var attempt by remember(entityId) { mutableStateOf(0) }
    var state by remember(entityId) { mutableStateOf<CameraStreamState>(CameraStreamState.Loading) }
    var failedFormats by remember(entityId) { mutableStateOf(emptySet<CameraStreamFormat>()) }
    var muted by remember(entityId) { mutableStateOf(true) }
    var hasAudio by remember(entityId) { mutableStateOf(false) }

    LaunchedEffect(entityId, attempt, failedFormats) {
        state = CameraStreamState.Loading
        if (!CameraSupport.isAvailable(entity)) {
            state = CameraStreamState.Failed(CameraStreamError.UNAVAILABLE)
            return@LaunchedEffect
        }
        val source = environment.resolver.resolve(requireNotNull(entity), failedFormats)
        state = if (source == null) {
            CameraStreamState.Failed(CameraStreamError.UNREACHABLE)
        } else {
            CameraStreamState.Playing(source, hasAudio = false)
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        when (val current = state) {
            CameraStreamState.Loading -> LoadingStream()

            is CameraStreamState.Failed -> StreamFailure(
                error = current.error,
                label = label,
                onRetry = {
                    // A retry starts over from the best format: the previous failure may well have
                    // been the network rather than the format itself.
                    failedFormats = emptySet()
                    attempt++
                },
            )

            is CameraStreamState.Playing -> {
                val onPlaybackError: () -> Unit = {
                    val exhausted = failedFormats + current.source.format
                    if (exhausted.size >= CameraStreamFormat.values().size) {
                        state = CameraStreamState.Failed(CameraStreamError.PLAYBACK)
                    } else {
                        // Fall back to the next format rather than declaring the camera broken.
                        failedFormats = exhausted
                    }
                }
                when (val source = current.source) {
                    is CameraStreamSource.Hls -> HlsCameraPlayer(
                        url = source.url,
                        muted = muted,
                        modifier = Modifier.fillMaxSize(),
                        onAudioTrackDetected = { hasAudio = it },
                        onError = onPlaybackError,
                    )
                    is CameraStreamSource.Mjpeg -> {
                        // MJPEG is a sequence of images: there is no audio track to control.
                        LaunchedEffect(source.url) { hasAudio = false }
                        MjpegCameraPlayer(
                            url = source.url,
                            token = environment.token,
                            contentDescription = label,
                            modifier = Modifier.fillMaxSize(),
                            onError = onPlaybackError,
                        )
                    }
                }
                if (showControls) {
                    CameraOverlayControls(
                        entityId = entityId,
                        entity = entity,
                        environment = environment,
                        hasAudio = hasAudio,
                        muted = muted,
                        onMuteToggle = { muted = !muted },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingStream() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(30.dp),
            color = AppleColors.primary,
            strokeWidth = 2.5.dp,
        )
        StatusMessage(stringResource(R.string.camera_stream_loading))
    }
}

@Composable
private fun CameraOverlayControls(
    entityId: String,
    entity: HaEntity?,
    environment: CameraCenterEnvironment,
    hasAudio: Boolean,
    muted: Boolean,
    onMuteToggle: () -> Unit,
) {
    val capabilities = remember(entityId, entity) {
        entity?.let(environment.capabilitiesOf)
    }
    Box(Modifier.fillMaxSize().padding(12.dp)) {
        // A stream with no audio track shows no control at all rather than a lying one.
        if (hasAudio) {
            IconAction(
                icon = if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                description = stringResource(
                    if (muted) R.string.camera_audio_unmute_desc else R.string.camera_audio_mute_desc,
                ),
                onClick = onMuteToggle,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        // Only the movements this very camera really supports; a fixed camera gets nothing.
        if (capabilities != null && capabilities.supportsPtz) {
            PtzPad(
                capabilities = capabilities,
                onAction = { action -> environment.onPtz(capabilities, entityId, action) },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

@Composable
private fun PtzPad(
    capabilities: CameraCapabilities,
    onAction: (PtzAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun control(action: PtzAction, icon: ImageVector, description: Int) {
        if (action in capabilities.ptz) {
            IconAction(
                icon = icon,
                description = stringResource(description),
                onClick = { onAction(action) },
            )
        } else {
            Spacer(Modifier.size(44.dp))
        }
    }

    val directionalActions = capabilities.ptz.intersect(
        setOf(PtzAction.PAN_LEFT, PtzAction.PAN_RIGHT, PtzAction.TILT_UP, PtzAction.TILT_DOWN),
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (directionalActions.isNotEmpty()) {
            PtzJoystick(
                actions = directionalActions,
                onAction = onAction,
            )
        }
        if (PtzAction.ZOOM_IN in capabilities.ptz || PtzAction.ZOOM_OUT in capabilities.ptz) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                control(PtzAction.ZOOM_OUT, Icons.Outlined.ZoomOut, R.string.camera_ptz_zoom_out_desc)
                control(PtzAction.ZOOM_IN, Icons.Outlined.ZoomIn, R.string.camera_ptz_zoom_in_desc)
            }
        }
    }
}

/** A compact spring-loaded joystick: releasing it sends one movement on the dominant axis. */
@Composable
private fun PtzJoystick(
    actions: Set<PtzAction>,
    onAction: (PtzAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var knob by remember { mutableStateOf(Offset.Zero) }
    val description = listOfNotNull(
        stringResource(R.string.camera_ptz_left_desc).takeIf { PtzAction.PAN_LEFT in actions },
        stringResource(R.string.camera_ptz_right_desc).takeIf { PtzAction.PAN_RIGHT in actions },
        stringResource(R.string.camera_ptz_up_desc).takeIf { PtzAction.TILT_UP in actions },
        stringResource(R.string.camera_ptz_down_desc).takeIf { PtzAction.TILT_DOWN in actions },
    ).joinToString(" · ")

    Canvas(
        modifier
            .size(112.dp)
            .semantics { contentDescription = description }
            .pointerInput(actions) {
                val limit = minOf(size.width, size.height).toFloat() * 0.28f
                detectDragGestures(
                    onDragStart = { knob = Offset.Zero },
                    onDragCancel = { knob = Offset.Zero },
                    onDragEnd = {
                        val threshold = limit * 0.35f
                        val action = if (kotlin.math.abs(knob.x) >= kotlin.math.abs(knob.y)) {
                            if (knob.x < -threshold) PtzAction.PAN_LEFT
                            else if (knob.x > threshold) PtzAction.PAN_RIGHT else null
                        } else {
                            if (knob.y < -threshold) PtzAction.TILT_UP
                            else if (knob.y > threshold) PtzAction.TILT_DOWN else null
                        }
                        if (action != null && action in actions) onAction(action)
                        knob = Offset.Zero
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val candidate = knob + amount
                        val distance = hypot(candidate.x, candidate.y)
                        knob = if (distance <= limit || distance == 0f) candidate
                        else candidate * (limit / distance)
                    },
                )
            },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(AppleColors.elevated.copy(alpha = 0.9f), radius = size.minDimension * 0.48f)
        drawCircle(AppleColors.frostedBorder, radius = size.minDimension * 0.48f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        drawCircle(AppleColors.primary.copy(alpha = 0.88f), radius = size.minDimension * 0.18f, center = center + knob)
    }
}

@Composable
private fun StreamFailure(
    error: CameraStreamError,
    label: String,
    onRetry: () -> Unit,
) {
    val message = when (error) {
        CameraStreamError.UNAVAILABLE -> R.string.camera_stream_unavailable
        CameraStreamError.UNREACHABLE, CameraStreamError.PLAYBACK -> R.string.camera_stream_error
    }
    val retryDescription = stringResource(R.string.camera_stream_retry_desc, label)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusMessage(stringResource(message))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(AppleShapes.pill)
                .background(AppleColors.frostedFill, AppleShapes.pill)
                .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                .appleClickable(onRetry)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { contentDescription = retryDescription },
        ) {
            Icon(Icons.Outlined.Refresh, null, tint = AppleColors.primary, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.camera_stream_retry),
                style = AppleTypography.bodyMedium,
                color = AppleColors.primary,
            )
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        style = AppleTypography.bodyMedium,
        color = AppleColors.secondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    active: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(AppleShapes.pill)
            .background(AppleColors.frostedFill, AppleShapes.pill)
            .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
            .appleClickable(onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) AppleColors.accent else AppleColors.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}
