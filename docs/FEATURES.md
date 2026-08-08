# Portal Launcher — Feature Reference

Exhaustive inventory of what the app actually does, derived from the code (not from intent).

Scope note: this is a **launcher** that takes the home role on any Android 9+ device, whose default
screen is a clock and whose Home Assistant integration is optional. Portal-specific behaviour (the
dream/sleep presence proxy, the API 28 blur fallback) is called out where it appears.
Every non-obvious claim carries a `file:line`. Updated 2026-08-08 against branch
`feature/home-assistant-extended-support`.

Conventions used below:
- **Interactive** = issues Home Assistant service calls. **Read-only** = display only.
- ⚠️ = rough edge, hardcoded value, or silent failure worth knowing about.
- 💀 = dead code / dead setting: present but unreachable or never read.

---

## Contents

- [1. Idle screen (clock / screensaver)](#1-idle-screen-clock--screensaver)
- [2. Pill system (the ranked chip tray)](#2-pill-system-the-ranked-chip-tray)
- [3. Control panels](#3-control-panels)
- [4. Media](#4-media)
- [5. Device, sensors, power, presence proxy](#5-device-sensors-power-presence-proxy)
- [6. Home Assistant + MQTT integration](#6-home-assistant--mqtt-integration)
- [7. Settings, preferences](#7-settings-preferences)
- [8. Overlays, gestures, navigation](#8-overlays-gestures-navigation)
- [9. Design system](#9-design-system)
- [10. Architecture](#10-architecture)
- [11. Tests and build](#11-tests-and-build)
- [12. Known gaps, dead code, rough edges](#12-known-gaps-dead-code-rough-edges)

---

## 1. Idle screen (clock / screensaver)

### Time and date
- Time `HH:mm` (24h) or `h:mm a` (12h); date `EEEE d MMMM`, uppercased with custom title-casing — `ClockScreen.kt:70-71,104,224-227`.
- ⚠️ Both refresh on a **15-second** loop, not per second (`ClockScreen.kt:217`), so the displayed minute can lag up to 15 s.
- ⚠️ Locale is the device default. The UI strings are hardcoded French but the date will render in whatever locale the device is set to.

### Clock theming
Editor: Settings → Application → "Thème de l'horloge" → `ClockThemeActivity` (immersive fullscreen over the *real* wallpaper with frozen mock content, `ClockThemeActivity.kt:20-54`).

| Control | Range | Source |
|---|---|---|
| Font | 10 bundled variable fonts (Space Grotesk, Oswald, Playfair Display, Montserrat, JetBrains Mono, Orbitron, Teko, Roboto Slab, Exo 2, Inter) | `ClockTheme.kt:6-21` |
| Weight | 100–900, snapped to hundreds | `ClockThemeScreen.kt:168` |
| Size | UI 90–180 sp | `ClockTheme.kt:53` |
| Letter spacing | UI −2..12 sp | `ClockTheme.kt:54` |
| Tint | 6 swatches (white, amber, mint, blue, pink, violet) | `ClockTheme.kt:24-35` |
| 12h / 24h | switch | `ClockThemeScreen.kt:207-219` |

- Variable fonts are pinned per weight so there is no synthetic bolding — `DesignTokens.kt:74-75`.
- Only the **time** uses weight/size/letter-spacing; the date inherits font + tint only (hardcoded 20 sp) — `ClockScreen.kt:102-130`, documented at `ClockTheme.kt:41`.
- ⚠️ Writes to disk on every slider tick, no debounce (`ClockThemeScreen.kt:81` → `ClockThemeActivity.kt:28`). The launcher re-reads the theme `ON_RESUME` (`LauncherActivity.kt:251-257`), so the home screen updates when the editor closes.
- ⚠️ UI ranges are narrower than the persisted clamps (`Prefs` allows size 60–200, spacing −5..15) — `Prefs.kt:113,117`.

### Background
Three modes via `Prefs.backgroundMode` — `DynamicBackground.kt:34-38`:
- `neutral` — static radial gradient (`:50-59`).
- `nature` — ⚠️ cycles **5 hardcoded Unsplash URLs** every 30 s with a 2 s crossfade; no offline fallback, failures only log and leave the last frame (`:26-32,89-122`).
- `custom` — user photo at `filesDir/wallpaper.jpg`, falls back to the gradient if absent (`:62-87`). Cache-busted by a `wallpaperVersion` counter bumped on settings change (`LauncherActivity.kt:232-243`).

Scrim: separate darkening layer, 0–60 %, default 25 %, only when mode ≠ neutral — `LauncherActivity.kt:464-470`, `Prefs.kt:98-100`. Tuned in **OpacityPreviewActivity**: real wallpaper + frozen mock clock + vertical fill slider with 20 haptic steps, committed on release only (`OpacityPreviewScreen.kt:106-112`).

### Weather
- Driven by the HA `weather.*` entity. ⚠️ The entity is picked as the **first** `weather.*` in `get_states` order — non-deterministic, no user choice (`HaStateRepository.kt:219`).
- Condition → French label via exhaustive `when` over 15 conditions (`WeatherCard.kt:72-88`).
- Icons are **hand-drawn animated Canvas glyphs**: rotating sun rays on a 9 s loop, sine-flashing lightning, bobbing fog lines, drops, snowflakes — `WeatherGlyph.kt:39-155`. No remote images, nothing to break.
- ⚠️ Day/night is a hardcoded local-clock window (night = hour < 7 or ≥ 21), not real sunrise/sunset — `WeatherCard.kt:50-53`.
- Panel (tap the temperature pill): current conditions + horizontal hourly strip (≤12) + daily list (≤7), `high° / low°` — `WeatherPanel.kt:70-121`. Empty forecasts render nothing, no loading or error state.

### Other idle-screen elements
- **Temperature pill**: `Appartement {min}–{max}` + `Ext. {outdoor}` — `ClockScreen.kt:132-143`.
- **Offline banner**: shown only when `!connected && lastUpdateAt > 0`, ticking every second, amber pill reading "Hors ligne — infos figées depuis {N}s" — `ClockScreen.kt:144-147,191-210`. ⚠️ Never rolls over to hours: after 100 minutes it reads "142min".
- **Pill tray**: collapsed renders 3 pills whenever at least 3 enabled compatible devices are available (with one exceptional extra critical slot only when four remain readable); expanded reaches 9 with up to 6 secondary pills. After alerts, pins and relevant activity, calm unpinned devices fill remaining positions. The bottom Maison icon was removed; Maison remains reachable by swipe — `ClockScreen.kt`, `HomePillComposer.kt`, `HomeCriticalCapacityPolicy.kt`.
- Expanding still grows the bottom gradient 360→620 dp over 450 ms and arms auto-return — `LauncherActivity.kt:941-945`.

---

## 2. Pill system, catalog and Maison composition

Pipeline: HA snapshot + `Prefs.pillRules` + the versioned Maison preferences → complete
`PillCatalogSnapshot` → dynamic ranking → `HomePillComposer` (3 primary / 6 secondary / favorite
overflow) and `HomePageBuilder` (ordered Maison rails) → `LauncherViewModel.uiState` → Compose.
The 100 ms sampled transform stays on `Dispatchers.Default`; capacity is applied only by the home
composer, never while building the catalog — `PillRepository.kt:173-247`,
`PillPriorityEngine.kt:20-59`.

### Supported pill kinds and detection
`PillRules.kt` retains legacy enum values so old preferences remain decodable, but
`PillSupport` only admits actionable devices plus movement and opening sensors:

| Kind | Base | Detected from |
|---|---|---|
| SAFETY | 90 | domain `alarm_control_panel` |
| APPLIANCE | 58 | entity id contains washer/dryer/dishwasher/printer tokens **and** is the main state sensor |
| VACUUM | 58 | domain `vacuum` |
| TIMER | 50 | domain `timer` or class `duration` |
| LOCK | 35 | domain `lock` or class `lock` |
| COVER | 26 | domain `cover` |
| THERMOSTAT | 24 | domain `climate` |
| OPENING | 25 | class door / window / opening / garage_door |
| SWITCH | 17 | domain `switch` |
| LIGHTS | 16 | domain `light` |
| FAN | 15 | domain `fan`, not matching "purif" |
| PURIFIER | 15 | domain `fan` + name/id contains "purif" |
| MEDIA | 13 | domain `media_player` |
| HUMIDIFIER | 20 | domain `humidifier` |
| WATER_HEATER | 24 | domain `water_heater` |
| VALVE | 30 | domain `valve` |
| SIREN | 45 | domain `siren` |
| LAWN_MOWER | 28 | domain `lawn_mower` |
| GENERIC | 15 | movement binary sensors: motion / occupancy / moving |

`scene`, `script`, `person`, `device_tracker`, energy/air/temperature/battery sensors and every
binary sensor except movement and door/window/opening classes are deliberately unsupported. Old
rules for those entities stay persisted but cannot re-enter the catalog, settings families,
automatic groups or panel routing.

**Eligibility gate**: a persistent target must pass `PillSupport.isAllowedAsPersistedChip`; entities
without a routed panel (notably button/number/select/camera and passive diagnostic sensors) are not
catalogued. Compatible devices discovered after the saved rules are included in Maison even before
being enabled for dynamic ranking. An explicitly disabled rule remains discoverable in Settings but
is hidden from Accueil, Maison and automatic-group members; only enabled rules can be pinned from
Settings or feed dynamic ranking — `PillRules.kt:179-203`,
`PillCatalogBuilder.kt:27-63,166-190`.

**Related-entity auto-linking**: a `logicalKey` (entity id stripped of `_state`, `_contact`, `_temperature`, `_humidity`, …) groups sibling sensors into `relatedEntityIds`, so a washer's cycle/completion sensors ride along with its main pill instead of becoming their own — `PillRules.kt:233-272`.

### Stable identity, persistence and migration

`LauncherChip` remains a live rendering model. Persisted identity is a typed `PillRef`: `device:<entity_id>`,
`area:<area_id>`, `kind:<PillKind>` or `manual:<immutable id>`; no localized display label is used
as an identity — `HomePillModels.kt:6-47`, `HomePillPreferencesCodec.kt:113-131`.

`HomePillPreferences` schema v1 stores the Maison toggle, unlimited ordered pins, section visibility /
order / item order, and manual groups. The first read writes defaults (Maison and all automatic
sections enabled, no pins, no manual groups) without touching legacy `pill_rules`. Corrupt or future
JSON falls back in memory without overwriting its source; unknown stable references survive decode.
Writes are process-serialized, canonical-JSON deduplicated and emit one
`homePillPreferences` live-change event — `HomePillPreferencesCodec.kt:18-111`,
`Prefs.kt:283-356`.

### Complete catalog and groups

`PillCatalogBuilder` produces, without a nine-item limit:

- one `ResolvedPill` per compatible device;
- automatic area groups keyed by HA `area_id` and labelled separately through `areaNameById`;
- automatic type groups keyed by `PillKind`;
- locally persisted manual groups, including heterogeneous groups;
- typed representations of historical aggregates for dynamic-ranking compatibility.

The same device can therefore appear individually, in its room, in its kind, in multiple manual
groups and in Favorites. Group membership is derived locally and never writes HA area/type state —
`PillCatalogBuilder.kt:15-115`, `HaStateRepository.kt:72-82,227-252`.

Group summaries report active/available member counts. A collective action exists only for an
explicit safe intersection: turn off for light/switch/fan/purifier sets, close for covers, lock for
locks; mixed unsupported groups get none — `PillCatalogBuilder.kt:117-163,263-269`.

The remaining historical dynamic aggregates retain their prior state summaries:
- **openingGroup** — "Toutes fermées" / "N ouverte(s)", priority rises with how long something has been open (`:229-253`).
- **mediaGroup** — playing/buffering counts; a paused player only counts if paused less than 30 s ago (`:109-133`).
- ⚠️ **purifierGroup** takes only the **first** enabled purifier; additional ones are silently invisible (`:135-156`).

### Availability, ranking and nine-slot composition

Availability is three-state: `AVAILABLE`, `STALE`, `UNAVAILABLE`. An individually unknown,
unavailable or missing entity is unavailable; a global HA disconnect marks the cached snapshot stale
instead, keeping it rendered while blocking new pins and service actions. A group remains renderable
while at least one member is available or stale — `HomePillModels.kt:49-57,95-109`,
`PillCatalogBuilder.kt:227-230`.

`PillPriorityEngine.select` now returns the complete deterministic dynamic ranking; there is no
`.take(9)` in the engine. Existing per-kind state scores and recency rules remain, with score
descending, normalized label and stable id as tie-breakers. Calm unpinned enabled devices are the
final fallback used to keep the fixed 3/9 tray full — `PillPriorityEngine.kt:20-59`,
`PillCatalogBuilder.kt:166-190,233-256`.

`HomePillComposer` is a pure, atomic projection with this precedence:

1. available/renderable critical alerts;
2. available/renderable pins in persisted order;
3. relevant dynamic candidates in deterministic score order.
4. calm enabled individual devices, by priority then stable label/id.

It deduplicates references and duplicate incidents (an individual representation wins over its group),
then splits 3 primary + 6 secondary; further available pins become `favoriteOverflow`. Removing or
losing a visible pin promotes the first available overflow item, while the unavailable reference stays
persisted and automatically regains its logical rank when it returns. A disabled device is omitted
without deleting that pin, so re-enabling it from Settings restores its previous position —
`HomePillComposer.kt`.

The pure alert policy treats active safety/alarm states, an active siren and a jammed lock as critical.
An unlocked/open lock is a high warning but does not overtake pins. Critical alerts sort by severity,
recency and stable key, never mutate `pinnedOrder`, and may use one exceptional fourth primary slot
only when width and font scale allow it — `PillAlertPolicy.kt:5-42`,
`HomeCriticalCapacityPolicy.kt:7-18`.

### Auto-init on fresh install
One-shot, gated by `pillAutoGroupsInitialized`: auto-enables only LIGHTS, MEDIA and PURIFIER.
Openings and movement sensors remain manually selectable; passive sensor, scene and presence kinds
are not selectable at all.

### Interaction
- Tap opens the typed panel for individual devices, including SWITCH; FAN alone retains its direct
  `toggle`. A single light opens its detail controls immediately without a redundant one-item
  browser; a group opens the group browser — `ChipMapper.kt:39-47`,
  `LauncherActivity.kt:837-863`.
- Long-press on Accueil or Maison opens the semantic context menu instead of routing immediately.
  It offers pin/unpin, add-device-to-manual-group, reorder (drag plus first/before/after/last), and
  "Ouvrir les commandes". Individual devices also offer "Ne plus afficher cet appareil", which
  disables their Settings rule while preserving pins/membership for later restoration. Stale targets still allow safe actions such as unpin but block commands —
  `HomePillContextMenu.kt:192-303,329-355`.
- Drag reorder is armed explicitly from the menu, locks pager/vertical scrolling, stages relative
  movement and commits once on drop; cancellation writes nothing. Every reorder path updates the
  same persisted `pinnedOrder` — `HomePillContextMenu.kt:99-175`,
  `LauncherActivity.kt:881-919,1093`.
- If a device leaves the tray while its panel is open, `LauncherViewModel.panelChip` retains the
  last-known-good chip. Group navigation retains the selected typed device destination and renders a
  recoverable unavailable state rather than tearing down the stack — `LauncherViewModel.kt:131-152`,
  `PanelState.kt:19-37`.

---

## 3. Control panels

Router: `ChipActionsPanel` → exhaustive `when (chip.toPanelKind())`, no catch-all — `SidePanel.kt:174-191`. Shared chrome = frosted card, circular "×" top-left, centred icon+label pill, `chip.value` as title, scrollable body (`SidePanel.kt:118-171`). Live entity access via `rememberEntity()`, which dedups HA pushes by comparing state + attributes so unrelated updates don't recompose an open control (`PanelHelpers.kt:36-48`).

Unavailable handling: single-entity panels render "Appareil indisponible" (`PanelHelpers.kt:54-69`).
List-based lights and purifier panels retain their existing per-member degradation.

| Panel | Mode | Controls → HA service |
|---|---|---|
| **Lights** | Interactive | `light.turn_on/turn_off`; `brightness_pct`; `color_temp_kelvin`; `rgb_color`; `hs_color` |
| **Thermostat** | Interactive | `climate.set_temperature`, `climate.set_hvac_mode` |
| **Cover** | Interactive | `cover.open_cover / close_cover / stop_cover / set_cover_position` |
| **Lock** | Interactive | `lock.lock`, `lock.unlock` |
| **Alarm** | Interactive | `alarm_control_panel.alarm_arm_away / _home / _night / _vacation / alarm_disarm` (+ `code`) |
| **Vacuum** | Interactive | `vacuum.start / pause / stop / return_to_base / locate` |
| **Switch** | Interactive | `switch.toggle` |
| **Fan** | Interactive | `fan.toggle`, `fan.set_percentage`, `fan.oscillate` |
| **Purifier** | Interactive | `fan.turn_off`, `fan.set_preset_mode` |
| **Weather** | Read-only | — |

### Group browser (`GroupBrowserPanel.kt`)

Area, kind and manual-group pills open a typed `PanelRequest.Group(destination, device?)` rather
than an id-special-cased panel. The root shows the group summary, an optional explicitly safe
collective action and the currently resolved members. Selecting a member replaces the content with
its existing control panel; Back pops to the group, while Close dismisses the whole stack. A member
that vanishes while selected gets a recoverable "Appareil indisponible" state —
`PanelState.kt:14-37,92-167`, `GroupBrowserPanel.kt:92-188,296-325`.

Collective services are mapped only from `GroupCollectiveAction` plus the member's typed kind/domain;
localized group labels are never inspected. Stale groups remain readable but expose neither member
commands nor collective commands — `GroupBrowserPanel.kt:51-90,157-184,218-275`.

### Lights — the deepest panel (`LightDetailPanel.kt`)
- Capability detection from `supported_color_modes`: colour, white channel, brightness, on/off-only — `:121-128`.
- Room grouping from the HA area registry: a 2-column grid of room cards showing on-count/total, drilling into a per-room light list; flat list if only one area — `:686-751`.
- Brightness and colour-temperature are **custom vertical fill sliders**, live-throttled at 110 ms while dragging (`LiveThrottle`, `:638-647`), final value on release. Kelvin range read from `min/max_color_temp_kelvin` (default 2000–6500 K).
- 8 colour presets + 4 white presets; long-press a colour preset opens a drag **colour wheel** that previews locally and commits `hs_color` on release — `:538-624`.
- "Tout éteindre" row appears only when something is on (`:753-772`). Lights with an integrated white channel and no `color_temp` show a static label instead of controls (`:309-315`).

### Alarm — code entry (`AlarmPanel.kt`)
- Arm modes are discovered from the `supported_features` bitmask, not hardcoded — `:106-117`, bits in `PanelHelpers.kt:72`.
- Whether a code is required comes from `code_arm_required` + `code_format` — `PanelHelpers.kt:86-89`.
- Wrong-code detection is inferred: HA gives no signal, so the panel snapshots the state, waits 2.6 s, and if nothing moved it flags `wrongCode`, which shakes and clears the pad — `:158-172`.
- ⚠️ `code_format` only says numeric-vs-text, not length, so the keypad is variable-length with an explicit ✓ key.

### Other panel notes
- ⚠️ **Thermostat is single-setpoint**: `target_temp_low/high` are never read even though `heat_cool` is a handled mode label (`ThermostatPanel.kt:144`). The dial is a 270° drag ring that commits only on release.
- ⚠️ **Purifier and Fan are two entirely different UIs for the same `fan` domain** with no shared code (mode selector vs toggle + speed + oscillate).
- ⚠️ **Weather bypasses the shared panel chrome** and reimplements its own header/close button (`WeatherPanel.kt:59-67`).
- 💀 `VacuumFeature.CLEAN_SPOT` and `.START` constants are declared and never used — there is no clean-mode or spot-clean UI (`PanelHelpers.kt:75`).
- ⚠️ Lock battery % is not read from an attribute here; it arrives via whatever `chip.details` the mapper populated.

---

## 4. Media

### Session model (`MediaSessionBuilder.kt`)
- Players are grouped into one session by `title|artist` (lowercased), falling back to `media_content_id`, then the entity id — `:36-39`.
- Visible sessions = anything `playing`/`buffering`; if nothing is playing, `paused` entities whose `lastChanged` is within **30 s** — `:22-31`. ⚠️ If `lastChanged` fails both `Instant` and `OffsetDateTime` parsing the code defaults to "now", so an unparseable timestamp reads as infinitely recent. Low real-world risk: HA's ISO-8601 with a numeric offset fails `Instant.parse` but succeeds on `OffsetDateTime` — which is why the fallback chain exists — so only a blank or malformed `last_changed` triggers it.
- Sort: previously-primary first (continuity), then oldest `lastChanged`, then entity id — `:85-91`.
- Cover art from `entity_picture`, prefixed with the HA base URL when relative; bearer token supplied as a header at load time — `:46-49`, `MediaPlayerView.kt:112-120`.

### Primary / secondary
- Primary = the session matching the current selection, else the first — `LauncherActivity.kt:287-292`.
- Secondary sessions linger **6 s** after their players disappear, so a stopped stream fades instead of popping — `:295-304`. Two mini cards render, then "+N autres".
- Swipe left/right on the artwork cycles sessions with a ±2.2° rotation and off-screen animation — `MediaPlayerView.kt:187-223`.

### Controls
- Transport → `media_player.media_play_pause / media_previous_track / media_next_track`.
- ⚠️ **Asymmetry**: primary transport targets only the representative `entityId` (`LauncherActivity.kt:603-611`) while secondary transport loops over every player in the session (`:621-629`). Whether leader-only is correct is integration-dependent — but see the grouping note below: this code never identifies a real coordinator.
- Volume: per-player sliders in a dialog, committed on release via `volume_set` — `MediaPlayerView.kt:520-565`, `LauncherActivity.kt:612-618`.
- ⚠️ **Mute is display-only** — the icon reflects `is_volume_muted`, but `media_player.volume_mute` is called nowhere in the app (verified by exhaustive grep); tapping the row just opens the volume dialog. The other "mute" hits in the codebase are the Portal's own mic/speaker mute switches published *to* HA — a different feature.
- Secondary play/pause updates local state optimistically before calling the service; primary does not, so primary feels laggier by design — `LauncherActivity.kt:370-379`.

### Multi-room / grouped music
- "Diffuser dans" lists every `media_player` in the whole snapshot that exposes a `group_members` attribute at all — i.e. any player whose integration supports grouping, playing or not (`MediaSessionBuilder.kt:70-79`).
- The current leader is pinned as "Principal", checked and disabled — `MediaPlayerView.kt:580-584`.
- Unchecking → `media_player.unjoin` on that member. Checking → `media_player.join` on the leader with `group_members: [thatOneMember]` — `LauncherActivity.kt:639-649`.
- Each tap sends only the toggled member, not the accumulated selection. That looked like a bug but isn't: HA's `join` is additive for the group-capable integrations, and `selectedGroupMembers` is local optimistic state re-synced from `groupMemberIds`. Verified and refuted.
- ⚠️ The real fragility is *which* entity is the target: the session's representative is `matchingPlayers.first()` (`MediaSessionBuilder.kt:34,43`), i.e. arbitrary map-iteration order among players sharing a title/artist. It is **never** derived from `group_members` or any coordinator concept. If the integration requires the coordinator entity for transport, a tap can land on a follower and silently no-op. This is also what makes the primary-vs-secondary transport asymmetry above risky rather than merely inconsistent.
- 💀 No seek bar, no `media_position`/`media_duration` handling, no source/app selection — confirmed absent, not partially built.

### Panel behaviour
- Auto-opens when a session appears; retargets when the primary swaps; never clobbers a user-opened panel; closes when playback stops — `PanelState.kt:67-85`.
- A user dismissal is remembered per session key (`dismissedAutoKey`) and suppresses reopening while that session keeps playing; it clears when playback actually stops.
- Auto-open is **state-driven** (keyed on both the primary id and "is the panel closed"), so closing another panel restores the media panel mid-session — `LauncherViewModel.kt:101-109`.
- The auto-return idle timer deliberately spares AUTO media panels: it doesn't arm for them and won't dismiss them — `LauncherActivity.kt:323-338`.

---

## 5. Device, sensors, power, presence proxy

### Presence proxy (`DeviceStateHub.kt`)
State = `(presence: PRESENT|ABSENT|UNKNOWN, display: OFF|SCREENSAVER|APP|DREAMING, confident, source)`, logged as `PortalLauncherState: PRESENT/APP -> PRESENT/SCREENSAVER source=…` (`:34,217`).

Occupancy is inferred from the Portal's **dream/sleep lifecycle** — no special permission needed (`:58-100`):

| Trigger | Result | Source tag |
|---|---|---|
| `ACTION_DREAMING_STARTED` | PRESENT / DREAMING | `dream_started` |
| dreaming stopped, user exited an app ≤4 s ago | PRESENT / APP | `dream_user_exit` |
| dreaming stopped, screen interactive | PRESENT / SCREENSAVER or APP | `dream_redream` |
| dreaming stopped, screen not interactive | ABSENT / OFF | `dream_sleep` |
| `ACTION_SCREEN_ON` / `_OFF` / `USER_PRESENT` | recompute / ABSENT / PRESENT | `screen_*`, `user_present` |
| launcher resumed / paused | PRESENT-SCREENSAVER / recompute | `launcher`, `launcher_paused` |
| foreground app changed (accessibility) | PRESENT / APP | `accessibility` |

⚠️ Without the accessibility service, foreground-app tracking is impossible and presence degrades to **PRESENT but not confident** (`:184-187`). The 4-second grace on app exit is `USER_EXIT_GRACE_MS` (`:35`).

### Power modes
- `FOLLOW_PRESENCE` (default) — normal idle arming, lets the Portal sleep.
- `ALWAYS_ON` — sets `FLAG_KEEP_SCREEN_ON` and short-circuits the whole sleep scheduler — `LauncherActivity.kt:143-149`, `SleepScheduler.kt:25,40,46,56`.
- `devKeepScreenOn` is a developer flag with identical effect.
- Settable from Settings **and** from HA via the `power_mode` MQTT select.

### Screen timeout
- `screenTimeoutMinutes` 1–240, default 10; exact-while-idle `AlarmManager` alarm → `SleepReceiver` → `ScreenControl.sleep()` — `SleepScheduler.kt:54-72`.
- Only arms while the display is SCREENSAVER or DREAMING (`:23-36`), and re-arm calls are throttled to one per 5 s because every touch triggers one (`:20`).

### Wake / sleep
- `wake()` — `FULL_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` held 3 s, then re-arms the timeout (`ScreenControl.kt:13-23`).
- `sleep()` — delegates to the accessibility service's `GLOBAL_ACTION_LOCK_SCREEN`. ⚠️ **With no accessibility service there is no fallback**: sleep silently fails with a log line (`ScreenControl.kt:25-36`).
- `enableAccessibility()` writes `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` directly, which needs adb-granted `WRITE_SECURE_SETTINGS`; retried best-effort on every boot (`ScreenControl.kt:57-91`, `BootReceiver.kt:11`).

### Sensors
| Sensor | Cadence / gating | Notes |
|---|---|---|
| Ambient light (lux) | 2 s throttle + 1.5 lx delta | `SensorBridge.kt:24-25,111-118` |
| Ambient temperature | 30 s + 0.2 °C delta | only on hardware that has it; user offset −20..+20 °C applied at publish time; `republishTemperature()` re-emits immediately on offset change (`:94-109`) |
| Sound level (0–100) | published every 2 s | needs `RECORD_AUDIO`; RMS→dBFS, floor −60 dBFS (`SoundMonitor.kt:126-129`) |
| IP address | published once at MQTT connect | ⚠️ never refreshed, so it goes stale after a DHCP change |

**`SoundMonitor` releases the microphone** whenever `AudioManager.mode` indicates a call in progress, polling every 500 ms until free, so Portal video calls aren't starved — `SoundMonitor.kt:86-96`. It also exposes `frameSink`/`wakeSink` hooks (40 ms chunks) for a future intercom or wake-word feature (`:35-39,74`).

💀 **Retired sensors**: accelerometer X/Y/Z, the Portal's RGB light sensor (vendor type `65537`) and tap/tilt events. `SensorBridge.start()` registers only light and temperature (`:66-75`), so `handleAccel`/`handleRgb` never run, and their MQTT discovery configs are actively retracted on connect (`HaDiscovery.kt` `staleTopics()`). The tap-detection code — including a ×0.25 threshold compensation specific to the Portal+ "cipher" whose accelerometer sits on the moving screen arm (`:44-49`) — is still present and dormant. The Settings tap-sensitivity slider is therefore inert.

### Tones
Pure synthesised PCM, no bundled assets, played as `USAGE_MEDIA` so the HA volume slider applies — `TonePlayer.kt:11-12`:
- `doorbell` — two-tone E5→C5 ding-dong.
- `alert` — 3 × 880 Hz beeps.
Both triggered from MQTT and both paired with an on-screen alert overlay.

### Wireless ADB
⚠️ Entirely root-gated: `su -c "setprop service.adb.tcp.port … && stop adbd && start adbd"` — `AdbControl.kt:43-51`. Fails silently to a `false` return on a locked build. Re-applied on boot if it was enabled. `rebootDevice()` is also `su`-gated.

### Permissions → what dies without them
| Permission | Without it |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | no HA, no MQTT |
| `FOREGROUND_SERVICE` | bridge gets killed |
| `RECEIVE_BOOT_COMPLETED` | nothing auto-starts after reboot |
| `WAKE_LOCK` | `wake()` throws |
| `RECORD_AUDIO` | sound sensor disables itself with a log line telling you the exact `adb` command |
| `WRITE_SETTINGS` (appop) | brightness writes fail silently |
| `WRITE_SECURE_SETTINGS` | can't auto-enable the accessibility service → no confident presence, and no remote sleep unless enabled by hand |
| accessibility service enabled | no foreground-app tracking, no `sleep()` |

### Portability flags
The presence proxy depends on Portal dream broadcasts; tap compensation is keyed to `Build.DEVICE == "cipher"`; the RGB sensor type is a Meta vendor constant; ADB control needs root; several permissions need manual adb grants. None of it crashes elsewhere, but a generic Android tablet degrades to screen-on/off presence only.

---

## 6. Home Assistant + MQTT integration

### WebSocket (`HaStateRepository.kt`)
- One OkHttpClient, `Proxy.NO_PROXY` (local HA must bypass any tablet proxy), 30 s ping interval — `:29-31`.
- Bootstrap order after `auth_ok`: `id 1 get_states`, `3 area_registry`, `4 entity_registry`, `5 device_registry`, then `6 subscribe_events state_changed`, then `7`/`8` `weather/subscribe_forecast` (hourly, daily) — `:204-223`. The id must strictly increase per connection; using a lower id gets the subscription rejected and silently kills all live updates. That was a real outage, hence the comment at `:217`.
- Areas resolve entity → own area, else its device's area; recomputed only when the map actually changes — `:172-181`.
- **Liveness watchdog**: pings every 30 s; if no inbound frame for 75 s, `forceReconnect()` (close 4000, null the socket, restart). This exists because the Portal's doze kills sockets silently without ever firing `onFailure`/`onClosed` — `:110-131`.
- ⚠️ Reconnect is a **flat 5 s retry** with no backoff or cap (`:104-108`), unlike MQTT's exponential 2→60 s.
- ⚠️ The watchdog only checks "any frame received", so an instance that keeps ponging while state updates have stopped still looks alive.
- Service calls: fire-and-forget with an incrementing id from 100, ack/nack only logged — `:283-311`. No confirmed-vs-optimistic reconciliation; the UI waits for HA's `state_changed` push.

### mDNS discovery
`NsdManager` on `_home-assistant._tcp.`, resolves serialised through a pending queue (NSD can only resolve one at a time), URL from the TXT `internal_url`/`base_url` else `http://host:port` — `HaMdnsDiscovery.kt:14-29,119-157`.

### MQTT bridge (`MqttBridgeService.kt`)
- Foreground service, Paho v3, client id `portallauncher-${deviceId.take(8)}`, `cleanSession`, 30 s keepalive.
- **LWT**: screen-state topic → `"OFF"`, QoS 1, retained — HA sees the panel as screen-off if the connection drops.
- Reconnect: exponential 2 s → 60 s cap; resets to 5 s after any completed connect+run cycle — `:122-136`.
- On each connect: clears retained command topics and **retracts stale discovery configs**, re-subscribes, republishes discovery + a full state snapshot, then loops every 5 s polling volume/mute/brightness (Android has no reliable push for those).

**Published to HA** (~21 entities, all under one device block `Meta Portal`): screen switch, screen-state sensor, presence binary sensor with JSON attributes, ambient light, temperature (+ offset number), sound level, mic-mute switch, volume number, volume-mute switch, brightness number, screen-timeout switch + minutes number, power-mode select, IP address, doorbell button, alert button. Plus a discovery-less free-text `notification` topic.

**Consumed command topics**: screen on/off, temp offset, mic mute, volume, volume mute, sound play, notification, brightness, screen timeout on/off, timeout minutes, power mode. Each handled on a dedicated single-thread executor, each wrapped in `runCatching` so one bad payload can't kill the queue — `:246-323`.

### Live-apply bus
`SettingsChangeBus` carries changed field names. Consumers in `LauncherActivity.kt:237-249`:
- `backgroundMode` → wallpaper re-read.
- `haUrl` / `haToken` → HA reconnect (fingerprint-compared, no-op if unchanged).
- `brokerHost` / `brokerPort` / `username` / `password` → MQTT reconnect.
- ⚠️ **Everything else has no consumer**: `tempOffset`, `screenTimeoutEnabled/Minutes`, `powerMode`, `tapThreshold`, `autoReturn*`, `deviceName`, `adb*` persist but are not applied live, unlike the MQTT command path which does apply them.

### Credentials
- `haToken` and MQTT `password` live in `EncryptedSharedPreferences` (AES256-SIV keys / GCM values), with a one-time migration that scrubs the old plaintext copies — `Prefs.kt:19-32,241-257`.
- ⚠️ If the Keystore is unavailable it falls back to plain SharedPreferences with only a log line — a silent security downgrade.


---

## 7. Settings, preferences

### Pill and Maison editor

The Pills tile is now a small settings hierarchy with three entry points — `PillsSettingsPage.kt:62-142`:

- **Accueil** previews all 3 primary and 6 secondary pin slots, labels unused positions
  "Automatique", lists overflow and temporarily unavailable pins separately, and provides ordered
  first/previous/next/last controls — `PillsSettingsPage.kt:199-288`.
- **Page Maison** toggles the page, controls visibility/order and per-section item order, exposes every
  automatic room/type group as a pin target, and creates/orders manual groups —
  `PillsSettingsPage.kt:290-408`.
- **Appareils disponibles** preserves the existing per-device activation rule while adding pin/unpin
  independently of whether the target currently ranks in the home nine. Targets are grouped by the
  existing user-facing `PillFamily` buckets; unavailable/stale targets cannot be newly pinned —
  `PillsSettingsPage.kt:410-445,550-604`, `HomeSettingsReducer.kt:42-61,209-265`.

Manual-group detail supports rename, pin/unpin, add/remove and reorder members, move a member to
another manual group, and confirmed deletion. Deleting a group removes only dangling local group
references; it never disables or deletes member devices — `PillsSettingsPage.kt:447-548`,
`HomeSettingsReducer.kt:100-170`.

All these operations are typed `HomeSettingsAction`s reduced outside composables, written through the
same atomic `Prefs` codec and reflected through `SettingsChangeBus` in the active launcher. The
settings catalog is complete rather than top-nine-limited and uses stable `area_id` references —
`HomeSettingsReducer.kt:14-40,63-207`, `SettingsActivity.kt:242-265`.

---

## 8. Overlays, gestures, navigation

### Logical pager pages
One flat `HorizontalPager` holds `[Maison?, Accueil, app page 0, app page 1, …]`
(`LauncherPager.kt`). It is not a pager nested inside the apps page: Maison rails are the only nested
horizontal scrollers.

`LauncherPagerLayout` maps the logical identities `House`, `Clock`, `Apps(n)` to physical indices.
With Maison enabled those are `0`, `1`, `2+n`; without it they are `0`, `1+n`. The initial destination
is always Accueil, and a hot Maison toggle remaps the settled logical identity so an application page
never silently shifts to a neighbour — `LauncherPager.kt:60-149,264-281`.

The clock header collapse is relative to the logical Accueil index. Its alpha falls to zero over
Maison, which owns its own title/header, while app pages keep the clock collapsed. HOME, Back and
auto-return call the Maison-aware `returnToClockPage(..., layout)` and therefore always target the
main Accueil, never Maison — `LauncherPager.kt:151-209`, `LauncherBack.kt:44-64`,
`LauncherActivity.kt:800-831`.

When enabled, Maison is reachable by a left-to-right gesture from Accueil. There is deliberately no
extra bottom home icon. When disabled, the physical page disappears without leaving a placeholder.

### Maison page and rails

Maison renders an independently vertical `LazyColumn` of non-empty ordered sections: available pins
(Favoris), automatic rooms, one section per populated kind (individual devices only; the redundant
aggregate type pill is not repeated at the start of its own line),
and rendered manual groups. Hidden and empty sections are omitted while their preferences remain
stored. An empty catalog produces one page-level explanation/action rather than repeated empty rails —
`HomePageBuilder.kt:6-105`, `HomePage.kt:91-178,430-469`.

Each section owns a saveable `LazyRow` keyed by stable section id. A pure responsive policy chooses
one or two rows from width, available height, item count and font scale; the deterministic fill is
top-to-bottom then left-to-right and never exceeds two rows. Pills retain a 48 dp minimum target and
keyboard/D-pad activation — `HomeRailLayoutPolicy.kt:3-28`, `HomePage.kt:285-428`.

The header's **Modifier** mode exposes section before/after/hide controls plus group-management
entry points, and explains that automatic room/type membership is managed in HA. Pill long-press
opens the same complete context menu used by Accueil. TalkBack descriptions include name, state,
pin/stale/critical status and group membership — `HomePage.kt:180-282,489-529`.

A down event inside a rail reserves the gesture for that rail; pager navigation remains available
from headers, gaps and margins. A dedicated 24 dp right edge guarantees Maison → Accueil beside long
rails, and both pager and vertical scrolling lock during pill reorder —
`HomePage.kt:322-347`, `LauncherActivity.kt:1093,1194-1205`.

### App pages
- The clock header is a sibling above the pager, not inside Accueil. It is hidden over Maison; from Accueil it shrinks to 34 % over the clock→apps swipe, driven by the logical-clock-relative offset through a `graphicsLayer` scale — GPU only, no relayout per frame (the Portal is API 28) — and stays collapsed on later app pages.
- Because the header is drawn *above* the pager it would be a dead zone for the swipe, so it forwards its own horizontal drags to the pager via `dispatchRawDelta` and settles at 25 % of a page width or 600 px/s (`pagerDragForward`). Its reported height follows the visible (scaled) height so the collapsed clock stops eating taps meant for the first row of icons (`collapsingHeight`).
- Tap/long-press on the clock keep their meaning (HA / quick actions) but only while it is expanded (`collapse < 0.5`).
- **Masquées** and **Réglages** live in the top bar, right-aligned beside the clock (`LauncherHeaderActions.kt`), and fade in with the swipe so the idle clock screen stays bare. They used to be grid tiles, which took cells meant for apps and looked draggable.
- Pill/app drag and an active Maison rail gesture lock parent pager scrolling. An open panel reserves
  33 % of the relevant axis and remeasures Clock, Maison and application pages into the remaining
  area; only the full-screen wallpaper/scrims continue underneath. Sitting on Maison or any app page
  arms auto-return like the expanded tray does.
- **A pause keeps the page when the launcher itself opened something** (an app, a shortcut, Settings, app info, uninstall — `openingFromLauncher`); any other pause, screen-off included, resets to the clock so the panel never wakes up on the grid. Without that split the page snapped home before the launched app even appeared, and coming back landed on the clock instead of where the icon was.
- Auto-return is frozen while the launcher is not resumed: it exists for an idle *visible* panel, and letting the countdown run behind another app dragged the page home behind the user's back. Coming back re-arms it from zero.
- Every return to Accueil goes through the logical-layout-aware `returnToClockPage`, which launches the scroll in a scope that outlives the caller's effect. Awaiting it inside a `LaunchedEffect` keyed on the trigger stranded the pager mid-scroll — crossing the page midpoint cleared the trigger, the key changed, the effect was cancelled, and a few icons stayed faintly visible over the clock.
- Page count is derived: every occupied page, plus a trailing empty one **only while an icon is in hand**. That page is how new pages are created (drag an icon onto it) but dead weight otherwise — an extra dot and a swipe into nothing — so it appears with the drag and goes away with it. Dropping back onto an earlier page removes it from under the current page, so the pager clamps back to the last real one.
- The wallpaper dims a further 45 % across the swipe, and the tray gradient fades out; both alphas are read in the layer phase, so no recomposition per frame.
- Apps are enumerated **once** off-main with icons pre-rasterized to `ImageBitmap` (`AppListStore`), refreshed through `LauncherApps.Callback` (registered for the activity's whole life, since an uninstall happens while the launcher is paused). The old drawer called `getApplicationIcon()` inside composition — disk I/O on the main thread, invisible behind a fade but a visible stutter inside a pager.

### App grid: free placement, menu, pages
Icons sit at the exact cell they were dropped in — an absolutely-positioned grid, not a lazy list, because holes are the point and pages replace scrolling (`AppGridPage.kt`). Long-press an icon → its menu; keep moving the finger → the menu closes and the icon is picked up. One gesture, arbitrated the way real launchers do it.

- **Cells** come from the page size (`gridSpecFor`, min 3×2), so hit-testing is arithmetic: a point maps to a cell with no reported rectangles involved (`cellAt`). Pages report the spec they afford, and placement re-resolves against it.
- **Nothing is compacted.** `placeItems` only moves an item in two cases: its cell no longer exists (the grid shrank, e.g. on rotation) or two items claim the same one — then it takes the first free cell instead of vanishing or overlapping. A freshly installed app sorts last, so an install never disturbs the arrangement.
- **A drop on an occupied cell swaps** the two icons. There is no dense order to cascade along under free placement, and refusing the drop would just throw the gesture away.
- **Cross-page drags**: hold the icon against a page edge for 450 ms and the pager flips (`EDGE_FLIP_*`), which is also how you reach the drag-only empty page and thus create one. The edges are the **pager's viewport**, not a page's rectangle: pages slide, so a scrolled-away page's edges are meaningless — reading one made every position look like "against the right edge" and none like the left, so only forward flips worked. The drag state lives *above* the pager (`GridDragState`) because a page-local one could not survive the flip; the icon is drawn by an overlay in root coordinates, and the page under the icon's centre decides the target cell.
- The gesture belongs to the page it started on, so it must survive that page moving and being scrolled off: its `pointerInput` is keyed on `page` + `spec` only (keying it on the page's rect rebuilt the node on every flip, ending the drag exactly when it had to continue), the on-page items are read through `rememberUpdatedState`, and while an icon is in hand **every** page stays composed (`beyondViewportPageCount`). A cancel is still treated as a drop, as a last-resort safety net.
- The hovered cell is outlined from the draw phase, so following the finger costs no recomposition.
- **Item menu** (`AppContextMenu.kt`): the app's own shortcuts (manifest + dynamic + pinned, max 4), then Renommer / Masquer / Infos de l'application / Désinstaller. A pinned shortcut gets Retirer instead. Uninstall is hidden for system apps and for Portal itself (`ApplicationInfo.FLAG_SYSTEM`).
- Shortcuts come from `LauncherApps.getShortcuts()`, which **throws unless Portal is the selected home app**. Every call is gated on `hasShortcutHostPermission()`, and when it is false the menu says so rather than looking like an app with no shortcuts.
- The menu is positioned from its *measured* height (flip above the tile, clamp into the screen, scroll if still too tall). Its panel swallows taps with a raw pointer handler, **not** `clickable`, which would merge the whole panel into one semantics node and hide the rows from TalkBack.
- Placement lives in `Prefs.appPlacements` (key → page/col/row), renames in `Prefs.appLabels`, hidden items in `Prefs.hiddenApps`. A pre-pages arrangement (`appOrder`) is converted to cells once, so upgrading does not scatter it.
- Hidden apps come back through the top-bar button — hiding would otherwise be a one-way trip.
- Icons use `Modifier.nonConsumingClickable`: `appleClickable` consumes the `down`, and a consumed event cancels the container's pending long-press — which silently killed the whole item menu.

### Widgets
Bound through our own `AppWidgetHost` (`WidgetHostController.kt`), added from the surface menu → **Ajouter un widget**.

- The picker is ours, not `ACTION_APPWIDGET_PICK`: the system one is platform-styled and says nothing about size, and size is what decides whether a widget fits a page at all. Each entry shows its app and its cell footprint.
- Adding is allocate → bind → configure. `bindAppWidgetIdIfAllowed` first; binding widgets is **not a permission an app can hold**, so when it fails the user is asked through `ACTION_APPWIDGET_BIND`. If the provider declares a `configure` activity it runs before the widget is kept. Every failure path releases the id — a leaked id keeps the provider updating a widget nobody can see.
- The host listens only between `onStart` and `onStop`: it is a live connection to other processes.
- An id whose provider is gone (app uninstalled) is dropped and released rather than holding cells with nothing to draw.
- Widget views are hosted with `AndroidView`, whose factory runs once per node — the view is a connection, not something to rebuild on recomposition.
- **Multi-cell items** run through the grid model: `GridSpan` + `footprint`, so placement, collision, drag and the drop highlight all work on rectangles. A span wider than the page is clamped; a footprint that no longer fits is re-homed like any out-of-range cell. Icons never land inside a widget.
- A dragged widget lands **centred on the finger** (`originForCentre`) and is nudged back onto the page, so dropping a wide one near an edge still fits.
- Resizing is `−`/`+` per axis in the item menu, in whole cells, bounded by the page. A deliberate simplification over a drag-handle frame: the capability is there, the gesture surface is not.

### Being the tablet's launcher
`CATEGORY_HOME` + `singleTask`, plus `stateNotNeeded` / `clearTaskOnLaunch` / `taskAffinity=""` as AOSP Launcher3 declares them. HOME pressed while already home routes through `onNewIntent` and returns to logical Accueil, closing menus, collapsing the tray, leaving Maison edit/reorder mode and dismissing a USER panel. `PinShortcutActivity` accepts `ACTION_CONFIRM_PIN_SHORTCUT` headlessly (a wall panel has nobody to confirm a dialog) and rasterizes the icon on the spot, because a `ShortcutInfo`'s icon cannot be resolved again after the request is consumed.

### Back
Back is always consumed — finishing a home activity gives a black flash while the system restarts it. Innermost surface first: item menu → hidden list → widget picker → surface menu → USER panel (including one-level group pop) → logical Accueil from Maison/apps → nothing (`ui/LauncherBack.kt`, `ui/panel/PanelState.kt`, pure and unit-tested). An AUTO (media) panel is deliberately spared, as everywhere else.

### Overlays
- **Surface menu** — long-press the clock **or an empty cell of the app grid**: fond d'écran (`ACTION_SET_WALLPAPER` chooser), Réglages, Composants, plus **Définir comme launcher par défaut** (`ACTION_HOME_SETTINGS`) when the home role is missing — offered first, since without it app shortcuts and pin requests cannot work at all. The item menu offers the same fix where the user actually notices the problem.
- Its backdrop decides whether a tap was outside the panel (both rectangles in root coordinates) instead of relying on the panel to swallow it: overlapping siblings both receive a gesture, consuming it from the panel is dispatch-order dependent, and the `clickable` that used to do it merged the whole panel into one semantics node — hiding every row from TalkBack.
- **Quick actions** — long-press the clock. Blurred backdrop + 40 % scrim, two entries (Réglages / Composants). Dismiss by scrim tap or a >120 px downward swipe. The app drawer that used to live here is now page 1. Launching an app notifies the presence proxy.
- **Alert overlay** — raised from MQTT (`sound/play` or `notification`), auto-dismisses after 5 s, restartable; scrim tap dismisses, taps inside are swallowed. Also blurs the whole scene to 16 dp while visible.


### Gestures
A left-to-right gesture from Accueil reaches Maison and a right-to-left gesture reaches apps (when Maison is enabled; otherwise the former has no page); Back/HOME/auto-return restore Accueil. Long-press an app icon → its menu; long-press then drag → move it to any free cell, swap with an occupied one, or hold at a page edge to carry it to another app page. Tap the clock → open HA (1 s debounce). Long-press the clock → quick actions. Tap a pill → its existing direct command/panel or a group browser; long-press → the complete pill context menu. An explicitly armed horizontal pill drag reorders pins on drop. Tap the weather pill → weather panel. Swipe down on the quick-actions panel → dismiss. Swipe horizontally on the artwork → change session. Vertical drag on the custom sliders → brightness / Kelvin / cover position. Drag the thermostat ring → setpoint, committed on release. Every touch also feeds the sleep scheduler and the auto-return reset via `dispatchTouchEvent`. No pinch or rotate anywhere.

`Modifier.appleClickable` is the single tap primitive: rippleless, with a GPU-only press-scale (no recomposition), and long-press consumes the gesture so parents don't also fire — `Interactions.kt:37-64`.

### Panel-reserved layout
Panels slide into a reserved 33 % zone: right side in landscape, bottom in portrait. The wallpaper
remains continuous, while Clock, Maison and application pages are animated and remeasured into the
unobstructed 67 %, so no interactive element is hidden below the panel. The last payload remains
mounted briefly for a complete exit transition — `LauncherPanelLayout.kt`.

---

## 9. Design system

`ui/theme/DesignTokens.kt`:
- **Palette** — true black `#000000`, elevated `#1C1C1E`, frosted fill white 8 %, frosted border white 10 %, iOS blue `#0A84FF`, green `#30D158`, yellow `#FFD60A`, red `#FF453A`, switch green `#34C759`. `stateColor(state)` maps MQTT state strings onto it.
- **Radii** — card/panel 28 dp, tray 40 dp, pill 50 %, section 16 dp.
- **Motion** — one spring (damping 0.7, medium stiffness) plus fade 250 ms, slide 300 ms, press scale 0.96.
- **Type** — Space Grotesk variable, pinned per weight to avoid synthetic bolding; display 120 sp Thin down to label 11 sp Medium.
- Custom primitives, all hand-built rather than restyled Material: `IosSwitch` (51×31 dp track, spring thumb), `GlassButton`, `PillButton`, `VerticalFillSlider`, `VerticalColorTempSlider`, `VerticalSegmentedSelector`, `PinKeypad`, `ThermostatArc`, `WheelPicker`, `AccessoryGrid`, `StatusChip` with animated washer/air glyphs.
- Haptics on the keypad and the drag controls (`LocalHapticFeedback`); none on overlay dismiss.

⚠️ **`blurCompat()` is a no-op below API 31 and the Portal runs API 28**, so every "frosted blur" backdrop degrades on device to a flat translucent fill. It looks like real blur only in previews and on newer hardware.

⚠️ Three places hardcode colours outside the tokens: the offline banner's amber (no amber exists in the palette at all), the side-panel background, and the alert pill.

---

## 10. Architecture

### Entry points
- **Activities** — `LauncherActivity` (HOME / LAUNCHER / LEANBACK_LAUNCHER, `singleTask`, `fullSensor`, the only exported one), `SettingsActivity`, `PlaygroundActivity`, `OpacityPreviewActivity`, `ClockThemeActivity` — `AndroidManifest.xml:26-63`.
- **Services** — `MqttBridgeService` (foreground), `ScreenAccessibility` (permission-gated by the system) — `:65-81`.
- **Receivers** — `BootReceiver` (`BOOT_COMPLETED`), `SleepReceiver` (alarm-driven, no filter) — `:83-91`.
- **Queries** — declares visibility into the HA companion app and any `MAIN`/`LAUNCHER` activity, so the app drawer can enumerate what's installed — `:14-20`.

### Dependency injection
`PortalApp` is the only `@HiltAndroidApp`. `di/AppModule.kt` is deliberately thin — it provides `Prefs` and `SettingsChangeBus`; `PillRepository` is a `@Singleton @Inject` repository with the application context. Everything lives in `SingletonComponent`; there are no narrower scopes.

Notably, **`LauncherViewModel` is not Hilt-injected**: it is built by a manual `viewModelFactory` and fed `pills.snapshotFlow(prefs)`, a service-call lambda and the repository's atomic Home-preference updater. The VM still has zero Android or Hilt dependency and is directly unit-testable with fake flows/functions — `LauncherActivity.kt:543-550`, `LauncherViewModel.kt:92-96`.

### Data flow
```
HaStateRepository.states() + area/device registries    ← WebSocket, Dispatchers.IO
  + Prefs.pillRules + HomePillPreferences
  → PillRepository.snapshotFlow                        ← sample(100ms), scan
      → PillCatalogBuilder                             ← devices, groups, availability
      → PillPriorityEngine                             ← non-truncated dynamic ranking
      → HomePillComposer / HomePageBuilder             ← 3/6/overflow + ordered rails
      → MediaSessionBuilder / temperature summary
                                      flowOn(Dispatchers.Default)
  → LauncherViewModel.uiState                          ← one immutable snapshot StateFlow
  → ClockTray / HomePage / GroupBrowserPanel           ← render + typed intents only
```
The raw→UI projection is atomic and runs in exactly one place, off both the socket thread and the
main thread — `PillRepository.kt:199-247`. Settings/launcher/Maison mutations all go through the
same versioned preference store, whose change event is combined into `snapshotFlow` for immediate
recomposition — `PillRepository.kt:137-141,173-188`, `Prefs.kt:300-355`.

⚠️ `uiState` is nominally `WhileSubscribed(5000)`, but the VM's own `init` collector subscribes for the VM's whole lifetime, so upstream never actually goes cold. Deliberate for an always-on kiosk, documented at `LauncherViewModel.kt:48-51`.

The VM exposes catalog, area ids/names, Home preferences, `HomeComposition` and `HomePageModel` in
the same immutable `LauncherUiState`. It provides typed pin/reorder/toggle APIs, owns the panel
reducer and derives `panelChip` through a pure `scan` that keeps the last-known-good chip, so an open
panel does not snap shut when its pill temporarily leaves the composition —
`LauncherViewModel.kt:39-63,129-152,193-232`.

### Layering, and where it leaks
`ui/panel/PanelState.kt` is a pure reducer, including the typed group/device stack.
`ui/mapper/ChipMapper.kt` is the single place where legacy chip ids and kinds are branched on,
keeping call sites free of display-label matching. `domain/home` contains the Compose/Android-free
catalog, alert, composition, rail and Maison reducers; `LauncherChip` is kept on the live rendering
side of that boundary.

Three known leaks:
1. **`LauncherActivity.kt` remains a large orchestration composable** holding transient pager/menu/drag state. Persistent pins/groups and composition no longer live there, but extracting the remaining UI coordination is still unfinished.
2. **`WeatherController` is a second, parallel state-holder** — a plain class with `mutableStateOf` and a manual `start()`/`stop()`, talking to `PillRepository`'s legacy debounced `Listener` instead of `snapshotFlow`. Two different ways of getting HA data into Compose coexist, and nothing flags it.

A typed `ChipVisual` enum to replace the stringly-typed `LauncherChip.state` was considered and **explicitly deferred** — the engine emits ad-hoc state strings that don't fit an enum cleanly, and there's no visual-regression net to migrate against (`domain/model/Models.kt:45-49`).

---

## 11. Tests and build

### Coverage
540 automated tests in 73 test files remain under `app/src/test`; there is still no `androidTest` source set. Compose
semantics/layout tests run under Robolectric on the JVM (`@Config(sdk = [28])`), not on a physical
device.

The Maison work adds direct coverage for:

- `HomePillPreferencesCodec` round-trip/defaults/unknown ids/corruption and the non-destructive
  `Prefs` migration;
- complete catalog eligibility, area/type/manual groups, partial availability and global stale state;
- pure dynamic alert policy and the 3/6/overflow composer, including restoration/promotion and stable
  tie-breaks;
- Maison section/order and one/two-row layout policies;
- logical pager remapping, HOME/Back/auto-return targeting and the typed group panel reducer;
- Settings reducers for every pin/section/manual-group mutation and accessible reorder alternative;
- Robolectric semantics for the collapsed/expanded tray, Maison icon, context menu, rails, empty/stale
  states and group browser;
- explicit non-regression contracts for SWITCH/FAN, Presence/Energy, media `PanelSource`, panels and
  application-page mapping.

Representative files: `HomePillComposerTest.kt`, `PillCatalogBuilderTest.kt`,
`HomePillPreferencesCodecTest.kt`, `HomePageBuilderTest.kt`, `HomeSettingsReducerTest.kt`,
`LauncherPagerTest.kt`, `HomeTrayAcceptanceTest.kt`, `GroupBrowserAcceptanceTest.kt` and
`FeatureAcceptanceNonRegressionTest.kt`, plus `HomeAccessibilityAcceptanceTest.kt` and
`HomeRailPagerGestureTest.kt` for TalkBack/large-text/D-pad and nested-gesture contracts.
`./gradlew test` passed with 552 tests, 0 failed/skipped, on 2026-08-08.

**Still without proportionate automated coverage**:
- the entire HA connectivity layer — `HaStateRepository`, `HaApiClient`, `HaDiscovery`, `HaMdnsDiscovery`. The reconnect and liveness-watchdog logic this README advertises is untested at the unit level.
- `MqttBridgeService` — discovery payload construction and publishing.
- the whole device subsystem — `DeviceStateHub`, `SensorBridge`, `SoundMonitor`, `ScreenControl`, `SleepScheduler`, `BootReceiver`, `AdbControl`.
- most of the clock/idle surface — `DynamicBackground`, `WeatherGlyph`/`WeatherCard`/`WeatherPanel`, `ClockTheme`; the new tray itself has semantics tests.
- most legacy rendered panels and the component library; the new group browser and its nested-panel path are covered.
- the rendered settings navigation, setup wizard, and HA connection flow; the new settings mutation/projection layer is covered as pure code.
- no golden/screenshot tests exist (no Paparazzi or Roborazzi), so visual regressions in the design language are caught only by looking at real hardware.

### Build
- AGP 8.3.2, Kotlin 2.0.20 (Compose compiler plugin), KSP only for Hilt.
- **minSdk 28, targetSdk 28, compileSdk 35** — targetSdk is pinned to the Portal's actual OS while compiling against a much newer SDK, with `android.suppressUnsupportedCompileSdk=35` silencing the warning. None of the behaviour changes gated on API 29+ are exercised.
- `isMinifyEnabled = false` even in release — no R8, so release APKs are unminified. Low risk for a sideloaded kiosk app, worth knowing.
- **Release unit tests are disabled on purpose** (`app/build.gradle.kts:36-48`): the Compose test manifest that supplies the `ComponentActivity` for `createComposeRule()` is `debugImplementation`-only by design, and Robolectric can't merge a test-only manifest into a release variant. Disabling `testReleaseUnitTest` is the correct fix rather than shipping test-only manifest content.
- Dependencies of note: Paho MQTT 1.2.5 (from a **custom Eclipse repo**, not Maven Central), OkHttp 4.12, Coil 2.7, Compose BOM 2024.09, Hilt 2.52. ⚠️ `androidx.security:security-crypto` is on **1.1.0-alpha06** and is the one dependency hardcoded outside the version catalog — an alpha for the library holding the HA token. `material-icons-extended` pulls thousands of icons for a handful of uses; `hilt-navigation-compose` is declared but there is no Compose Navigation in the app (activities are the navigation unit).

### i18n footprint
`res/values/strings.xml` holds **3 strings**: the app name and two accessibility service labels. Everything else is a Kotlin literal — roughly **590 user-facing French strings** across ~25 files.

Biggest concentrations: `PillPriorityEngine.kt` (72), `PlaygroundScreen.kt` (59, dev-only so low priority), `PillRules.kt` (47), `SettingsScreen.kt` (41), `MediaPlayerView.kt` (40), `HaDiscovery.kt` (22, needs triage — some are HA-facing entity names), `HomeConnectionPage.kt` (21), `LightDetailPanel.kt` (21), `ClockScreen.kt` (19), `SetupWizard.kt` (17).

⚠️ Extraction is **not** purely mechanical. Domain logic still generates labels without a
`Context`, so either it takes a resource lookup or returns keys resolved at the Compose boundary.
`ClockFont`/`ClockTint` store labels as `String` fields on an enum, and enum constructors cannot call
`@Composable`, so those need a mapping function.

---

## 12. Known gaps, dead code, rough edges

### Dead settings and constants
| Item | Status |
|---|---|
| Tap-sensitivity slider | inert — the accelerometer is never registered |

| `priorityBoost` on pill rules | decoded, clamped, added to scores — but no UI writes it and all 9 grouped kinds ignore it |
| `VacuumFeature.CLEAN_SPOT` / `.START` | declared, never checked |
| `ConnectionDot` | composable exists, only its own preview uses it |
| `ClockScreen(drawBackground = true)` | preview-only path; production always draws the background one level up |
| `toChip` branches for legacy AIR / CLIMATE / BATTERY / ENERGY / SCENE / PRESENCE | unreachable behind the strict support gate; retained only to keep exhaustive handling of codec-compatible `PillKind` values |
| Accelerometer / RGB / tap-tilt MQTT entities | retired, discovery actively retracted |

### Behavioural rough edges
- A fresh install auto-enables three pill kinds: lights, media and purifier.
- The historical dynamic purifier aggregate still summarizes only the first purifier; all compatible purifiers are nevertheless present individually in Maison and automatic groups.
- Settings' entity refresh overwrites `label` / `kind` / `relatedEntityIds` from the live HA scan, so those fields can't hold a manual edit.
- Pill reordering is a relative horizontal drag after an explicit menu action, not a free-position drag overlay. Accueil exposes only the visible nine; overflow order is reachable through the Favoris rail or Settings.
- Maison rail gestures remain rail-owned for their full gesture. Pager navigation beside a long rail is guaranteed through a dedicated 24 dp edge, but there is no velocity/residual-delta handoff from a rail at its scroll boundary.
- The new accessibility and responsive contracts have Robolectric/logic coverage, but no device-level TalkBack, D-pad or large-font screenshot suite; real Android 9 wall-panel validation is still manual.
- Media: mute not actionable, no seek, primary transport hits the leader only, `join` sends one member at a time.
- Thermostat has no dual setpoint.
- HA reconnect has no backoff; the watchdog can be fooled by a ponging-but-silent instance.

- The weather entity is whichever `weather.*` HA lists first.
- The offline banner never rolls over to hours.
- The `nature` background has no offline fallback and its 5 image URLs are hardcoded.
- No unavailable state on the list-based panels.
- Silent fallback to unencrypted preferences if the Keystore is unavailable.

### Not implemented at all
`button` / `number` / `select` HA entities · presence zones · media seek and source selection · vacuum clean modes · light theme and accent colour · comprehensive portrait/small-screen adaptation outside the new Maison/tray surfaces · complete i18n (new pill/Maison settings strings are extracted in English/French, but much of the older UI remains Kotlin-literal French).

### Verified NOT bugs
Claims that looked like defects and were checked, then refuted — recorded so nobody re-opens them:
- **`media_player.join` sending one member at a time** is fine: HA's `join` is additive for the group-capable integrations, and `selectedGroupMembers` is local optimistic state, not the payload.
- **`QuickTiles.kt` is not dead** — `StatusChip`, `ChipGlyph`, `WasherGlyph` and `AirGlyph` are all reachable from `ClockScreen`. (The file was later removed entirely in the "refactor code structure" commit.)
- **The auto-return fix is airtight**, verified structurally: `onInteraction()` cannot arm a stopped timer (`armed` is set only by `start()`/`stop()`); a `PanelRequest.Media` is only ever constructed with `source = AUTO`, and the dismiss site is gated on `source == USER`, so the timeout *structurally* cannot dismiss a media panel; and the state-driven auto-open loop converges in every traced ordering, because `MutableStateFlow.update` doesn't re-emit an equal state.
