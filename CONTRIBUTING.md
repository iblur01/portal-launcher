# Contributing

Contributions are welcome while Portal Launcher remains in beta.

## Before starting

- Check existing issues, the roadmap in [CHANGELOG.md](CHANGELOG.md), and the current branch history.
- Keep the product focused on shared wall panels rather than general-purpose dashboard behavior.
- Discuss broad architecture or interaction changes before implementing them.

## Development workflow

1. Create a focused branch.
2. Make the smallest coherent change.
3. Add or update tests for behavior and responsive policies.
4. Update both English and French resources for user-facing text.
5. Run:

   ```sh
   ./gradlew testDebugUnitTest assembleDebug
   ```

6. Validate interface changes on compact and expanded landscape sizes.
7. Update documentation when behavior or configuration changes.

## Code expectations

- Kotlin and Compose should follow the patterns already present in the nearest module.
- Keep domain projections deterministic and testable.
- Avoid device-name checks; derive adaptive behavior from the window.
- Preserve user data and backward-compatible preference decoding.
- Do not add credentials, authenticated URLs or personal entity IDs to logs, fixtures or screenshots.
- Keep touch targets, contrast, focus navigation and larger text usable.

## Pull requests

Explain the user-visible outcome, implementation boundary, tests run and devices/window sizes checked.
Include screenshots for visual changes. Call out migrations, security implications and known gaps.

By contributing, you agree that your contribution is licensed under the repository's MIT license.
