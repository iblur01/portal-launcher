package com.iblu01.portallauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iblu01.portallauncher.LauncherChip
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.domain.home.HomeComposition
import com.iblu01.portallauncher.domain.home.Availability
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.clockFontFamily
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.scaled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun ClockScreen(
    backgroundMode: String,
    weather: WeatherUi,
    temperatures: TemperatureSummary,
    chips: List<LauncherChip>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
    onChipClick: (LauncherChip) -> Unit = {},
    onChipLongPress: (LauncherChip) -> Unit = {},
    selectedChipKey: String? = null,
    onWeatherClick: () -> Unit = {},
    connected: Boolean = true,
    lastUpdateAt: Long = 0L,
    modifier: Modifier = Modifier,
    drawBackground: Boolean = true,
    clockTheme: ClockTheme = ClockTheme(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            }
    ) {
        if (drawBackground) {
            AmbientBackground(mode = backgroundMode, modifier = Modifier.fillMaxSize())

            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f))
            )

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
            )
        }

        ClockHeader(
            weather = weather,
            temperatures = temperatures,
            onWeatherClick = onWeatherClick,
            connected = connected,
            lastUpdateAt = lastUpdateAt,
            clockTheme = clockTheme,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        ClockTray(
            chips = chips,
            pillsExpanded = pillsExpanded,
            onPillsExpandedChange = onPillsExpandedChange,
            onChipClick = onChipClick,
            onChipLongPress = onChipLongPress,
            selectedChipKey = selectedChipKey,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (!connected) {
            ConnectionProblemBanner(
                lastUpdateAt = lastUpdateAt,
                usesMdnsAddress = false,
                onClick = onTap,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

/** Collapsed header height reserved on the apps page, i.e. the clock at [ClockCollapsedScale]. */
val ClockHeaderCollapsedHeight = 92.dp

/** Scale the header shrinks to when the pager is fully on the apps page. */
const val ClockCollapsedScale = 0.34f
private val COMPACT_DATE_LIFT = 28.dp

/** Decorative clock details disappear before the header reaches its compact state. */
internal fun clockDetailAlpha(collapse: Float): Float =
    (1f - collapse.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)

private const val COMPACT_SCREEN_MAX_DIAGONAL_INCHES = 6f

internal fun isCompactClockScreen(
    widthPixels: Int,
    heightPixels: Int,
    xdpi: Float,
    ydpi: Float,
): Boolean {
    if (widthPixels <= 0 || heightPixels <= 0 || xdpi <= 0f || ydpi <= 0f) return false
    val widthInches = widthPixels / xdpi
    val heightInches = heightPixels / ydpi
    return sqrt(widthInches * widthInches + heightInches * heightInches) <=
        COMPACT_SCREEN_MAX_DIAGONAL_INCHES
}

@Composable
private fun rememberCompactClockScreen(): Boolean {
    val metrics = LocalContext.current.resources.displayMetrics
    return isCompactClockScreen(
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        xdpi = metrics.xdpi,
        ydpi = metrics.ydpi,
    )
}

/**
 * The clock block (date, time and weather pill). Pinned above the pager, it shrinks
 * toward the top as [collapse] goes 0→1 so the apps grid gets the room back.
 *
 * The shrink is a pure [graphicsLayer] transform (GPU, no relayout) — the Portal is API 28 with a
 * weak GPU, so per-frame text remeasuring during a drag would drop frames.
 */
@Composable
fun ClockHeader(
    weather: WeatherUi,
    temperatures: TemperatureSummary,
    onWeatherClick: () -> Unit,
    connected: Boolean,
    lastUpdateAt: Long,
    clockTheme: ClockTheme,
    modifier: Modifier = Modifier,
    collapse: () -> Float = { 0f },
) {

    val time by rememberClock(if (clockTheme.format24h) "HH:mm" else "h:mm a")
    val date by rememberClock(clockTheme.dateFormat.fullPattern)
    val compactDate by rememberClock(clockTheme.dateFormat.compactPattern)
    val compactScreen = rememberCompactClockScreen()
    val useCompactTemperatureHeader = connected && compactScreen
    val compactTemperaturesAvailable = compactIndoorTemperature(
        temperatures.indoorMin,
        temperatures.indoorMax,
    ) != null || compactOutdoorTemperature(temperatures.outdoor, weather.temp) != null
    Column(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - (1f - ClockCollapsedScale) * collapse()
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scale
                scaleY = scale
            }
            .padding(top = if (compactScreen) 24.dp else 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val timeWeight = FontWeight(clockTheme.weight)
        Row(
            modifier = Modifier.graphicsLayer { alpha = clockDetailAlpha(collapse()) },
            horizontalArrangement = Arrangement.spacedBy(
                if (useCompactTemperatureHeader && compactTemperaturesAvailable) 7.dp else 0.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleCase(if (useCompactTemperatureHeader) compactDate else date)
                    .uppercase(Locale.getDefault()),
                style = AppleTypography.titleMedium.copy(
                    fontFamily = clockFontFamily(clockTheme.font, FontWeight.Medium),
                    fontWeight = FontWeight.Medium,
                    fontSize = if (useCompactTemperatureHeader) 15.sp else 20.sp,
                    letterSpacing = if (useCompactTemperatureHeader) 1.35.sp else 2.3.sp,
                ),
                color = clockTheme.tint.color.copy(alpha = 0.7f),
            )
            if (useCompactTemperatureHeader && compactTemperaturesAvailable) {
                Text("•", style = AppleTypography.bodySmall.copy(fontSize = 10.sp), color = AppleColors.secondary)
                CompactTemperatures(
                    temperatures = temperatures,
                    weather = weather,
                    onClick = onWeatherClick,
                )
            }
        }
        Spacer(Modifier.height((if (compactScreen) 0.dp else 2.dp) * clockTheme.elementSpacing))
        val timeStyle = AppleTypography.displayLarge.copy(
                fontFamily = clockFontFamily(clockTheme.font, timeWeight),
                fontSize = clockTheme.size.sp,
                fontWeight = timeWeight,
                letterSpacing = clockTheme.letterSpacing.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.35f),
                    offset = Offset(0f, 2f),
                    blurRadius = 12f
                )
            )
        val timeParts = time.split(':', limit = 2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Reclaim the date's visual slot as it fades, so the minimal time sits at the same top
            // position it would have had if the date were not in the layout at all.
            modifier = Modifier
                .graphicsLayer {
                    translationY = -COMPACT_DATE_LIFT.toPx() * collapse().coerceIn(0f, 1f)
                }
                .clearAndSetSemantics { contentDescription = time },
        ) {
            Text(
                timeParts.first(),
                style = timeStyle,
                color = clockTheme.tint.color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            if (timeParts.size == 2) {
                val fontScale = LocalDensity.current.fontScale
                val colonWidth = (clockTheme.size * fontScale * 0.18f).dp
                val colonHeight = (clockTheme.size * fontScale * 0.64f).dp
                Canvas(Modifier.size(colonWidth, colonHeight)) {
                    val radius = size.width * 0.30f
                    drawCircle(clockTheme.tint.color, radius, Offset(size.width / 2f, size.height * 0.34f))
                    drawCircle(clockTheme.tint.color, radius, Offset(size.width / 2f, size.height * 0.66f))
                }
                val trailing = timeParts.last()
                val suffixStart = trailing.lastIndexOf(' ').takeIf { it >= 0 }
                val minute = suffixStart?.let { trailing.substring(0, it) } ?: trailing
                val dayPeriod = suffixStart?.let { trailing.substring(it + 1) }
                Text(
                    minute,
                    style = timeStyle,
                    color = clockTheme.tint.color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                if (!dayPeriod.isNullOrBlank()) {
                    Text(
                        dayPeriod,
                        style = timeStyle.copy(
                            fontSize = (clockTheme.size * 0.28f).sp,
                            letterSpacing = 0.sp,
                        ),
                        color = clockTheme.tint.color.copy(alpha = 0.78f),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .alignByBaseline()
                            .padding(start = 6.dp),
                    )
                }
            }
        }
        // Keep these nodes composed throughout the gesture. Removing them when alpha reaches zero
        // causes a structural recomposition and a text remeasure exactly halfway through the swipe.
        if (connected && !useCompactTemperatureHeader) {
            Spacer(Modifier.height(8.dp * clockTheme.elementSpacing))
            Row(
                modifier = Modifier
                    .graphicsLayer { alpha = clockDetailAlpha(collapse()) }
                    .clip(AppleShapes.pill)
                    .background(Color.White.copy(alpha = 0.15f), AppleShapes.pill)
                    .border(0.5.dp, AppleColors.frostedBorder, AppleShapes.pill)
                    .appleClickable(onWeatherClick)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.clock_indoor_temp_format, temperatures.indoorMin, temperatures.indoorMax), style = AppleTypography.bodySmall.copy(fontSize = 15.sp), color = AppleColors.primary)
                Text(stringResource(R.string.clock_outdoor_temp_format, temperatures.outdoor.takeUnless { it == "—" } ?: weather.temp), style = AppleTypography.bodySmall.copy(fontSize = 15.sp), color = AppleColors.secondary)
            }
        }
    }
}

@Composable
private fun CompactTemperatures(
    temperatures: TemperatureSummary,
    weather: WeatherUi,
    onClick: () -> Unit,
) {
    val indoorTemperature = compactIndoorTemperature(temperatures.indoorMin, temperatures.indoorMax)
    val outdoorTemperature = compactOutdoorTemperature(temperatures.outdoor, weather.temp)
    val indoorDescription = indoorTemperature?.let {
        stringResource(R.string.clock_indoor_temp_format, temperatures.indoorMin, temperatures.indoorMax)
    }
    val outdoorDescription = outdoorTemperature?.let {
        stringResource(R.string.clock_outdoor_temp_format, it)
    }
    Row(
        modifier = Modifier
            .appleClickable(onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(indoorDescription, outdoorDescription).joinToString(", ")
            },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (indoorTemperature != null) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = AppleColors.primary,
                modifier = Modifier.size(11.dp),
            )
            Text(
                indoorTemperature,
                style = AppleTypography.bodySmall.copy(fontSize = 15.sp),
                color = AppleColors.primary,
                maxLines = 1,
            )
        }
        if (outdoorTemperature != null) {
            WeatherIcon(weather.glyph, Modifier.size(14.dp))
            Text(
                outdoorTemperature,
                style = AppleTypography.bodySmall.copy(fontSize = 15.sp),
                color = AppleColors.secondary,
                maxLines = 1,
            )
        }
    }
}

