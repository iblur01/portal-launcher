package com.iblu01.portallauncher.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.R
import com.iblu01.portallauncher.ui.components.SettingsDivider
import com.iblu01.portallauncher.ui.components.SettingsRow
import com.iblu01.portallauncher.ui.components.SettingsSection
import com.iblu01.portallauncher.ui.components.SettingsSubPageHeader
import com.iblu01.portallauncher.ui.components.SettingsTextField
import com.iblu01.portallauncher.ui.components.SettingsToggle
import com.iblu01.portallauncher.ui.theme.AppleColors
import com.iblu01.portallauncher.ui.theme.AppleTypography
import com.iblu01.portallauncher.voice.MicCalibrationFailure
import com.iblu01.portallauncher.voice.MicCalibrationState
import com.iblu01.portallauncher.voice.parseVoiceEndpoint
import com.iblu01.portallauncher.voice.wakeWordLabel
import com.iblu01.portallauncher.voice.VoicePhase
import com.iblu01.portallauncher.voice.VoiceUiState

/** Wake-word models bundled in `assets/wakeword`. */
private val WAKE_WORDS = listOf(
    "wakeword/hey_jarvis_v0.1.onnx",
    "wakeword/alexa_v0.1.onnx",
)

private data class VoiceDraft(
    val enabled: Boolean,
    val url: String,
    val token: String,
    val wakeWord: String,
    val threshold: Int,
    val idleSeconds: Int,
)

/**
 * Pipecat Assist satellite settings. Text fields are committed on leave (like the Home Assistant
 * connection page) so a half-typed URL never restarts the wake engine mid-keystroke.
 */
