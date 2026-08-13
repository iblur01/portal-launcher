# Changelog

## 1.0

Première version stable. Sortie de beta après `0.0.7-beta`.

### Added

- **Configuration à distance depuis un téléphone** : un serveur HTTP local embarqué sert une page de réglages sur le réseau Wi-Fi ; le panneau affiche un QR code et un code d'accès à usage unique. L'adresse Home Assistant, le jeton, le MQTT et la sélection des pills se saisissent depuis le clavier du téléphone. La page se ferme dès qu'on quitte l'écran.
- **Onboarding « avec un téléphone »** : l'étape Home Assistant propose deux voies explicites — configuration par téléphone (QR code) ou saisie directe sur le panneau — avec un avertissement dédié sur écran compact.
- **Dossiers dans la grille d'applications** : un dossier se crée en déposant une icône sur une autre, s'ouvre en popup centré, se renomme d'un tap sur son titre et se dissout automatiquement sous deux membres ou quand une app est désinstallée. La tuile affiche les quatre premiers membres en 2×2.
- **Packs d'icônes tiers** : détection des packs installés (ADW / GO / Nova), lecture d'`appfilter.xml` et application aux icônes de la grille ; les apps non thémées gardent leur icône d'origine.
- **Pastilles de notification** : un service d'écoute allume une pastille sur les applications qui ont quelque chose en attente (aucun contenu n'est lu ni conservé), y compris sur les dossiers. Les notifications permanentes n'allument rien.
- **Sauvegarde et restauration de la disposition** : export/import d'un fichier JSON versionné et lisible contenant placements, dossiers, renommages, apps masquées, raccourcis épinglés, échelle de grille et pack d'icônes — sans aucun identifiant ni mot de passe.
- **Provisionnement root automatique** : sur un panneau rooté, Portal s'accorde lui-même toutes les autorisations système (rôle launcher, accessibilité, accès notifications, réglages sécurisés, exclusion doze) en un bouton, dans l'onboarding comme dans les réglages.
- **Panneaux Porte et Mouvement** : nouveaux panneaux dédiés aux ouvrants et aux détecteurs de présence, avec état et horodatage « depuis ».
- **Nouvelle catégorie de pill Mouvement** : les détecteurs de mouvement/présence rejoignent « Sécurité & accès ».
- **Logos de source sur les pochettes média** : 40 logos de fournisseurs (Spotify, Netflix, Plex, Sonos, YouTube, Tidal…) intégrés en vectoriel et résolus depuis `app_name`, `source` ou le domaine du lecteur.
- **Page Maison groupée par pièce ou par type** : un sélecteur dans l'en-tête bascule entre les deux organisations, la préférence est mémorisée.
- **Prévisions météo à onglets** : le panneau météo distingue « Heures » et « Jours », avec la plage min/max du jour.
- **Fond d'écran personnalisé dans l'onboarding** : choix d'une photo locale pendant la configuration initiale, en plus des modes système, calme et Immich.
- **Récapitulatif de fin d'onboarding** : l'écran final résume les choix effectués.

### Changed

- **Panneaux adaptatifs** : la composition d'un panneau est décidée par ses propres dimensions, pas par l'orientation de l'appareil ; les panneaux passent en pleine page sur écran compact au lieu de rester dockés au tiers de l'écran.
- **Divulgation progressive du média** : les contrôles de lecture principaux sont conservés en priorité et le détail secondaire disparaît à mesure que la hauteur utile diminue.
- **Horloge et bandeau compacts** : hiérarchie, marges et espacement repensés pour les petits écrans, températures intérieure/extérieure condensées dans l'en-tête et masquées quand elles sont indisponibles, séparation nette entre les pills et les points du pager.
- **Icônes météo** : le glyphe dessiné au Canvas est remplacé par les icônes Meteocons statiques embarquées dans l'APK, disponibles hors ligne.
- **Dépôt sur une case occupée** : le geste crée un dossier au lieu d'échanger les deux icônes ; l'échange ne subsiste que quand le regroupement est impossible (widget, tailles différentes).
- **Textes français au vouvoiement** : l'ensemble des écrans de configuration passe du tutoiement au vouvoiement, avec des formulations plus claires.
- **Chargement d'images unifié** : un seul `ImageLoader` Coil pour tout le processus, avec décodeur SVG, budgets mémoire/disque explicites (12 % du heap, 32 Mo), RGB565 et contournement du proxy HTTP pour le trafic local.
- **Documentation** : README réécrit, nouveaux guides `docs/GETTING-STARTED.md`, `docs/ARCHITECTURE.md`, `docs/CONFIGURATION.md`, `docs/DEVELOPMENT.md`, `docs/TESTING.md`, et `docs/FEATURES.md` remis à jour.

### Removed

