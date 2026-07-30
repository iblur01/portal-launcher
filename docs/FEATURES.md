# Portal Launcher — Feature Reference

Exhaustive inventory of what the app actually does, derived from the code (not from intent).

Scope note: this is a **launcher** that takes the home role on any Android 9+ device, whose default
screen is a clock and whose Home Assistant integration is optional. Portal-specific behaviour (the
dream/sleep presence proxy, the API 28 blur fallback) is called out where it appears.
Every non-obvious claim carries a `file:line`. Written 2026-07-28 against branch
`refactor/mad-panel-architecture`.

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
- **Presence micro-chip**, top-left: up to 4 overlapping avatars of people home, nothing if the house is empty; avatars come from HA `entity_picture` (bearer-authenticated) with an initial-letter fallback — `PresencePanel.kt:44-59,104-121`. It is the only `FLOATING` chip and is excluded from the tray so it never double-renders (`LauncherActivity.kt:384-385`).
- **Temperature pill**: `Appartement {min}–{max}` + `Ext. {outdoor}` — `ClockScreen.kt:132-143`.
- **Offline banner**: shown only when `!connected && lastUpdateAt > 0`, ticking every second, amber pill reading "Hors ligne — infos figées depuis {N}s" — `ClockScreen.kt:144-147,191-210`. ⚠️ Never rolls over to hours: after 100 minutes it reads "142min".
- **Expand affordance**: "Voir plus d'informations" appears only when there are more than 3 chips; collapsed shows 3, expanded shows up to 9 in rows of 3 — `ClockScreen.kt:159-185`. Expanding also grows the bottom gradient 360→620 dp over 450 ms (`LauncherActivity.kt:389-393`) and arms auto-return.

---

## 2. Pill system (the ranked chip tray)

Pipeline: `Prefs.pillRules` → `PillRepository.transformSnapshots` (sampled 100 ms, `Dispatchers.Default`) → `PillPriorityEngine.select` → `LauncherViewModel.uiState` → tray — `PillRepository.kt:133,150`, `PillPriorityEngine.kt:18`.

### The 20 kinds and how each is detected
`PillRules.kt:6-27` (kind + base priority), detection in `PillSupport.kind` (`:172-193`):

| Kind | Base | Detected from |
|---|---|---|
| SAFETY | 90 | `alarm_control_panel`, or binary_sensor with class smoke/CO/gas/moisture/safety/tamper |
| APPLIANCE | 58 | entity id contains washer/dryer/dishwasher/printer tokens **and** is the main state sensor |
| VACUUM | 58 | domain `vacuum` |
| TIMER | 50 | domain `timer` or class `duration` |
| LOCK | 35 | domain `lock` or class `lock` |
| AIR | 35 | class aqi / CO₂ / VOC / pm25 / pm10 |
| COVER | 26 | domain `cover` |
| THERMOSTAT | 24 | domain `climate` |
| OPENING | 25 | class door / window / opening / garage_door |
| CLIMATE | 22 | class temperature / humidity |
| PRESENCE | 20 | domain `person` |
| BATTERY | 20 | class `battery` |
| ENERGY | 18 | class energy / power / current / voltage |
| SWITCH | 17 | domain `switch` |
| LIGHTS | 16 | domain `light` |
| FAN | 15 | domain `fan`, not matching "purif" |
| PURIFIER | 15 | domain `fan` + name/id contains "purif" |
| MEDIA | 13 | domain `media_player` |
| SCENE | 12 | domain `scene` / `script` |
| GENERIC | 15 | fallback |

⚠️ **Eligibility gate**: `PillSupport.isPrimary` (`PillRules.kt:159-171`) only admits `sensor` entities for the air/temperature/humidity classes and the one appliance-state sensor. Arbitrary `sensor.*`, `input_boolean`, etc. are invisible to the pill system entirely, whatever the rule says.

**Related-entity auto-linking**: a `logicalKey` (entity id stripped of `_state`, `_contact`, `_temperature`, `_humidity`, …) groups sibling sensors into `relatedEntityIds`, so a washer's cycle/completion sensors ride along with its main pill instead of becoming their own — `PillRules.kt:199-213`.

### Ranking
One sort for everything: `compareByDescending{priority}.thenBy{label}` then **`.take(9)`** — `PillPriorityEngine.kt:34`.
- ⚠️ Overflow past 9 is silently dropped, with no "+N more" affordance anywhere.
- Per-kind state overrides dominate the base priority. Highlights: alarm triggered → 100; safety sensor active → 100; lock unlocked/jammed → 88; battery ≤10 % → 82; open door → `55 + min(age_minutes, 30)` so it escalates the longer it stays open; appliance finished → 76 but only for 10 minutes; battery visible only ≤30 %.
- ⚠️ Scores are not on a normalised scale — they were tuned per kind ad hoc.

