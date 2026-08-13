# Configuration

## On-device setup

The first-run assistant is the preferred configuration path. It covers launcher capabilities, grid
and appearance, Home Assistant, MQTT, hidden apps and gestures. It can be opened again from Settings.

Settings also exposes focused pages for Home connectivity, Home pills/groups, background and clock,
launcher behavior and developer/device operations.

## Optional Android capabilities

Portal Launcher can operate without privileged access. Additional grants improve kiosk behavior:

```sh
adb shell appops set com.iblu01.portallauncher WRITE_SETTINGS allow
adb shell pm grant com.iblu01.portallauncher android.permission.WRITE_SECURE_SETTINGS
```

- `WRITE_SETTINGS` enables direct system-brightness control.
- `WRITE_SECURE_SETTINGS` allows supported system setup to be automated.
- Notification access enables launcher notification dots.
- Accessibility access enables screen-control behavior when it cannot be provisioned directly.
- Root, when detected, offers one-tap provisioning of supported capabilities.

The assistant explains each capability and allows progress when a nonessential grant is unavailable.

## Home Assistant

Provide the base URL and a long-lived access token. Portal tests the address and entity response
before treating the connection as ready. Secrets are never intentionally logged.

Supported entry methods:

- directly on a sufficiently large panel;
- phone-assisted local web configuration;
- the privileged ADB provisioning action documented in [Getting started](GETTING-STARTED.md).

## MQTT

MQTT is optional and uses credentials distinct from Home Assistant. Configure the broker host, port,
optional username/password and device name. The bridge publishes Home Assistant discovery/state and
implements the bounded external-app session contract described in [session-protocol.md](session-protocol.md).

## Remote browser setup

The panel starts an embedded NanoHTTPD server, normally on port `8080` and otherwise on a free port.
Every protected request requires the token displayed or embedded in the QR code. The browser flow
configures Home Assistant and MQTT, presents a final **You can close this tab** screen, and notifies
the panel immediately after saving.

The server is deliberately scoped to the activity lifecycle. Leaving the screen invalidates the URL.
Both devices must be on the same local network; cleartext HTTP is used only for this temporary LAN
exchange.

## Home page and pills

Home Assistant-compatible entities are projected into:

- a small urgency-ranked tray on the clock screen;
- favorites and grouped sections on the full Home page.

The Home page can be grouped by room or by device type. The type view starts directly with type
sections; the room overview rail is not duplicated there. Visibility, favorites, ordering and manual
groups are stored locally.

## Appearance

Background modes are Android wallpaper, the offline Calm background, a locally selected image and
Immich. Immich settings and cache behavior are documented in [photo-sources.md](photo-sources.md).
Clock typography, grid scale, element spacing and interface opacity have dedicated preview screens.

## Language

English is the default resource set and French is provided in `values-fr`. The application follows
its locale helper and Android resource selection.