private fun isTemperatureAvailable(value: String): Boolean =
    value.isNotBlank() && value !in setOf("—", "--", "—°", "--°")

internal fun compactIndoorTemperature(minimum: String, maximum: String): String? = when {
    !isTemperatureAvailable(minimum) && !isTemperatureAvailable(maximum) -> null
    !isTemperatureAvailable(minimum) -> maximum
    !isTemperatureAvailable(maximum) -> minimum
    minimum == maximum -> minimum
    else -> "${minimum.removeSuffix("°")}–$maximum"
}

internal fun compactOutdoorTemperature(outdoor: String, weather: String): String? =
    outdoor.takeIf(::isTemperatureAvailable) ?: weather.takeIf(::isTemperatureAvailable)

internal fun compactTrayItemLimit(compactScreen: Boolean, expanded: Boolean): Int = when {
    !expanded -> 3
    compactScreen -> 6
    else -> 9
}

/** The bottom chip tray: the "voir plus" toggle plus up to 3 (collapsed) or 9 (expanded) chips. */
@Composable
fun ClockTray(
    chips: List<LauncherChip>,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
    onChipClick: (LauncherChip) -> Unit,
    onChipLongPress: (LauncherChip) -> Unit,
    selectedChipKey: String?,
    modifier: Modifier = Modifier,
) {
    val compactScreen = rememberCompactClockScreen()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp.scaled())
            .padding(bottom = (if (compactScreen) 40.dp else 36.dp).scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((if (compactScreen) 4.dp else 8.dp).scaled()),
    ) {
        if (chips.size > 3) {
            ClockTrayControls(
                showExpand = chips.size > 3,
                pillsExpanded = pillsExpanded,
                onPillsExpandedChange = onPillsExpandedChange,
            )
        }
        val visible = chips.take(compactTrayItemLimit(compactScreen, pillsExpanded))
        visible.chunked(3).forEach { rowChips ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp.scaled(), Alignment.CenterHorizontally)) {
                rowChips.forEach { chip ->
                    StatusChip(chip, selected = chip.id == selectedChipKey, onClick = { onChipClick(chip) }, onLongPress = { onChipLongPress(chip) })
                }
            }
        }
    }
}

