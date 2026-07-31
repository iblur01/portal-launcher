# Bounded App-Session Protocol

This document defines the protocol, state machine, MQTT contract, Android runtime adapter, and
local settings behavior for Portal's bounded external-app sessions.

## Design goals

- **Fail-closed**: unknown packages, malformed commands, disabled kill switch, or expired requests are
  rejected before any side effect.
- **Bounded inputs**: payload size, field lengths, durations, and expiry are capped.
- **Machine-readable errors**: operational reasons are never returned as free-form strings; a fixed
  enumeration of rejection codes is emitted instead.
- **Idempotent commands**: a repeated `request_id` replays the recorded result without side effects.
- **Local-only kill switch**: the feature defaults to disabled and is controlled by a local device flag;
  there is no remote override.
- **Restart-safe but not resuming**: a fresh process starts from an idle `completed` state and never
  resumes a session from the previous process.

## MQTT topics

All topics are under the device-specific prefix `portal/<deviceId>/session`.

| Topic | Direction | QoS | Retained | Purpose |
|-------|-----------|-----|----------|---------|
| `portal/<deviceId>/session/command` | Incoming | 1 | No (clear-message retained) | Request to start/end/cancel a session. |
| `portal/<deviceId>/session/event` | Outgoing | 1 | No | Transient lifecycle events (accepted, launching, active, ending, completed, expired, failed, rejected). |
| `portal/<deviceId>/session/state` | Outgoing | 1 | Yes | Last-known lifecycle state for Home Assistant. |
| `portal/<deviceId>/session/enabled` | Outgoing | 1 | Yes | Read-only local enabled state (`ON` / `OFF`). There is no matching command topic. |

The bridge publishes a zero-byte retained message to `.../command` on connect so that a stale
retained command from a previous broker session cannot be replayed after reconnect.

## Command schema

Only the fields listed below are accepted. Extra fields, missing required fields, or type mismatches
are rejected.

```json
{
  "schema_version": 1,
  "request_id": "req-001",
  "action": "start",
  "package": "com.example.app",
  "duration_s": 60,
  "expires_at": 1234567890,
  "reason": "Homework break"
}
```

### Field table

| Field | Required | Type | Bounds |
|-------|----------|------|--------|
| `schema_version` | Yes | Integer | Must be exactly `1`. Strings/floats rejected. |
| `request_id` | Yes | String | 1–64 chars, `[A-Za-z0-9_-]`. No spaces or control chars. |
| `action` | Yes | String | `start`, `end`, or `cancel`. |
| `package` | Yes | String | 1–128 chars, starts with a letter, only `[A-Za-z0-9._-]`. Must be in the local allowlist. |
| `duration_s` | No | Integer | Positive. `start` only. Capped at the classification maximum. Defaults to the classification default. |
| `expires_at` | No | Integer | Unix epoch seconds, in the future, not beyond `now + classification.max`. `start` only. |
| `reason` | No | String | 0–120 chars after trim; control chars (`< 0x20`) removed except tab. |

`duration_s` and `expires_at` are **not** allowed on `end`/`cancel` commands.

Integer fields are accepted only as JSON integers; strings and floats are rejected (no coercion).

## State/event schema

```json
{
  "schema_version": 1,
  "lifecycle": "active",
  "request_id": "req-001",
  "package": "com.example.app",
  "expires_at": 1234567890,
  "reason": "Homework break",
  "code": null
}
```

| Field | Type | Meaning |
|-------|------|---------|
| `schema_version` | Integer | Always `1`. |
| `lifecycle` | String | `accepted`, `launching`, `active`, `ending`, `completed`, `expired`, or `rejected`. |
| `request_id` | String or `null` | Command request id; `null` for idle state. |
| `package` | String or `null` | Target package; `null` when not applicable. |
| `expires_at` | Integer or `null` | Epoch seconds when the active session expires; `null` when inactive. |
| `reason` | String or `null` | Sanitized user-provided reason from the command. Never an operational error string. |
| `code` | String or `null` | Machine-readable rejection code; present only when `lifecycle == "rejected"`. |

`null` is emitted for absent values so consumers see a stable schema.

## Transition table

| From event | Current state | Next state | Side effects | Notes |
|------------|---------------|------------|--------------|-------|
| `start` command | Idle, kill switch on | `accepted` → `launching` | `LaunchApp` | Recorded for replay. |
| Foreground = target package | `launching` | `active` | None | Update replay record. |
| `end`/`cancel` command | `active` | `ending` | `ReturnToLauncher` | Update replay record. |
| Foreground = launcher / none | `ending` | `completed` | None | Update replay record; session removed. |
| `onReturnToLauncher()` | `ending` | `completed` | None | Mirrors the above without a foreground report. |
| `now >= expires_at` | `launching` or `active` | `expired` | `ReturnToLauncher` | Exactly one side effect. |
| `now >= expires_at` | `ending` | `expired` | None | Terminal. |
| Kill switch `OFF` | Any active | `ending` | `ReturnToLauncher` | Update replay record. |
| Restart | — | `completed` (idle) | None | Fresh manager only; no resume. |
| Duplicate `request_id` | Any | Replay recorded result | None | No side effects. |

Only one session may be active at a time. A second `start` while a session is active is rejected.

## Rejection codes

