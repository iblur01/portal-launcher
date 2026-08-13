# Architecture

## Overview

Portal Launcher is a single-module Android application written in Kotlin and Jetpack Compose. It
combines three roles in one process:

1. Android launcher and app-grid host;
2. Home Assistant client and focused control surface;
3. optional MQTT bridge exposing the panel back to the home.

Hilt supplies application-scoped dependencies. Coroutines and `StateFlow` carry long-lived state;
Compose renders immutable UI models.

## Main layers

### Android and integration layer

The package root contains platform entry points and long-lived integration components:

- `LauncherActivity` hosts the launcher and panel navigation.
- `Prefs` owns persisted configuration and encrypted secrets.
- `HaStateRepository` maintains Home Assistant state and forecast data.
- `PillRepository` projects Home Assistant entities into the launcher catalog.
- `MqttBridgeService` publishes discovery/state and accepts supported commands.
- `WebConfigActivity`, `WebConfigServer` and real resources under `src/main/resources/webconfig/`
  implement temporary phone-assisted setup.
- `RootProvisioning`, onboarding activities, receivers and Android services isolate privileged or
  lifecycle-specific work.

### Domain layer

`domain/` contains projections that can be tested without rendering UI:

- media session composition;
- Home catalog, ranking, grouping and page construction;
- shared immutable models.

The Home pipeline is broadly:

```text
Home Assistant state
        ↓
PillRepository / PillCatalogBuilder
        ↓
PillCatalogSnapshot
        ├── HomePillComposer → compact ranked tray
        └── HomePageBuilder  → favorites + room/type sections
        ↓
LauncherViewModel
        ↓
Compose UI
```

### UI layer

`ui/` is organized by responsibility:

- `components/` contains panels and reusable launcher components;
- `components/controls/` contains direct-manipulation controls such as sliders, selectors and the
  alarm keypad;
- `home/` renders the full Home page;
- `apps/` owns launcher placement, folders, icon packs and backups;
- `mapper/` maps entity state to typed chip/panel actions;
- `onboarding/` contains the first-run state machine and adaptive screens;
- `screens/`, `settings/` and `theme/` hold secondary pages and design tokens.

## Adaptive layout

Layouts derive behavior from the available window rather than a device name. Width and height are
considered separately because a 5-inch landscape display can be wide but extremely short. Panels
use compact full-screen or horizontal splits when needed; the onboarding uses one focused action on
short displays and richer compositions when both dimensions allow them.

## Persistence and security

- Home Assistant tokens and MQTT passwords are stored through encrypted preferences when the
  platform keystore is available.
- Launcher layout, folders, icon choices, Home grouping and visibility are local preferences/files.
- Remote configuration uses a fresh short-lived token, compares it in constant time and sends
  `Cache-Control: no-store` responses.
- Layout backups deliberately exclude credentials.

## Background work

The MQTT bridge is an Android service. Boot and sleep/dream receivers reconnect relevant behavior.
Home Assistant state is distributed through an observable store so panels subscribe to individual
entities instead of forcing global recomposition.

## Further specifications

- [Home pills](PILLS_HOME_TECHNICAL_SPEC.md)
- [Photo sources](photo-sources.md)
- [Bounded session protocol](session-protocol.md)