### Grouped pills
Nine kinds never appear individually, only merged into one pill (`groupedKinds`, `PillPriorityEngine.kt:20-22`): openings, temperatures, lights, media, purifier, air, scenes, presence, energy. Notable behaviours:
- **openingGroup** — "Toutes fermées" / "N ouverte(s)", priority rises with how long something has been open (`:229-253`).
- **temperatureGroup** — "Min X · Max Y", warning below 16 °C or above 28 °C; humidity siblings shown as "· NN%" (`:190-212`).
- **relatedBatteryAlert** — not a kind: any *related* entity that is a battery ≤30 % surfaces here. The only way a non-primary battery becomes visible (`:214-227`).
- **mediaGroup** — playing/buffering counts; a paused player only counts if paused less than 30 s ago (`:109-133`).
- ⚠️ **purifierGroup** takes only the **first** enabled purifier; additional ones are silently invisible (`:135-156`).
- ⚠️ **energyGroup** guesses the main meter by regex `maison|total|grid|global|home|conso`, else takes an arbitrary power sensor — there is no explicit "main meter" designation (`:60-79`).

### Auto-init on fresh install
One-shot, gated by `pillAutoGroupsInitialized` (`PillRepository.kt:108-119`): auto-enables LIGHTS, MEDIA, PURIFIER, SCENE, PRESENCE, ENERGY. ⚠️ **OPENING, AIR and CLIMATE are excluded**, so a fresh install shows no door/window, air-quality or temperature pills until the user enables them by hand.

### Interaction
- Tap: SWITCH and FAN call `toggle` directly; every other kind opens its panel — `ChipMapper.kt`, `LauncherActivity.kt:357-362`.
- Long-press: always opens the control panel, ⚠️ except media, which has no long-press at all (`LauncherActivity.kt:364-369`).
- The open panel's chip gets an accent fill + border; only the media chip hides itself while its panel is open (`LauncherActivity.kt:382-387`).
- If a chip's entity goes unavailable or falls out of the top 9 **while its panel is open**, the panel keeps the last-known-good snapshot instead of closing — `LauncherViewModel.kt:84-89`. The tray chip disappears underneath, so the highlight target vanishes; cosmetic only.

---

## 3. Control panels

Router: `ChipActionsPanel` → exhaustive `when (chip.toPanelKind())`, no catch-all — `SidePanel.kt:174-191`. Shared chrome = frosted card, circular "×" top-left, centred icon+label pill, `chip.value` as title, scrollable body (`SidePanel.kt:118-171`). Live entity access via `rememberEntity()`, which dedups HA pushes by comparing state + attributes so unrelated updates don't recompose an open control (`PanelHelpers.kt:36-48`).

Unavailable handling: single-entity panels render "Appareil indisponible" (`PanelHelpers.kt:54-69`). ⚠️ List-based panels (lights, purifier, air, energy, scenes, presence) have no unavailable state and degrade silently.

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
| **Scenes/Scripts** | Interactive | `{scene\|script}.turn_on`, domain inferred from the entity id prefix |
| **Air quality** | Read-only | — |
| **Energy** | Read-only | — |
| **Presence** | Read-only | — |
| **Weather** | Read-only | — |

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
- ⚠️ **Energy detects its sensors by matching the French labels** `"Puissance"` and `"Aujourd'hui"` (`EnergyPanel.kt:37-38`) — a brittle coupling that will break during i18n. Gauge auto-scales from 3000 W upward and never back down.
- ⚠️ **Purifier and Fan are two entirely different UIs for the same `fan` domain** with no shared code (mode selector vs toggle + speed + oscillate).
- ⚠️ **Weather bypasses the shared panel chrome** and reimplements its own header/close button (`WeatherPanel.kt:59-67`).
- Air quality renders full-bleed with a tinted radial glow and a 2–3 column sensor grid; thresholds per unit type (µg/m³ >25/>50, ppm >800/>1000, AQI >50/>100, ppb >500/>800) — `AirQualityPanel.kt:233-282`.
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