/**
 * Maison-aware tray. The composition has already applied alert/pin/dynamic precedence; this
 * composable only renders its primary and secondary projections and reports interaction intents.
 */
@Composable
fun ClockTray(
    composition: HomeComposition,
    pinnedRefs: Set<PillRef>,
    manualGroups: List<ManualGroupMenuOption>,
    actions: HomePillActions,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
    selectedChipKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val compactScreen = rememberCompactClockScreen()
    val allVisible = composition.primary + composition.secondary
    var menuTargetKey by rememberSaveable { mutableStateOf<String?>(null) }
    val menuTarget = allVisible.firstOrNull { it.ref.stableKey == menuTargetKey }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp.scaled())
            .padding(bottom = (if (compactScreen) 40.dp else 36.dp).scaled()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((if (compactScreen) 4.dp else 8.dp).scaled()),
    ) {
        if (composition.secondary.isNotEmpty()) {
            ClockTrayControls(
                showExpand = composition.secondary.isNotEmpty(),
                pillsExpanded = pillsExpanded,
                onPillsExpandedChange = onPillsExpandedChange,
            )
        }
        val visible = if (pillsExpanded) {
            val totalLimit = compactTrayItemLimit(compactScreen, expanded = true)
            composition.primary + composition.secondary.take((totalLimit - composition.primary.size).coerceAtLeast(0))
        } else {
            composition.primary
        }
        // Single stable callback for every pill: capturing `pill` per item would rebuild the
        // lambda on each pass and defeat TrayPill's equality skip.
        val openMenu: (String) -> Unit = { key -> menuTargetKey = key }
        visible.chunked(3).forEach { rowPills ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp.scaled(), Alignment.CenterHorizontally)) {
                rowPills.forEach { pill ->
                    TrayPill(
                        pill = pill,
                        pinned = pill.ref in pinnedRefs,
                        selected = pill.chip.id == selectedChipKey,
                        actions = actions,
                        onOpenMenu = openMenu,
                    )
                }
            }
        }
    }

    HomePillContextMenu(
        target = menuTarget,
        isPinned = menuTarget?.ref in pinnedRefs,
        manualGroups = manualGroups,
        actions = actions,
        onDismiss = { menuTargetKey = null },
    )
}

