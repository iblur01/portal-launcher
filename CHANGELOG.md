# Changelog

## 1.0.1

### Added

- **In-app updates**: Portal checks GitHub for a newer release and installs it without leaving the launcher. A full-screen card shows the version number and the release notes, with three choices — *Update now*, *Remind me later* (24 h snooze) or *Skip this version*. The APK is downloaded to the app cache and handed to the system installer through a `FileProvider`.
- **Panel-friendly update polling**: the check runs at most once a day, only while Portal is in the foreground, and the prompt is held back whenever a panel, a menu, a folder, an overlay or the widget picker is open — so it never interrupts what you are doing.
- **Markdown rendering for release notes**: headings, bullet and numbered lists, bold, italic, inline code and clickable links.
- **New onboarding step "A tap in the void"**: a searchable app list picks what a tap on the empty home screen opens, with an explicit "No app" choice. The same "No app" option is available later from the settings app picker.
- **Light pills show the bulb's real colour**: single-entity `light.*` pills read `rgb_color`, falling back to `color_temp_kelvin`, and tint their icon circle with the light's live colour. The glyph flips between dark and light for contrast; lights that are off keep the neutral accent.
- **Breadcrumbs in settings sub-pages**, tappable to walk back up the tree.

### Changed

- **Settings reorganised into five categories with sub-pages**: *Home screen* (Displayed content, Apps & interaction), *Appearance* (Wallpaper, Clock), *Connected home* (Home Assistant & MQTT, App sessions), *Device* (Display, navigation & language) and *About*. Deep links emitted by older builds still resolve to the right page.
- **The whole runtime UI is localized**: around 170 hard-coded French strings moved to resources — pill kinds and families, entity and alarm states, weather conditions, washer phases, group actions, context menus, connection errors and accessibility labels. An English build no longer falls back to French. Counts such as "2 active out of 5" now use real plurals per locale.
- **Tap on empty home space is opt-in**: a fresh install no longer assumes the Home Assistant app is present. Until an app is picked, the tap does nothing.
- **Wide media player reworked**: the floating corner close button is replaced by a real panel header matching the vertical layout, the artwork takes the remaining height, and the source name reads top-left instead of stacked above the track title.
- **Vertical sliders feel immediate**: brightness and colour-temperature sliders handle tap and drag in a single gesture loop, so a tap commits at once and a tap-then-drag stays one continuous move.
- **Apple TV icon fixed**: the bundled vector is used instead of the icon-pack glyph, and its viewport was corrected to a 2:1 ratio so the logo is no longer squashed.
- Developer options (keep screen on, permissions, reboot) moved into the *About* page.
- The washer glyph now recognises the English phase names as well as the French ones.

### Removed

- The standalone *Developer* settings tile, folded into *About*.
- The implicit Home Assistant fallback for the empty-space tap gesture.

### Performance

- Update checks are rate-limited to one network call per 24 h and suspended while the launcher is not in the foreground, so a panel left running for weeks does not poll GitHub continuously.
- Pill colours animate through a shared spring instead of allocating new colour objects on every state push.
- Sliders consume pointer events in one gesture loop, removing the duplicated pointer-input handlers that both ran on every touch.

## 1.0

First stable release. Leaving beta after `0.0.7-beta`.

### Added

