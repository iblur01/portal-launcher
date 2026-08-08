package com.iblu01.portallauncher

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iblu01.portallauncher.domain.MediaSessionBuilder
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.domain.model.PillSnapshot
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ex-`object PillHub` — a Hilt `@Singleton`, `@Inject`-constructed. Consumers receive it by DI:
 * Android entry points via field/constructor injection, the launcher composable via a parameter,
 * the ViewModel via its injected [snapshotFlow] + [callService] (so it stays JVM-unit-testable).
 *
 * The heavy transform (select/media/temperatures) runs **only** in [snapshotFlow] on
 * [Dispatchers.Default] — never on the socket thread. The internal listener is lightweight:
 * it just refreshes the raw-state cache + runs the one-time group auto-init, then notifies the
 * pull-consumers with a trailing debounce.
 */
@Singleton
class PillRepository @Inject constructor(@ApplicationContext private val appContext: Context) {
    private val priorityEngine = PillPriorityEngine(appContext)
    /** Lightweight change notifier for pull-consumers (the weather card). Carries no payload —
     *  consumers read the cache fields below. */
    fun interface Listener { fun onData() }
    private val listeners = CopyOnWriteArraySet<Listener>()

    // The active HA connection. A StateFlow (not a plain var) so [snapshotFlow] can re-bind to a
    // fresh instance on reconnect / config change via flatMapLatest (fixes the stale-capture bug).
    private val activeRepo = MutableStateFlow<HaStateRepository?>(null)
    private var repositoryConfig: String? = null

    // Trailing-edge debounce for the pull-consumer notifications: HA streams state_changed in
    // bursts, and each notify writes Compose state (the weather card). Coalesce to one per window
    // on the main thread. The launcher hot path does NOT use this — it goes through snapshotFlow.
    private val notifyDebounceMs = 100L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val notifyRunnable = Runnable { listeners.forEach { it.onData() } }
    private fun scheduleNotify() {
        mainHandler.removeCallbacks(notifyRunnable)
        mainHandler.postDelayed(notifyRunnable, notifyDebounceMs)
    }

    /** Latest raw HA states, for detail panels / settings that need entity attributes. */
    @Volatile var latestStates: Map<String, HaEntity> = emptyMap()
        private set
    @Volatile var latestDeviceIds: Map<String, String> = emptyMap()
        private set

    /** entity_id -> area display name, from the HA area registry. Empty until registries load. */
    @Volatile var lightAreas: Map<String, String> = emptyMap()
        private set

    fun areaFor(entityId: String): String? = lightAreas[entityId]

    // Forecast/weather/staleness read straight through to the live connection.
    val lastUpdateAt: Long get() = activeRepo.value?.lastUpdateAt ?: 0L
    val weatherEntityId: String? get() = activeRepo.value?.weatherEntityId
    val hourlyForecast: List<ForecastPoint> get() = activeRepo.value?.hourlyForecast ?: emptyList()
    val dailyForecast: List<ForecastPoint> get() = activeRepo.value?.dailyForecast ?: emptyList()

    fun start(prefs: Prefs) {
        val requestedConfig = "${prefs.haUrl.trimEnd('/')}|${prefs.haToken.hashCode()}"
        if (activeRepo.value != null && repositoryConfig == requestedConfig) {
            Log.d("PortalPills", "start ignored: HA connection already active")
            return
        }
        Log.i("PortalPills", "starting: ${prefs.pillRules.count { it.enabled }} enabled rules")
        activeRepo.value?.stop()
        repositoryConfig = requestedConfig
        val repo = HaStateRepository(appContext, prefs.haUrl, prefs.haToken)
        // Lightweight listener: refresh the raw-state cache + one-time auto-init, then notify.
        // No select/media/temperature work here — that lives in snapshotFlow only.
        repo.addListener { states, _ ->
            latestStates = states
            latestDeviceIds = repo.deviceIdByEntity
            lightAreas = repo.areaByEntity
            if (repo.entityRegistryResolved) autoInitGroups(prefs, states, repo.deviceIdByEntity)
            scheduleNotify()
        }
        activeRepo.value = repo   // publish AFTER wiring so snapshotFlow re-binds to a live repo
        repo.start()
    }

    fun stop() { activeRepo.value?.stop(); activeRepo.value = null; repositoryConfig = null }

    fun addListener(listener: Listener) { listeners += listener; listener.onData() }
    fun removeListener(listener: Listener) { listeners -= listener }

    fun callService(domain: String, service: String, entityId: String?, data: Map<String, Any>? = null) {
        activeRepo.value?.callService(domain, service, entityId, data)
    }

