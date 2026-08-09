package com.iblu01.portallauncher.ui.onboarding

/** Whether a system capability is usable, still to grant, or absent from this device. */
enum class CapabilityStatus { GRANTED, MISSING, UNAVAILABLE }

/** The system capabilities the onboarding walks through, in the order they are presented. */
enum class Capability { DEFAULT_LAUNCHER, SCREEN_CONTROL, BRIGHTNESS }

/** Live status of every capability, re-read on every `onResume`. */
data class SystemCapabilities(
    val defaultLauncher: CapabilityStatus = CapabilityStatus.MISSING,
    val screenControl: CapabilityStatus = CapabilityStatus.MISSING,
    val brightness: CapabilityStatus = CapabilityStatus.MISSING,
) {
    operator fun get(capability: Capability): CapabilityStatus = when (capability) {
        Capability.DEFAULT_LAUNCHER -> defaultLauncher
        Capability.SCREEN_CONTROL -> screenControl
        Capability.BRIGHTNESS -> brightness
    }
}

/** mDNS lookup for Home Assistant / MQTT brokers. */
sealed interface DiscoveryState {
    data object Idle : DiscoveryState
    data object Searching : DiscoveryState
    data class Found(val count: Int) : DiscoveryState
    data object NothingFound : DiscoveryState
}

/** A connection test, with enough shape to drive a progress screen rather than a bare spinner. */
sealed interface TestState {
    data object Idle : TestState
    data class Running(val phase: TestPhase) : TestState
    data class Success(val summary: TestSummary) : TestState
    data class Failure(val error: OnboardingError) : TestState
}

/** The sub-steps shown while a test runs. */
enum class TestPhase {
    // Home Assistant
    CHECKING_ADDRESS, AUTHENTICATING, FETCHING_ENTITIES,

    // MQTT
    CONNECTING_BROKER, PUBLISHING_DEVICE, VERIFYING_ROUNDTRIP,
}

/** What a successful test found, used by the success screens. */
data class TestSummary(
    val entityCount: Int = 0,
    /** Human-facing breakdown, e.g. lights -> 12. Keys are [SummaryCategory] entries. */
    val breakdown: Map<SummaryCategory, Int> = emptyMap(),
    /** Feature names the MQTT bridge will expose (screen, brightness, volume, …). */
    val features: List<MqttFeature> = emptyList(),
)

enum class SummaryCategory { LIGHTS, OPENINGS, MEDIA, ALARM, WEATHER, CLIMATE, OTHER }

enum class MqttFeature { SCREEN, BRIGHTNESS, VOLUME, PRESENCE, NOTIFICATIONS }

/**
 * A failure the user can act on. Deliberately coarse: each case maps to one explanation and one
 * set of recovery actions, never to a raw exception message.
 */
enum class OnboardingError {
    HOST_UNREACHABLE,
    TIMEOUT,
    UNAUTHORIZED,
    INVALID_CERTIFICATE,
    INVALID_RESPONSE,
    BROKER_UNREACHABLE,
    BROKER_REFUSED,
    BROKER_BAD_CREDENTIALS,
    PUBLISH_FORBIDDEN,
    SUBSCRIBE_FORBIDDEN,
    UNKNOWN,
}

/** A Home Assistant instance offered by mDNS, or one typed by hand. */
data class HomeCandidate(val name: String, val url: String, val manual: Boolean = false)

/** An MQTT broker advertised over `_mqtt._tcp`. */
data class BrokerCandidate(val name: String, val host: String, val port: Int)

/**
 * One entity the user can show or hide during the optional pill selection.
 *
 * Flattened out of `PillCandidate` on purpose: the screen only needs a label, an id and a state, and
 * keeping `HaEntity` (with its raw JSON attributes) out of the UI state keeps it cheap to compare.
 */
data class PillOption(
    val entityId: String,
    val label: String,
    val enabled: Boolean,
)

/** One installed app, as shown by the "keep only what matters" step. */
data class OnboardingApp(
    val key: String,
    val packageName: String,
    val label: String,
    val system: Boolean,
    /** Apps the launcher needs; the UI must not let them be hidden. */
    val protected: Boolean = false,
    val recommended: Boolean = false,
)

/**
 * Whole onboarding state. Immutable, owned by [OnboardingViewModel]; screens receive it and emit
 * intents back, so no composable writes to `Prefs` directly.
 *
 * Secrets ([haToken], [mqttPassword]) live here for the duration of the flow only. They are never
 * logged and never written to a `SavedStateHandle` — persistence goes through the encrypted store.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val flags: OnboardingFlags = OnboardingFlags(),
    val isLoading: Boolean = false,

    val systemCapabilities: SystemCapabilities = SystemCapabilities(),
    val justGranted: Capability? = null,

    val gridScale: Float = 1f,
    val gridPreset: GridPreset? = GridPreset.BALANCED,
    val gridManual: Boolean = false,

    val backgroundMode: String = "system",
    val backgroundConfigured: Boolean = false,

    val haDiscovery: DiscoveryState = DiscoveryState.Idle,
    val discoveredHomes: List<HomeCandidate> = emptyList(),
    val selectedHome: HomeCandidate? = null,
    val haUrl: String = "",
    val haToken: String = "",
    val haTest: TestState = TestState.Idle,

    val brokerDiscovery: DiscoveryState = DiscoveryState.Idle,
    val discoveredBrokers: List<BrokerCandidate> = emptyList(),
    val mqttHost: String = "",
    val mqttPort: Int = 1883,
    val mqttAuthEnabled: Boolean = false,
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val mqttDeviceName: String = "",
    val mqttTest: TestState = TestState.Idle,

    val pillOptions: List<PillOption> = emptyList(),
    val pillOptionsLoading: Boolean = false,

    val apps: List<OnboardingApp> = emptyList(),
    val hiddenPackages: Set<String> = emptySet(),
) {
    val haConnected: Boolean get() = haTest is TestState.Success
    val mqttConnected: Boolean get() = mqttTest is TestState.Success

    /** Whether the Home Assistant form is complete enough to be worth testing. */
    val canTestHa: Boolean
        get() = OnboardingUrls.isValidHaUrl(haUrl) && haToken.isNotBlank()

    /** Whether the MQTT form is complete enough to be worth testing. */
    val canTestMqtt: Boolean
        get() = mqttHost.isNotBlank() && mqttPort in 1..65535 &&
            (!mqttAuthEnabled || mqttUsername.isNotBlank())

    /**
     * Redacted on purpose: the generated `toString()` would print the HA token and the broker
     * password, and this object ends up in crash traces and debug logs.
     */
    override fun toString(): String =
        "OnboardingUiState(step=$step, flags=$flags, haUrl=$haUrl, haToken=${redact(haToken)}, " +
            "haTest=$haTest, mqttHost=$mqttHost:$mqttPort, mqttPassword=${redact(mqttPassword)}, " +
            "mqttTest=$mqttTest, hidden=${hiddenPackages.size})"

    private fun redact(secret: String) = if (secret.isBlank()) "<empty>" else "***"
}