| Code | Meaning |
|------|---------|
| `payload_too_large` | Command payload exceeds 2048 bytes. |
| `invalid_json` | Payload is not valid JSON. |
| `unknown_schema_version` | `schema_version` missing, wrong, or non-integer. |
| `unknown_fields` | Command contains a field not in the schema. |
| `malformed_request_id` | `request_id` missing, wrong type, or does not match grammar. |
| `unknown_action` | `action` missing or not `start`/`end`/`cancel`. |
| `malformed_package` | `package` missing, wrong type, or does not match grammar. |
| `unknown_package` | `package` is not in the local allowlist. |
| `malformed_reason` | `reason` is present but not a string. |
| `duration_out_of_range` | `duration_s` is missing, zero, negative, or non-integer. |
| `duration_exceeds_max` | `duration_s` exceeds the classification maximum. |
| `expires_at_invalid` | `expires_at` is present but non-integer. |
| `expires_at_in_the_past` | `expires_at` is not in the future. |
| `expires_at_exceeds_max` | `expires_at` is beyond `now + classification.max`. |
| `end_command_with_temporal_fields` | `end`/`cancel` included `duration_s` or `expires_at`. |
| `rate_limited` | Commands arrived faster than the configured interval. |
| `kill_switch_disabled` | The local kill switch is off. |
| `session_already_active` | A session is already active; only one allowed. |
| `expired_before_launch` | `start` command was already expired on arrival. |
| `no_active_session` | `end`/`cancel` arrived with no active session. |
| `package_mismatch` | `end`/`cancel` package does not match the active session. |
| `session_already_ending` | `end`/`cancel` arrived while the session is already ending. |
| `request_id_conflict` | Duplicate `request_id` with different command fields. |
| `launch_failed` | Android could not resolve or launch the locally allowed package. |
| `return_to_launcher_failed` | Android could not return to Portal after an ending or expiry transition. |

## Idempotency and replay rules

1. The manager records the latest result for every processed `request_id`.
2. A duplicate command (same `request_id` and every field identical: `action`, `package`,
   `duration_s`, `expires_at`, `reason`) replays the recorded result **without side effects** and
   **before** the rate-limit check.
3. A conflicting duplicate (same `request_id` but different command fields) is rejected with
   `request_id_conflict`.
4. Lifecycle updates (`launching` → `active`, `ending` → `completed`, `expired`) update the replay
   record so that a later duplicate returns the latest state, never a stale `launching` result.
5. Rejected commands are also recorded, so duplicates replay the same rejection.
6. Rate-limited commands are recorded, so a duplicate of a previously rate-limited command replays
   the rate-limited result even after the rate-limit window has passed.
7. Records expire after a bounded TTL and count cap to prevent unbounded memory growth.

## Restart rules

- The manager is instantiated once per process.
- A fresh manager has no `activeSession` and an implicit `completed` idle state.
- The serializer provides `idleState()` to publish the initial retained state after a restart.
- Any incomplete session from a previous process is intentionally dropped; the new process never
  resumes or re-activates it.

## Kill switch rules

- The kill switch is a **local-only** device setting; it defaults to `OFF` (fail-closed).
- When the switch is `OFF`, all `start` commands are rejected with `kill_switch_disabled`.
- Toggling the switch to `OFF` while a session is active immediately transitions it to `ending` and
  requests a return to the launcher.
- There is no remote command that can force sessions to be enabled; the switch must be set on the
  device through the local settings surface.
- Home Assistant discovery exposes enabled state as a read-only diagnostic binary sensor and does
  not include a `command_topic`.

## Allowlist

- The allowlist defaults to empty and is stored only in Portal's local preferences.
- The on-device Application settings page can add installed apps, cycle each app's classification,
  clear the list, and arm/disarm the feature.
- Each entry is `package + classification`; malformed stored entries are ignored and storage is
  capped at 32 entries.
- Each classification (`HOME`, `MEDIA`, `UTILITY`, `COMMUNICATION`) defines a default duration and a
  hard maximum duration; the parser enforces both.
- Runtime launch uses only Android's package-manager launch intent for the exact allowed package.
  Commands cannot supply a component, URI, deep link, extras, or arbitrary intent.
- Unknown packages are rejected before any side effect.

## Runtime behavior

- The bridge clears the retained command topic before subscribing. Empty clear payloads are ignored.
- One coordinator is built per service process and preserved across ordinary MQTT reconnects. A local
  allowlist change ends any active session and atomically replaces the coordinator.
- `DeviceStateHub.foregroundPackage` drives `launching → active`; a broker-independent one-second tick
  checks expiry so a network outage cannot extend a session. No new Usage Access or location
  permission is requested.
- The bridge republishes current session state every five seconds. Home Assistant discovery uses
  `expire_after: 15`, so the diagnostic becomes unavailable after broker/device loss instead of
  displaying stale `active` state indefinitely.
- A successful explicit return to Portal completes an `ending` session immediately; foreground
  observation is a second idempotent confirmation path. Once return begins, expiry cannot overwrite
  `ending` with `expired`.
- Portal's existing `DeviceStateHub` continues to cancel/apply `SleepScheduler` around external-app
  transitions; sessions do not add a second screen-sleep scheduler.
- Launch/return failures publish bounded `failed` event/state payloads without exception text.

## Example flow

1. Broker publishes `start` command on `portal/<deviceId>/session/command`.
2. Bridge parses and forwards to the manager.
3. Manager emits `accepted` then `launching` events on `.../session/event` and publishes `launching`
   as retained state on `.../session/state`.
4. Runtime launches the app; when the app reaches the foreground, manager emits `active`.
5. When the session expires or an `end` command arrives, manager emits `ending` with a
   `ReturnToLauncher` side effect, then `completed` once the launcher is in the foreground.
6. On reconnect, the bridge clears the command topic and publishes the retained `completed` state.