- **Remote configuration from a phone**: an embedded local HTTP server serves a settings page over the Wi-Fi network; the panel displays a QR code and a single-use access code. The Home Assistant address, the token, MQTT and the pill selection are entered from the phone's keyboard. The page closes as soon as the screen is left.
- **"With a phone" onboarding**: the Home Assistant step offers two explicit paths — configuration by phone (QR code) or direct entry on the panel — with a dedicated warning on compact screens.
- **Folders in the app grid**: a folder is created by dropping an icon onto another one, opens as a centered popup, is renamed with a tap on its title, and dissolves automatically below two members or when an app is uninstalled. The tile shows the first four members in a 2×2 layout.
- **Third-party icon packs**: detection of installed packs (ADW / GO / Nova), reading of `appfilter.xml` and application to the grid icons; unthemed apps keep their original icon.
- **Notification dots**: a listener service lights up a dot on applications that have something pending (no content is read or retained), including on folders. Ongoing notifications light up nothing.
- **Layout backup and restore**: export/import of a versioned, human-readable JSON file containing placements, folders, renames, hidden apps, pinned shortcuts, grid scale and icon pack — without any credential or password.
- **Automatic root provisioning**: on a rooted panel, Portal grants itself all system permissions (launcher role, accessibility, notification access, secure settings, doze exclusion) with a single button, both in onboarding and in settings.
- **Door and Motion panels**: new panels dedicated to openings and presence detectors, with state and a "since" timestamp.
- **New Motion pill category**: motion/presence detectors join "Security & access".
- **Source logos on media artwork**: 40 provider logos (Spotify, Netflix, Plex, Sonos, YouTube, Tidal…) bundled as vectors and resolved from `app_name`, `source` or the player's domain.
- **Home page grouped by room or by type**: a selector in the header switches between the two organizations, and the preference is remembered.
- **Tabbed weather forecast**: the weather panel distinguishes "Hours" and "Days", with the day's min/max range.
- **Custom wallpaper in onboarding**: choice of a local photo during initial configuration, in addition to the system, calm and Immich modes.
- **Onboarding completion summary**: the final screen summarizes the choices that were made.

### Changed

- **Adaptive panels**: a panel's composition is decided by its own dimensions, not by the device orientation; panels go full-page on compact screens instead of staying docked to a third of the screen.
- **Progressive disclosure for media**: the main playback controls are kept in priority and secondary detail disappears as the usable height decreases.
- **Compact clock and header**: hierarchy, margins and spacing reworked for small screens, indoor/outdoor temperatures condensed into the header and hidden when unavailable, clear separation between the pills and the pager dots.
- **Weather icons**: the Canvas-drawn glyph is replaced by the static Meteocons icons bundled in the APK, available offline.
- **Drop on an occupied cell**: the gesture creates a folder instead of swapping the two icons; the swap only remains when grouping is impossible (widget, different sizes).
- **French text switched to formal address**: all configuration screens move from the informal *tutoiement* to the formal *vouvoiement* (French second-person forms), with clearer wording.
- **Unified image loading**: a single Coil `ImageLoader` for the whole process, with an SVG decoder, explicit memory/disk budgets (12% of the heap, 32 MB), RGB565 and HTTP proxy bypass for local traffic.
- **Documentation**: README rewritten, new guides `docs/GETTING-STARTED.md`, `docs/ARCHITECTURE.md`, `docs/CONFIGURATION.md`, `docs/DEVELOPMENT.md`, `docs/TESTING.md`, and `docs/FEATURES.md` brought up to date.

### Removed

- Taps no longer pass through panels: an opaque panel absorbs all pointer events, including during its closing animation.
- The complete `meteocons/fill` weather icon set (over 120 SVGs) is replaced by a set reduced to the conditions actually used.
- Removal of `latestStates` from the UI state: raw Home Assistant states no longer travel through the launcher's global state.
- Removal of the "coming soon" mention on Home page grouping, which is now functional.

### Performance

- **Per-entity observable store**: Home Assistant states are no longer broadcast in a global state recomposed as a whole. Each entity has its own observable slot; a `state_changed` on `light.salon` only invalidates the composables that read `light.salon`. Measured: from 122-145 ms per push on a small device down to a single frame.
- **Optimistic UI**: an action immediately writes the predicted state (`turn_on`, `toggle`, `lock`/`unlock`, `open_cover`/`close_cover`, `media_play_pause`…); the real state takes over as soon as it brings something new, with automatic rollback after 4 s without confirmation. Entities in `assumed_state` keep the prediction.
- **Immutable snapshot on the socket side**: the state map becomes a single-writer `PersistentMap` — no more defensive copy of 765 entries on every event, no more lock.
- **Action → confirmation latency divided by six**: the snapshot sampling window goes from 100 ms to 16 ms, and an unsampled raw flow feeds the per-entity store.
- **Memoized pill discovery**: discovery only depends on the set of identifiers and the registries, never on state values; it is only recomputed when a device appears or disappears.
- **Single-pass entity indexing**: the nested scans (765 entities × ~60 candidates on every push) are replaced by a shared per-device index, with memoization of slugs and logical keys.
- **Identities preserved in the UI state**: a projection that is rebuilt but identical keeps its previous instance, so that Compose's "strong skipping" does not recompose the subtrees of the bar and of the Home page.
- **Baseline profile**: addition of `baseline-prof.txt` and of `profileinstaller`, for an AOT-compiled cold start rather than interpreted + JIT.
- **Silent MQTT bridge**: on a root-provisioned device, the bridge runs as a plain service, without an ongoing notification, with automatic fallback to the foreground service elsewhere.
- **Conditional presence probe**: the occupancy proxy is only exposed on hardware that actually has a screensaver component.

