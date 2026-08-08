package com.iblu01.portallauncher
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.domain.model.HaSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.iblu01.portallauncher.ui.icons.HaIcons
import com.iblu01.portallauncher.ui.icons.IconRef
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.net.Proxy

class HaStateRepository(appContext: Context, private val url: String, private val token: String) {
    fun interface Listener { fun onStates(states: Map<String, HaEntity>, connected: Boolean) }
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val client = OkHttpClient.Builder()
        // Local Home Assistant traffic must not inherit the tablet's HTTP proxy.
        .proxy(Proxy.NO_PROXY)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init { HaIcons.init(appContext, client) }

    /** Custom icon-set modules registered in the frontend, from `lovelace/resources`. */
    @Volatile private var iconResourceUrls: List<String> = emptyList()
    /** Icon-pack downloads run here so socket callbacks never touch the network or the disk. */
    @Volatile private var iconExecutor = newIconExecutor()
    private fun newIconExecutor() = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ha-icon-packs").apply { isDaemon = true }
    }
    private val states = linkedMapOf<String, HaEntity>()
    private var socket: WebSocket? = null
    private var connected = false
    private var enabled = false
    private val retryHandler = Handler(Looper.getMainLooper())
    private val watchdogHandler = Handler(Looper.getMainLooper())

    /** Wall-clock of the last frame received (any message, incl. pong). Drives the liveness watchdog. */
    @Volatile private var lastActivityAt = 0L

    /** Wall-clock of the last real state update (get_states / state_changed). Surfaced to the UI for staleness. */
    @Volatile var lastUpdateAt = 0L
        private set

    /** First weather.* entity found, and its subscribed forecasts. */
    @Volatile var weatherEntityId: String? = null
        private set
    @Volatile var hourlyForecast: List<ForecastPoint> = emptyList()
        private set
    @Volatile var dailyForecast: List<ForecastPoint> = emptyList()
        private set

    /** entity_id -> area display name, resolved from the HA area/entity/device registries. */
    @Volatile var areaByEntity: Map<String, String> = emptyMap()
        private set
    @Volatile var deviceIdByEntity: Map<String, String> = emptyMap()
        private set
    @Volatile var entityRegistryResolved: Boolean = false
        private set
    private var areaNames: Map<String, String> = emptyMap()        // area_id -> name
    private var entityAreaId: Map<String, String?> = emptyMap()    // entity_id -> area_id
    private var entityDeviceId: Map<String, String?> = emptyMap()  // entity_id -> device_id
    private var deviceAreaId: Map<String, String?> = emptyMap()    // device_id -> area_id

    fun addListener(listener: Listener) { listeners += listener; listener.onStates(states.toMap(), connected) }
    fun removeListener(listener: Listener) { listeners -= listener }

    /**
     * Cold [Flow] of raw HA snapshots (Findings 6, 7). Bridges the fire-and-forget [Listener]
     * callback to structured concurrency: registers a listener (which emits the current state
     * immediately), re-emits on every push, and unregisters on cancellation.
     *
     * Note: `trySend` runs on whatever thread fires the listener (the OkHttp socket thread) — it
     * only offers to the channel buffer, which is cheap. `flowOn(IO)` moves the subscription setup
     * (`addListener`/`awaitClose`), not the emission; the real transform work is moved off-thread
     * downstream by the collector's `flowOn(Default)`.
     */
    fun states(): Flow<HaSnapshot> = callbackFlow {
        val listener = Listener { states, connected ->
            trySend(
                HaSnapshot(
                    states = states,
                    connected = connected,
                    areaByEntity = areaByEntity,
                    lastUpdateAt = lastUpdateAt,
                    weatherEntityId = weatherEntityId,
                    hourlyForecast = hourlyForecast,
                    dailyForecast = dailyForecast,
                    deviceIdByEntity = deviceIdByEntity,
                    entityRegistryResolved = entityRegistryResolved,
                )
            )
        }
        addListener(listener)
        awaitClose { removeListener(listener) }
    }.flowOn(Dispatchers.IO)
    fun start() {
        enabled = true
        if (token.isBlank()) { Log.w(TAG, "start skipped: token is blank"); return }
        if (socket != null) { Log.d(TAG, "start skipped: socket already active"); return }
        entityRegistryResolved = false
        deviceIdByEntity = emptyMap()
        entityDeviceId = emptyMap()
        if (iconExecutor.isShutdown) iconExecutor = newIconExecutor()
        val wsUrl = url.trimEnd('/').replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/api/websocket"
        Log.i(TAG, "connecting to ${url.trimEnd('/')} (token present)")
        lastActivityAt = System.currentTimeMillis()
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), WsListener())
        scheduleWatchdog()
    }
    fun stop() { enabled = false; retryHandler.removeCallbacksAndMessages(null); watchdogHandler.removeCallbacksAndMessages(null); socket?.close(1000, "screen stopped"); socket = null; connected = false; iconExecutor.shutdownNow(); notifyListeners() }
    private fun reconnect() {
        if (!enabled || token.isBlank()) return
        retryHandler.removeCallbacksAndMessages(null)
        retryHandler.postDelayed({ if (enabled && socket == null) start() }, 5_000)
    }

    /** Periodic liveness probe: pings HA, and if no frame arrives for [STALE_MS] tears the socket down and reconnects.
     *  Catches sockets that die silently (device doze, NAT drop) without firing onFailure/onClosed. */
    private val watchdog = object : Runnable {
        override fun run() {
            val current = socket
            if (enabled && current != null) {
                val silentMs = System.currentTimeMillis() - lastActivityAt
                if (connected && silentMs > STALE_MS) {
                    Log.w(TAG, "watchdog: no frame for ${silentMs}ms; forcing reconnect")
                    forceReconnect()
                    return
                }
                val id = synchronized(this@HaStateRepository) { msgIdCounter++ }
                runCatching { current.send("{\"id\":$id,\"type\":\"ping\"}") }
            }
            watchdogHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }
    private fun scheduleWatchdog() {
        watchdogHandler.removeCallbacks(watchdog)
        watchdogHandler.postDelayed(watchdog, HEARTBEAT_MS)
    }
    private fun forceReconnect() {
        connected = false
        socket?.close(4000, "watchdog: stale connection")
        socket = null
        notifyListeners()
        start()
    }
    private fun notifyListeners() {
        com.iblu01.portallauncher.ui.ConnectionStatus.haConnected = connected
        val copy = synchronized(states) { states.toMap() }
        listeners.forEach { it.onStates(copy, connected) }
    }
    private fun parseAreaNames(arr: JSONArray): Map<String, String> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .mapNotNull { o ->
                val id = o.optString("area_id"); val name = o.optString("name")
                if (id.isBlank() || name.isBlank()) null else id to name
            }.toMap()

    private fun parseEntityRegistry(arr: JSONArray) {
        val areaMap = HashMap<String, String?>(); val devMap = HashMap<String, String?>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val eid = o.optString("entity_id"); if (eid.isBlank()) continue
            areaMap[eid] = o.optString("area_id").takeIf { it.isNotBlank() && !o.isNull("area_id") }
            devMap[eid] = o.optString("device_id").takeIf { it.isNotBlank() && !o.isNull("device_id") }
        }
        entityAreaId = areaMap; entityDeviceId = devMap
        deviceIdByEntity = devMap.mapNotNull { (entityId, deviceId) -> deviceId?.let { entityId to it } }.toMap()
    }

    /** JS-module resources only; a CSS or HTML resource can never register an icon set. */
    private fun parseIconResources(arr: JSONArray): List<String> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .filter { it.optString("type") in setOf("module", "js") }
            .mapNotNull { it.optString("url").takeIf { url -> url.isNotBlank() } }

    /**
     * Fetches whatever custom-namespace icons the current states reference and are not cached yet.
     * Cheap and idempotent when everything is already on disk, so it can be called on every trigger;
     * the real work is serialised onto [iconExecutor], off the socket thread.
     */
    private fun syncIconPacks() {
        val store = HaIcons.packs ?: return
        val urls = iconResourceUrls
        if (urls.isEmpty()) return
        iconExecutor.execute {
            val snapshot = synchronized(states) { states.values.toList() }
            val wanted = snapshot.mapNotNullTo(mutableSetOf<IconRef>()) {
                HaIcons.resolver.refFor(it)?.takeUnless { ref -> ref.isMdi }
            }
            if (store.sync(url, token, urls, wanted)) HaIcons.revision++
        }
    }

    private fun parseDeviceAreas(arr: JSONArray): Map<String, String?> {
        val m = HashMap<String, String?>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id"); if (id.isBlank()) continue
            m[id] = o.optString("area_id").takeIf { it.isNotBlank() && !o.isNull("area_id") }
        }
        return m
    }

    /** Rebuild entity->area name once registries arrive; area on the entity wins, else its device's area. */
    private fun resolveAreas() {
        if (areaNames.isEmpty() || entityAreaId.isEmpty()) return
        val resolved = entityAreaId.keys.mapNotNull { eid ->
            val areaId = entityAreaId[eid] ?: entityDeviceId[eid]?.let { deviceAreaId[it] }
            val name = areaId?.let { areaNames[it] } ?: return@mapNotNull null
            eid to name
        }.toMap()
        if (resolved != areaByEntity) { areaByEntity = resolved; notifyListeners() }
    }

    private fun parseState(o: JSONObject): HaEntity? {
        val id = o.optString("entity_id"); if (id.isBlank()) return null
        return HaEntity(id, o.optString("state"), o.optJSONObject("attributes") ?: JSONObject(), o.optString("last_changed"))
    }
    private fun parseForecast(arr: JSONArray): List<ForecastPoint> =
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { o ->
            ForecastPoint(
                datetime = o.optString("datetime"),
                temp = o.optDouble("temperature", Double.NaN).let { if (it.isNaN()) 0.0 else it },
                tempLow = o.optDouble("templow", Double.NaN).let { if (it.isNaN()) null else it },
                condition = o.optString("condition"),
            )
        }
    private inner class WsListener : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            lastActivityAt = System.currentTimeMillis()
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (msg.optString("type")) {
                "auth_required" -> webSocket.send(JSONObject().put("type", "auth").put("access_token", token).toString())
                "auth_ok" -> {
                    connected = true; Log.i(TAG, "authenticated; requesting states")
                    webSocket.send("{\"id\":1,\"type\":\"get_states\"}")
                    webSocket.send("{\"id\":3,\"type\":\"config/area_registry/list\"}")
                    webSocket.send("{\"id\":4,\"type\":\"config/entity_registry/list\"}")
                    webSocket.send("{\"id\":5,\"type\":\"config/device_registry/list\"}")
                }
                "auth_invalid" -> { connected = false; Log.e(TAG, "authentication rejected by Home Assistant"); notifyListeners() }
                "result" -> {
                    val id = msg.optInt("id")
                    if (id == 1 && msg.optBoolean("success")) {
                        val result = msg.optJSONArray("result") ?: JSONArray()
                        synchronized(states) { states.clear(); for (i in 0 until result.length()) parseState(result.optJSONObject(i) ?: continue)?.let { states[it.entityId] = it } }
                        lastUpdateAt = System.currentTimeMillis()
                        Log.i(TAG, "received ${result.length()} states; subscribing to changes")
                        // Id must exceed the registry list ids (3,4,5) — HA requires per-connection ids to strictly increase.
                        notifyListeners(); webSocket.send("{\"id\":6,\"type\":\"subscribe_events\",\"event_type\":\"state_changed\"}")
                        weatherEntityId = synchronized(states) { states.keys.firstOrNull { it.startsWith("weather.") } }
                        weatherEntityId?.let { w ->
                            webSocket.send("{\"id\":$FORECAST_HOURLY_ID,\"type\":\"weather/subscribe_forecast\",\"forecast_type\":\"hourly\",\"entity_id\":\"$w\"}")
                            webSocket.send("{\"id\":$FORECAST_DAILY_ID,\"type\":\"weather/subscribe_forecast\",\"forecast_type\":\"daily\",\"entity_id\":\"$w\"}")
                        }
                        // HA's own per-domain/device-class icon defaults, plus the custom icon-set
                        // modules the frontend loads. Sent last: HA requires strictly increasing ids.
                        webSocket.send("{\"id\":$ICONS_ID,\"type\":\"frontend/get_icons\",\"category\":\"entity_component\"}")
                        webSocket.send("{\"id\":$RESOURCES_ID,\"type\":\"lovelace/resources\"}")
                    } else if (id == ICONS_ID) {
                        if (msg.optBoolean("success")) {
                            HaIcons.resolver.componentIcons = msg.optJSONObject("result")?.optJSONObject("resources")
                            notifyListeners()
                            syncIconPacks()
                        } else Log.w(TAG, "frontend/get_icons failed: ${msg.optJSONObject("error")}")
                    } else if (id == RESOURCES_ID) {
                        // Absent on YAML-mode dashboards, and forbidden for non-admin tokens: custom
                        // icon namespaces simply stay unresolved and fall back, which is fine.
                        if (msg.optBoolean("success")) {
                            iconResourceUrls = parseIconResources(msg.optJSONArray("result") ?: JSONArray())
                            syncIconPacks()
                        } else Log.i(TAG, "lovelace/resources unavailable; custom icon sets disabled")
                    } else if (id in 3..5) {
                        val success = msg.optBoolean("success")
                        val result = if (success) msg.optJSONArray("result") ?: JSONArray() else JSONArray()
                        when (id) {
                            3 -> if (success) areaNames = parseAreaNames(result)
                            4 -> {
                                parseEntityRegistry(result)
                                entityRegistryResolved = true
                                // Wake consumers even when the registry is empty/forbidden: they
                                // can now safely use the legacy fallback instead of racing id=4.
                                notifyListeners()
                            }
                            5 -> if (success) deviceAreaId = parseDeviceAreas(result)
                        }
                        resolveAreas()
                    } else if (id == 6) {
                        if (!msg.optBoolean("success")) Log.e(TAG, "subscribe_events failed: ${msg.optJSONObject("error")}")
                    } else if (id >= 100) {
                        if (msg.optBoolean("success")) Log.i(TAG, "service call $id succeeded")
                        else Log.e(TAG, "service call $id failed: ${msg.optJSONObject("error")}")
                    }
                }
                "event" -> {
                    val event = msg.optJSONObject("event") ?: return
                    val eventId = msg.optInt("id")
                    if (eventId == FORECAST_HOURLY_ID || eventId == FORECAST_DAILY_ID) {
                        val list = parseForecast(event.optJSONArray("forecast") ?: JSONArray())
                        if (eventId == FORECAST_HOURLY_ID) hourlyForecast = list else dailyForecast = list
                        notifyListeners()
                        return
                    }
                    val data = event.optJSONObject("data") ?: return
                    val entity = data.optJSONObject("new_state")?.let(::parseState)
                    val id = data.optString("entity_id")
                    synchronized(states) { if (entity == null) states.remove(id) else states[entity.entityId] = entity }
                    lastUpdateAt = System.currentTimeMillis()
                    notifyListeners()
                    // An entity that just started pointing at a custom namespace needs its icon
                    // fetched. Checked in-memory here; the disk lookup happens on the executor.
                    if (entity != null && HaIcons.resolver.refFor(entity)?.isMdi == false) syncIconPacks()
                }
            }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (socket !== webSocket) {
                Log.d(TAG, "ignoring failure from stale WebSocket")
                return
            }
            Log.w(TAG, "WebSocket failed; scheduling reconnect", t)
            connected = false
            socket = null
            notifyListeners()
            reconnect()
        }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (socket !== webSocket) {
                Log.d(TAG, "ignoring close from stale WebSocket: code=$code")
                return
            }
            Log.w(TAG, "WebSocket closed: code=$code reason=$reason; scheduling reconnect")
            connected = false
            socket = null
            notifyListeners()
            reconnect()
        }
    }

    private var msgIdCounter = 100

    fun callService(domain: String, service: String, entityId: String?, data: Map<String, Any>? = null) {
        val socketRef = socket ?: return
        val id = synchronized(this) { msgIdCounter++ }
        val msg = JSONObject().apply {
            put("id", id)
            put("type", "call_service")
            put("domain", domain)
            put("service", service)
            val serviceData = JSONObject()
            if (entityId != null) {
                serviceData.put("entity_id", entityId)
            }
            data?.forEach { (k, v) ->
                serviceData.put(
                    k,
                    when (v) {
                        is Collection<*> -> JSONArray(v)
                        is Array<*> -> JSONArray(v.toList())
                        else -> v
                    }
                )
            }
            if (serviceData.length() > 0) {
                put("service_data", serviceData)
            }
        }
        socketRef.send(msg.toString())
        Log.i(TAG, "service call $id sent: $domain.$service target=${entityId.orEmpty()}")
    }

    private companion object {
        const val TAG = "PortalHaState"
        const val HEARTBEAT_MS = 30_000L   // ping cadence
        const val STALE_MS = 75_000L       // ~2 missed pings before force-reconnect
        const val FORECAST_HOURLY_ID = 7
        const val FORECAST_DAILY_ID = 8
        const val ICONS_ID = 9
        const val RESOURCES_ID = 10
    }
}
