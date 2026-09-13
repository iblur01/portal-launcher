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
import com.iblu01.portallauncher.DEFAULT_GEMINI_LIVE_MODEL
import com.iblu01.portallauncher.DEFAULT_GEMINI_VOICE
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
import androidx.compose.runtime.LaunchedEffect
import com.iblu01.portallauncher.DEFAULT_WAKE_WORD
import com.iblu01.portallauncher.ui.components.SettingsPicker
import com.iblu01.portallauncher.voice.GeminiLive
import com.iblu01.portallauncher.voice.GeminiProbe
import com.iblu01.portallauncher.voice.WakeWordCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.iblu01.portallauncher.voice.MicCalibrationFailure
import com.iblu01.portallauncher.voice.MicCalibrationState
import com.iblu01.portallauncher.voice.deviceHasEchoCanceler
import com.iblu01.portallauncher.voice.wakeWordLabel
import com.iblu01.portallauncher.voice.VoicePhase
import com.iblu01.portallauncher.voice.VoiceScheduler
import com.iblu01.portallauncher.voice.VoiceToolCall
import com.iblu01.portallauncher.voice.VoiceUiState

private data class VoiceDraft(
    val enabled: Boolean,
    val apiKey: String,
    val model: String,
    val voice: String,
    val prompt: String,
    val bargeIn: Boolean,
    val dailyLimit: Int,
    val wakeWord: String,
    val threshold: Int,
    val idleSeconds: Int,
)

/**
 * Gemini Live assistant settings. Text fields are committed on leave (like the Home Assistant
 * connection page) so a half-typed key never restarts the wake engine mid-keystroke.
 */
