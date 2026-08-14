---
name: portal-launcher-release
description: Automates the complete release workflow for the portal-launcher repo (iblur01/portal-launcher). Use when the user wants to "deploy", "release", publish a version ("publier une version"), prepare a 0.0.X ("préparer une 0.0.X"), merge into main ("merge dans main"), or make a release build ("faire un build release"). The target branch is main. This skill analyzes the branch commits, determines the version, updates CHANGELOG.md, bumps versionCode/versionName in build.gradle.kts, creates a PR, merges it, builds a signed APK, creates a GitHub release and deletes the branch.
user-invocable: true
argument-hint: "[version] [branch]"
---

# Portal Launcher — Release Workflow

This skill fully automates the release process for the `iblur01/portal-launcher` repo.

## Prerequisites

The repo must be at `/home/tdelannoy-fdi/dev/perso/portal-launcher`. The signing keystore is in `~/.portal-launcher-signing/portal-launcher-release.jks` and the credentials are in `app/local.properties` (gitignored).

## Workflow

The user may provide:
- `$ARGUMENTS`: may contain a version number (e.g. `0.0.5-beta`) or be empty (version auto-detected by incrementing the current versionCode on main).

### Step 1 — Determine the source branch

If the user mentions a branch name, use it. Otherwise, use the current branch (`git branch --show-current`). **Never create a release from main directly** — the source branch must be a feature branch.

### Step 2 — Determine the version

If the user provided an explicit version, use it.
Otherwise, read the current `versionCode` in `main:app/build.gradle.kts`, increment it, and derive the corresponding `versionName` (e.g. versionCode 4 → version 0.0.4-beta).

### Step 3 — Analyze the changes

Run `git diff main...HEAD --stat` and `git log main...HEAD --oneline` to get the list of modified files and the commits.

Use the `explore` agent with `thoroughness: "very thorough"` to analyze ALL modified files in depth. The agent must return a structured summary in English listing:
- **Added**: new features, files, components, APIs
- **Changed**: behavior changes, UI refactors
- **Removed**: removed code/functionality
- **Performance**: notable optimizations
- **i18n**: new strings added
- **Tests**: new tests

### Step 4 — Update CHANGELOG.md

Add an entry at the top of the file with the format:

```markdown
## X.Y.Z-beta

### Added
- ...

### Changed
- ...

### Removed
- ...

### Performance
- ...
```

### Step 5 — Bump version

In `app/build.gradle.kts`, update `versionCode` and `versionName`.

### Step 6 — Commit, push, PR, merge

```bash
git add CHANGELOG.md app/build.gradle.kts
git commit -m "chore: bump version to X.Y.Z-beta and update changelog"
git push
```

Create the PR with `gh pr create --base main --head {branch} --title "..." --body "..."`.
Merge with `gh pr merge {number} --merge --delete-branch --subject "..."`.

### Step 7 — Tag and release

```bash
git checkout main && git pull
git tag -a "vX.Y.Z-beta" -m "release: vX.Y.Z-beta — {summary}"
git push origin "vX.Y.Z-beta"
```

### Step 8 — Build signed APK

```bash
ANDROID_HOME=/home/tdelannoy-fdi/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean assembleRelease
```

Verify the signature with `apksigner`:
```bash
/home/tdelannoy-fdi/Android/Sdk/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### Step 9 — Create the GitHub release with the APK

```bash
cp app/build/outputs/apk/release/app-release.apk /tmp/portal-launcher-vX.Y.Z-beta.apk
gh release create "vX.Y.Z-beta" \
  --title "vX.Y.Z-beta — {short summary}" \
  --notes "{CHANGELOG condensed into markdown}" \
  /tmp/portal-launcher-vX.Y.Z-beta.apk
```

### Step 10 — Cleanup

```bash
git branch -d {branch}  # local branch already deleted by gh merge --delete-branch
git remote prune origin
```

## Notes

- The keystore is the same for all releases (no rotation).
- The APK uses the v2 signature only (no v1 JAR signing). This is sufficient for minSdk 28.
- The local branch is deleted by `gh pr merge --delete-branch`; you only need to prune the remote tracking ref.
- If `gh` is not authenticated, ask the user to run `gh auth login` first.