/**
 * One tray pill as its own restart scope. The heavy modifier chain (drag, semantics, key events)
 * is built HERE, so a tray pass with an equal [pill] skips the whole thing — before this
 * extraction, every HA push that touched any chip's details rebuilt these modifiers for all
 * visible pills and recomposed each StatusChip (~16 ms per push on the small device).
 */
@Composable
private fun TrayPill(
    pill: ResolvedPill,
    pinned: Boolean,
    selected: Boolean,
    actions: HomePillActions,
    onOpenMenu: (String) -> Unit,
) {
    val available = pill.availability == Availability.AVAILABLE
    val context = LocalContext.current
    val openLabel = stringResource(R.string.home_open_item, pill.chip.label)
    val actionsLabel = stringResource(R.string.home_item_actions, pill.chip.label)
    StatusChip(
        chip = pill.chip,
        selected = selected,
        // StatusChip installs its combined tap/long-press detector only when
        // onClick is non-null. A guarded no-op keeps long-press available on stale
        // pinned pills so they can still be unpinned while commands stay blocked.
        onClick = { if (available) actions.onOpen(pill) },
        onLongPress = { onOpenMenu(pill.ref.stableKey) },
        modifier = Modifier
            .heightIn(min = 48.dp)
            .homePillReorderDrag(pill, actions)
            .semantics(mergeDescendants = true) {
                contentDescription = trayPillAccessibilityLabel(context, pill, pinned)
                role = Role.Button
                if (available) {
                    onClick(label = openLabel) {
                        actions.onOpen(pill)
                        true
                    }
                }
                onLongClick(label = actionsLabel) {
                    onOpenMenu(pill.ref.stableKey)
                    true
                }
            }
            .onKeyEvent { event ->
                if (available && event.type == KeyEventType.KeyUp &&
                    event.key in setOf(Key.Enter, Key.DirectionCenter, Key.Spacebar)
                ) {
                    actions.onOpen(pill)
                    true
                } else false
            }
            .focusable(),
    )
}