@Composable
fun VoiceAssistantSettingsPage(
    prefs: Prefs,
    voiceState: VoiceUiState = VoiceUiState(),
    toolCalls: List<VoiceToolCall> = emptyList(),
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
    var apiKey by remember { mutableStateOf(prefs.voiceGeminiApiKey) }
    var model by remember { mutableStateOf(prefs.voiceGeminiModel) }
    var voice by remember { mutableStateOf(prefs.voiceGeminiVoice) }
    var prompt by remember { mutableStateOf(prefs.voiceGeminiPrompt) }
    var bargeIn by remember { mutableStateOf(prefs.voiceBargeIn) }
    var dailyLimit by remember { mutableStateOf(prefs.voiceDailySessionLimit) }
    var wakeWord by remember { mutableStateOf(prefs.voiceAssistantWakeWord) }
    var threshold by remember { mutableStateOf(prefs.voiceAssistantThreshold) }
    var idleSeconds by remember { mutableStateOf(prefs.voiceAssistantIdleSeconds) }

    val draft by rememberUpdatedState(
        VoiceDraft(enabled, apiKey, model, voice, prompt, bargeIn, dailyLimit, wakeWord, threshold, idleSeconds),
    )

    DisposableEffect(Unit) {
        onDispose {
            prefs.voiceAssistantEnabled = draft.enabled
            prefs.voiceGeminiApiKey = draft.apiKey
            prefs.voiceGeminiModel = draft.model
            prefs.voiceGeminiVoice = draft.voice
            prefs.voiceGeminiPrompt = draft.prompt
            prefs.voiceBargeIn = draft.bargeIn
            prefs.voiceDailySessionLimit = draft.dailyLimit
            prefs.voiceAssistantWakeWord = draft.wakeWord
            prefs.voiceAssistantThreshold = draft.threshold
            prefs.voiceAssistantIdleSeconds = draft.idleSeconds
            // No callback to the controller: it re-reads these on every launcher resume, and
            // leaving settings always goes through one.
        }
    }

    val echoCancellerDetected = remember { deviceHasEchoCanceler() }

    // Wake words come from what is actually on the device, so dropping an .onnx in makes it
    // selectable without a release.
    val wakeWords = remember { WakeWordCatalog.available(context).ifEmpty { listOf(DEFAULT_WAKE_WORD) } }

    // The Live model list has no static truth: Google retires these previews on its own
    // schedule. Asked once per visit when a key is stored, with the bundled names as the
    // fallback so the field is never an empty dropdown.
    var liveModels by remember { mutableStateOf(GeminiLive.FALLBACK_LIVE_MODELS) }
    var modelsFromApi by remember { mutableStateOf(false) }
    LaunchedEffect(apiKey.isNotBlank()) {
        if (apiKey.isBlank()) return@LaunchedEffect
        val probed = withContext(Dispatchers.IO) { GeminiProbe.listModels(apiKey) }
        if (probed is GeminiProbe.Result.Ok && probed.live.isNotEmpty()) {
            liveModels = probed.live.sorted()
            modelsFromApi = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SettingsSubPageHeader(
            stringResource(R.string.settings_voice_title),
            onBack,
            showBack = showBack,
            badge = stringResource(R.string.voice_beta_badge),
        )
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
                label = stringResource(R.string.settings_voice_api_key),
                value = apiKey,
                onValueChange = { apiKey = it },
                isPassword = true,
            )
            SettingsPicker(
                label = stringResource(R.string.settings_voice_model),
                value = model,
                options = liveModels,
                onSelect = { model = it },
                hint = stringResource(
                    if (modelsFromApi) R.string.settings_voice_model_hint_live else R.string.settings_voice_model_hint_offline,
                ),
            )
            SettingsPicker(
                label = stringResource(R.string.settings_voice_voice_name),
                value = voice,
                options = GeminiLive.VOICES,
                onSelect = { voice = it },
            )
            SettingsTextField(
                label = stringResource(R.string.settings_voice_prompt),
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = stringResource(R.string.settings_voice_prompt_placeholder),
            )
            SettingsDivider()
            SettingsToggle(
                label = stringResource(R.string.settings_voice_barge_in),
                checked = bargeIn,
                onCheckedChange = { bargeIn = it },
            )
            Text(
                text = stringResource(
                    if (echoCancellerDetected) {
                        R.string.settings_voice_barge_in_supported
                    } else {
                        R.string.settings_voice_barge_in_unsupported
                    },
                ),
                style = AppleTypography.bodySmall,
                color = if (echoCancellerDetected) AppleColors.tertiary else AppleColors.warning,
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
                        prefs.voiceGeminiApiKey = apiKey
                        prefs.voiceGeminiModel = model
                        prefs.voiceGeminiVoice = voice
                        prefs.voiceGeminiPrompt = prompt
                        prefs.voiceBargeIn = bargeIn
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
            SettingsPicker(
                label = stringResource(R.string.settings_voice_wake_model),
                value = wakeWord,
                options = wakeWords,
                onSelect = { wakeWord = it },
                labelOf = ::wakeWordLabel,
                hint = stringResource(R.string.settings_voice_wake_model_hint, WakeWordCatalog.customDir(context).absolutePath),
            )
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

        SettingsSection(stringResource(R.string.settings_voice_section_limits)) {
            SettingsTextField(
                label = stringResource(R.string.settings_voice_daily_limit),
                value = dailyLimit.toString(),
                onValueChange = { value -> dailyLimit = value.toIntOrNull()?.coerceIn(0, 5000) ?: dailyLimit },
                keyboardType = KeyboardType.Number,
            )
            Text(
                text = stringResource(R.string.settings_voice_daily_limit_hint),
                style = AppleTypography.bodySmall,
                color = AppleColors.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            SettingsDivider()

            // Read straight from prefs rather than kept in state: this list changes when an
            // alarm fires, not when the page is edited, and a stale queue on screen would be
            // read as the queue itself being wrong.
            val scheduled = remember(voiceState.phase) { VoiceScheduler.pending(prefs) }
            SettingsRow(
                label = stringResource(R.string.settings_voice_scheduled),
                value = if (scheduled.isEmpty()) stringResource(R.string.settings_voice_scheduled_none) else scheduled.size.toString(),
                onClick = {},
            )
            scheduled.forEach { action ->
                Text(
                    text = stringResource(
                        R.string.settings_voice_scheduled_entry,
                        action.title,
                        ((action.atMs - System.currentTimeMillis()) / 60_000L).toInt().coerceAtLeast(0),
                    ),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            SettingsDivider()

            var facts by remember { mutableStateOf(prefs.voiceFacts) }
            SettingsRow(
                label = stringResource(R.string.settings_voice_facts),
                value = if (facts.isEmpty()) {
                    stringResource(R.string.settings_voice_facts_none)
                } else {
                    stringResource(R.string.settings_voice_facts_clear)
                },
                onClick = {
                    prefs.voiceFacts = emptyList()
                    facts = emptyList()
                },
            )
            facts.forEach { fact ->
                Text(
                    text = fact,
                    style = AppleTypography.bodySmall,
                    color = AppleColors.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                )
            }
            SettingsDivider()

            // The journal: without it, "the assistant did the wrong thing" is unfalsifiable.
            SettingsRow(
                label = stringResource(R.string.settings_voice_tool_log),
                value = toolCalls.size.takeIf { it > 0 }?.toString(),
                onClick = {},
            )
            if (toolCalls.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_voice_tool_log_none),
                    style = AppleTypography.bodySmall,
                    color = AppleColors.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            toolCalls.forEach { call ->
                Text(
                    text = "${call.name} ${call.args}\n→ ${call.result}",
                    style = AppleTypography.bodySmall,
                    color = if (call.ok) AppleColors.tertiary else AppleColors.warning,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
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
            MicCalibrationFailure.ROOM_NOT_SILENT -> R.string.settings_voice_calibration_failed_noisy
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
