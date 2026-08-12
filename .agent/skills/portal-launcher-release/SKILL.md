---
name: portal-launcher-release
description: Automatise le workflow de release complet pour le repo portal-launcher (iblur01/portal-launcher). Utiliser quand l'utilisateur veut "deploy", "release", "publier une version", "préparer une 0.0.X", "merge dans main", ou "faire un build release". La branche cible est main. Ce skill analyse les commits de la branche, détermine la version, met à jour CHANGELOG.md, bump versionCode/versionName dans build.gradle.kts, crée une PR, la merge, build un APK signé, crée une release GitHub et supprime la branche.
user-invocable: true
argument-hint: "[version] [branch]"
---

# Portal Launcher — Release Workflow

Ce skill automatise entièrement le processus de release pour le repo `iblur01/portal-launcher`.

## Prérequis

Le repo doit être à `/home/tdelannoy-fdi/dev/perso/portal-launcher`. Le keystore de signature est dans `~/.portal-launcher-signing/portal-launcher-release.jks` et les credentials sont dans `app/local.properties` (gitignoré).

## Workflow

L'utilisateur peut fournir :
- `$ARGUMENTS` : peut contenir un numéro de version (ex: `0.0.5-beta`) ou être vide (version auto-détectée par incrément du versionCode actuel sur main).

### Étape 1 — Déterminer la branche source

Si l'utilisateur mentionne un nom de branche, l'utiliser. Sinon, utiliser la branche courante (`git branch --show-current`). **Ne jamais créer de release depuis main directement** — la branche source doit être une branche de feature.

### Étape 2 — Déterminer la version

Si l'utilisateur a fourni une version explicite, l'utiliser.
Sinon, lire le `versionCode` actuel dans `main:app/build.gradle.kts`, l'incrémenter, et en déduire le `versionName` correspondant (ex: versionCode 4 → version 0.0.4-beta).

### Étape 3 — Analyser les modifications

Faire `git diff main...HEAD --stat` et `git log main...HEAD --oneline` pour avoir la liste des fichiers modifiés et les commits.

Utiliser l'agent `explore` avec `thoroughness: "very thorough"` pour analyser en profondeur TOUS les fichiers modifiés. L'agent doit retourner un résumé structuré en français listant :
- **Added** : nouvelles features, fichiers, composants, APIs
- **Changed** : modifications de comportement, refactors UI
- **Removed** : code/fonctionnalités supprimées
- **Performance** : optimisations notables
- **i18n** : nouvelles strings ajoutées
- **Tests** : nouveaux tests

### Étape 4 — Mettre à jour CHANGELOG.md

Ajouter une entrée en tête du fichier avec le format :

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

### Étape 5 — Bump version

Dans `app/build.gradle.kts`, mettre à jour `versionCode` et `versionName`.

### Étape 6 — Commit, push, PR, merge

```bash
git add CHANGELOG.md app/build.gradle.kts
git commit -m "chore: bump version to X.Y.Z-beta and update changelog"
git push
```

Créer la PR avec `gh pr create --base main --head {branche} --title "..." --body "..."`.
Merger avec `gh pr merge {numero} --merge --delete-branch --subject "..."`.

### Étape 7 — Tag et release

```bash
git checkout main && git pull
git tag -a "vX.Y.Z-beta" -m "release: vX.Y.Z-beta — {résumé}"
git push origin "vX.Y.Z-beta"
```

### Étape 8 — Build APK signé

```bash
ANDROID_HOME=/home/tdelannoy-fdi/android-sdk JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew clean assembleRelease
```

Vérifier la signature avec `apksigner` :
```bash
/home/tdelannoy-fdi/Android/Sdk/build-tools/35.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

### Étape 9 — Créer la release GitHub avec APK

```bash
cp app/build/outputs/apk/release/app-release.apk /tmp/portal-launcher-vX.Y.Z-beta.apk
gh release create "vX.Y.Z-beta" \
  --title "vX.Y.Z-beta — {résumé court}" \
  --notes "{CHANGELOG réduit en markdown}" \
  /tmp/portal-launcher-vX.Y.Z-beta.apk
```

### Étape 10 — Nettoyage

```bash
git branch -d {branche}  # locale déjà supprimée par gh merge --delete-branch
git remote prune origin
```

## Notes

- Le keystore est identique à toutes les releases (pas de rotation).
- L'APK utilise la signature v2 seulement (pas de v1 JAR signing). C'est suffisant pour minSdk 28.
- La branche locale est supprimée par `gh pr merge --delete-branch`, il suffit de pruner le remote tracking.
- Si `gh` n'est pas authentifié, demander à l'utilisateur de faire `gh auth login` d'abord.
