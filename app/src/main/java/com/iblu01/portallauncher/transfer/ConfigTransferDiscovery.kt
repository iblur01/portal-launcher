package com.iblu01.portallauncher.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.util.ArrayDeque

/**
 * Finds receivers advertising `_portal-config._tcp.` on the local link.
 *
 * Resolution is strictly serial: NsdManager on the API levels this app supports mishandles
 * concurrent [NsdManager.resolveService] calls, so found services queue up and are resolved one at
 * a time. Each resolved record is validated by [ConfigTransferProtocol.parseAdvertisement] — wrong
 * version, malformed session id or fingerprint, or a non-private host address and it is dropped
 * rather than shown to the user.
 *
 * [start]'s callback delivers an immutable snapshot of the current candidates, on an NsdManager
 * worker thread; a ViewModel should hand it straight to a StateFlow rather than mutate UI from it.
 */
class ConfigTransferDiscovery(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val lock = Any()
    private val candidates = LinkedHashMap<String, ConfigTransferCandidate>()
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null
    private var onCandidates: ((List<ConfigTransferCandidate>) -> Unit)? = null

    val isDiscovering: Boolean get() = synchronized(lock) { listener != null }

    fun start(onCandidates: (List<ConfigTransferCandidate>) -> Unit): Boolean {
        val manager = nsd ?: return false
        val discovery = synchronized(lock) {
            if (listener != null) return false
            candidates.clear()
            pending.clear()
            resolving = false
            this.onCandidates = onCandidates
            discoveryListener().also { listener = it }
        }
        return runCatching {
            manager.discoverServices(
                ConfigTransferProtocol.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discovery,
            )
        }.onFailure {
            Log.w(TAG, "could not start discovery: ${it.javaClass.simpleName}")
            synchronized(lock) { listener = null; this.onCandidates = null }
        }.isSuccess
    }

    /** Idempotent. Drops the callback first so no snapshot arrives after the caller is gone. */
    fun stop() {
        val manager = nsd ?: return
        val current = synchronized(lock) {
            val previous = listener
            listener = null
            onCandidates = null
            pending.clear()
            candidates.clear()
            previous
        } ?: return
        runCatching { manager.stopServiceDiscovery(current) }
            .onFailure { Log.w(TAG, "could not stop discovery: ${it.javaClass.simpleName}") }
    }

    private fun discoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) = Unit

        override fun onServiceFound(service: NsdServiceInfo?) {
            val found = service ?: return
            if (found.serviceType?.trimEnd('.') !=
                ConfigTransferProtocol.SERVICE_TYPE.trimEnd('.')
            ) {
                return
            }
            synchronized(lock) {
                if (listener == null) return
                pending.addLast(found)
            }
            resolveNext()
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            val name = service?.serviceName ?: return
            val snapshot = synchronized(lock) {
                if (candidates.remove(name) == null) return
                candidates.values.toList()
            }
            emit(snapshot)
        }

        override fun onDiscoveryStopped(serviceType: String?) = Unit

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.w(TAG, "start discovery failed ($errorCode)")
            synchronized(lock) { listener = null; onCandidates = null }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.w(TAG, "stop discovery failed ($errorCode)")
        }
    }

    /** Takes the next queued service if nothing is being resolved; otherwise returns immediately. */
    private fun resolveNext() {
        val manager = nsd ?: return
        val next = synchronized(lock) {
            if (listener == null || resolving) return
            val head = pending.pollFirst() ?: return
            resolving = true
            head
        }
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo?) {
                resolved?.let(::accept)
                finishResolve()
            }

            override fun onResolveFailed(service: NsdServiceInfo?, errorCode: Int) {
                Log.w(TAG, "resolve failed ($errorCode)")
                finishResolve()
            }
        }
        runCatching { manager.resolveService(next, resolveListener) }
            .onFailure {
                Log.w(TAG, "could not resolve: ${it.javaClass.simpleName}")
                finishResolve()
            }
    }

    private fun finishResolve() {
        synchronized(lock) { resolving = false }
        resolveNext()
    }

    private fun accept(resolved: NsdServiceInfo) {
        val host = resolved.host?.hostAddress ?: return
        val attributes = resolved.attributes.orEmpty()
            .mapNotNull { (key, value) ->
                key?.let { it to String(value ?: ByteArray(0), Charsets.UTF_8) }
            }
            .toMap()
        val candidate = ConfigTransferProtocol.parseAdvertisement(
            serviceName = resolved.serviceName.orEmpty(),
            host = host,
            port = resolved.port,
            attributes = attributes,
        ) ?: return
        val snapshot = synchronized(lock) {
            if (listener == null) return
            if (candidates.put(candidate.serviceName, candidate) == candidate) return
            candidates.values.toList()
        }
        emit(snapshot)
    }

    private fun emit(snapshot: List<ConfigTransferCandidate>) {
        synchronized(lock) { onCandidates }?.invoke(snapshot)
    }

    private companion object {
        private const val TAG = "ConfigDiscovery"
    }
}
