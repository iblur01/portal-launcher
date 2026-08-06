package com.iblu01.portallauncher.ui.onboarding

import com.iblu01.portallauncher.HaApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the Home Assistant check the onboarding shows step by step.
 *
 * [onPhase] is called as the check progresses so the screen can name what it is doing rather than
 * spin. Credentials are only ever passed through to [HaApiClient]; nothing is logged here.
 */
@Singleton
class HaOnboardingTester @Inject constructor() {

    suspend fun test(url: String, token: String, onPhase: (TestPhase) -> Unit): TestState =
        withContext(Dispatchers.IO) {
            val baseUrl = OnboardingUrls.normalizeHaUrl(url)
            val cleanToken = token.trim()
            if (!OnboardingUrls.isValidHaUrl(baseUrl) || cleanToken.isEmpty()) {
                return@withContext TestState.Failure(OnboardingError.HOST_UNREACHABLE)
            }
            val client = HaApiClient(baseUrl, cleanToken)

            onPhase(TestPhase.CHECKING_ADDRESS)
            val reachable = client.testConnection()
            if (!reachable.ok) {
                return@withContext TestState.Failure(
                    OnboardingDiagnostics.classifyHaFailure(reachable.statusCode, reachable.body)
                )
            }

            onPhase(TestPhase.AUTHENTICATING)
            onPhase(TestPhase.FETCHING_ENTITIES)
            val states = client.getStates()
            if (!states.ok) {
                return@withContext TestState.Failure(
                    OnboardingDiagnostics.classifyHaFailure(states.statusCode, states.body)
                )
            }
            val summary = OnboardingDiagnostics.summarize(states.body)
            if (summary.entityCount == 0) {
                TestState.Failure(OnboardingError.INVALID_RESPONSE)
            } else {
                TestState.Success(summary)
            }
        }
}

/**
 * Verifies the MQTT bridge end to end rather than just opening a socket: connect, subscribe,
 * publish to a device-scoped test topic, and wait for the broker to hand the message back. A broker
 * that accepts the connection but refuses the topic ACL is a failure the user needs to see now, not
 * the first time Home Assistant fails to show the panel.
 */
@Singleton
class MqttOnboardingTester @Inject constructor() {

    suspend fun test(
        host: String,
        port: Int,
        username: String,
        password: String,
        deviceId: String,
        onPhase: (TestPhase) -> Unit,
    ): TestState = withContext(Dispatchers.IO) {
        val uri = "tcp://${host.trim()}:${port.coerceIn(1, 65535)}"
        val topic = "portal/$deviceId/onboarding/test"
        var phase = TestPhase.CONNECTING_BROKER
        var client: MqttClient? = null
        try {
            onPhase(phase)
            client = MqttClient(uri, "portal-onboarding-${System.currentTimeMillis()}", MemoryPersistence())
            client.timeToWait = CONNECT_TIMEOUT_MS
            client.connect(
                MqttConnectOptions().apply {
                    isCleanSession = true
                    connectionTimeout = (CONNECT_TIMEOUT_MS / 1000).toInt()
                    keepAliveInterval = 10
                    if (username.trim().isNotEmpty()) {
                        userName = username.trim()
                        this.password = password.toCharArray()
                    }
                }
            )

            // Subscribe first: the roundtrip only proves anything if the listener is already armed
            // when the publish lands.
            phase = TestPhase.VERIFYING_ROUNDTRIP
            val received = CountDownLatch(1)
            client.subscribe(topic, 0, IMqttMessageListener { _, _ -> received.countDown() })

            phase = TestPhase.PUBLISHING_DEVICE
            onPhase(TestPhase.PUBLISHING_DEVICE)
            val payload = """{"source":"onboarding","ts":${System.currentTimeMillis()}}"""
            client.publish(topic, MqttMessage(payload.toByteArray()).apply { qos = 0; isRetained = false })

            phase = TestPhase.VERIFYING_ROUNDTRIP
            onPhase(TestPhase.VERIFYING_ROUNDTRIP)
            val roundtrip = received.await(ROUNDTRIP_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            // Clear the retained slot even though we published non-retained: a broker configured to
            // force retention would otherwise leave the test message behind.
            runCatching {
                client.publish(topic, MqttMessage(ByteArray(0)).apply { qos = 0; isRetained = true })
            }

            if (!roundtrip) {
                TestState.Failure(OnboardingError.SUBSCRIBE_FORBIDDEN)
            } else {
                TestState.Success(TestSummary(features = MqttFeature.values().toList()))
            }
        } catch (e: MqttException) {
            TestState.Failure(OnboardingDiagnostics.classifyMqttFailure(e.reasonCode, e.message, phase))
        } catch (e: Exception) {
            TestState.Failure(OnboardingDiagnostics.classifyMqttFailure(0, e.message, phase))
        } finally {
            runCatching { client?.disconnect(1_000) }
            runCatching { client?.close() }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val ROUNDTRIP_TIMEOUT_MS = 5_000L
    }
}
