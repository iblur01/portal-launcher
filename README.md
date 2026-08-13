# Portal Launcher

An Android launcher designed for shared wall panels and smart displays.

Portal Launcher replaces the normal home screen with a calm clock, an app launcher and focused
Home Assistant controls. It is built for landscape devices from compact 5-inch displays to
10-inch panels, but runs on any Android 9+ device.

> The latest tagged release is **0.0.7-beta**. This branch also contains unreleased work planned
> for the next beta; see [CHANGELOG.md](CHANGELOG.md) and the current Git history.

## Highlights

- Real Android home launcher with multiple pages, free icon placement, widgets, folders, shortcuts,
  hidden apps and third-party icon packs.
- Clock-first idle screen with configurable typography, backgrounds and Immich photo rotation.
- Optional Home Assistant integration with live, ranked status pills and responsive device panels.
- Home page organized by room or device type, with favorites and custom groups.
- MQTT bridge for exposing the panel back to Home Assistant and bounded external-app sessions.
- Guided onboarding adapted independently to screen width and height.
- Phone-assisted configuration over the local network using a temporary QR code and access token.
- English and French interfaces.

## Screenshots

| Clock | Apps | Home controls |
|---|---|---|
| ![Clock screen](docs/screenshots/home-clock.jpg) | ![App grid](docs/screenshots/apps-grid.png) | ![Expanded pills](docs/screenshots/home-pills-expanded.jpg) |

More interface captures are available in [docs/screenshots](docs/screenshots).

## Quick start

Requirements: Android Studio or a JDK compatible with the Android Gradle Plugin, Android SDK 35,
and an Android 9+ device reachable through ADB.

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Select Portal Launcher as the Home app. On devices without a default-launcher settings screen:

```sh
adb shell cmd package set-home-activity \
  com.iblu01.portallauncher/.LauncherActivity
```

The first launch opens the setup assistant. Home Assistant and MQTT are optional; the launcher and
clock work without them.

For detailed setup, permissions, ADB provisioning and local web configuration, read
[Getting started](docs/GETTING-STARTED.md) and [Configuration](docs/CONFIGURATION.md).

## Documentation

- [Feature reference](docs/FEATURES.md)
- [Getting started](docs/GETTING-STARTED.md)
- [Configuration](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development](docs/DEVELOPMENT.md)
- [Testing](docs/TESTING.md)
- [Immich and photo sources](docs/photo-sources.md)
- [Home pills technical specification](docs/PILLS_HOME_TECHNICAL_SPEC.md)
- [MQTT session protocol](docs/session-protocol.md)
- [Contributing](CONTRIBUTING.md)
- [Version history](CHANGELOG.md)

## Project status

Portal Launcher is beta software used on real wall-panel hardware. It is distributed by sideloading,
not through an app store. The application intentionally targets Android API 28 for compatibility
with older smart displays while compiling against API 35.

The current development branch includes substantial work after `0.0.7-beta`, notably responsive
small-panel layouts, additional Home Assistant panels, folders and icon packs, root provisioning,
remote browser setup and a per-entity observable state store.

## License

[MIT](LICENSE)
