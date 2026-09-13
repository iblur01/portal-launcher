# Roadmap

## Phase 1 — Secure nearby configuration transfer

**Goal:** Configure a new Portal Launcher device by securely copying the full configuration from an already-configured device on the same local network.

**Scope:**

- Receiver advertisement during onboarding
- Automatic discovery by configured devices
- Explicit sender-side consent prompt
- Authenticated, encrypted, one-time transfer including secrets
- Dedicated receiving state in onboarding
- Atomic validation and import with success/failure recovery
- Automated protocol, serialization, security, and onboarding tests

**Done when:** Two devices on the same network can complete the flow without manually re-entering any application configuration, and no transfer occurs without explicit approval on the configured device.

## Future phase — Linked-device synchronization

**Goal:** Keep configuration synchronized across previously associated Portal Launcher devices.

This phase is deferred until Phase 1 is implemented and validated. Its design must explicitly address conflicts, device membership and revocation, offline changes, key rotation, and the scope of synchronized settings.
