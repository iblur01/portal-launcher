---
status: resolved
trigger: "Portal Launcher crashes instantly on an Echo Show 5 running LineageOS / Android 11"
created: 2026-08-12
updated: 2026-08-12
---

## Symptoms

- Expected behavior: Portal Launcher opens normally.
- Actual behavior: The process crashes immediately on launch.
- Error messages: Initially unknown; captured from ADB logcat.
- Timeline: Reproduced on the current Android 11 custom ROM; no prior working state was established.
- Reproduction: Start `com.iblu01.portallauncher/.LauncherActivity` on the connected Echo Show 5.

## Current Focus

- hypothesis: Confirmed and fixed.
- test: Two cold launches after reinstalling the patched debug APK.
- expecting: Onboarding remains visible and no AndroidRuntime fatal exception is emitted.
- next_action: None.

## Evidence

- timestamp: 2026-08-12T13:51:50+02:00
  observation: `LauncherActivity` started `OnboardingActivity`, called `finish()`, then crashed in `LauncherActivity.onDestroy()` with `lateinit property appList has not been initialized`.
- timestamp: 2026-08-12T13:55:29+02:00
  observation: Patched APK completed a second cold launch into `OnboardingActivity`; process remained alive and logcat contained no fatal exception or ANR.

## Eliminated

- hypothesis: The LineageOS GPU/graphics stack is the direct crash source.
  reason: Mali, gralloc, and ION warnings are non-fatal; the fatal exception was a deterministic Kotlin lifecycle error in application code.

## Resolution

- root_cause: On a fresh install, `LauncherActivity.onCreate()` hands off to onboarding and calls `finish()` before initializing `appList`; `onDestroy()` then accessed the uninitialized lateinit property.
- fix: Guard `appList.stop()` with `::appList.isInitialized`.
- verification: `testDebugUnitTest` and `assembleDebug` passed; the APK was freshly installed and survived two cold starts into onboarding with no crash or ANR.
- files_changed: `app/src/main/java/com/iblu01/portallauncher/LauncherActivity.kt`
