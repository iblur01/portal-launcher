---
title: Linked-device configuration synchronization
trigger_condition: Secure one-time nearby configuration transfer is implemented and validated on real devices
planted_date: 2026-08-28
---

# Linked-device configuration synchronization

Extend the trust relationship established during nearby configuration transfer so that associated devices may propagate later configuration changes automatically.

Before implementation, decide:

- Whether the group is peer-to-peer or has a designated authority
- How concurrent edits and offline devices are reconciled
- Which settings are synchronized and which remain device-local
- How devices are removed, compromised keys are revoked, and group keys rotate
- Whether updates require confirmation when secrets change

This is deliberately excluded from the first implementation.
