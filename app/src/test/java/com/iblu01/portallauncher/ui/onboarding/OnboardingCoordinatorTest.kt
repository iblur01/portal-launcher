package com.iblu01.portallauncher.ui.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.iblu01.portallauncher.Prefs
import com.iblu01.portallauncher.PillKind
import com.iblu01.portallauncher.PillRule
import com.iblu01.portallauncher.HomePillPreferencesCodec
import com.iblu01.portallauncher.domain.home.CameraCenterMode
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ManualPillGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingCoordinatorTest {
    private lateinit var prefs: Prefs
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = Prefs(context)
        prefs.resetOnboarding()
        prefs.haToken = ""
        prefs.password = ""
        prefs.voiceGeminiApiKey = ""
        prefs.mqttOnboardingConfigured = false
        prefs.clearImmichConfiguration()
        prefs.hiddenApps = emptySet()
        prefs.appOrder = emptyList()
        prefs.onboardingMetricsChannel = ""
        prefs.onboardingMetricsHighestStep = ""
        prefs.onboardingMetricsStartedAt = 0L
        prefs.onboardingMetricsLastDurationMs = 0L
        prefs.onboardingMetricsAbandonCount = 0
    }

    private fun coordinator() = OnboardingCoordinator(prefs, clockMillis = { now })

    @Test
    fun `lot four commits home layout and complete optional gemini settings`() {
        prefs.voiceGeminiApiKey = "stored-key"
        val coordinator = coordinator()
        val home = HomePillPreferencesCodec.defaults().copy(
            pinnedOrder = listOf(PillRef.Device("light.kitchen"), PillRef.Special("cameras")),
            homeSections = HomePillPreferencesCodec.defaults().homeSections.mapIndexed { index, section ->
                section.copy(visible = section.sectionId != "kind:MEDIA", order = 100 - index,
                    itemOrder = if (section.sectionId == "favorites") listOf(PillRef.Device("light.kitchen")) else emptyList())
            },
            manualGroups = listOf(ManualPillGroup("ground-floor", "Rez-de-chaussée", null,
                listOf(PillRef.Device("light.kitchen"), PillRef.Device("switch.coffee")))),
        )
        val homeResult = coordinator.executeLocal(OnboardingCommand.SaveHomeConfiguration(
            disabledIntegrations = setOf("weather"),
            pillRules = listOf(PillRule("light.kitchen", PillKind.LIGHTS, "Cuisine")),
            preferences = home,
            cameras = CameraPreferences(order = listOf("camera.entry"), mainCameraId = "camera.entry", defaultMode = CameraCenterMode.GRID),
        )) as OnboardingCommandResult.Applied

        assertEquals(setOf("weather"), homeResult.snapshot.disabledHaIntegrations)
        assertEquals("device:light.kitchen", homeResult.snapshot.homePillPreferences.pinnedOrder.first().stableKey)
        assertEquals(CameraCenterMode.GRID, homeResult.snapshot.cameraPreferences.defaultMode)
        assertEquals("ground-floor", homeResult.snapshot.homePillPreferences.manualGroups.single().id)
        assertEquals(2, homeResult.snapshot.homePillPreferences.manualGroups.single().members.size)
        assertFalse(homeResult.snapshot.homePillPreferences.homeSections.first { it.sectionId == "kind:MEDIA" }.visible)

        val voice = coordinator.executeLocal(OnboardingCommand.SaveGemini(
            enabled = true, prompt = "Réponses brèves.", wakeWord = "wakeword/alexa_v0.1.onnx",
            threshold = 62, idleSeconds = 35, dailyLimit = 80, bargeIn = true,
        )) as OnboardingCommandResult.Applied
        assertTrue(voice.snapshot.geminiEnabled)
        assertEquals("Réponses brèves.", voice.snapshot.geminiPrompt)
        assertEquals(62, voice.snapshot.geminiThreshold)
        assertEquals(80, voice.snapshot.geminiDailyLimit)
        assertFalse(voice.snapshot.toString().contains("stored-key"))
    }

    @Test
    fun `snapshot exposes credential presence without any secret value`() {
        prefs.haToken = "ha-super-secret"
        prefs.password = "mqtt-super-secret"
        prefs.voiceGeminiApiKey = "gemini-super-secret"
        prefs.mqttOnboardingConfigured = true

        val snapshot = coordinator().snapshot()
        val printed = snapshot.toString()

        assertTrue(snapshot.homeAssistant.configured)
        assertTrue(snapshot.mqtt.configured)
        assertTrue(snapshot.gemini.configured)
        assertFalse(printed.contains("ha-super-secret"))
        assertFalse(printed.contains("mqtt-super-secret"))
        assertFalse(printed.contains("gemini-super-secret"))

        val command = OnboardingCommand.SaveHomeAssistant(
            "http://192.168.1.20:8123",
            SecretInput.of("command-secret"),
        )
        assertFalse(command.toString().contains("command-secret"))
    }

    @Test
    fun `a stale revision cannot overwrite the latest setting`() {
        val coordinator = coordinator()
        val initial = coordinator.snapshot()
        val applied = coordinator.execute(
            "editor-a",
            initial.revision,
            OnboardingCommand.SaveLauncher(0.9f, "neutral", 0.25f),
        )
        assertTrue(applied is OnboardingCommandResult.Applied)

        val stale = coordinator.execute(
            "editor-a",
            initial.revision,
            OnboardingCommand.SaveLauncher(1.3f, "system", 0.8f),
        )

        assertEquals(OnboardingCommandError.REVISION_CONFLICT, (stale as OnboardingCommandResult.Rejected).error)
        assertEquals(0.9f, prefs.gridScale, 0.001f)
        assertEquals("neutral", prefs.backgroundMode)
    }

    @Test
    fun `only one browser session edits until expiry or explicit takeover`() {
        val coordinator = coordinator()
        val revision = coordinator.snapshot().revision
        assertTrue(
            coordinator.execute("editor-a", revision, OnboardingCommand.SkipHomeAssistant)
                is OnboardingCommandResult.Applied
        )

        val blocked = coordinator.execute(
            "editor-b",
            coordinator.snapshot().revision,
            OnboardingCommand.SkipMqtt,
        )
        assertEquals(
            OnboardingCommandError.EDITOR_ALREADY_ACTIVE,
            (blocked as OnboardingCommandResult.Rejected).error,
        )

        val takeover = coordinator.execute(
            "editor-b",
            coordinator.snapshot().revision,
            OnboardingCommand.SkipMqtt,
            takeOver = true,
        )
        assertTrue(takeover is OnboardingCommandResult.Applied)

        now += OnboardingCoordinator.EDITOR_LEASE_MS + 1
        val afterExpiry = coordinator.execute(
            "editor-c",
            coordinator.snapshot().revision,
            OnboardingCommand.SaveFinishOptions(skipAppCleanup = true, gesturesSeen = false),
        )
        assertTrue(afterExpiry is OnboardingCommandResult.Applied)
    }

    @Test
    fun `a stale takeover is rejected before it can steal the editor lease`() {
        val coordinator = coordinator()
        val initialRevision = coordinator.snapshot().revision
        coordinator.execute("editor-a", initialRevision, OnboardingCommand.SkipHomeAssistant)

        val staleTakeover = coordinator.execute(
            "editor-b",
            initialRevision,
            OnboardingCommand.SkipMqtt,
            takeOver = true,
        )
        assertEquals(
            OnboardingCommandError.REVISION_CONFLICT,
            (staleTakeover as OnboardingCommandResult.Rejected).error,
        )

        val originalEditor = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.SkipMqtt,
        )
        assertTrue(originalEditor is OnboardingCommandResult.Applied)
    }

    @Test
    fun `releasing a stopped web host lets its recreation acquire immediately`() {
        val firstHost = coordinator()
        firstHost.execute("editor-a", firstHost.snapshot().revision, OnboardingCommand.SkipHomeAssistant)
        firstHost.releaseEditorLease()

        val recreatedHost = coordinator()
        val result = recreatedHost.execute(
            "editor-b",
            recreatedHost.snapshot().revision,
            OnboardingCommand.SkipMqtt,
        )

        assertTrue(result is OnboardingCommandResult.Applied)
    }

    @Test
    fun `a rich local subpage mutation bumps the shared revision`() {
        val coordinator = coordinator()
        val before = coordinator.snapshot().revision

        val snapshot = coordinator.updateLocalConfiguration { it.immichShuffle = !it.immichShuffle }

        assertTrue(snapshot.revision > before)
        assertEquals(snapshot.revision, prefs.onboardingRevision)
    }

    @Test
    fun `lot three settings commit atomically and immich key stays redacted`() {
        val coordinator = coordinator()
        val background = coordinator.executeLocal(
            OnboardingCommand.SaveBackground(
                mode = "immich",
                opacity = .4f,
                immichUrl = "https://photos.maison.lan",
                immichApiKey = SecretInput.of("immich-secret"),
                immichAlbumIds = listOf("d876f318-3d2d-4f40-b8ab-40d3e8203fa1"),
                immichShuffle = false,
                immichRefreshMinutes = 30,
                immichCadenceSeconds = 45,
            )
        ) as OnboardingCommandResult.Applied

        assertEquals("immich", background.snapshot.backgroundMode)
        assertTrue(background.snapshot.immich.keyConfigured)
        assertFalse(background.snapshot.toString().contains("immich-secret"))
        assertEquals(30, prefs.immichRefreshMinutes)

        coordinator.executeLocal(OnboardingCommand.SaveClock(OnboardingClockConfig(
            font = "inter", weight = 600, size = 120f, letterSpacing = 1f,
            tint = "white", format24h = false, dateFormat = "iso", elementSpacing = .8f,
        )))
        coordinator.executeLocal(OnboardingCommand.SaveApps(
            hiddenPackages = setOf("com.android.calendar"),
            order = listOf("com.android.settings", "com.android.calendar"),
            iconPack = "com.example.icons", notificationDots = true,
            homeAssistantPackage = "io.homeassistant.companion.android",
            tapAppPackage = "com.android.calendar",
        ))
        val final = coordinator.executeLocal(OnboardingCommand.SaveBehavior(
            keepScreenOn = true, screenTimeoutEnabled = true, screenTimeoutMinutes = 15,
            autoReturnEnabled = true, autoReturnDelaySeconds = 20, gesturesSeen = true,
        )) as OnboardingCommandResult.Applied

        assertEquals("inter", final.snapshot.clock.font)
        assertEquals(600, final.snapshot.clock.weight)
        assertTrue(final.snapshot.notificationDots)
        assertEquals("io.homeassistant.companion.android", final.snapshot.homeAssistantPackage)
        assertEquals("com.android.calendar", final.snapshot.tapAppPackage)
        assertTrue(final.snapshot.behavior.keepScreenOn)
        assertTrue(final.snapshot.gesturesSeen)
    }

    @Test
    fun `lot three rejects unsafe ranges and insecure immich by default`() {
        val coordinator = coordinator()
        val insecure = coordinator.executeLocal(OnboardingCommand.SaveBackground(
            mode = "immich", opacity = .2f, immichUrl = "http://192.168.14.20",
            immichApiKey = SecretInput.of("secret"),
        )) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.INVALID_IMMICH_URL, insecure.error)

        val invalidCadence = coordinator.executeLocal(OnboardingCommand.SaveBackground(
            mode = "immich", opacity = .2f, immichUrl = "https://photos.maison.lan",
            immichApiKey = SecretInput.of("secret"), immichRefreshMinutes = 4,
            immichCadenceSeconds = 3_601,
        )) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.INVALID_BEHAVIOR, invalidCadence.error)

        val behavior = coordinator.executeLocal(OnboardingCommand.SaveBehavior(
            keepScreenOn = false, screenTimeoutEnabled = true, screenTimeoutMinutes = 0,
            autoReturnEnabled = true, autoReturnDelaySeconds = 2, gesturesSeen = false,
        )) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.INVALID_BEHAVIOR, behavior.error)
    }

    @Test
    fun `default-only completion requires and exposes a warning`() {
        val coordinator = coordinator()
        assertTrue(OnboardingWarning.DEFAULTS_WILL_BE_USED in coordinator.snapshot().warnings)

        val rejected = coordinator.executeLocal(OnboardingCommand.Complete(defaultsWarningAccepted = false))
        assertEquals(
            OnboardingCommandError.DEFAULTS_WARNING_REQUIRED,
            (rejected as OnboardingCommandResult.Rejected).error,
        )
        assertFalse(prefs.onboardingCompleted)

        coordinator.executeLocal(OnboardingCommand.Complete(defaultsWarningAccepted = true))
        assertTrue(prefs.onboardingCompleted)
    }

    @Test
    fun `local metrics retain only funnel enums counters and duration`() {
        val coordinator = coordinator()
        coordinator.executeLocal(OnboardingCommand.SelectChannel(OnboardingChannel.WEB))
        now += 750
        coordinator.executeLocal(OnboardingCommand.Navigate(OnboardingStep.BACKGROUND))
        now += 1_250
        coordinator.executeLocal(OnboardingCommand.Complete(defaultsWarningAccepted = true))

        assertEquals("WEB", prefs.onboardingMetricsChannel)
        assertEquals("COMPLETE", prefs.onboardingMetricsHighestStep)
        assertEquals(2_000L, prefs.onboardingMetricsLastDurationMs)
        assertEquals(0L, prefs.onboardingMetricsStartedAt)
        assertEquals(0, prefs.onboardingMetricsAbandonCount)
        val values = listOf(prefs.onboardingMetricsChannel, prefs.onboardingMetricsHighestStep)
        assertFalse(values.any { it.contains("http") || it.contains("token") || it.contains("password") || it.contains("key") })
    }

    @Test
    fun `reset changes only progress and invalidates older revisions`() {
        prefs.gridScale = 1.2f
        prefs.backgroundMode = "custom"
        prefs.haToken = "keep-me"
        prefs.onboardingHomeAssistantSelected = true
        val coordinator = coordinator()
        val before = coordinator.snapshot().revision

        coordinator.executeLocal(OnboardingCommand.ResetProgress(confirmed = true))

        assertFalse(prefs.onboardingCompleted)
        assertEquals(OnboardingChannel.WEB.name, prefs.onboardingChannel)
        assertTrue(prefs.onboardingRevision > before)
        assertEquals(1.2f, prefs.gridScale, 0.001f)
        assertEquals("custom", prefs.backgroundMode)
        assertEquals("keep-me", prefs.haToken)
        assertTrue(prefs.onboardingHomeAssistantSelected)
    }

    @Test
    fun `home assistant skip never changes mqtt state`() {
        val coordinator = coordinator()
        coordinator.executeLocal(OnboardingCommand.SkipHomeAssistant)

        assertTrue(prefs.homeAssistantOnboardingSkipped)
        assertFalse(prefs.mqttOnboardingSkipped)
    }

    @Test
    fun `provider selection is independent from stored credentials and resumes gemini`() {
        prefs.haToken = "existing-ha"
        val coordinator = coordinator()
        val selected = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.SelectProviders(homeAssistant = false, mqtt = true, gemini = true),
        ) as OnboardingCommandResult.Applied

        assertFalse(selected.snapshot.homeAssistantSelected)
        assertTrue(selected.snapshot.homeAssistant.configured)
        assertTrue(selected.snapshot.mqttSelected)
        assertFalse(selected.snapshot.mqtt.configured)
        assertTrue(selected.snapshot.geminiSelected)
        assertFalse(selected.snapshot.geminiEnabled)

        val resumed = coordinator.execute(
            "editor-a",
            selected.snapshot.revision,
            OnboardingCommand.Navigate(OnboardingStep.GEMINI),
        ) as OnboardingCommandResult.Applied
        assertEquals(OnboardingStep.GEMINI, resumed.snapshot.step)
    }

    @Test
    fun `preview lease sequence commit and stale protection are one coordinator transaction`() {
        val coordinator = coordinator()
        val preview = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.82f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Applied

        assertEquals("preview-123", preview.snapshot.launcherPreview?.id)
        assertEquals(1L, preview.snapshot.launcherPreview?.sequence)
        assertEquals(preview.snapshot.revision, prefs.onboardingRevision)
        assertEquals("editor-a", prefs.onboardingEditorSession)
        assertEquals(1f, prefs.gridScale, 0.001f)

        val blocked = coordinator.execute(
            "editor-b",
            preview.snapshot.revision,
            OnboardingCommand.PreviewLauncher("preview-456", 1.18f, "system", 0.3f),
        ) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.EDITOR_ALREADY_ACTIVE, blocked.error)

        val takeover = coordinator.execute(
            "editor-b",
            preview.snapshot.revision,
            OnboardingCommand.PreviewLauncher("preview-456", 1.18f, "system", 0.3f),
            takeOver = true,
        ) as OnboardingCommandResult.Applied
        assertEquals("preview-456", takeover.snapshot.launcherPreview?.id)

        val staleCommit = coordinator.execute(
            "editor-b",
            takeover.snapshot.revision,
            OnboardingCommand.SaveLauncher(0.82f, "neutral", 0.2f, previewId = "preview-123", previewSequence = 1L),
        ) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.STALE_PREVIEW, staleCommit.error)
        assertEquals(1f, prefs.gridScale, 0.001f)

        val committed = coordinator.execute(
            "editor-b",
            takeover.snapshot.revision,
            OnboardingCommand.SaveLauncher(1.18f, "system", 0.3f, previewId = "preview-456", previewSequence = 2L),
        ) as OnboardingCommandResult.Applied
        assertEquals(null, committed.snapshot.launcherPreview)
        assertEquals(1.18f, prefs.gridScale, 0.001f)

        val delayedPreview = coordinator.execute(
            "editor-b",
            committed.snapshot.revision,
            OnboardingCommand.PreviewLauncher("preview-456", 0.7f, "neutral", 0.1f),
        ) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.STALE_PREVIEW, delayedPreview.error)
        assertEquals(null, coordinator.snapshot().launcherPreview)
    }

    @Test
    fun `revert requires the current preview id`() {
        val coordinator = coordinator()
        val preview = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Applied

        val stale = coordinator.execute(
            "editor-a",
            preview.snapshot.revision,
            OnboardingCommand.RevertLauncher("preview-old", previewSequence = 1L),
        ) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.STALE_PREVIEW, stale.error)

        val reverted = coordinator.execute(
            "editor-a",
            preview.snapshot.revision,
            OnboardingCommand.RevertLauncher("preview-123", previewSequence = 1L),
        ) as OnboardingCommandResult.Applied
        assertEquals(null, reverted.snapshot.launcherPreview)
        assertEquals(1f, prefs.gridScale, 0.001f)
    }

    @Test
    fun `rejected takeover rolls back lease and preview atomically`() {
        val coordinator = coordinator()
        val preview = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Applied

        val rejected = coordinator.execute(
            "editor-b",
            preview.snapshot.revision,
            OnboardingCommand.PreviewLauncher("bad", 1.1f, "system", 0.3f),
            takeOver = true,
        ) as OnboardingCommandResult.Rejected

        assertEquals(OnboardingCommandError.INVALID_PREVIEW_ID, rejected.error)
        assertEquals("editor-a", prefs.onboardingEditorSession)
        assertEquals("preview-123", coordinator.snapshot().launcherPreview?.id)
    }

    @Test
    fun `same preview id cannot commit an older sequence`() {
        val coordinator = coordinator()
        val first = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Applied
        val second = coordinator.execute(
            "editor-a",
            first.snapshot.revision,
            OnboardingCommand.PreviewLauncher("preview-123", 1.1f, "system", 0.3f),
        ) as OnboardingCommandResult.Applied

        val stale = coordinator.execute(
            "editor-a",
            second.snapshot.revision,
            OnboardingCommand.SaveLauncher(0.9f, "neutral", 0.2f, "preview-123", 1L),
        ) as OnboardingCommandResult.Rejected

        assertEquals(OnboardingCommandError.INVALID_PREVIEW_SEQUENCE, stale.error)
        assertEquals(1f, prefs.gridScale, 0.001f)
        assertEquals(2L, coordinator.snapshot().launcherPreview?.sequence)
    }

    @Test
    fun `active preview cannot be committed without both id and sequence`() {
        val coordinator = coordinator()
        val previewed = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Applied

        val missingIdentity = coordinator.execute(
            "editor-a",
            previewed.snapshot.revision,
            OnboardingCommand.SaveLauncher(0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.INVALID_PREVIEW_SEQUENCE, missingIdentity.error)
        assertEquals("preview-123", coordinator.snapshot().launcherPreview?.id)
        assertEquals(1f, prefs.gridScale, 0.001f)
    }

    @Test
    fun `test authorization observes lease revision and takeover`() {
        val coordinator = coordinator()
        val revision = coordinator.snapshot().revision
        assertTrue(coordinator.authorize("editor-a", revision) is OnboardingCommandResult.Applied)
        val blocked = coordinator.authorize("editor-b", revision) as OnboardingCommandResult.Rejected
        assertEquals(OnboardingCommandError.EDITOR_ALREADY_ACTIVE, blocked.error)
        assertTrue(coordinator.authorize("editor-b", revision, takeOver = true) is OnboardingCommandResult.Applied)
        assertEquals(revision, coordinator.snapshot().revision)
    }

    @Test
    fun `device rejection leaves preview revision lease and committed values unchanged`() {
        val coordinator = OnboardingCoordinator(prefs, clockMillis = { now }, previewApplier = { false })
        val before = coordinator.snapshot()

        val rejected = coordinator.execute(
            "editor-a",
            before.revision,
            OnboardingCommand.PreviewLauncher("preview-123", 0.9f, "neutral", 0.2f),
        ) as OnboardingCommandResult.Rejected

        assertEquals(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, rejected.error)
        assertEquals(before.revision, coordinator.snapshot().revision)
        assertEquals(null, coordinator.snapshot().launcherPreview)
        assertEquals("", prefs.onboardingEditorSession)
        assertEquals(1f, prefs.gridScale, 0.001f)
    }

    @Test
    fun `failed device commit rolls back prefs and keeps current preview`() {
        var acceptDeviceUpdate = true
        val coordinator = OnboardingCoordinator(
            prefs,
            clockMillis = { now },
            previewApplier = { acceptDeviceUpdate },
        )
        val previewed = coordinator.execute(
            "editor-a",
            coordinator.snapshot().revision,
            OnboardingCommand.PreviewLauncher("preview-123", 1.18f, "system", 0.3f),
        ) as OnboardingCommandResult.Applied
        acceptDeviceUpdate = false

        val rejected = coordinator.execute(
            "editor-a",
            previewed.snapshot.revision,
            OnboardingCommand.SaveLauncher(1.18f, "system", 0.3f, "preview-123", 1L),
        ) as OnboardingCommandResult.Rejected

        assertEquals(OnboardingCommandError.DEVICE_PREVIEW_NOT_APPLIED, rejected.error)
        assertEquals(1f, prefs.gridScale, 0.001f)
        assertEquals("preview-123", coordinator.snapshot().launcherPreview?.id)
        assertEquals(previewed.snapshot.revision, coordinator.snapshot().revision)
    }

    @Test
    fun `saving Gemini enablement does not rewrite provider selection`() {
        prefs.onboardingGeminiSelected = true
        val coordinator = coordinator()

        val disabled = coordinator.executeLocal(OnboardingCommand.SaveGemini(enabled = false))
            as OnboardingCommandResult.Applied

        assertTrue(disabled.snapshot.geminiSelected)
        assertFalse(disabled.snapshot.geminiEnabled)
        assertEquals(OnboardingStep.GEMINI, nextStep(OnboardingStep.MQTT_TEST, disabled.snapshot.flags))
    }
}