- Les taps ne traversent plus les panneaux : un panneau opaque absorbe tous les événements pointeur, y compris pendant son animation de fermeture.
- Le jeu complet d'icônes météo `meteocons/fill` (plus de 120 SVG) est remplacé par un jeu réduit aux conditions réellement utilisées.
- Suppression de `latestStates` de l'état d'interface : les états bruts Home Assistant ne transitent plus par l'état global du launcher.
- Retrait de la mention « bientôt disponible » sur le groupement de la page Maison, désormais fonctionnel.

### Performance

- **Store observable par entité** : les états Home Assistant ne sont plus diffusés dans un état global recomposé en bloc. Chaque entité possède son propre slot observable ; un `state_changed` sur `light.salon` n'invalide que les composables qui lisent `light.salon`. Mesuré : de 122-145 ms par push sur petit appareil à une seule frame.
- **Interface optimiste** : une action écrit immédiatement l'état prédit (`turn_on`, `toggle`, `lock`/`unlock`, `open_cover`/`close_cover`, `media_play_pause`…) ; le vrai état reprend la main dès qu'il apporte du nouveau, avec retour arrière automatique après 4 s sans confirmation. Les entités en `assumed_state` conservent la prédiction.
- **Snapshot immuable côté socket** : la carte d'états devient une `PersistentMap` à écrivain unique — plus de copie défensive de 765 entrées à chaque événement, plus de verrou.
- **Latence action → confirmation divisée par six** : la fenêtre d'échantillonnage des snapshots passe de 100 ms à 16 ms, et un flux brut non échantillonné alimente le store par entité.
- **Découverte des pills mémoïsée** : la découverte ne dépend que de l'ensemble des identifiants et des registres, jamais des valeurs d'état ; elle n'est recalculée que quand un appareil apparaît ou disparaît.
- **Indexation des entités en une passe** : les balayages imbriqués (765 entités × ~60 candidats à chaque push) sont remplacés par un index par appareil partagé, avec mémoïsation des slugs et des clés logiques.
- **Identités préservées dans l'état d'interface** : une projection reconstruite mais identique conserve son instance précédente, pour que le « strong skipping » de Compose ne recompose pas les sous-arbres de la barre et de la page Maison.
- **Profil baseline** : ajout de `baseline-prof.txt` et de `profileinstaller`, pour un démarrage à froid compilé AOT plutôt qu'interprété + JIT.
- **Pont MQTT silencieux** : sur appareil provisionné en root, le pont tourne en service simple, sans notification permanente, avec repli automatique sur le service de premier plan ailleurs.
- **Sonde de présence conditionnée** : le proxy d'occupation n'est exposé que sur le matériel disposant réellement d'un composant écran de veille.

## 0.0.7-beta

### Added

- **Maison Home Assistant** : nouvelle page optionnelle avec sections configurables, favoris, pièces, groupes, entités individuelles, menus contextuels et réorganisation des raccourcis.
- **Panneaux Home Assistant étendus** : prise en charge des humidificateurs, chauffe-eau, vannes, sirènes, tondeuses, lave-linge, groupes et entités génériques, avec navigation et contrôles adaptés à leurs capacités.
- **Icônes Home Assistant et MDI** : police et index embarqués, résolution des icônes natives et personnalisées, cache local et rafraîchissement ciblé.
- **Provisionnement ADB protégé** : activité cachée permettant de préconfigurer les identifiants Home Assistant sur les appareils administrés.
- **Compatibilité TV et D-pad** : bannière Android TV, navigation clavier/télécommande et alias de débogage dédiés.

### Changed

- **Accueil responsive** : la page Maison utilise désormais une grille verticale adaptative de une à quatre colonnes et un en-tête partagé plus compact.
- **Navigation du launcher** : ordre stable entre Maison, Accueil et applications, retour vers Accueil et transitions de pager optimisées.
- **Pills Home Assistant** : catalogue complet, priorités contextuelles, alertes et capacités critiques, tout en réduisant le bruit des entités auxiliaires.
- **Panneaux et réglages** : alarmes, médias, thermostats, purificateurs, accessoires, fonds et configuration des pills ont été enrichis et adaptés aux différentes tailles d'écran.

### Removed

- Les anciens panneaux dédiés Air Quality, Energy, Presence et Scenes ont été remplacés par les contrats génériques et les groupes Home Assistant.
- Les rails horizontaux imbriqués de la page Maison et leur arbitrage gestuel ont été remplacés par une grille à défilement vertical unique.

### Performance

- Les transformations de snapshots Home Assistant sont échantillonnées et exécutées hors du thread principal.
- Les transitions du pager et de l'horloge privilégient les transformations graphiques afin de limiter les recompositions.
- L'index MDI est lu directement depuis l'APK sans chargement complet en mémoire.

### i18n

- Ajout des traductions anglaises et françaises pour la page Maison, les réglages, les appareils, les alarmes et le Playground.

