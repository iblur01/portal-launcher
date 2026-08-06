# Changelog

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
