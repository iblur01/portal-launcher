package com.iblu01.portallauncher

import android.Manifest
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.PillButton
import com.iblu01.portallauncher.ui.components.AmbientBackground
import com.iblu01.portallauncher.ui.components.wallpaperFile
import com.iblu01.portallauncher.ui.onboarding.Capability
import com.iblu01.portallauncher.ui.onboarding.OnboardingCapabilities
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme
import com.iblu01.portallauncher.ui.theme.ClockDateFormat
import com.iblu01.portallauncher.ui.theme.ClockFont
import com.iblu01.portallauncher.ui.theme.ClockTheme
import com.iblu01.portallauncher.ui.theme.ClockTint
import com.iblu01.portallauncher.ui.theme.clockFontFamily
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Remote configuration: shows a QR code for the URL of an embedded web server, so the panel's
 * settings can be filled in from a phone keyboard instead of an on-screen one.
 *
 * The server holds credentials, so its lifetime is exactly this screen's: started in [onStart],
 * stopped in [onStop]. Its access code is fresh on every start, which also means a QR code
 * photographed earlier is useless.
 */
@AndroidEntryPoint
class WebConfigActivity : ComponentActivity() {
    @Inject lateinit var prefs: Prefs

    private var server: WebConfigServer? = null
    private var endpoint by mutableStateOf<Endpoint?>(null)
    private var homeAssistantSaved by mutableStateOf(false)
    private var mqttSaved by mutableStateOf(false)
    private var browserConnected by mutableStateOf(false)
    private var browserStep by mutableStateOf("GRID")
    private var launcherPreview by mutableStateOf<WebLauncherPreview?>(null)
    @Volatile private var serverGeneration = 0L
    @Volatile private var systemActionPending = false
    private var launcherPresented = false