### Tests

- Couverture étendue des préférences Maison, du catalogue et des priorités, de la navigation, des panneaux Home Assistant, des icônes, de l'accessibilité et des reducers de réglages.

## 0.0.5-beta

### Added

- **Entity contracts**: `ClimateEntityContract` and `FanEntityContract` — UI-neutral adapters that extract capabilities from Home Assistant entities. Climate handles all thermostat modes (heat, cool, auto, heat_cool, dry, fan_only) with target temp ranges. Fan maps to one of three control modes: on/off, percentage slider, or preset selector.
- **`PortalThreeWayControl`**: reusable three-action capsule control (previous / center / next) shared between media player and covers, with compact and regular densities.
- **`PortalVacuum`**: dedicated vacuum controls — large play/pause disk button, status chip, action chips (stop/dock/locate), room selection chips with toggles.
- **`HorizontalSegmentedSelector`**: Apple Home-style horizontal picker with drag gesture, haptics, and animated floating capsule highlight.
- **Alarm alert handling**: `PanelSource.ALERT` with highest priority — alarm chips surface immediately, `SleepScheduler.alarmHold` keeps screen on until disarmed, dismiss/rearm logic with per-alarm key.
- **Battery display**: vacuum entities show battery percentage in `SidePanel` header and quick tiles, with color-coded indicators (red ≤10%, orange ≤20%).
- **Domain-specific accent colors**: lock turquoise, fan blue, thermostat orange/blue.
- **Playground screen**: interactive panel lab with 14 fake entities for testing all panel layouts.
- **WallpaperPage** activated (was `.disabled`) with native Android wallpaper picker integration.
- **System wallpaper mode**: new `"system"` background mode uses native `WallpaperService` with scroll offset protocol for live wallpaper parallax.
- **Tests**: `PanelStateTest` (28+ reducer scenarios), `ClimateEntityContractTest`, `FanEntityContractTest`.

### Changed

- **All panel controls are now responsive**: sliders, switches, selectors, keypad use proportional scaling (`RoundedCornerShape(percent = 30)`, 96:240 viewport ratio, `contentScale`) instead of fixed dp values. Controls adapt to any screen size (wall tablet, phone).
- **`PanelHeader`**: unified header component used by all panels — frosted navigation circle, optional icon + title, configurable accent, battery indicator.
- **Light panel**: single `AdaptiveLightDetail` layout replaces separate portrait/landscape layouts. Smoother color presets (sunset, candle, soft pink, forest, lagoon, dusk, lavender, evening).
- **Thermostat panel**: Canvas‑drawn arc replaced by `ClimateEntityContract` + `PortalThermostat`. Mode selection via `WheelPicker`. Optimistic UI with 5s timeout. Support for `heat_cool` range mode with dual handles.
- **Cover panel**: three separate `GlassButton` replaced by single `PortalThreeWayControl` with labels. Slider viewport responsive.
- **Vacuum panel**: `PanelModeButton` rows replaced by `VacuumRunButton` + `VacuumStatusChip` + `VacuumActionChips`. Speed selector via `WheelPicker`.
- **Fan/Switch controls**: `BigCircleButton` replaced by responsive `VerticalSwitch` with optimistic UI. Fan uses `FanEntityContract` for mode‑appropriate controls (on/off switch, percentage slider, or preset selector).
- **Alarm panel**: `AccessoryGrid` replaced by responsive `VerticalSegmentedSelector`. Added `DISABLED` arm state. Optimistic arming with 5s timeout. Keypad pulse animation during code verification.
- **Lock panel**: accent changed to turquoise. Responsive viewport.
- **Media player**: transport controls replaced by `PortalThreeWayControl`. Header uses `PanelHeader` with source title.
- **WallpaperPage**: activated from `.disabled`; new "Choose Android wallpaper" picker integration.
- **SettingsScreen**: new `WALLPAPER` page and navigation tile.
- **styles.xml**: window background transparent with `windowShowWallpaper=true` for native wallpaper support.
- **PRODUCT.md**: enriched with 7 design principles, Apple Home interaction reference, wall-panel usage personas.

### Performance

- `PortalWheelPicker`: only emits `onSelect` when scrolling stops (not on every item), reducing HA service calls.
- Keypad: input disabled during loading state to prevent double-taps.

## 0.0.4-beta

### Added

