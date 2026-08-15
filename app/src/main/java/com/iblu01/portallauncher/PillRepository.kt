package com.iblu01.portallauncher

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iblu01.portallauncher.domain.MediaSessionBuilder
import com.iblu01.portallauncher.domain.home.CameraPreferences
import com.iblu01.portallauncher.domain.home.HomePageBuilder
import com.iblu01.portallauncher.domain.home.HomePillComposer
import com.iblu01.portallauncher.domain.home.HomePillPreferences
import com.iblu01.portallauncher.domain.home.PillCatalogBuilder
import com.iblu01.portallauncher.domain.home.PillCatalogSnapshot
import com.iblu01.portallauncher.domain.home.PillRef
import com.iblu01.portallauncher.domain.home.ResolvedPill
import com.iblu01.portallauncher.domain.model.ForecastPoint
import com.iblu01.portallauncher.domain.model.PillSnapshot
import com.iblu01.portallauncher.domain.model.TemperatureSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.scan
import java.util.concurrent.CopyOnWriteArraySet
import java.util.Locale
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
    private val catalogBuilder = PillCatalogBuilder(priorityEngine)
    /** Lightweight change notifier for pull-consumers (the weather card). Carries no payload —
     *  consumers read the cache fields below. */
    fun interface Listener { fun onData() }
    private val listeners = CopyOnWriteArraySet<Listener>()

    // The active HA connection. A StateFlow (not a plain var) so [snapshotFlow] can re-bind to a
    // fresh instance on reconnect / config change via flatMapLatest (fixes the stale-capture bug).
    private val activeRepo = MutableStateFlow<HaStateRepository?>(null)
    private var repositoryConfig: String? = null
    /** Changes whenever the backing HA server or credentials change. */
    @Volatile var connectionGeneration: Long = 0L
        private set

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
    @Volatile var latestAreaIdByEntity: Map<String, String> = emptyMap()
        private set
    @Volatile var latestAreaNameById: Map<String, String> = emptyMap()
        private set
    @Volatile var latestConnected: Boolean = false
        private set
    /** Last complete catalog projected by the active snapshot flow, useful to Settings. */
    @Volatile var latestCatalog: PillCatalogSnapshot = emptyCatalog()
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
        connectionGeneration++
        repositoryConfig = requestedConfig
        val repo = HaStateRepository(appContext, prefs.haUrl, prefs.haToken)
        // Lightweight listener: refresh the raw-state cache + one-time auto-init, then notify.
        // No select/media/temperature work here — that lives in snapshotFlow only.
        repo.addListener { states, connected ->
            latestStates = states
            latestConnected = connected
            latestDeviceIds = repo.deviceIdByEntity
            lightAreas = repo.areaByEntity
            latestAreaIdByEntity = repo.areaIdByEntity
            latestAreaNameById = repo.areaNameById
            if (repo.entityRegistryResolved) {
                migrateNoisePolicy(prefs, states, repo.deviceIdByEntity, repo.entityCategoryByEntity)
                autoInitGroups(prefs, states, repo.deviceIdByEntity)
            }
            scheduleNotify()
        }
        activeRepo.value = repo   // publish AFTER wiring so snapshotFlow re-binds to a live repo
        repo.start()
    }

    fun stop() {
        activeRepo.value?.stop()
        activeRepo.value = null
        repositoryConfig = null
        latestConnected = false
    }

    fun addListener(listener: Listener) { listeners += listener; listener.onData() }
    fun removeListener(listener: Listener) { listeners -= listener }

    fun callService(
        domain: String,
        service: String,
        entityId: String?,
        data: Map<String, Any>? = null,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        val repo = activeRepo.value
        if (repo == null) {
            onResult?.invoke(false)
            return
        }
        repo.callService(domain, service, entityId, data, onResult)
    }

    /** entity_id -> integration that created it. Empty until the entity registry loads. */
    val entityPlatformByEntity: Map<String, String>
        get() = activeRepo.value?.entityPlatformByEntity.orEmpty()

    /**
     * One-shot websocket request on the active connection (see [HaStateRepository.request]).
     * [onResult] receives null when nothing is connected, so a caller always gets an answer.
     */
    fun request(payload: org.json.JSONObject, onResult: (org.json.JSONObject?) -> Unit) {
        val repo = activeRepo.value
        if (repo == null) {
            onResult(null)
            return
        }
        repo.request(payload, onResult)
    }

    /** Atomic preference reducer used by launcher, Maison and Settings entry points. */
    fun updateHomePillPreferences(
        prefs: Prefs,
        transform: (HomePillPreferences) -> HomePillPreferences,
    ): HomePillPreferences = prefs.updateHomePillPreferences(transform)

    /**
     * Enables/disables one individual pill rule and publishes the same settings event as the
     * Settings screen. Disabling never deletes pins or manual membership, so re-enabling restores
     * the device at its previous position.
     */
    fun setPillEnabled(prefs: Prefs, pill: ResolvedPill, enabled: Boolean): Boolean {
        val device = pill.ref as? PillRef.Device ?: return false
        val current = prefs.pillRules
        val index = current.indexOfFirst { it.entityId == device.entityId }
        val updated = if (index >= 0) {
            current.mapIndexed { ruleIndex, rule ->
                if (ruleIndex == index) rule.copy(enabled = enabled) else rule
            }
        } else {
            current + PillRule(
                entityId = device.entityId,
                kind = pill.chip.kind,
                label = pill.chip.label,
                enabled = enabled,
                relatedEntityIds = pill.chip.details.mapNotNull { detail ->
                    detail.entityId.takeIf(String::isNotBlank)
                },
            )
        }
        if (updated == current) return false
        prefs.pillRules = updated
        SettingsChangeBus.get().emit(PILL_RULES_CHANGE_KEY)
        return true
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
        val autoKinds = setOf(PillKind.LIGHTS, PillKind.MEDIA, PillKind.PURIFIER)
        val additions = PillSupport.candidates(states.values.toList(), deviceIds)
            .filter { it.kind in autoKinds && it.primary.entityId !in existingIds }
            .map(PillSupport::defaultRule)
        prefs.pillRules = existing + additions
        prefs.pillAutoGroupsInitialized = true
        Log.i("PortalPills", "auto-enabled ${additions.size} light/media/purifier entities")
    }

    private fun migrateNoisePolicy(
        prefs: Prefs,
        states: Map<String, HaEntity>,
        deviceIds: Map<String, String>,
        entityCategories: Map<String, String>,
    ) {
        if (prefs.pillNoisePolicyVersion >= 1 || states.isEmpty()) return
        val entities = states.values.toList()
        val candidates = PillSupport.candidates(entities, deviceIds).associateBy { it.primary.entityId }
        val migrated = prefs.pillRules.map { rule ->
            val candidate = candidates[rule.entityId] ?: return@map rule
            rule.copy(
                enabled = rule.enabled &&
                    PillSupport.isAutomaticallyEnabled(candidate, entities, deviceIds, entityCategories),
            )
        }
        prefs.pillRules = migrated
        prefs.pillNoisePolicyVersion = 1
        SettingsChangeBus.get().emit(PILL_RULES_CHANGE_KEY)
        Log.i("PortalPills", "applied noise policy: ${migrated.count(PillRule::enabled)}/${migrated.size} enabled")
    }

    /**
     * Raw, untransformed snapshot stream for the per-entity UI store (`HaStates`): one emission
     * per socket event, no sampling — the store's granular invalidation makes per-push apply
     * cheap, and the UI-side collector conflates if the main thread falls behind. Re-binds to a
     * fresh connection exactly like [snapshotFlow].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun rawSnapshots(): Flow<com.iblu01.portallauncher.domain.model.HaSnapshot> =
        activeRepo.flatMapLatest { repo -> repo?.states() ?: emptyFlow() }

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
            source = activeRepo.flatMapLatest { repo ->
                if (repo == null) emptyFlow()
                else combine(
                    repo.states(),
                    SettingsChangeBus.get().changes
                        .filter {
                            it == Prefs.HOME_PILL_PREFERENCES_CHANGE_KEY ||
                                it == Prefs.CAMERA_PREFERENCES_CHANGE_KEY ||
                                it == PILL_RULES_CHANGE_KEY
                        }
                        .map { Unit }
                        .onStart { emit(Unit) },
                ) { snapshot, _ -> snapshot }
            },
            rulesProvider = { prefs.pillRules },
            haUrl = prefs.haUrl,
            homePreferencesProvider = { prefs.homePillPreferences },
            cameraPreferencesProvider = { prefs.cameraPreferences },
        ).onEach { latestCatalog = it.catalog }

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
        homePreferencesProvider: () -> HomePillPreferences = { HomePillPreferencesCodec.defaults() },
        cameraPreferencesProvider: () -> CameraPreferences = { CameraPreferences() },
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
        sampleMs: Long = SNAPSHOT_SAMPLE_MS,
    ): Flow<PillSnapshot> =
        source
            .let { if (sampleMs > 0) it.sample(sampleMs) else it }
            .scan(emptySet<String>() to (null as PillSnapshot?)) { (prevPrimaryIds, _), s ->
                val rules = rulesProvider()   // read once per emission
                val homePreferences = homePreferencesProvider()
                val cameraPreferences = cameraPreferencesProvider()
                val media = MediaSessionBuilder.build(s.states, haUrl, prevPrimaryIds)
                val catalog = catalogBuilder.build(
                    rules = rules,
                    states = s.states,
                    deviceIdByEntity = s.deviceIdByEntity,
                    entityCategoryByEntity = s.entityCategoryByEntity,
                    areaIdByEntity = s.areaIdByEntity,
                    areaNameById = s.areaNameById,
                    manualGroups = homePreferences.manualGroups,
                    cameraPreferences = cameraPreferences,
                    connected = s.connected,
                )
                val homeComposition = HomePillComposer.compose(catalog, homePreferences)
                val homePage = HomePageBuilder.build(appContext, catalog, homePreferences)
                val snapshot = PillSnapshot(
                    chips = (homeComposition.primary + homeComposition.secondary).map { it.chip },
                    media = media,
                    temperatures = temperatureSummary(rules, s.states),
                    connected = s.connected,
                    latestStates = s.states,
                    areaByEntity = s.areaByEntity,
                    lastUpdateAt = s.lastUpdateAt,
                    weatherEntityId = s.weatherEntityId,
                    hourlyForecast = s.hourlyForecast,
                    dailyForecast = s.dailyForecast,
                    areaIdByEntity = s.areaIdByEntity,
                    areaNameById = s.areaNameById,
                    catalog = catalog,
                    homePreferences = homePreferences,
                    homeComposition = homeComposition,
                    homePage = homePage,
                )
                val nextPrimaryIds = media.firstOrNull()?.players?.map { it.entityId }?.toSet().orEmpty()
                nextPrimaryIds to snapshot
            }
            .mapNotNull { it.second }
            .flowOn(dispatcher)

    private companion object {
        // Emit at most one transformed snapshot per window; coalesces bursts without starving
        // sustained streams (sample keeps emitting the latest, unlike debounce which would stall).
        // One frame is enough to coalesce a burst; the previous 100 ms window added a visible
        // uniform delay on the action → confirmation path.
        const val SNAPSHOT_SAMPLE_MS = 16L
        const val PILL_RULES_CHANGE_KEY = "pillRules"

        fun emptyCatalog() = PillCatalogSnapshot(
            devices = emptyMap(),
            groups = emptyMap(),
            availability = emptyMap(),
            dynamicCandidates = emptyList(),
        )
    }

    internal fun temperatureSummary(rules: List<PillRule>, states: Map<String, HaEntity>): TemperatureSummary {
        val targetUnit = selectWeatherEntityId(states.keys)
            ?.let(states::get)
            ?.attributes
            ?.optString("temperature_unit")
            .toTemperatureUnit()
            ?: TemperatureUnit.CELSIUS
        val readings = rules.filter { it.enabled && it.kind == PillKind.CLIMATE }.mapNotNull { rule ->
            val entity = states[rule.entityId] ?: return@mapNotNull null
            if (entity.deviceClass != "temperature") return@mapNotNull null
            val sourceUnit = entity.attributes.optString("unit_of_measurement").toTemperatureUnit()
                ?: targetUnit
            entity.state.toDoubleOrNull()?.let { Triple(entity, rule.label, convertTemperature(it, sourceUnit, targetUnit)) }
        }
        val outdoorTokens = Regex("extérieur|exterieur|outdoor|outside|dehors", RegexOption.IGNORE_CASE)
        val outdoor = readings.firstOrNull { (entity, label) -> outdoorTokens.containsMatchIn(entity.entityId) || outdoorTokens.containsMatchIn(label) }
        val indoor = readings.filterNot { it === outdoor }
        fun fmt(value: Double?) = value?.let {
            val number = if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(Locale.getDefault(), it)
            "$number${targetUnit.symbol}"
        } ?: "—"
        return TemperatureSummary(fmt(indoor.minOfOrNull { it.third }), fmt(indoor.maxOfOrNull { it.third }), fmt(outdoor?.third))
    }
}

internal enum class TemperatureUnit(val symbol: String) { CELSIUS("°C"), FAHRENHEIT("°F") }

internal fun String?.toTemperatureUnit(): TemperatureUnit? = when (this?.trim()?.uppercase()) {
    "°C", "C", "CELSIUS" -> TemperatureUnit.CELSIUS
    "°F", "F", "FAHRENHEIT" -> TemperatureUnit.FAHRENHEIT
    else -> null
}

internal fun convertTemperature(value: Double, from: TemperatureUnit, to: TemperatureUnit): Double = when {
    from == to -> value
    from == TemperatureUnit.FAHRENHEIT -> (value - 32.0) * 5.0 / 9.0
    else -> value * 9.0 / 5.0 + 32.0
}
