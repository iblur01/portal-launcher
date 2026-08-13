# Development

## Toolchain

- Android Gradle Plugin 8.3.2
- Kotlin 2.0.20
- compile SDK 35
- minimum and target SDK 28
- Jetpack Compose with the 2024.09.00 BOM
- Java/Kotlin JVM target 1.8

The target SDK is intentionally kept at 28 for the old smart-display hardware supported by this
sideload-only project.

## Common commands

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew testDebugUnitTest assembleDebug
```

The release-equivalent `productionTest` build uses the debug signing key so it can replace a debug
installation without losing preferences. It is intended for profiling only and must not be
distributed.

## Repository layout

```text
app/src/main/java/.../       Android, domain and Compose sources
app/src/main/resources/     Browser configuration HTML/CSS/JS
app/src/main/res/           Android resources and translations
app/src/test/               JVM, Robolectric and Compose behavior tests
docs/                       User and technical documentation
```

## Working principles

- Keep integration and Android APIs outside pure domain projections where practical.
- Represent panel actions with typed models rather than stringly routed UI behavior.
- Derive responsive decisions from available width and height, not hard-coded device models.
- Preserve unavailable or stale state honestly; do not silently display it as current.
- Never log Home Assistant tokens, MQTT passwords or authenticated configuration URLs.
- Add English and French strings together for user-facing text.
- Keep compact-panel interactions fully usable without vertical overflow.

## Debug entry points

The debug manifest exposes actions for onboarding, Settings and remote configuration:

```sh
adb shell am start -a com.iblu01.portallauncher.action.ONBOARDING
adb shell am start -a com.iblu01.portallauncher.action.SETTINGS
adb shell am start -a com.iblu01.portallauncher.action.WEB_CONFIG
```

These aliases are not exported in release builds.

## Release signing

Release credentials are read from the gitignored `local.properties`:

```properties
release.storeFile=/absolute/path/to/keystore.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```

Without these values Gradle can still produce an unsigned release artifact.

## Documentation

Update documentation in the same change when behavior, configuration, architecture or supported
entities change. Keep the root README short and link detailed material from `docs/`.