### Pages
One flat `HorizontalPager` holds everything: `[clock, app page 0, app page 1, …]` (`LauncherPager.kt`). Not a pager nested inside the apps page — two horizontal scrollers competing for the same drag has no good answer, and stock launchers treat their home screens as one pager for the same reason. Swipe right-to-left to reach the apps, and keep going for further pages.
- The clock header is **pinned above every page**, not inside one. It shrinks to 34 % over the clock→apps swipe, driven by `currentPage + currentPageOffsetFraction` through a `graphicsLayer` scale — GPU only, no relayout per frame (the Portal is API 28) — and stays collapsed on later pages because the fraction saturates at 1.
- Because the header is drawn *above* the pager it would be a dead zone for the swipe, so it forwards its own horizontal drags to the pager via `dispatchRawDelta` and settles at 25 % of a page width or 600 px/s (`pagerDragForward`). Its reported height follows the visible (scaled) height so the collapsed clock stops eating taps meant for the first row of icons (`collapsingHeight`).
- Tap/long-press on the clock keep their meaning (HA / quick actions) but only while it is expanded (`collapse < 0.5`).
- **Masquées** and **Réglages** live in the top bar, right-aligned beside the clock (`LauncherHeaderActions.kt`), and fade in with the swipe so the idle clock screen stays bare. They used to be grid tiles, which took cells meant for apps and looked draggable.
- The pager locks to the clock page while a panel is open (`userScrollEnabled = !isSplit`), and sitting on any app page arms auto-return like the expanded tray does.
- **A pause keeps the page when the launcher itself opened something** (an app, a shortcut, Settings, app info, uninstall — `openingFromLauncher`); any other pause, screen-off included, resets to the clock so the panel never wakes up on the grid. Without that split the page snapped home before the launched app even appeared, and coming back landed on the clock instead of where the icon was.
- Auto-return is frozen while the launcher is not resumed: it exists for an idle *visible* panel, and letting the countdown run behind another app dragged the page home behind the user's back. Coming back re-arms it from zero.
- Every return to the clock goes through `returnToClockPage`, which launches the scroll in a scope that outlives the caller's effect. Awaiting it inside a `LaunchedEffect` keyed on the trigger stranded the pager mid-scroll — crossing the page midpoint cleared the trigger, the key changed, the effect was cancelled, and a few icons stayed faintly visible over the clock.
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
`CATEGORY_HOME` + `singleTask`, plus `stateNotNeeded` / `clearTaskOnLaunch` / `taskAffinity=""` as AOSP Launcher3 declares them. HOME pressed while already home routes through `onNewIntent` and returns to the clock page, closing the menu, the tray and the overlay. `PinShortcutActivity` accepts `ACTION_CONFIRM_PIN_SHORTCUT` headlessly (a wall panel has nobody to confirm a dialog) and rasterizes the icon on the spot, because a `ShortcutInfo`'s icon cannot be resolved again after the request is consumed.

### Back
Back is always consumed — finishing a home activity gives a black flash while the system restarts it. Innermost surface first: item menu → hidden list → surface menu → USER panel → back to the clock page → nothing (`ui/LauncherBack.kt`, pure and unit-tested). An AUTO (media) panel is deliberately spared, as everywhere else.

### Overlays
- **Surface menu** — long-press the clock **or an empty cell of the app grid**: fond d'écran (`ACTION_SET_WALLPAPER` chooser), Réglages, Composants, plus **Définir comme launcher par défaut** (`ACTION_HOME_SETTINGS`) when the home role is missing — offered first, since without it app shortcuts and pin requests cannot work at all. The item menu offers the same fix where the user actually notices the problem.
- Its backdrop decides whether a tap was outside the panel (both rectangles in root coordinates) instead of relying on the panel to swallow it: overlapping siblings both receive a gesture, consuming it from the panel is dispatch-order dependent, and the `clickable` that used to do it merged the whole panel into one semantics node — hiding every row from TalkBack.
- **Quick actions** — long-press the clock. Blurred backdrop + 40 % scrim, two entries (Réglages / Composants). Dismiss by scrim tap or a >120 px downward swipe. The app drawer that used to live here is now page 1. Launching an app notifies the presence proxy.
- **Alert overlay** — raised from MQTT (`sound/play` or `notification`), auto-dismisses after 5 s, restartable; scrim tap dismisses, taps inside are swallowed. Also blurs the whole scene to 16 dp while visible.


