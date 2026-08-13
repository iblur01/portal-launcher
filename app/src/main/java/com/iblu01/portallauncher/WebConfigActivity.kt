package com.iblu01.portallauncher

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleShapes
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.ui.theme.PortalTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
    private var configurationSaved by mutableStateOf(false)

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
                    configurationSaved = configurationSaved,
                    onBack = ::finish,
                    onContinue = {
                        setResult(RESULT_OK)
                        finish()
                    },
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
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        stopServer()
        super.onStop()
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer() {
        if (server != null) return
        val mainHandler = Handler(Looper.getMainLooper())
        val started = WebConfigServer.launch(
            prefs = prefs,
            // Called from a server worker thread; the bridge is started and stopped from main.
            onMqttConfigChanged = {
                mainHandler.post {
                    MqttBridgeService.stop(this)
                    MqttBridgeService.start(this)
                }
            },
            onConfigSaved = { mainHandler.post { configurationSaved = true } },
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
        server?.stop()
        server = null
        endpoint = null
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun WebConfigScreen(
    endpoint: WebConfigActivity.Endpoint?,
    configurationSaved: Boolean,
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

            if (configurationSaved) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = AppleColors.active,
                        modifier = Modifier.size(if (short) 48.dp else 64.dp),
                    )
                    Spacer(Modifier.height(if (short) 10.dp else 18.dp))
                    Text(
                        stringResource(R.string.web_config_saved_title),
                        style = AppleTypography.headlineLarge,
                        color = AppleColors.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.web_config_saved_body),
                        style = AppleTypography.bodyLarge,
                        color = AppleColors.secondary,
                    )
                    Spacer(Modifier.height(if (short) 16.dp else 28.dp))
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().widthIn(max = 420.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PillButton(
                            label = stringResource(R.string.web_config_saved_action),
                            onClick = onContinue,
                            primary = true,
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