@Composable
private fun ClockTrayControls(
    showExpand: Boolean,
    pillsExpanded: Boolean,
    onPillsExpandedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp.scaled()),
    ) {
        if (showExpand) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(48.dp.scaled())
                    .clip(AppleShapes.pill)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (pillsExpanded) stringResource(R.string.clock_collapse_content_desc) else stringResource(R.string.clock_expand_content_desc),
                    ) { onPillsExpandedChange(!pillsExpanded) }
                    .padding(horizontal = 12.dp.scaled()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (pillsExpanded) stringResource(R.string.clock_collapse_pills) else stringResource(R.string.clock_expand_pills),
                    style = AppleTypography.bodySmall.copy(fontSize = 13.sp.scaled()),
                    color = AppleColors.secondary.copy(alpha = 0.62f),
                )
                Icon(
                    if (pillsExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowUp,
                    contentDescription = if (pillsExpanded) stringResource(R.string.clock_collapse_content_desc) else stringResource(R.string.clock_expand_content_desc),
                    tint = AppleColors.secondary.copy(alpha = 0.62f),
                )
            }
        }
    }
}

internal fun trayPillAccessibilityLabel(context: android.content.Context, pill: ResolvedPill, pinned: Boolean): String = buildString {
    append(pill.chip.label)
    if (pill.chip.value.isNotBlank()) append(", ${pill.chip.value}")
    append(", ${pill.ref.groupDescription(context)}")
    if (pinned) append(", ${context.getString(R.string.home_pinned).lowercase()}")
    if (pill.alert != null || pill.chip.state.equals("critical", ignoreCase = true)) {
        append(", ${context.getString(R.string.home_accessibility_critical)}")
    }
    if (pill.availability == Availability.STALE) append(", ${context.getString(R.string.home_accessibility_stale)}")
}

@Composable
fun ConnectionProblemBanner(
    lastUpdateAt: Long,
    usesMdnsAddress: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed by produceState(initialValue = 0L, lastUpdateAt) {
        while (true) {
            value = if (lastUpdateAt > 0L) {
                ((System.currentTimeMillis() - lastUpdateAt) / 1000L).coerceAtLeast(0L)
            } else 0L
            kotlinx.coroutines.delay(1_000L)
        }
    }
    val ago = if (elapsed < 60) "${elapsed}s" else "${elapsed / 60}min"
    Column(
        modifier = modifier
            .widthIn(max = 620.dp)
            .fillMaxWidth()
            .clip(AppleShapes.panel)
            .background(Color(0xE6221B12), AppleShapes.panel)
            .border(0.5.dp, Color(0x99FF9F0A), AppleShapes.panel)
            .appleClickable(onClick)
            .testTag("connectionProblemBanner")
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            if (lastUpdateAt > 0L) stringResource(R.string.clock_stale_banner_format, ago)
            else stringResource(R.string.connection_banner_unreachable),
            style = AppleTypography.titleMedium,
            color = Color(0xFFFFC062),
        )
        Text(
            stringResource(
                if (usesMdnsAddress) R.string.connection_banner_mdns_hint
                else R.string.connection_banner_generic_hint,
            ),
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
        )
    }
}

@Composable
private fun rememberClock(pattern: String) = produceState(initialValue = format(pattern), pattern) {
    while (true) {
        value = format(pattern)
        kotlinx.coroutines.delay(15_000L)
    }
}

private fun format(pattern: String): String =
    SimpleDateFormat(pattern, Locale.getDefault()).format(Date())

private fun titleCase(value: String): String =
    value.split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

@Preview(widthDp = 640, heightDp = 400)
@Composable
private fun ClockScreenPreview() {
    PortalTheme {
        ClockScreen(
            backgroundMode = "neutral",
            weather = WeatherUi(temp = "21°", indoorTemp = "18°", city = "Nantes", condition = "Nuageux", glyph = WeatherGlyph("partly-cloudy-day")),
            temperatures = TemperatureSummary("18°", "22°", "21°"),
            chips = listOf(
                LauncherChip("washer", "washer", "Machine", "Rinçage", "active"),
                LauncherChip("vacuum", "vacuum", "Aspirateur", "Nettoyage", "active"),
                LauncherChip("doors", "door", "Portes", "Fermées", "ok"),
                LauncherChip("windows", "window", "Fenêtres", "Fermées", "ok"),
                LauncherChip("lock", "lock", "Serrure", "Verrouillée", "ok"),
                LauncherChip("air_q", "air", "Qualité d'air", "Bonne", "ok"),
            ),
            onTap = {},
            onLongPress = {},
            pillsExpanded = false,
            onPillsExpandedChange = {}
        )
    }
}