### Gestures
Swipe right-to-left anywhere on the home page (clock band included) → app grid; swipe back, or wait for auto-return. Long-press an app icon → its menu; long-press then drag → move it to any free cell, swap with an occupied one, or hold at a page edge to carry it to another page. Tap the clock → open HA (1 s debounce). Long-press the clock → quick actions. Tap chip → toggle or panel. Long-press chip → panel (except media). Tap the weather pill → weather panel. Swipe down on the quick-actions panel → dismiss. Swipe horizontally on the artwork → change session. Vertical drag on the custom sliders → brightness / Kelvin / cover position. Drag the thermostat ring → setpoint, committed on release. Every touch also feeds the sleep scheduler and the auto-return reset via `dispatchTouchEvent` (`LauncherActivity.kt:135-141`). No pinch or rotate anywhere.

`Modifier.appleClickable` is the single tap primitive: rippleless, with a GPU-only press-scale (no recomposition), and long-press consumes the gesture so parents don't also fire — `Interactions.kt:37-64`.

### Split layout
`isSplit` whenever a panel resolves. 67/33 split, `tween(500)`: a `Row` in landscape, a `Column` in portrait, chosen from `BoxWithConstraints`. The panel stays mounted through the collapse animation and only unmounts once the clock is back to full width — `LauncherActivity.kt:489-554`.

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
`PortalApp` is the only `@HiltAndroidApp`. `di/AppModule.kt` is deliberately thin — it provides just `Prefs` (needs `Context`) and `SettingsChangeBus`; `PillRepository` is `@Singleton @Inject constructor()`. Everything lives in `SingletonComponent`; there are no narrower scopes.

Notably, **`LauncherViewModel` is not Hilt-injected**: it's built by a manual `viewModelFactory` and fed `pills.snapshotFlow(prefs)` plus a `callService` lambda (`LauncherActivity.kt:266-275`). That's on purpose — the VM has zero Android or Hilt dependency, which is why it can be unit-tested directly with fakes.

### Data flow
```
HaStateRepository.states()      ← WebSocket, Dispatchers.IO
  → PillRepository.snapshotFlow ← sample(100ms), scan, PillPriorityEngine + MediaSessionBuilder
                                  flowOn(Dispatchers.Default)
  → LauncherViewModel.uiState   ← stateIn(WhileSubscribed(5s))
  → Compose                     ← collectAsStateWithLifecycle
```
The raw→UI transform runs in exactly one place, off both the socket thread and the main thread — `PillRepository.kt:132-178`.

⚠️ `uiState` is nominally `WhileSubscribed(5000)`, but the VM's own `init` collector subscribes for the VM's whole lifetime, so upstream never actually goes cold. Deliberate for an always-on kiosk, documented at `LauncherViewModel.kt:48-51`.

The VM also owns the panel reducer and derives `panelChip` through a pure `scan` that keeps the last-known-good chip, so an open panel doesn't snap shut when its chip briefly leaves the top 9 (`:76-89`).

### Layering, and where it leaks
`ui/panel/PanelState.kt` is a pure reducer. `ui/mapper/ChipMapper.kt` is the single place where chip ids and kinds are branched on, keeping the call sites free of string matching. `domain/model/Models.kt` is Compose-free and Android-free by declaration.

Three known leaks:
1. **`LauncherActivity.kt` is still a ~600-line god composable** holding UI state that belongs in the VM. The `design §N` / `step N/10` comments throughout mark this as a staged, in-progress migration rather than an accident.
2. **`WeatherController` is a second, parallel state-holder** — a plain class with `mutableStateOf` and a manual `start()`/`stop()`, talking to `PillRepository`'s legacy debounced `Listener` instead of `snapshotFlow`. Two different ways of getting HA data into Compose coexist, and nothing flags it.
3. **`PresencePanel` constructs its own `Prefs(context)`** via `remember` rather than taking it from the graph or a `CompositionLocal` — even though `LocalCallService`/`LocalHaStates`/`LocalAreas` already exist next door (`LauncherActivity.kt:447-451`).

A typed `ChipVisual` enum to replace the stringly-typed `LauncherChip.state` was considered and **explicitly deferred** — the engine emits ad-hoc state strings that don't fit an enum cleanly, and there's no visual-regression net to migrate against (`domain/model/Models.kt:45-49`).

---

## 11. Tests and build

### Coverage
113 tests, 18 files, **all under `app/src/test`**. There is no `androidTest` source set at all — the two "Compose UI tests" run under Robolectric on the JVM (`@Config(sdk = [28])`), not on a device.

**Covered**: the panel reducer (16 cases, every branch including interleaved user/auto), the priority engine (18), chip mapping (9), the media session builder (8), the VM's stream composition via Turbine (8), the `PillRepository` transform, `HaEntity` equality, panel helpers. Two Compose tests are targeted regression guards for a specific past bug (alarm-keypad jank), not general coverage.