## 0.0.7-beta

### Added

- **Home Assistant Home page**: new optional page with configurable sections, favorites, rooms, groups, individual entities, context menus and shortcut reordering.
- **Extended Home Assistant panels**: support for humidifiers, water heaters, valves, sirens, lawn mowers, washing machines, groups and generic entities, with navigation and controls adapted to their capabilities.
- **Home Assistant and MDI icons**: bundled font and index, resolution of native and custom icons, local cache and targeted refresh.
- **Protected ADB provisioning**: hidden activity allowing Home Assistant credentials to be preconfigured on managed devices.
- **TV and D-pad compatibility**: Android TV banner, keyboard/remote navigation and dedicated debug aliases.

### Changed

- **Responsive home**: the Home page now uses an adaptive vertical grid of one to four columns and a more compact shared header.
- **Launcher navigation**: stable order between the House page, Home screen and applications, back navigation to the Home screen, and optimized pager transitions.
- **Home Assistant pills**: complete catalog, contextual priorities, alerts and critical capabilities, while reducing the noise from auxiliary entities.
- **Panels and settings**: alarms, media, thermostats, purifiers, accessories, backgrounds and pill configuration have been enriched and adapted to the different screen sizes.

### Removed

- The former dedicated Air Quality, Energy, Presence and Scenes panels have been replaced by the generic contracts and the Home Assistant groups.
- The nested horizontal rails of the Home page and their gesture arbitration have been replaced by a single vertically scrolling grid.

### Performance

- Home Assistant snapshot transformations are sampled and executed off the main thread.
- Pager and clock transitions favor graphic transformations in order to limit recompositions.
- The MDI index is read directly from the APK without loading it fully into memory.

### i18n

- Added English and French translations for the Home page, the settings, the devices, the alarms and the Playground.

### Tests

- Extended coverage of the Home preferences, of the catalog and priorities, of the navigation, of the Home Assistant panels, of the icons, of accessibility and of the settings reducers.

## 0.0.5-beta

### Added

- **Entity contracts**: `ClimateEntityContract` and `FanEntityContract` — UI-neutral adapters that extract capabilities from Home Assistant entities. Climate handles all thermostat modes (heat, cool, auto, heat_cool, dry, fan_only) with target temp ranges. Fan maps to one of three control modes: on/off, percentage slider, or preset selector.
- **`PortalThreeWayControl`**: reusable three-action capsule control (previous / center / next) shared between media player and covers, with compact and regular densities.
- **`PortalVacuum`**: dedicated vacuum controls — large play/pause disk button, status chip, action chips (stop/dock/locate), room selection chips with toggles.
- **`HorizontalSegmentedSelector`**: Apple Home-style horizontal picker with drag gesture, haptics, and animated floating capsule highlight.
- **Alarm alert handling**: `PanelSource.ALERT` with highest priority — alarm chips surface immediately, `SleepScheduler.alarmHold` keeps screen on until disarmed, dismiss/rearm logic with per-alarm key.
- **Battery display**: vacuum entities show battery percentage in `SidePanel` header and quick tiles, with color-coded indicators (red ≤10%, orange ≤20%).
- **Domain-specific accent colors**: lock turquoise, fan blue, thermostat orange/blue.
- **Playground screen**: interactive panel lab with 14 fake entities for testing all panel layouts.
- **WallpaperPage** activated (was `.disabled`) with native Android wallpaper picker integration.
- **System wallpaper mode**: new `"system"` background mode uses native `WallpaperService` with scroll offset protocol for live wallpaper parallax.
- **Tests**: `PanelStateTest` (28+ reducer scenarios), `ClimateEntityContractTest`, `FanEntityContractTest`.