    /** Address and access code of the running server; null while it is not listening. */
    data class Endpoint(val url: String, val code: String)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent {
            PortalTheme {
                WebConfigScreen(
                    endpoint = endpoint,
                    homeAssistantSaved = homeAssistantSaved,
                    mqttSaved = mqttSaved,
                    browserConnected = browserConnected,
                    browserStep = browserStep,
                    launcherPreview = launcherPreview,
                    committedGridScale = prefs.gridScale,
                    committedBackgroundMode = prefs.backgroundMode,
                    committedBackgroundOpacity = prefs.bgOverlayOpacity,
                    committedClockTheme = prefs.clockTheme,
                    onBack = ::finish,
                    onContinue = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startServer()
    }

    override fun onResume() {
        super.onResume()
        systemActionPending = false
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        if (!systemActionPending) stopServer()
        super.onStop()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    override fun finish() {
        if (homeAssistantSaved || mqttSaved) setResult(RESULT_OK)
        super.finish()
    }

    private fun startServer() {
        if (server != null) return
        browserConnected = false
        browserStep = "GRID"
        launcherPreview = null
        LauncherWebPreview.apply(null)
        val generation = ++serverGeneration
        val mainHandler = Handler(Looper.getMainLooper())
        val started = WebConfigServer.launch(
            prefs = prefs,
            // Called from a server worker thread; the bridge is started and stopped from main.
            onMqttConfigChanged = {
                mainHandler.post {
                    if (generation != serverGeneration) return@post
                    MqttBridgeService.stop(this)
                    MqttBridgeService.start(this)
                }
            },
            onConfigSaved = { section ->
                mainHandler.post {
                    if (generation != serverGeneration) return@post
                    if (section == WebConfigSection.HOME_ASSISTANT || section == WebConfigSection.ALL) {
                        homeAssistantSaved = true
                    }
                    if (section == WebConfigSection.MQTT || section == WebConfigSection.ALL) {
                        mqttSaved = true
                    }
                    // The controller re-reads its settings on the next launcher resume, which
                    // leaving the web config always goes through — the bus only says "re-read".
                    if (section == WebConfigSection.VOICE || section == WebConfigSection.ALL) {
                        SettingsChangeBus.get().emit("voiceGeminiApiKey")
                    }
                    SettingsChangeBus.get().emit("haUrl")
                    SettingsChangeBus.get().emit("haToken")
                    SettingsChangeBus.get().emit("brokerHost")
                }
            },
            onOnboardingComplete = {
                mainHandler.post {
                    if (generation != serverGeneration) return@post
                    setResult(RESULT_OK)
                    finish()
                }
            },
            onBrowserConnected = { snapshot ->
                mainHandler.post {
                    if (generation != serverGeneration) return@post
                    browserConnected = true
                    browserStep = snapshot.step.name
                    if (!launcherPresented) {
                        launcherPresented = true
                        systemActionPending = true
                        startActivity(
                            Intent(this, LauncherActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        )
                    }
                }
            },
            onLauncherPreview = { preview ->
                if (generation != serverGeneration) {
                    false
                } else if (Looper.myLooper() == Looper.getMainLooper()) {
                    if (generation == serverGeneration) {
                        launcherPreview = preview
                        LauncherWebPreview.apply(preview)
                        if (preview == null) {
                            SettingsChangeBus.get().emit("gridScale")
                            SettingsChangeBus.get().emit("backgroundMode")
                            SettingsChangeBus.get().emit("bgOverlayOpacity")
                        }
                        true
                    } else false
                } else {
                    val claimed = AtomicBoolean(false)
                    val finished = CountDownLatch(1)
                    val task = Runnable {
                        if (claimed.compareAndSet(false, true) && generation == serverGeneration) {
                            launcherPreview = preview
                            LauncherWebPreview.apply(preview)
                            if (preview == null) {
                                SettingsChangeBus.get().emit("gridScale")
                                SettingsChangeBus.get().emit("backgroundMode")
                                SettingsChangeBus.get().emit("bgOverlayOpacity")
                            }
                        }
                        finished.countDown()
                    }
                    mainHandler.post(task)
                    if (finished.await(750, TimeUnit.MILLISECONDS)) {
                        generation == serverGeneration
                    } else if (claimed.compareAndSet(false, true)) {
                        // The task has not started: cancel it so a negative ACK cannot apply late.
                        mainHandler.removeCallbacks(task)
                        false
                    } else {
                        // The main thread already claimed the tiny state update; it will apply.
                        generation == serverGeneration
                    }
                }
            },
            onSystemAction = { action ->
                if (action == "microphone") {
                    mainHandler.post {
                        if (generation == serverGeneration) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4301)
                    }
                    true
                } else {
                    val intent = when (action) {
                        "default_launcher" -> OnboardingCapabilities(applicationContext)
                            .settingsIntentFor(Capability.DEFAULT_LAUNCHER)
                        "screen_control" -> OnboardingCapabilities(applicationContext)
                            .settingsIntentFor(Capability.SCREEN_CONTROL)
                        "brightness" -> OnboardingCapabilities(applicationContext)
                            .settingsIntentFor(Capability.BRIGHTNESS)
                        "notifications" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        else -> null
                    }
                    if (intent == null || intent.resolveActivity(packageManager) == null) false else {
                        systemActionPending = true
                        mainHandler.post {
                            if (generation == serverGeneration) startActivity(intent)
                            else systemActionPending = false
                        }
                        true
                    }
                }
            },
            onScreenCapture = LauncherWebPreview::capturePng,
        )
        val ip = localIpv4()
        if (started == null || ip == null) {
            started?.stop()
            endpoint = null
            Toast.makeText(this, R.string.web_config_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        server = started
        endpoint = Endpoint(
            url = "http://$ip:${started.listeningPort}/?t=${started.token}",
            code = started.token,
        )
    }


    private fun stopServer() {
        serverGeneration += 1
        server?.releaseOnboardingEditor()
        server?.stop()
        server = null
        endpoint = null
        browserConnected = false
        browserStep = "GRID"
        launcherPreview = null
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    companion object {
        fun onboardingIntent(context: Context): Intent =
            Intent(context, WebConfigActivity::class.java)
    }
}

@Composable
private fun WebConfigScreen(
    endpoint: WebConfigActivity.Endpoint?,
    homeAssistantSaved: Boolean,
    mqttSaved: Boolean,
    browserConnected: Boolean,
    browserStep: String,
    launcherPreview: WebLauncherPreview?,
    committedGridScale: Float,
    committedBackgroundMode: String,
    committedBackgroundOpacity: Float,
    committedClockTheme: ClockTheme,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColors.background),
    ) {
        val landscape = maxWidth > maxHeight
        val short = maxHeight <= 520.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = if (landscape) 24.dp else 16.dp,
                    vertical = if (short) 10.dp else 16.dp,
                ),
        ) {
            SettingsSubPageHeader(
                title = stringResource(R.string.web_config_title),
                onBack = onBack,
            )

            if (browserConnected) {
                ConnectedBrowserPreview(
                    step = browserStep,
                    preview = launcherPreview,
                    committedGridScale = committedGridScale,
                    committedBackgroundMode = committedBackgroundMode,
                    committedBackgroundOpacity = committedBackgroundOpacity,
                    committedClockTheme = committedClockTheme,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                return@Column
            }

            if (homeAssistantSaved || mqttSaved) {
                val complete = homeAssistantSaved && mqttSaved
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(
                            if (complete) R.string.web_config_complete_title
                            else R.string.web_config_progress_title,
                        ),
                        style = AppleTypography.headlineLarge,
                        color = AppleColors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(
                            if (complete) R.string.web_config_complete_body
                            else R.string.web_config_progress_body,
                        ),
                        style = AppleTypography.bodyLarge,
                        color = AppleColors.secondary,
                    )
                    Spacer(Modifier.height(if (short) 14.dp else 24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 620.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        ConfigStatusCard(
                            title = "Home Assistant",
                            configured = homeAssistantSaved,
                            modifier = Modifier.weight(1f),
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_provider_homeassistant),
                                    contentDescription = null,
                                    tint = if (homeAssistantSaved) AppleColors.active else AppleColors.tertiary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                        )
                        ConfigStatusCard(
                            title = "MQTT",
                            configured = mqttSaved,
                            modifier = Modifier.weight(1f),
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.Wifi,
                                    contentDescription = null,
                                    tint = if (mqttSaved) AppleColors.active else AppleColors.tertiary,
                                    modifier = Modifier.size(30.dp),
                                )
                            },
                        )
                    }
                    if (complete) {
                        Spacer(Modifier.height(if (short) 16.dp else 28.dp))
                        PillButton(
                            label = stringResource(R.string.web_config_saved_action),
                            onClick = onContinue,
                            primary = true,
                            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        )
                    }
                }
                return@Column
            }

