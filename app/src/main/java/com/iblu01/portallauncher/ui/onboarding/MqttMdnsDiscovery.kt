package com.iblu01.portallauncher.ui.onboarding

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Optional discovery of MQTT brokers advertising `_mqtt._tcp`.
 *
 * Most Home Assistant installs do **not** advertise their broker, so this finds nothing on plenty
 * of networks — which is why the Home Assistant host stays the primary suggestion and manual entry
 * is always available. Nothing about MQTT credentials is discoverable; only an address can be.
 *
 * Mirrors [com.iblu01.portallauncher.HaMdnsDiscovery]: callbacks land on the main thread and
 * resolves are serialised, since NSD resolves one service at a time on these API levels.
 */
class MqttMdnsDiscovery(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val main = Handler(Looper.getMainLooper())

    private var listener: NsdManager.DiscoveryListener? = null
    private var onUpdate: ((List<BrokerCandidate>) -> Unit)? = null

    private val found = LinkedHashMap<String, BrokerCandidate>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start(onUpdate: (List<BrokerCandidate>) -> Unit) {
        val manager = nsd ?: return
        if (listener != null) return
        this.onUpdate = onUpdate
        found.clear(); pending.clear(); resolving = false

        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $errorCode")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                main.post { pending.addLast(service); resolveNext() }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
        }
        listener = discovery
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices threw", e)
            listener = null
        }
    }

    fun stop() {
        val manager = nsd ?: return
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        onUpdate = null
        pending.clear(); resolving = false; found.clear()
    }

    private fun resolveNext() {
        if (resolving) return
        val manager = nsd ?: return
        val service = pending.removeFirstOrNull() ?: return
        resolving = true
        manager.runCatchingResolve(service) { resolved ->
            val host = resolved?.host?.hostAddress
            if (resolved != null && !host.isNullOrBlank() && resolved.port > 0 && listener != null) {
                val candidate = BrokerCandidate(
                    name = resolved.serviceName.ifBlank { host },
                    host = host,
                    port = resolved.port,
                )
                if (found.put("$host:${resolved.port}", candidate) == null) {
                    onUpdate?.invoke(found.values.toList())
                }
            }
            resolving = false
            resolveNext()
        }
    }

    private fun NsdManager.runCatchingResolve(
        service: NsdServiceInfo,
        onDone: (NsdServiceInfo?) -> Unit,
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                main.post { onDone(null) }
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                main.post { onDone(serviceInfo) }
            }
        }
        try {
            resolveService(service, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "resolveService threw", e)
            main.post { onDone(null) }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_mqtt._tcp."
        const val TAG = "MqttMdnsDiscovery"
    }
}