### Changed

- **All panel controls are now responsive**: sliders, switches, selectors, keypad use proportional scaling (`RoundedCornerShape(percent = 30)`, 96:240 viewport ratio, `contentScale`) instead of fixed dp values. Controls adapt to any screen size (wall tablet, phone).
- **`PanelHeader`**: unified header component used by all panels — frosted navigation circle, optional icon + title, configurable accent, battery indicator.
- **Light panel**: single `AdaptiveLightDetail` layout replaces separate portrait/landscape layouts. Smoother color presets (sunset, candle, soft pink, forest, lagoon, dusk, lavender, evening).
- **Thermostat panel**: Canvas‑drawn arc replaced by `ClimateEntityContract` + `PortalThermostat`. Mode selection via `WheelPicker`. Optimistic UI with 5s timeout. Support for `heat_cool` range mode with dual handles.
- **Cover panel**: three separate `GlassButton` replaced by single `PortalThreeWayControl` with labels. Slider viewport responsive.
- **Vacuum panel**: `PanelModeButton` rows replaced by `VacuumRunButton` + `VacuumStatusChip` + `VacuumActionChips`. Speed selector via `WheelPicker`.
- **Fan/Switch controls**: `BigCircleButton` replaced by responsive `VerticalSwitch` with optimistic UI. Fan uses `FanEntityContract` for mode‑appropriate controls (on/off switch, percentage slider, or preset selector).
- **Alarm panel**: `AccessoryGrid` replaced by responsive `VerticalSegmentedSelector`. Added `DISABLED` arm state. Optimistic arming with 5s timeout. Keypad pulse animation during code verification.
- **Lock panel**: accent changed to turquoise. Responsive viewport.
- **Media player**: transport controls replaced by `PortalThreeWayControl`. Header uses `PanelHeader` with source title.
- **WallpaperPage**: activated from `.disabled`; new "Choose Android wallpaper" picker integration.
- **SettingsScreen**: new `WALLPAPER` page and navigation tile.
- **styles.xml**: window background transparent with `windowShowWallpaper=true` for native wallpaper support.
- **PRODUCT.md**: enriched with 7 design principles, Apple Home interaction reference, wall-panel usage personas.

### Performance

- `PortalWheelPicker`: only emits `onSelect` when scrolling stops (not on every item), reducing HA service calls.
- Keypad: input disabled during loading state to prevent double-taps.

## 0.0.4-beta

### Added

- **Onboarding wizard**: new 15-step guided setup wizard (`OnboardingActivity`) replacing the legacy 3-step `SetupWizard`. Organized in 3 chapters — *Launcher* (welcome, system permissions, grid density, wallpaper), *Home* (Home Assistant discovery/credentials/test, pills configuration, remote control, MQTT setup/test), *Finish* (hidden apps cleanup, gestures hints, completion summary). Opens automatically on first launch and can be relaunched from Settings.
- **mDNS discovery**: automatic Home Assistant and MQTT broker discovery on the local network via mDNS (`MqttMdnsDiscovery`).
- **Home Assistant connection test**: 3-phase test (address check, authentication, device retrieval) with detailed diagnostics and device count by category.
- **MQTT roundtrip test**: 3-phase test (connect, publish, verify) with granular error diagnosis per phase.
- **Pills configuration**: guided entity selection from Home Assistant with search, bulk toggle, and recommended presets.
- **Wallpaper configuration**: 4 wallpaper modes (Calm gradient, Nature/Unsplash cycling, My Photo, Immich albums) with dedicated sub-configuration pages.
- **Immich photo source**: fetch and cycle wallpapers from Immich albums with configurable server, API key, album selection, and refresh frequency.
- **produtionTest build variant**: release-equivalent build signed with debug key, allowing in-place updates on debug-installed devices without losing preferences or credentials.
- **Settings > Information page**: app version display, GitHub Releases update checker, APK download and install via `FileProvider`.
- **Debug activity aliases**: `OnboardingDevTrigger` and `SettingsDevTrigger` exported in debug builds for ADB-driven testing.
- **Unit tests**: 5 new test suites covering ViewModel transitions, navigation logic, URL validation/normalization, grid scaling, and connection diagnostics (~45 tests).
- **~315 new i18n strings**: comprehensive English and French translations for all onboarding screens, settings, and diagnostics.