            if (endpoint == null) {
                Text(
                    stringResource(R.string.web_config_unavailable),
                    style = AppleTypography.bodyLarge,
                    color = AppleColors.error,
                    modifier = Modifier.padding(top = 24.dp),
                )
                return@Column
            }

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (landscape) {
                    val qrSize = minOf(maxWidth * 0.30f, maxHeight * 0.72f, 300.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (short) 22.dp else 40.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QrBlock(endpoint = endpoint, qrSize = qrSize, modifier = Modifier.weight(0.9f))
                        DetailsBlock(
                            endpoint = endpoint,
                            onCopyAddress = { clipboard.setText(AnnotatedString(endpoint.url)) },
                            onCopyCode = { clipboard.setText(AnnotatedString(endpoint.code)) },
                            compact = short,
                            modifier = Modifier.weight(1.1f),
                        )
                    }
                } else {
                    val qrSize = minOf(maxWidth * 0.72f, maxHeight * 0.42f, 280.dp)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        QrBlock(endpoint = endpoint, qrSize = qrSize, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(if (short) 12.dp else 24.dp))
                        DetailsBlock(
                            endpoint = endpoint,
                            onCopyAddress = { clipboard.setText(AnnotatedString(endpoint.url)) },
                            onCopyCode = { clipboard.setText(AnnotatedString(endpoint.code)) },
                            compact = short,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedBrowserPreview(
    step: String,
    preview: WebLauncherPreview?,
    committedGridScale: Float,
    committedBackgroundMode: String,
    committedBackgroundOpacity: Float,
    committedClockTheme: ClockTheme,
    modifier: Modifier = Modifier,
) {
    val scale = preview?.gridScale ?: committedGridScale
    val mode = preview?.backgroundMode ?: committedBackgroundMode
    val opacity = preview?.backgroundOpacity ?: committedBackgroundOpacity
    val clockTheme = preview?.clock?.let {
        ClockTheme(
            font = ClockFont.fromKey(it.font), weight = it.weight, size = it.size,
            letterSpacing = it.letterSpacing, tint = ClockTint.fromKey(it.tint),
            format24h = it.format24h, dateFormat = ClockDateFormat.fromKey(it.dateFormat),
            elementSpacing = it.elementSpacing,
        )
    } ?: committedClockTheme
    val context = LocalContext.current
    val previewApps = remember(context) {
        context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        ).map { it.loadLabel(context.packageManager).toString() }
            .filter(String::isNotBlank).distinct().take(32)
    }
    val columns = (6f / scale).toInt().coerceIn(4, 8)
    val surface = when (mode) {
        "system" -> Color(0xFF1D2735)
        "custom" -> Color(0xFF243447)
        "immich" -> Color(0xFF26352D)
        else -> Color(0xFF111318)
    }
    Column(
        modifier = modifier.padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Navigateur connecté", style = AppleTypography.titleMedium, color = AppleColors.primary)
                Text(step.replace('_', ' ').lowercase(), style = AppleTypography.bodySmall, color = AppleColors.secondary)
            }
            Text(
                if (preview == null) "Réglages validés" else "Aperçu #${preview.sequence}",
                style = AppleTypography.bodySmall,
                color = if (preview == null) AppleColors.active else AppleColors.secondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(surface)
                .border(1.dp, AppleColors.frostedBorder, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AmbientBackground(
                mode = mode,
                wallpaperVersion = wallpaperFile(LocalContext.current).lastModified().hashCode(),
                overlayOpacity = opacity,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    if (clockTheme.format24h) "08:42" else "8:42 AM",
                    style = AppleTypography.headlineLarge.copy(
                        fontFamily = clockFontFamily(clockTheme.font, FontWeight(clockTheme.weight)),
                        fontWeight = FontWeight(clockTheme.weight),
                        fontSize = (clockTheme.size / 3f).sp,
                        letterSpacing = (clockTheme.letterSpacing / 3f).sp,
                    ),
                    color = clockTheme.tint.color,
                    textAlign = TextAlign.Center,
                )
                repeat(4) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(columns) { column ->
                            val label = previewApps.getOrNull(row * columns + column).orEmpty()
                            Box(
                                Modifier
                                    .size(42.dp * scale)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Color(0xFF30363D))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(5.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label.take(2).uppercase(),
                                    style = AppleTypography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${columns} colonnes · échelle ${"%.2f".format(scale)} · assombrissement ${(opacity * 100).toInt()} %",
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
        )
    }
}

@Composable
private fun ConfigStatusCard(
    title: String,
    configured: Boolean,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppleShapes.card)
            .background(
                if (configured) AppleColors.active.copy(alpha = 0.14f) else AppleColors.elevated,
                AppleShapes.card,
            )
            .border(
                1.dp,
                if (configured) AppleColors.active.copy(alpha = 0.65f) else AppleColors.frostedBorder,
                AppleShapes.card,
            )
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon()
        Text(title, style = AppleTypography.titleMedium, color = AppleColors.primary)
        Text(
            stringResource(
                if (configured) R.string.web_config_status_configured
                else R.string.web_config_status_pending,
            ),
            style = AppleTypography.bodySmall,
            color = if (configured) AppleColors.active else AppleColors.tertiary,
        )
    }
}

@Composable
private fun QrBlock(
    endpoint: WebConfigActivity.Endpoint,
    qrSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val qr = remember(endpoint.url) { qrImage(endpoint.url) }
        Image(
            bitmap = qr,
            contentDescription = stringResource(R.string.web_config_qr_description),
            modifier = Modifier
                .size(qrSize)
                .clip(AppleShapes.card)
                .background(Color.White)
                .padding(12.dp),
        )
    }
}

@Composable
private fun DetailsBlock(
    endpoint: WebConfigActivity.Endpoint,
    onCopyAddress: () -> Unit,
    onCopyCode: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.web_config_subtitle),
            style = AppleTypography.bodyLarge,
            color = AppleColors.primary,
        )
        Spacer(Modifier.height(if (compact) 10.dp else 20.dp))
        SettingsSection(title = stringResource(R.string.web_config_section_manual)) {
            SettingsRow(
                label = stringResource(R.string.web_config_label_address),
                value = endpoint.url.substringBefore("/?t="),
                onClick = onCopyAddress,
            )
            SettingsRow(
                label = stringResource(R.string.web_config_label_code),
                value = endpoint.code,
                onClick = onCopyCode,
            )
        }
        Text(
            stringResource(R.string.web_config_same_network_note),
            style = AppleTypography.bodySmall,
            color = AppleColors.secondary,
            modifier = Modifier.padding(
                start = if (compact) 8.dp else 16.dp,
                top = if (compact) 8.dp else 16.dp,
                end = if (compact) 8.dp else 16.dp,
            ),
        )
    }
}

/** Renders [content] as a QR code bitmap; the module count decides the size, Compose scales it. */
private fun qrImage(content: String): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        QR_PIXELS,
        QR_PIXELS,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { i ->
        if (matrix.get(i % width, i / width)) BLACK else WHITE
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val QR_PIXELS = 512
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
