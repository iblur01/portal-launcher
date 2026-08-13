package com.iblu01.portallauncher.ui.onboarding

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iblu01.portallauncher.HaApiClient
import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.HaInstance
import com.iblu01.portallauncher.PillSupport
import com.iblu01.portallauncher.HaMdnsDiscovery
import com.iblu01.portallauncher.Prefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Owns the whole first-run flow: one immutable [OnboardingUiState], one transition per user intent.
 *
 * Two rules the screens rely on:
 * - every transition persists the step it lands on, so an app killed in the middle of an Android
 *   settings trip resumes where it was;
 * - a choice is written to [Prefs] the moment it is made, not at the end, so abandoning the flow
 *   never loses what was already decided.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: Prefs,
    private val capabilities: OnboardingCapabilities,
    private val haTester: HaOnboardingTester,
    private val mqttTester: MqttOnboardingTester,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var haDiscovery: HaMdnsDiscovery? = null
    private var brokerDiscovery: MqttMdnsDiscovery? = null
    private var runningTest: Job? = null

    /** Candidates behind [OnboardingUiState.pillOptions], kept out of the UI state (raw JSON). */
    private var pillCandidates: List<com.iblu01.portallauncher.PillCandidate> = emptyList()

    private fun initialState(): OnboardingUiState {
        val flags = OnboardingFlags(
            homeAssistantSkipped = prefs.homeAssistantOnboardingSkipped,
            mqttSkipped = prefs.mqttOnboardingSkipped,
            appCleanupSkipped = prefs.appCleanupOnboardingSkipped,
        )
        val haUrl = prefs.haUrl
        return OnboardingUiState(
            step = resumeStep(prefs.onboardingStep, completed = false),
            flags = flags,
            systemCapabilities = capabilities.read(),
            gridScale = prefs.gridScale,
            gridPreset = GridPreset.forScale(prefs.gridScale),
            gridManual = GridPreset.forScale(prefs.gridScale) == null,
            backgroundMode = prefs.backgroundMode,
            haUrl = haUrl,
            haToken = prefs.haToken,
            mqttHost = prefs.brokerHost,
            mqttPort = prefs.brokerPort,
            mqttAuthEnabled = prefs.username.isNotBlank(),
            mqttUsername = prefs.username,
            mqttPassword = prefs.password,
            mqttDeviceName = prefs.deviceName,
            hiddenPackages = prefs.hiddenApps,
        )
    }

    // --- Navigation ---------------------------------------------------------------------------

    /** Moves to [step], persisting it so the flow can be resumed there. */
    private fun goTo(step: OnboardingStep) {
        prefs.onboardingStep = step.name
        _state.update { it.copy(step = step) }
    }

    fun continueFromWelcome() = goTo(OnboardingStep.SYSTEM_SETUP)

    /** The main action of every step that has nothing else to do first. */
    fun goNext() {
        val current = _state.value
        nextStep(current.step, current.flags)?.let(::goTo)
    }

    fun goBack() {
        val current = _state.value
        previousStep(current.step, current.flags)?.let(::goTo)
    }

    // --- Chapter 1 ----------------------------------------------------------------------------

    /** Re-reads every capability. Called from `onResume`, i.e. after each system-settings trip. */
    fun refreshCapabilities() {
        probeRoot()
        val fresh = capabilities.read()
        _state.update { previous ->
            val newlyGranted = Capability.values().firstOrNull {
                previous.systemCapabilities[it] != CapabilityStatus.GRANTED &&
                    fresh[it] == CapabilityStatus.GRANTED
            }
            previous.copy(
                systemCapabilities = fresh,
                justGranted = newlyGranted ?: previous.justGranted,
            )
        }
    }

    /** Clears the "just granted" check once its animation has played. */
    fun acknowledgeGrant() = _state.update { it.copy(justGranted = null) }

    /**
     * The system screen to open for [capability], or null when it is granted from inside the app
     * (the microphone) or unavailable on this device.
     */
    fun settingsIntentFor(capability: Capability): Intent? = capabilities.settingsIntentFor(capability)

    fun tryEnableScreenControlDirectly(): Boolean = capabilities.tryEnableScreenControlDirectly()

    /** Probes for root off the main thread; the answer is cached by the shell itself. */
    private fun probeRoot() {
        if (_state.value.rootAvailable) return
        viewModelScope.launch {
            val rooted = withContext(Dispatchers.IO) { capabilities.isRootAvailable() }
            if (rooted) _state.update { it.copy(rootAvailable = true) }
        }
    }

    /** One tap on a rooted panel: grants everything, then re-reads what actually landed. */
    fun provisionWithRoot() {
        if (_state.value.rootProvisioning) return
        _state.update { it.copy(rootProvisioning = true) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { capabilities.provisionWithRoot() }
            _state.update { it.copy(rootProvisioning = false) }
            refreshCapabilities()
        }
    }

    fun adbSetHomeCommand(): String = capabilities.adbSetHomeCommand()

    fun selectGridPreset(preset: GridPreset) {
        prefs.gridScale = preset.scale
        _state.update { it.copy(gridScale = preset.scale, gridPreset = preset, gridManual = false) }
    }

    fun selectGridScale(scale: Float) {
        prefs.gridScale = scale
        _state.update {
            it.copy(gridScale = prefs.gridScale, gridPreset = GridPreset.forScale(prefs.gridScale))
        }
    }

    fun setGridManual(manual: Boolean) = _state.update { it.copy(gridManual = manual) }

    /** Applies a background mode live — the launcher reads [Prefs.backgroundMode] on next draw. */
    fun selectBackground(mode: String, configured: Boolean = false) {
        prefs.backgroundMode = mode
        _state.update { it.copy(backgroundMode = mode, backgroundConfigured = configured) }
    }

    fun setBackgroundOpacity(opacity: Float) {
        prefs.bgOverlayOpacity = opacity
    }

    // --- Chapter 2: Home Assistant --------------------------------------------------------------

    fun startHomeDiscovery() {
        if (haDiscovery != null) return
        _state.update { it.copy(haDiscovery = DiscoveryState.Searching) }
        haDiscovery = HaMdnsDiscovery(context).also { discovery ->
            discovery.start { instances -> onHomesDiscovered(instances) }
        }
    }

    fun stopHomeDiscovery() {
        haDiscovery?.stop()
        haDiscovery = null
        _state.update {
            if (it.haDiscovery is DiscoveryState.Searching && it.discoveredHomes.isEmpty()) {
                it.copy(haDiscovery = DiscoveryState.NothingFound)
            } else it
        }
    }

    private fun onHomesDiscovered(instances: List<HaInstance>) {
        val homes = instances.map { HomeCandidate(name = it.name, url = it.url) }
        _state.update {
            it.copy(
                discoveredHomes = homes,
                haDiscovery = if (homes.isEmpty()) DiscoveryState.NothingFound
                else DiscoveryState.Found(homes.size),
            )
        }
    }

    /** No instance answered within the allotted time. */
    fun markDiscoveryEmpty() = _state.update {
        if (it.discoveredHomes.isEmpty()) it.copy(haDiscovery = DiscoveryState.NothingFound) else it
    }

    fun selectDiscoveredHome(home: HomeCandidate) {
        setHaUrl(home.url)
        _state.update { it.copy(selectedHome = home) }
        goTo(OnboardingStep.HOME_ASSISTANT_CREDENTIALS)
    }

    fun chooseManualHome() {
        _state.update { it.copy(selectedHome = null) }
        goTo(OnboardingStep.HOME_ASSISTANT_CREDENTIALS)
    }

    fun setHaUrl(url: String) = _state.update { it.copy(haUrl = url, haTest = TestState.Idle) }

    fun setHaToken(token: String) =
        _state.update { it.copy(haToken = token.trim(), haTest = TestState.Idle) }

    /** Runs the connection test and, on success, saves the credentials and moves on. */
    fun testHomeAssistant() {
        val current = _state.value
        if (!current.canTestHa) return
        goTo(OnboardingStep.HOME_ASSISTANT_TEST)
        runningTest?.cancel()
        runningTest = viewModelScope.launch {
            _state.update { it.copy(haTest = TestState.Running(TestPhase.CHECKING_ADDRESS)) }
            val result = haTester.test(current.haUrl, current.haToken) { phase ->
                _state.update { it.copy(haTest = TestState.Running(phase)) }
            }
            if (result is TestState.Success) {
                // Persisted through the app's existing encrypted store; never logged.
                prefs.haUrl = OnboardingUrls.normalizeHaUrl(current.haUrl)
                prefs.haToken = current.haToken
                prefs.homeAssistantOnboardingSkipped = false
                suggestMqttDefaults()
            }
            _state.update { it.copy(haTest = result) }
        }
    }

    /** Returns to the form with the address and token intact, as required after a failure. */
    fun editHomeAssistantCredentials() {
        runningTest?.cancel()
        _state.update { it.copy(haTest = TestState.Idle) }
        goTo(OnboardingStep.HOME_ASSISTANT_CREDENTIALS)
    }

    /** Drops the Home Assistant branch: no token screen, no pills, no MQTT. */
    fun skipHomeAssistant() {
        runningTest?.cancel()
        prefs.homeAssistantOnboardingSkipped = true
        _state.update {
            it.copy(
                flags = it.flags.copy(homeAssistantSkipped = true),
                haTest = TestState.Idle,
            )
        }
        goTo(OnboardingStep.HIDDEN_APPS)
    }

    /** Abandons only the credentials screen, keeping the rest of the flow as it was. */
    fun abandonHomeAssistantCredentials() = skipHomeAssistant()

    fun acceptRecommendedPills() {
        // The priority engine already ranks entities; recommended means "leave it to Portal".
        goNext()
    }

    /**
     * Loads the entities that can become pills, for the optional "choose mine" sub-page.
     *
     * Read from Home Assistant rather than from what the test collected: the test only counted
     * entities, and this list needs their attributes to be grouped the way the launcher does.
     */
    fun loadPillCandidates() {
        if (_state.value.pillOptions.isNotEmpty() || _state.value.pillOptionsLoading) return
        _state.update { it.copy(pillOptionsLoading = true) }
        viewModelScope.launch {
            val options = withContext(Dispatchers.IO) {
                val result = HaApiClient(prefs.haUrl, prefs.haToken).getStates()
                if (!result.ok) return@withContext emptyList()
                val entities = parseEntities(result.body)
                val rules = prefs.pillRules.associateBy { it.entityId }
                pillCandidates = PillSupport.candidates(entities)
                    .sortedWith(compareBy({ it.kind.ordinal }, { it.label.lowercase() }))
                pillCandidates.map { candidate ->
                        PillOption(
                            entityId = candidate.primary.entityId,
                            label = candidate.label,
                            // No stored rule means the priority engine decides: shown by default.
                            enabled = rules[candidate.primary.entityId]?.enabled ?: true,
                        )
                    }
            }
            _state.update { it.copy(pillOptions = options, pillOptionsLoading = false) }
        }
    }

    private fun parseEntities(raw: String?): List<HaEntity> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val entity = array.optJSONObject(index) ?: return@mapNotNull null
            val id = entity.optString("entity_id")
            if (id.isBlank()) null
            else HaEntity(
                entityId = id,
                state = entity.optString("state"),
                attributes = entity.optJSONObject("attributes") ?: JSONObject(),
                lastChanged = entity.optString("last_changed"),
            )
        }
    }.getOrDefault(emptyList())

    /** Shows or hides one entity, writing the rule straight away. */
    fun setPillEnabled(entityId: String, enabled: Boolean) {
        val options = _state.value.pillOptions.map {
            if (it.entityId == entityId) it.copy(enabled = enabled) else it
        }
        persistPillRules(options)
        _state.update { it.copy(pillOptions = options) }
    }

    /** The "show everything" / "hide everything" shortcuts of the sub-page. */
    fun setAllPillsEnabled(enabled: Boolean) {
        val options = _state.value.pillOptions.map { it.copy(enabled = enabled) }
        persistPillRules(options)
        _state.update { it.copy(pillOptions = options) }
    }

    /**
     * Writes the whole selection out as rules. Rules are rebuilt from the candidates rather than
     * from the UI list so each one keeps its kind and its related entities — dropping those would
     * flatten a grouped pill into a bare entity.
     */
    private fun persistPillRules(options: List<PillOption>) {
        val enabledById = options.associate { it.entityId to it.enabled }
        val existing = prefs.pillRules.associateBy { it.entityId }
        prefs.pillRules = pillCandidates.map { candidate ->
            val id = candidate.primary.entityId
            val rule = existing[id] ?: PillSupport.defaultRule(candidate)
            rule.copy(enabled = enabledById[id] ?: rule.enabled)
        }
    }

    // --- Chapter 2: MQTT ------------------------------------------------------------------------

    /** Pre-fills the broker from the Home Assistant host. A suggestion, never a discovery. */
    private fun suggestMqttDefaults() = _state.update { current ->
        val host = OnboardingUrls.suggestedMqttHost(current.haUrl, current.mqttHost)
        current.copy(
            mqttHost = host,
            mqttDeviceName = current.mqttDeviceName.ifBlank { prefs.deviceName },
        )
    }

    fun startBrokerDiscovery() {
        if (brokerDiscovery != null) return
        _state.update { it.copy(brokerDiscovery = DiscoveryState.Searching) }
        brokerDiscovery = MqttMdnsDiscovery(context).also { discovery ->
            discovery.start { brokers ->
                _state.update {
                    it.copy(
                        discoveredBrokers = brokers,
                        brokerDiscovery = if (brokers.isEmpty()) DiscoveryState.NothingFound
                        else DiscoveryState.Found(brokers.size),
                    )
                }
            }
        }
    }

    fun stopBrokerDiscovery() {
        brokerDiscovery?.stop()
        brokerDiscovery = null
        _state.update {
            if (it.brokerDiscovery is DiscoveryState.Searching && it.discoveredBrokers.isEmpty()) {
                it.copy(brokerDiscovery = DiscoveryState.NothingFound)
            } else it
        }
    }

    fun selectDiscoveredBroker(broker: BrokerCandidate) = _state.update {
        it.copy(mqttHost = broker.host, mqttPort = broker.port, mqttTest = TestState.Idle)
    }

    fun setMqttHost(host: String) = _state.update { it.copy(mqttHost = host, mqttTest = TestState.Idle) }
    fun setMqttPort(port: Int) = _state.update { it.copy(mqttPort = port, mqttTest = TestState.Idle) }
    fun setMqttAuthEnabled(enabled: Boolean) = _state.update {
        it.copy(mqttAuthEnabled = enabled, mqttTest = TestState.Idle)
    }
    fun setMqttUsername(username: String) = _state.update {
        it.copy(mqttUsername = username, mqttTest = TestState.Idle)
    }
    fun setMqttPassword(password: String) = _state.update {
        it.copy(mqttPassword = password, mqttTest = TestState.Idle)
    }
    fun setMqttDeviceName(name: String) = _state.update { it.copy(mqttDeviceName = name) }

    /** Enters the MQTT branch from the "control Portal from Home Assistant" screen. */
    fun configureMqtt() {
        prefs.mqttOnboardingSkipped = false
        _state.update { it.copy(flags = it.flags.copy(mqttSkipped = false)) }
        suggestMqttDefaults()
        goTo(OnboardingStep.MQTT_CONFIGURATION)
    }

    /** Keeps the Home Assistant connection, drops only the remote-control part. */
    fun skipMqtt() {
        runningTest?.cancel()
        prefs.mqttOnboardingSkipped = true
        _state.update {
            it.copy(flags = it.flags.copy(mqttSkipped = true), mqttTest = TestState.Idle)
        }
        goTo(OnboardingStep.HIDDEN_APPS)
    }

    fun testMqtt() {
        val current = _state.value
        if (!current.canTestMqtt) return
        goTo(OnboardingStep.MQTT_TEST)
        runningTest?.cancel()
        runningTest = viewModelScope.launch {
            _state.update { it.copy(mqttTest = TestState.Running(TestPhase.CONNECTING_BROKER)) }
            val result = mqttTester.test(
                host = current.mqttHost,
                port = current.mqttPort,
                username = if (current.mqttAuthEnabled) current.mqttUsername else "",
                password = if (current.mqttAuthEnabled) current.mqttPassword else "",
                deviceId = prefs.deviceId,
            ) { phase -> _state.update { it.copy(mqttTest = TestState.Running(phase)) } }
            if (result is TestState.Success) {
                prefs.brokerHost = current.mqttHost
                prefs.brokerPort = current.mqttPort
                prefs.username = if (current.mqttAuthEnabled) current.mqttUsername else ""
                prefs.password = if (current.mqttAuthEnabled) current.mqttPassword else ""
                prefs.deviceName = current.mqttDeviceName.ifBlank { prefs.deviceName }
            }
            _state.update { it.copy(mqttTest = result) }
        }
    }

    fun editMqttConfiguration() {
        runningTest?.cancel()
        _state.update { it.copy(mqttTest = TestState.Idle) }
        goTo(OnboardingStep.MQTT_CONFIGURATION)
    }

    // --- Chapter 3 ------------------------------------------------------------------------------

    /** Loads the installed apps, flagging the ones the launcher must not let you hide. */
    fun loadApps() {
        if (_state.value.apps.isNotEmpty()) return
        viewModelScope.launch {
            val apps = readInstalledApps()
            _state.update { it.copy(apps = apps) }
        }
    }

    private fun readInstalledApps(): List<OnboardingApp> {
        val pm = context.packageManager
        val settingsPackage = Intent(android.provider.Settings.ACTION_SETTINGS)
            .resolveActivity(pm)?.packageName
        val protectedPackages = setOfNotNull(
            context.packageName,
            settingsPackage,
            prefs.homeAssistantPackage.takeIf { it.isNotBlank() },
        )
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val activity = info.activityInfo ?: return@mapNotNull null
            val appInfo = runCatching { pm.getApplicationInfo(activity.packageName, 0) }.getOrNull()
            val system = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            OnboardingApp(
                key = activity.packageName,
                packageName = activity.packageName,
                label = info.loadLabel(pm).toString(),
                system = system,
                protected = activity.packageName in protectedPackages,
                recommended = activity.packageName == Prefs.DEFAULT_HA_PACKAGE,
            )
        }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Persists the hidden set. Protected packages are filtered out here as well as in the UI: the
     * launcher must stay reachable whatever the screen sent.
     */
    fun applyHiddenApps(packages: Set<String>) {
        val protectedPackages = _state.value.apps.filter { it.protected }.map { it.packageName }.toSet()
        val safe = packages - protectedPackages
        prefs.hiddenApps = safe
        prefs.appCleanupOnboardingSkipped = false
        _state.update { it.copy(hiddenPackages = safe) }
        goNext()
    }

    fun skipHiddenApps() {
        prefs.appCleanupOnboardingSkipped = true
        _state.update { it.copy(flags = it.flags.copy(appCleanupSkipped = true)) }
        goTo(OnboardingStep.GESTURES)
    }

    fun acknowledgeGestures() {
        prefs.gestureHintsSeen = true
        goNext()
    }

    // --- Ending -----------------------------------------------------------------------------------

    /**
     * Ends the flow from the welcome screen: everything keeps its default value, and the assistant
     * never opens by itself again (it stays available from the settings).
     */
    fun skipOnboarding() = completeOnboarding()

    fun completeOnboarding() {
        prefs.gestureHintsSeen = true
        prefs.onboardingCompleted = true
        prefs.onboardingVersion = ONBOARDING_VERSION
        prefs.onboardingStep = ""
        _state.update { it.copy(step = OnboardingStep.COMPLETE) }
    }

    override fun onCleared() {
        haDiscovery?.stop()
        brokerDiscovery?.stop()
        super.onCleared()
    }
}