### Removed

- **SetupWizard**: legacy 3-step setup wizard (`SetupWizard.kt`) fully replaced by the new onboarding flow.
- **SoundMonitor**: ambient sound level monitoring (`SoundMonitor.kt`), `RECORD_AUDIO` permission, and all related MQTT topics (`soundDiscoveryTopic`, `soundStateTopic`, `micMuteDiscoveryTopic`, `micMuteCommandTopic`, `micMuteStateTopic`).

### Changed

- **Onboarding gate**: devices already configured with Home Assistant before this version are never interrupted by the onboarding wizard (`shouldRunOnboarding` checks `legacyConfigured`).
- **Settings root**: Settings now always open on the main page — the SETUP section no longer exists.
- **LauncherActivity**: checks onboarding status at `onCreate` and delegates to `OnboardingActivity` if needed.
- **Prefs**: new properties for onboarding state (`onboardingCompleted`, `onboardingVersion`, `onboardingStep`, skip flags, gesture hints) with `resetOnboarding()`.

### Performance

- **LauncherPager**: collapse fraction read as lambda in `graphicsLayer` instead of `derivedStateOf`, avoiding recomposition on every swipe pixel. `PageDots` use `Canvas` draw instead of row/box layout.
- **ClockHeader**: collapse parameter read in `graphicsLayer` without recomposition. Secondary lines persist during swipe instead of being removed/recreated.
- **AppGridPage**: `onPage` callback wrapped in `remember(items, page)` to avoid reallocation on every recomposition.

## 0.0.3-beta

### Added

- **i18n / internationalization**: full translation system with English (default) and French. ~320 strings extracted from hardcoded values into `res/values/strings.xml` (EN) and `res/values-fr/strings.xml` (FR). Adding a new language now only requires creating a new `values-XX/strings.xml` file — no code changes needed.

### Changed

- **iOS-style selected chip**: when a tray chip is selected (its panel is open), it now renders with a white background, dark text, and white border — matching the iOS Home app look. The colored icon circle retains its accent color.

### Technical

- All user-facing strings now use `stringResource(R.string.*)` (Compose) or `context.getString(R.string.*)` (non-Compose) instead of hardcoded French text
- Plurals support for dynamic counts (e.g. "1 shortcut" / "2 shortcuts")
- `PillPriorityEngine` refactored from `object` to `class` with Context injection for resource access
- All 162 unit tests updated to assert against English string resources

## 0.0.2-beta

### Added

- **Device-scaled UI**: automatic layout scaling based on screen density (dpi)
- **Clock long-press shortcut**: long-press the clock header to jump directly to clock theme settings
- **Configurable grid density**: adjustable icon size slider in settings, controlling the app grid columns × rows

### Removed

- **Camera overlay**: experimental pop-up triggered by binary sensors (doorbell, motion) — not essential for the initial app scope
- **Web config server**: experimental remote-configuration HTTP server (NanoHTTPD) — not essential for the initial app scope
- **Wireless ADB**: experimental ADB toggle from developer settings — not essential for the initial app scope
- **Tap sensitivity**: experimental tap/tilt detection sensitivity slider — not essential for the initial app scope
- **Temperature offset**: experimental temperature calibration offset — not essential for the initial app scope

### Changed

- **Reboot button**: renamed "Redémarrer le Portal" to "Redémarrer"

## 0.0.1-beta

- Initial release
