package com.iblu01.portallauncher.domain.model

import com.iblu01.portallauncher.HaEntity
import com.iblu01.portallauncher.LauncherChip

/**
 * Pure domain models — no Compose, no Android UI imports. Moved out of `MockContent.kt`
 * (Findings 4 & 5) so the data layer and reducers can be unit-tested off the main thread.
 */

/**
 * Immutable raw snapshot emitted by `HaStateRepository.states()` (Finding 6). One frame of
 * everything the transform layer needs; captured at listener-callback time so downstream
 * `Flow` operators run off the socket thread.
 */
data class HaSnapshot(
    val states: Map<String, HaEntity>,
    val connected: Boolean,
    val areaByEntity: Map<String, String>,
    val lastUpdateAt: Long,
    val weatherEntityId: String?,
    val hourlyForecast: List<ForecastPoint>,
    val dailyForecast: List<ForecastPoint>,
    val deviceIdByEntity: Map<String, String> = emptyMap(),
    val entityRegistryResolved: Boolean = false,
)

/**
 * Transformed frame emitted by `PillRepository.snapshot` — selected chips, media sessions,
 * temperatures and metadata. Produced on `Dispatchers.Default` off the socket thread.
 * (`LauncherChip` is the current chip domain type; renamed to `ChipDomain`/split from
 * `ChipUi` at step 7.)
 */
data class PillSnapshot(
    val chips: List<LauncherChip>,
    val media: List<PlayingMedia>,
    val temperatures: TemperatureSummary,
    val connected: Boolean,
    val latestStates: Map<String, HaEntity>,
    val areaByEntity: Map<String, String>,
    val lastUpdateAt: Long,
    val weatherEntityId: String?,
    val hourlyForecast: List<ForecastPoint>,
    val dailyForecast: List<ForecastPoint>,
)

// NOTE: a typed `ChipVisual` enum was considered here to replace the stringly-typed
// `LauncherChip.state`, but wiring it end-to-end would touch PillPriorityEngine (explicitly
// out of scope — it produces the state strings, incl. "error" which no clean enum covered) and
// the air/lock glyph rendering, with no visual test safety net. Deferred rather than shipped
// half-typed. The `state: String` remains the contract until that typed migration is scoped.

data class PillDetail(
    val label: String,
    val value: String,
    /** HA entity backing this row; blank when the row is informational only. */
    val entityId: String = "",
    /** Whether the entity is currently in its "on" state (drives toggles). */
    val active: Boolean = false,
)

data class TemperatureSummary(val indoorMin: String = "—", val indoorMax: String = "—", val outdoor: String = "—")

/** One point of a HA weather forecast (hourly or daily). */
data class ForecastPoint(
    val datetime: String,
    val temp: Double,
    val tempLow: Double? = null,
    val condition: String = "",
)

data class MediaPlayerVolume(
    val entityId: String,
    val name: String,
    val volumePercent: Int,
    val isMuted: Boolean,
)

data class PlayingMedia(
    val entityId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val state: String,
    val coverUrl: String?,
    val volumePercent: Int,
    val isMuted: Boolean,
    val playerNames: List<String> = emptyList(),
    val players: List<MediaPlayerVolume> = emptyList(),
    val groupablePlayers: List<MediaPlayerVolume> = emptyList(),
    val groupMemberIds: List<String> = emptyList(),
)
