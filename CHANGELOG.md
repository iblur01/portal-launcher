# Changelog

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
