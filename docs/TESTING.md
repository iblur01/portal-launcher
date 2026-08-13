# Testing

## Test suite

The project keeps its automated tests under `app/src/test`. They run on the JVM; Android-facing
behavior uses Robolectric and the Compose test manifest from the debug source set.

Run the complete supported unit-test variant:

```sh
./gradlew testDebugUnitTest
```

Build and test together:

```sh
./gradlew testDebugUnitTest assembleDebug
```

Release and `productionTest` unit-test variants are disabled because their manifests intentionally
do not ship the debug-only Compose test activity.

## Coverage areas

The suite includes pure and Android-facing coverage for:

- pill compatibility, ranking, catalog discovery and Home page composition;
- Home preference serialization and migrations;
- launcher placement, folders, icon packs and layout backups;
- panel routing, disclosure policies and responsive layout decisions;
- alarm behavior and keypad regressions;
- onboarding navigation and ViewModel behavior;
- web-configuration page/server contracts;
- photo-source coordination and bounded MQTT sessions;
- ViewModel/flow behavior with coroutines and Turbine.

## Device validation

Automated tests do not replace visual checks on the target form factors. For layout work, validate
at least:

- a short landscape window around 960×480 (Echo Show 5 class);
- a larger landscape window around 1280×800 (Portal 10 class).

Useful checks include onboarding steps, panel overflow, system-bar immersion, keyboard visibility,
large-text behavior and back navigation. Use ADB screenshots for before/after comparison.

## Before submitting

1. Run `./gradlew testDebugUnitTest assembleDebug`.
2. Confirm new user-facing strings exist in English and French.
3. Exercise changed layouts at compact and expanded sizes.
4. Check that logs and tests contain no credentials.
5. Update the relevant document or changelog entry.