- **Onboarding wizard**: new 15-step guided setup wizard (`OnboardingActivity`) replacing the legacy 3-step `SetupWizard`. Organized in 3 chapters — *Launcher* (welcome, system permissions, grid density, wallpaper), *Home* (Home Assistant discovery/credentials/test, pills configuration, remote control, MQTT setup/test), *Finish* (hidden apps cleanup, gestures hints, completion summary). Opens automatically on first launch and can be relaunched from Settings.
- **mDNS discovery**: automatic Home Assistant and MQTT broker discovery on the local network via mDNS (`MqttMdnsDiscovery`).
- **Home Assistant connection test**: 3-phase test (address check, authentication, device retrieval) with detailed diagnostics and device count by category.
- **MQTT roundtrip test**: 3-phase test (connect, publish, verify) with granular error diagnosis per phase.
- **Pills configuration**: guided entity selection from Home Assistant with search, bulk toggle, and recommended presets.
- **Wallpaper configuration**: 4 wallpaper modes (Calm gradient, Nature/Unsplash cycling, My Photo, Immich albums) with dedicated sub-configuration pages.
- **Immich photo source**: fetch and cycle wallpapers from Immich albums with configurable server, API key, album selection, and refresh frequency.
- **produtionTest build variant**: release-equivalent build signed with debug key, allowing in-place updates on debug-installed devices without losing preferences or credentials.
- **Settings > Information page**: app version display, GitHub Releases update checker, APK download and install via `FileProvider`.
- **Debug activity aliases**: `OnboardingDevTrigger` and `SettingsDevTrigger` exported in debug builds for ADB-driven testing.
- **Unit tests**: 5 new test suites covering ViewModel transitions, navigation logic, URL validation/normalization, grid scaling, and connection diagnostics (~45 tests).
- **~315 new i18n strings**: comprehensive English and French translations for all onboarding screens, settings, and diagnostics.

### Removed

- **SetupWizard**: legacy 3-step setup wizard (`SetupWizard.kt`) fully replaced by the new onboarding flow.
- **SoundMonitor**: ambient sound level monitoring (`SoundMonitor.kt`), `RECORD_AUDIO` permission, and all related MQTT topics (`soundDiscoveryTopic`, `soundStateTopic`, `micMuteDiscoveryTopic`, `micMuteCommandTopic`, `micMuteStateTopic`).

### Changed

- **Onboarding gate**: devices already configured with Home Assistant before this version are never interrupted by the onboarding wizard (`shouldRunOnboarding` checks `legacyConfigured`).
- **Settings root**: Settings now always open on the main page — the SETUP section no longer exists.
- **LauncherActivity**: checks onboarding status at `onCreate` and delegates to `OnboardingActivity` if needed.
- **Prefs**: new properties for onboarding state (`onboardingCompleted`, `onboardingVersion`, `onboardingStep`, skip flags, gesture hints) with `resetOnboarding()`.

### Performance

- **LauncherPager**: collapse fraction read as lambda in `graphicsLayer` instead of `derivedStateOf`, avoiding recomposition on every swipe pixel. `PageDots` use `Canvas` draw instead of row/box layout.
- **ClockHeader**: collapse parameter read in `graphicsLayer` without recomposition. Secondary lines persist during swipe instead of being removed/recreated.
- **AppGridPage**: `onPage` callback wrapped in `remember(items, page)` to avoid reallocation on every recomposition.

## 0.0.3-beta

### Added

- **i18n / internationalization**: full translation system with English (default) and French. ~320 strings extracted from hardcoded values into `res/values/strings.xml` (EN) and `res/values-fr/strings.xml` (FR). Adding a new language now only requires creating a new `values-XX/strings.xml` file — no code changes needed.

### Changed

- **iOS-style selected chip**: when a tray chip is selected (its panel is open), it now renders with a white background, dark text, and white border — matching the iOS Home app look. The colored icon circle retains its accent color.

### Technical

- All user-facing strings now use `stringResource(R.string.*)` (Compose) or `context.getString(R.string.*)` (non-Compose) instead of hardcoded French text
- Plurals support for dynamic counts (e.g. "1 shortcut" / "2 shortcuts")
- `PillPriorityEngine` refactored from `object` to `class` with Context injection for resource access
- All 162 unit tests updated to assert against English string resources

## 0.0.2-beta

### Added

- **Device-scaled UI**: automatic layout scaling based on screen density (dpi)
- **Clock long-press shortcut**: long-press the clock header to jump directly to clock theme settings
- **Configurable grid density**: adjustable icon size slider in settings, controlling the app grid columns × rows

### Removed

- **Camera overlay**: experimental pop-up triggered by binary sensors (doorbell, motion) — not essential for the initial app scope
- **Web config server**: experimental remote-configuration HTTP server (NanoHTTPD) — not essential for the initial app scope
- **Wireless ADB**: experimental ADB toggle from developer settings — not essential for the initial app scope
- **Tap sensitivity**: experimental tap/tilt detection sensitivity slider — not essential for the initial app scope
- **Temperature offset**: experimental temperature calibration offset — not essential for the initial app scope

### Changed

- **Reboot button**: renamed "Redémarrer le Portal" to "Redémarrer"

## 0.0.1-beta

- Initial release
