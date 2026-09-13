# Requirements

## Configuration transfer

### CFG-001 — Local discovery

An unconfigured device must advertise on the local network that it is ready to receive a Portal Launcher configuration. Already-configured devices on the same network must be able to detect that advertisement automatically.

### CFG-002 — Explicit consent

When a configured device detects a receiver, it must display a proximity-style consent prompt identifying the new device. No configuration or secret may leave the configured device until the user explicitly accepts this prompt.

### CFG-003 — Complete secure transfer

After consent, the configured device must transfer the complete application configuration, including Home Assistant URLs, access tokens, API credentials, launcher layout, and other application preferences. The transfer must be encrypted and authenticated in transit and must not expose secrets through logs, notifications, discovery payloads, or persistent temporary files.

### CFG-004 — Receiver onboarding state

Once the configured device accepts, the new device must switch to a dedicated onboarding activity or state explaining that it is receiving configuration from another Portal Launcher device. It must show progress, success, and recoverable failure states.

### CFG-005 — Atomic application

The receiver must validate the transferred payload before applying it. A failed, interrupted, expired, or invalid transfer must not leave a partially configured application. On success, the receiver must persist the imported configuration and continue into the configured application.

### CFG-006 — Local-only and time-bounded pairing

Discovery and transfer must be restricted to the local network. Pairing invitations and transfer sessions must expire automatically, reject replay, and permit only the single transfer explicitly authorized by the sender.

## Deferred

### SYNC-001 — Linked-device configuration synchronization

Devices that have been explicitly associated should eventually support automatic propagation of configuration changes. Conflict resolution, source-of-authority rules, key lifecycle, membership revocation, and offline reconciliation are intentionally deferred until after the one-time transfer flow is delivered.
