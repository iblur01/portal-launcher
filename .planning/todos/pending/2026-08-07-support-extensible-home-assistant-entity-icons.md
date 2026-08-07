---
created: 2026-08-07T15:16:43.492Z
title: Support extensible Home Assistant entity icons
area: ui
files:
  - app/src/main/java/com/iblu01/portallauncher/HaStateRepository.kt:151
  - app/src/main/java/com/iblu01/portallauncher/ui/components/LauncherIcons.kt:36
  - app/src/main/java/com/iblu01/portallauncher/ui/components/MediaDevicesPanel.kt:48
---

## Problem

Portal currently ignores entity icons returned by Home Assistant and maps every item through a
small built-in Compose icon table. This loses user customizations such as `mdi:sonos` on media
players. The repository already downloads the Home Assistant entity registry, but only retains
area and device relationships. A fixed bundled icon list would still prevent users and third-party
integrations from introducing new icon families without an application release.

## Solution

Add an extensible entity-icon pipeline with this resolution order:

1. User-defined entity-registry `icon` (or compact display-registry `ic`).
2. State attribute `icon` supplied by the integration.
3. Authenticated `entity_picture` when an actual image is appropriate.
4. Portal's domain/device-class fallback.

Persist the resolved icon reference alongside entity metadata rather than mutating the HA state
attributes. Introduce an icon-provider interface capable of resolving namespaced references such
as `mdi:sonos`. Ship an MDI provider by default, cache rendered/vector assets locally, and allow
additional third-party icon repositories or packs to be registered through a manifest containing
namespace, version, license, checksum, icon index, and asset base URL. Providers must support
offline fallback, bounded cache storage, safe SVG/vector parsing, repository allowlisting or
explicit user approval, update/version invalidation, and graceful fallback for missing icons.

Use the same resolver for home pills, media-player rows, panel headers, accessory lists, and future
components so a Home Assistant customization is visually consistent everywhere.