**Zero coverage**:
- the entire HA connectivity layer — `HaStateRepository`, `HaApiClient`, `HaDiscovery`, `HaMdnsDiscovery`. The reconnect and liveness-watchdog logic this README advertises is untested at the unit level.
- `MqttBridgeService` — discovery payload construction and publishing.
- the whole device subsystem — `DeviceStateHub`, `SensorBridge`, `SoundMonitor`, `ScreenControl`, `SleepScheduler`, `BootReceiver`, `AdbControl`.
- the clock/idle surface — `ClockScreen`, `DynamicBackground`, `WeatherGlyph`/`WeatherCard`/`WeatherPanel`, `ClockTheme`, `PresencePanel`.
- **every rendered panel** and the whole component library.
- the settings UI, the setup wizard, and the HA connection flow.
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

⚠️ Extraction is **not** purely mechanical. The two biggest offenders are domain logic, not Compose — they generate labels without a `Context`, so either they take a resource lookup or they return keys resolved at the Compose boundary. `ClockFont`/`ClockTint` store labels as `String` fields on an enum, and enum constructors can't call `@Composable`, so those need a mapping function. And `EnergyPanel.kt:37-38` matches the literal strings `"Puissance"` and `"Aujourd'hui"` to find its sensors — translating those blanks the panel silently. Fix that first.

---

## 12. Known gaps, dead code, rough edges

### Dead settings and constants
| Item | Status |
|---|---|
| Tap-sensitivity slider | inert — the accelerometer is never registered |

| `priorityBoost` on pill rules | decoded, clamped, added to scores — but no UI writes it and all 9 grouped kinds ignore it |
| `LauncherChip.stale` | declared, never set to true |
| `VacuumFeature.CLEAN_SPOT` / `.START` | declared, never checked |
| `ConnectionDot` | composable exists, only its own preview uses it |
| `ClockScreen(drawBackground = true)` | preview-only path; production always draws the background one level up |
| `toChip` branches for OPENING / AIR / ENERGY | unreachable — those kinds are always grouped |
| Accelerometer / RGB / tap-tilt MQTT entities | retired, discovery actively retracted |

### Behavioural rough edges
- Pill overflow past 9 is invisible with no affordance.
- A fresh install auto-enables 6 pill kinds but not openings, air or temperature.
- Only the first purifier is ever shown.
- The energy "main meter" is a regex guess; the energy panel finds its sensors by matching French labels.
- Settings' entity refresh overwrites `label` / `kind` / `relatedEntityIds` from the live HA scan, so those fields can't hold a manual edit.
- Media: mute not actionable, no seek, primary transport hits the leader only, `join` sends one member at a time.
- Thermostat has no dual setpoint.
- HA reconnect has no backoff; the watchdog can be fooled by a ponging-but-silent instance.

- The weather entity is whichever `weather.*` HA lists first.
- The offline banner never rolls over to hours.
- The `nature` background has no offline fallback and its 5 image URLs are hardcoded.
- No unavailable state on the list-based panels.
- Silent fallback to unencrypted preferences if the Keystore is unavailable.

### Not implemented at all
`button` / `input_*` / `number` / `select` HA entities · presence zones · media seek and source selection · vacuum clean modes · light theme and accent colour · portrait/small-screen layout (one exception: `LightDetailPanel` does branch on orientation) · pill reorder and pinning · i18n (the UI is French-only).

### Verified NOT bugs
Claims that looked like defects and were checked, then refuted — recorded so nobody re-opens them:
- **`media_player.join` sending one member at a time** is fine: HA's `join` is additive for the group-capable integrations, and `selectedGroupMembers` is local optimistic state, not the payload.
- **`QuickTiles.kt` is not dead** — `StatusChip`, `ChipGlyph`, `WasherGlyph` and `AirGlyph` are all reachable from `ClockScreen`. (The file was later removed entirely in the "refactor code structure" commit.)
- **The auto-return fix is airtight**, verified structurally: `onInteraction()` cannot arm a stopped timer (`armed` is set only by `start()`/`stop()`); a `PanelRequest.Media` is only ever constructed with `source = AUTO`, and the dismiss site is gated on `source == USER`, so the timeout *structurally* cannot dismiss a media panel; and the state-driven auto-open loop converges in every traced ordering, because `MutableStateFlow.update` doesn't re-emit an equal state.