    /**
     * One-time group auto-init (design §1: "side-effect isolé, pas dans le map"). Single home for
     * this side-effect so it survives removing it from the transform path; guarded by the
     * persistent [Prefs.pillAutoGroupsInitialized] flag.
     */
    private fun autoInitGroups(prefs: Prefs, states: Map<String, HaEntity>, deviceIds: Map<String, String>) {
        if (states.isEmpty() || prefs.pillAutoGroupsInitialized) return
        val existing = prefs.pillRules
        val existingIds = existing.map { it.entityId }.toSet()
        val autoKinds = setOf(PillKind.LIGHTS, PillKind.MEDIA, PillKind.PURIFIER, PillKind.SCENE, PillKind.PRESENCE, PillKind.ENERGY)
        val additions = PillSupport.candidates(states.values.toList(), deviceIds)
            .filter { it.kind in autoKinds && it.primary.entityId !in existingIds }
            .map(PillSupport::defaultRule)
        prefs.pillRules = existing + additions
        prefs.pillAutoGroupsInitialized = true
        Log.i("PortalPills", "auto-enabled ${additions.size} light/media/purifier entities")
    }

    /**
     * The single transform pipeline (Findings 6/7): raw [HaStateRepository.states] → selected chips
     * + rebuilt media + temperatures, on [Dispatchers.Default].
     *
     * - [flatMapLatest] over [activeRepo] re-binds to a **fresh** connection on reconnect / config
     *   change (fixes the stale-repository-capture bug — no instance is ever captured in a `val`).
     * - [sample] coalesces state_changed bursts so the transform runs at most once per window,
     *   even under sustained 20 push/s load (replaces the old Handler debounce for this path).
     * - `scan` carries the previous emission's primary-media ids to keep the session sort stable.
     * - `prefs.pillRules` is read **once** per emission (was twice), off the SharedPreferences path.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun snapshotFlow(prefs: Prefs): Flow<PillSnapshot> =
        transformSnapshots(
            source = activeRepo.flatMapLatest { repo -> repo?.states() ?: emptyFlow() },
            rulesProvider = { prefs.pillRules },
            haUrl = prefs.haUrl,
        )

    /**
     * The raw-snapshot → [PillSnapshot] transform, extracted with pure inputs ([rulesProvider] read
     * once per emission, [haUrl]) and an injected [dispatcher] so it is JVM-unit-testable without a
     * `Prefs(Context)`. Production runs on [Dispatchers.Default].
     *
     * - [sample] coalesces state_changed bursts so the transform runs at most once per window,
     *   even under sustained 20 push/s load (unlike `debounce`, which stalls a continuous stream).
     * - `scan` carries the previous emission's primary-media ids to keep the session sort stable.
     */
    @OptIn(FlowPreview::class)
    internal fun transformSnapshots(
        source: Flow<com.iblu01.portallauncher.domain.model.HaSnapshot>,
        rulesProvider: () -> List<PillRule>,
        haUrl: String,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
        sampleMs: Long = SNAPSHOT_SAMPLE_MS,
    ): Flow<PillSnapshot> =
        source
            .let { if (sampleMs > 0) it.sample(sampleMs) else it }
            .scan(emptySet<String>() to (null as PillSnapshot?)) { (prevPrimaryIds, _), s ->
                val rules = rulesProvider()   // read once per emission
                val media = MediaSessionBuilder.build(s.states, haUrl, prevPrimaryIds)
                val snapshot = PillSnapshot(
                    chips = priorityEngine.select(rules, s.states),
                    media = media,
                    temperatures = temperatureSummary(rules, s.states),
                    connected = s.connected,
                    latestStates = s.states,
                    areaByEntity = s.areaByEntity,
                    lastUpdateAt = s.lastUpdateAt,
                    weatherEntityId = s.weatherEntityId,
                    hourlyForecast = s.hourlyForecast,
                    dailyForecast = s.dailyForecast,
                )
                val nextPrimaryIds = media.firstOrNull()?.players?.map { it.entityId }?.toSet().orEmpty()
                nextPrimaryIds to snapshot
            }
            .mapNotNull { it.second }
            .flowOn(dispatcher)

    private companion object {
        // Emit at most one transformed snapshot per window; coalesces bursts without starving
        // sustained streams (sample keeps emitting the latest, unlike debounce which would stall).
        const val SNAPSHOT_SAMPLE_MS = 100L
    }

    private fun temperatureSummary(rules: List<PillRule>, states: Map<String, HaEntity>): TemperatureSummary {
        val readings = rules.filter { it.enabled && it.kind == PillKind.CLIMATE }.mapNotNull { rule ->
            val entity = states[rule.entityId] ?: return@mapNotNull null
            if (entity.deviceClass != "temperature") return@mapNotNull null
            entity.state.toDoubleOrNull()?.let { Triple(entity, rule.label, it) }
        }
        val outdoorTokens = Regex("extérieur|exterieur|outdoor|outside|dehors", RegexOption.IGNORE_CASE)
        val outdoor = readings.firstOrNull { (entity, label) -> outdoorTokens.containsMatchIn(entity.entityId) || outdoorTokens.containsMatchIn(label) }
        val indoor = readings.filterNot { it === outdoor }
        fun fmt(value: Double?) = value?.let { if (it % 1.0 == 0.0) "${it.toInt()}°" else "%.1f°".format(it) } ?: "—"
        return TemperatureSummary(fmt(indoor.minOfOrNull { it.third }), fmt(indoor.maxOfOrNull { it.third }), fmt(outdoor?.third))
    }
}
