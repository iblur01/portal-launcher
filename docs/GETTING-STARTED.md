# Getting started

## Requirements

- Android 9 (API 28) or newer.
- A landscape display is recommended; compact and large landscape layouts are both supported.
- ADB for installation and for optional provisioning on locked-down panels.
- Home Assistant and an MQTT broker only if those integrations are wanted.

## Build and install

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Choose Portal Launcher when Android asks for a Home app. If the device does not expose that screen:

```sh
adb shell cmd package set-home-activity \
  com.iblu01.portallauncher/.LauncherActivity
```

## First-run assistant

The assistant configures the system capabilities, launcher grid, background, optional Home
Assistant/MQTT integration, hidden apps and gestures. Its composition follows the available window:

- large panels can show explanatory content and controls side by side;
- short landscape panels show one decision at a time;
- credential-heavy configuration can be handed to a phone through a QR code;
- optional permissions and integrations can be skipped.

The debug build exposes an ADB-only alias for replaying the assistant:

```sh
# Continue from saved progress
adb shell am start -a com.iblu01.portallauncher.action.ONBOARDING

# Reset progress and restart
adb shell am start -a com.iblu01.portallauncher.action.ONBOARDING --ez reset true
```

## Home Assistant provisioning over ADB

For a keyboard-less display, provision and test the address and token from the development machine:

```sh
adb shell am start \
  -a com.iblu01.portallauncher.action.PROVISION_HOME_ASSISTANT \
  --es ha_url "http://homeassistant.local:8123" \
  --es ha_token "YOUR_LONG_LIVED_ACCESS_TOKEN"
```

This entry point requires the privileged `DUMP` permission held by the ADB shell. The token may be
retained in the host shell history, so use an appropriate shell-history policy.

## Remote browser configuration

Choose **Configure with a phone** during onboarding or open remote configuration from Settings.
The panel starts a temporary HTTP server, displays a QR code and requires a fresh access token. The
phone must be on the same local network. When the browser saves the values:

1. the browser confirms that the tab can be closed;
2. the panel reports that it received the configuration;
3. **Test and continue** returns to onboarding and tests Home Assistant.

The server stops when the activity is left, and previously photographed QR codes stop working.

## Next steps

- See [Configuration](CONFIGURATION.md) for permissions and integrations.
- See [Testing](TESTING.md) before submitting changes.
- See [Development](DEVELOPMENT.md) for repository conventions.
