package com.iblu01.portallauncher.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Publishes `_portal-config._tcp.` while onboarding waits for a configuration.
 *
 * The record is broadcast to the whole link, so it carries strictly public material: protocol
 * version, the session id (a capability only in combination with the server's own checks), a display
 * name for the consent prompt, and the SHA-256 fingerprint of the receiver's ephemeral public key.
 * No token, no key, no configuration value ever goes in here.
 *
 * Registration is asynchronous; [start] only means the request was submitted. The platform may rename
 * the service on a name conflict, which is why [onRegistered] reports the name actually published.
 */
class ConfigTransferAdvertiser(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager

    private var listener: NsdManager.RegistrationListener? = null

    val isAdvertising: Boolean get() = listener != null

    /**
     * Registers the service. [attributes] should come from
     * [ConfigReceiverServer.advertisementAttributes]; [port] from the running server's listening port.
     */
    fun start(
        serviceName: String,
        port: Int,
        attributes: Map<String, String>,
        onRegistered: (String) -> Unit = {},
        onFailure: (ConfigTransferFailure) -> Unit = {},
    ): Boolean {
        val manager = nsd ?: return false
        if (listener != null) return false

        val info = NsdServiceInfo().apply {
            this.serviceName = ConfigTransferProtocol.sanitizeDisplayName(serviceName)
                .ifEmpty { DEFAULT_SERVICE_NAME }
            serviceType = ConfigTransferProtocol.SERVICE_TYPE
            this.port = port
            attributes.forEach { (key, value) -> setAttribute(key, value) }
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                onRegistered(registered.serviceName.orEmpty())
            }

            override fun onRegistrationFailed(service: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "registration failed ($errorCode)")
                listener = null
                onFailure(ConfigTransferFailure.NETWORK)
            }

            override fun onServiceUnregistered(service: NsdServiceInfo?) = Unit

            override fun onUnregistrationFailed(service: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "unregistration failed ($errorCode)")
            }
        }
        listener = registration
        return runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration) }
            .onFailure {
                // Class name only: an NSD error can echo back the record we submitted.
                Log.w(TAG, "could not advertise: ${it.javaClass.simpleName}")
                listener = null
            }
            .isSuccess
    }

    /** Idempotent: safe to call from onStop / onDestroy without tracking whether start() succeeded. */
    fun stop() {
        val manager = nsd ?: return
        val current = listener ?: return
        listener = null
        runCatching { manager.unregisterService(current) }
            .onFailure { Log.w(TAG, "could not stop advertising: ${it.javaClass.simpleName}") }
    }

    private companion object {
        private const val TAG = "ConfigAdvertiser"
        private const val DEFAULT_SERVICE_NAME = "Portal Launcher"
    }
}
