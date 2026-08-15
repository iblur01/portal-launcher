# Portal Launcher

An Android launcher designed for shared wall panels and smart displays.

Portal Launcher replaces the normal home screen with a calm clock, an app launcher and focused
Home Assistant controls. It is built for landscape devices from compact 5-inch displays to
10-inch panels, but runs on any Android 9+ device.

> **Portal Launcher 1.0 is now available.** Download the APK from
> [GitHub Releases](https://github.com/iblur01/portal-launcher/releases/latest).

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

Portal Launcher requires Android 9 or newer. Download the APK from the
[latest GitHub release](https://github.com/iblur01/portal-launcher/releases/latest); building the
project is only necessary for development.

### Install with ADB

Connect the device through ADB, download `portal-launcher-v1.0.apk` from the
[v1.0 release](https://github.com/iblur01/portal-launcher/releases/tag/v1.0), then run:

```sh
adb install -r portal-launcher-v1.0.apk
```

### Install from the device

Open the [Releases page](https://github.com/iblur01/portal-launcher/releases/latest) in a browser on
the panel, download the APK and open it. Android may ask you to allow that browser to install
unknown applications. This permission can be disabled again after installation.

Select Portal Launcher as the Home app. On devices without a default-launcher settings screen:

```sh
adb shell cmd package set-home-activity \
  com.iblu01.portallauncher/.LauncherActivity
```

The first launch opens the setup assistant. Home Assistant and MQTT are optional; the launcher and
clock work without them.

## Updates

Portal Launcher includes its own version manager. Open **Settings → Information → Check for
updates** to look for future releases, download the new APK and start the Android installer without
returning to a computer. Android may request permission for Portal Launcher to install unknown
applications the first time this is used.

For detailed setup, permissions, ADB provisioning and local web configuration, read
[Getting started](docs/GETTING-STARTED.md) and [Configuration](docs/CONFIGURATION.md).

## Documentation

- [Feature reference](docs/FEATURES.md)
- [Getting started](docs/GETTING-STARTED.md)
- [Configuration](docs/CONFIGURATION.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Development](docs/DEVELOPMENT.md)
- [Testing](docs/TESTING.md)
- [Next release: scenes and camera center](docs/NEXT_RELEASE_SCENES_CAMERAS_SPEC.md)
- [Immich and photo sources](docs/photo-sources.md)
- [Home pills technical specification](docs/PILLS_HOME_TECHNICAL_SPEC.md)
- [MQTT session protocol](docs/session-protocol.md)
- [Contributing](CONTRIBUTING.md)
- [Version history](CHANGELOG.md)

## Project status

Portal Launcher 1.0 is the first stable release. It is distributed as an APK through GitHub Releases,
not through an app store. The application intentionally targets Android API 28 for compatibility
with older smart displays while compiling against API 35. Source builds and development setup are
documented in [Development](docs/DEVELOPMENT.md).

## License

[MIT](LICENSE)