@Composable
fun VoiceAssistantSettingsPage(
    prefs: Prefs,
    voiceState: VoiceUiState = VoiceUiState(),
    calibrationState: MicCalibrationState? = null,
    onStartConnectionTest: () -> Unit = {},
    onStopConnectionTest: () -> Unit = {},
    onCalibrate: () -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    val context = LocalContext.current
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasMicPermission = granted }

    var enabled by remember { mutableStateOf(prefs.voiceAssistantEnabled) }
    var url by remember { mutableStateOf(prefs.voiceAssistantUrl) }
    var token by remember { mutableStateOf(prefs.voiceAssistantToken) }
    var wakeWord by remember { mutableStateOf(prefs.voiceAssistantWakeWord) }
    var threshold by remember { mutableStateOf(prefs.voiceAssistantThreshold) }
    var idleSeconds by remember { mutableStateOf(prefs.voiceAssistantIdleSeconds) }

    val draft by rememberUpdatedState(
        VoiceDraft(enabled, url, token, wakeWord, threshold, idleSeconds),
    )

    DisposableEffect(Unit) {
        onDispose {
            prefs.voiceAssistantEnabled = draft.enabled
            prefs.voiceAssistantUrl = draft.url
            prefs.voiceAssistantToken = draft.token
            prefs.voiceAssistantWakeWord = draft.wakeWord
            prefs.voiceAssistantThreshold = draft.threshold
            prefs.voiceAssistantIdleSeconds = draft.idleSeconds
            // No callback to the controller: it re-reads these on every launcher resume, and
            // leaving settings always goes through one.
        }
    }

    val resolved = parseVoiceEndpoint(url)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubPageHeader(stringResource(R.string.settings_voice_title), onBack, showBack = showBack)
        Text(
            text = stringResource(R.string.settings_voice_intro),
            style = AppleTypography.bodyLarge,
            color = AppleColors.secondary,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
        )

        SettingsSection(stringResource(R.string.settings_voice_section_server)) {
            SettingsToggle(
                label = stringResource(R.string.settings_voice_enabled),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            SettingsDivider()
            SettingsTextField(
                label = stringResource(R.string.settings_voice_url),
                value = url,
                onValueChange = { url = it },
                placeholder = "http://homeassistant.local:7860/api/offer",
            )
            SettingsTextField(
                label = stringResource(R.string.settings_voice_token),
                value = token,
                onValueChange = { token = it },
                isPassword = true,
            )
            Text(
                text = resolved?.offerUrl ?: stringResource(R.string.settings_voice_url_invalid),
                style = AppleTypography.bodySmall,
                color = if (resolved == null) AppleColors.warning else AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.settings_voice_connection_status),
                value = voiceConnectionLabel(voiceState),
                onClick = {
                    if (voiceState.phase.isSessionActive) onStopConnectionTest()
                    else {
                        // Test exactly what is currently visible in the form, even before the
                        // user leaves the page and triggers the normal DisposableEffect save.
                        prefs.voiceAssistantEnabled = enabled
                        prefs.voiceAssistantUrl = url
                        prefs.voiceAssistantToken = token
                        prefs.voiceAssistantWakeWord = wakeWord
                        prefs.voiceAssistantThreshold = threshold
                        prefs.voiceAssistantIdleSeconds = idleSeconds
                        onStartConnectionTest()
                    }
                },
            )
            Text(
                text = stringResource(
                    if (voiceState.phase.isSessionActive) R.string.settings_voice_connection_stop_hint
                    else R.string.settings_voice_connection_test_hint,
                ),
                style = AppleTypography.bodySmall,
                color = if (voiceState.phase == VoicePhase.ERROR) AppleColors.warning else AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            voiceState.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    style = AppleTypography.bodySmall,
                    color = AppleColors.warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        SettingsSection(stringResource(R.string.settings_voice_section_wake)) {
            SettingsRow(
                label = stringResource(R.string.settings_voice_microphone),
                value = stringResource(
                    if (hasMicPermission) R.string.settings_voice_microphone_allowed
                    else R.string.settings_voice_microphone_blocked,
                ),
                onClick = {
                    if (!hasMicPermission) {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    Unit
                },
            )
            if (!hasMicPermission) {
                Text(
                    text = stringResource(R.string.settings_voice_microphone_warning),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            if (hasMicPermission) {
                WakeSignalGraph(score = voiceState.microphoneLevel)
                Text(
                    text = stringResource(R.string.settings_voice_microphone_level, (voiceState.microphoneLevel * 100).toInt()),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    text = stringResource(R.string.settings_voice_live_score, (voiceState.wakeScore * 100).toInt()),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Text(
                    text = voiceState.lastWakeDetectionScore?.let { score ->
                        stringResource(R.string.settings_voice_wake_detected, (score * 100).toInt())
                    } ?: stringResource(R.string.settings_voice_wake_not_detected),
                    style = AppleTypography.bodySmall,
                    color = if (voiceState.lastWakeDetectionScore != null) AppleColors.accent else AppleColors.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.settings_voice_calibration),
                value = calibrationLabel(calibrationState, prefs),
                onClick = {
                    val running = calibrationState is MicCalibrationState.MeasuringNoise ||
                        calibrationState is MicCalibrationState.PlayingTone
                    if (hasMicPermission && !running) onCalibrate()
                },
            )
            Text(
                text = stringResource(
                    when (calibrationState) {
                        is MicCalibrationState.MeasuringNoise -> R.string.settings_voice_calibration_silence_hint
                        is MicCalibrationState.PlayingTone -> R.string.settings_voice_calibration_tone_hint
                        else -> R.string.settings_voice_calibration_hint
                    },
                ),
                style = AppleTypography.bodySmall,
                color = if (calibrationState is MicCalibrationState.Failed) AppleColors.warning else AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SettingsDivider()
            WAKE_WORDS.forEachIndexed { index, asset ->
                if (index > 0) SettingsDivider()
                SettingsRow(
                    label = wakeWordLabel(asset),
                    value = if (asset == wakeWord) stringResource(R.string.settings_voice_wake_active) else null,
                    onClick = { wakeWord = asset },
                )
            }
            SettingsDivider()
            SettingsTextField(
                label = stringResource(R.string.settings_voice_threshold),
                value = threshold.toString(),
                onValueChange = { value -> threshold = value.toIntOrNull()?.coerceIn(1, 95) ?: threshold },
                keyboardType = KeyboardType.Number,
            )
            Text(
                text = stringResource(R.string.settings_voice_threshold_hint),
                style = AppleTypography.bodySmall,
                color = if (voiceState.wakeScore * 100 < threshold) AppleColors.warning else AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SettingsTextField(
                label = stringResource(R.string.settings_voice_idle),
                value = idleSeconds.toString(),
                onValueChange = { value -> idleSeconds = value.toIntOrNull()?.coerceIn(5, 300) ?: idleSeconds },
                keyboardType = KeyboardType.Number,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun calibrationLabel(state: MicCalibrationState?, prefs: Prefs): String = when (state) {
    is MicCalibrationState.MeasuringNoise -> stringResource(R.string.settings_voice_calibration_measuring)
    is MicCalibrationState.PlayingTone -> stringResource(R.string.settings_voice_calibration_playing)
    is MicCalibrationState.Failed -> stringResource(
        when (state.reason) {
            MicCalibrationFailure.SPEAKER_INAUDIBLE -> R.string.settings_voice_calibration_failed_speaker
            MicCalibrationFailure.MIC_UNAVAILABLE -> R.string.settings_voice_calibration_failed_mic
        },
    )
    else -> prefs.voiceMicCalibration?.let { calibration ->
        stringResource(
            R.string.settings_voice_calibration_done,
            (calibration.noiseFloor * 1000).toInt() / 10f,
            calibration.maxGain,
        )
    } ?: stringResource(R.string.settings_voice_calibration_not_run)
}

@Composable
private fun voiceConnectionLabel(state: VoiceUiState): String = stringResource(
    when (state.phase) {
        VoicePhase.CONNECTING -> R.string.settings_voice_connection_connecting
        VoicePhase.LISTENING, VoicePhase.THINKING, VoicePhase.SPEAKING -> R.string.settings_voice_connection_connected
        VoicePhase.ERROR -> R.string.settings_voice_connection_error
        VoicePhase.MISSING_PERMISSION -> R.string.settings_voice_connection_no_microphone
        VoicePhase.MISCONFIGURED -> R.string.settings_voice_connection_invalid
        VoicePhase.WAITING_FOR_WAKE -> R.string.settings_voice_connection_ready
        VoicePhase.DISABLED -> R.string.settings_voice_connection_disabled
    },
)

@Composable
private fun WakeSignalGraph(score: Float) {
    var samples by remember { mutableStateOf(List(48) { 0f }) }
    LaunchedEffect(score) {
        samples = (samples.drop(1) + score.coerceIn(0f, 1f))
    }
    val lineColor = AppleColors.accent
    val guideColor = AppleColors.quaternary
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        drawLine(guideColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        val step = size.width / (samples.size - 1).coerceAtLeast(1)
        samples.zipWithNext().forEachIndexed { index, (first, second) ->
            drawLine(
                color = lineColor,
                start = Offset(index * step, size.height * (1f - first)),
                end = Offset((index + 1) * step, size.height * (1f - second)),
                strokeWidth = 4f,
            )
        }
    }
}
