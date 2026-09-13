# Cartographie — onboarding Web / appareil

## Décision produit à appliquer

- Au premier lancement, **la langue reste toujours choisie sur l'appareil**.
- Après la langue, un appareil dont la diagonale physique est **strictement inférieure à 6 pouces** utilise obligatoirement l'onboarding Web.
- À partir de 6 pouces, l'utilisateur choisit une seule voie : **continuer sur cet appareil** ou **continuer dans un navigateur**.
- Une voie choisie pilote le même état d'onboarding et les mêmes préférences. Il ne doit pas exister deux implémentations métier divergentes.
- La relance / remise à zéro de l'onboarding démarre depuis le navigateur. Elle ne doit pas effacer silencieusement les réglages métier.

> Hypothèse à confirmer avant implémentation : « reset tout » signifie recommencer tout l'onboarding en conservant les valeurs actuelles, comme `Prefs.resetOnboarding()` aujourd'hui. Un véritable factory reset (identifiants, disposition, widgets et préférences compris) est une fonction différente, destructive, à spécifier séparément avec confirmation forte.

## Parcours cible

```text
Premier lancement
      |
      v
Langue sur l'appareil (toujours)
      |
      v
Mesure fiable de la diagonale
      |
      +-- < 6" ------> Écran appareil minimal « Continuer sur le Web »
      |                  QR + URL + code + état de connexion + langue
      |                              |
      |                              v
      |                        Navigateur mobile/desktop
      |
      +-- >= 6" -----> Choix « Sur cet appareil / Dans un navigateur »
                           |                    |
                           v                    v
                     UI Compose          Même onboarding Web
                           \                    /
                            v                  v
                       Moteur d'état partagé + Prefs
                                  |
                                  v
                      Validation atomique + launcher
```

Si Android impose une action locale (autorisation système, choix du launcher par défaut, accès notifications, accessibilité), le navigateur demande l'action au panneau, affiche « Action requise sur l'écran », puis suit son statut. Cela reste un onboarding piloté à 100 % depuis le Web, mais Android ne permet pas au navigateur de contourner le consentement système.

## État actuel

### Démarrage et persistance

- `LauncherActivity.openOnboardingIfNeeded()` ouvre `OnboardingActivity` quand `onboarding_completed=false` et qu'aucun ancien token Home Assistant ne permet de considérer l'appareil déjà configuré.
- `Prefs` stocke la version, l'étape Compose courante, la complétion et les branches ignorées.
- `OnboardingViewModel` est déjà la machine d'état principale, mais elle est couplée à la navigation Compose et n'expose pas de commandes Web génériques.
- `WelcomeStep` contient déjà le sélecteur initial de langue et redémarre l'activité après changement de locale.

### Onboarding sur l'appareil

Le flux Compose couvre actuellement :

1. langue / bienvenue ;
2. capacités Android ;
3. taille de grille ;
4. fond ;
5. découverte et connexion Home Assistant ;
6. sélection des pills ;
7. MQTT ;
8. applications masquées ;
9. application ouverte au tap ;
10. gestes ;
11. fin.

Les choix sont écrits immédiatement dans `Prefs`; l'étape courante permet une reprise après interruption.

### Configuration Web actuelle

- `WebConfigActivity` démarre un `WebConfigServer` uniquement tant que l'activité est visible et montre QR, URL et code.
- Le serveur NanoHTTPD protège toutes les routes sensibles avec un token éphémère.
- `config.html` / `config.js` sont responsives, mais ne couvrent que Home Assistant, MQTT et un résumé.
- Le navigateur et le panneau peuvent choisir français ou anglais, mais le changement de langue Web ne met pas à jour la langue de l'application.
- La sauvegarde Web actuelle ne termine pas l'onboarding global et ne partage pas sa progression avec `OnboardingViewModel`.

### Taille physique

`ClockScreen.isCompactClockScreen()` calcule déjà la diagonale avec `widthPixels / xdpi` et `heightPixels / ydpi`, avec un seuil actuel `<= 6f`. Cette logique doit sortir de l'écran horloge pour devenir une politique appareil testable et partagée.

La nouvelle règle est **`< 6.0`**, pas `<= 6.0`. Les métriques invalides (`xdpi/ydpi <= 0`) ne doivent pas autoriser par défaut le parcours device : politique sûre recommandée = Web obligatoire, avec journal diagnostic non sensible. Certains constructeurs déclarant de mauvaises densités, prévoir aussi un override debug/administrateur.

## Architecture cible

### 1. Politique de canal

Créer un composant pur, par exemple `OnboardingChannelPolicy` :

- entrée : pixels, xdpi/ydpi, éventuel override ;
- sortie : `WEB_REQUIRED`, `DEVICE_OR_WEB`, `UNKNOWN_WEB_REQUIRED` ;
- seuil unique `6f`, strictement inférieur ;
- tests aux frontières 5,99 / 6,00 / 6,01 et métriques invalides.

`LauncherActivity` continue d'être le gate de premier lancement. Après la langue, `OnboardingActivity` applique cette politique. Un petit appareil ne doit jamais rendre les étapes Compose autres que langue et écran de jumelage Web.

### 2. Moteur d'onboarding unique

Extraire de `OnboardingViewModel` un service indépendant de l'UI, par exemple `OnboardingCoordinator` :

- snapshot sérialisable : version, étape, canal, flags, valeurs non secrètes, capacités et tests en cours ;
- commandes typées : choisir grille/fond, tester HA/MQTT, choisir pills/apps, ignorer une branche, demander une action Android, terminer ;
- transitions et validations communes au Web et à Compose ;
- secrets acceptés en entrée mais jamais renvoyés par l'API (`configured: true` remplace leur valeur) ;
- écriture immédiate ou transactionnelle selon l'étape, puis complétion atomique.

Le Compose devient un client du coordinator. Le serveur Web utilise exactement les mêmes commandes. `OnboardingStep` reste la source d'ordre et de reprise, mais le progrès doit aussi stocker le canal choisi.

### 3. Hôte Web permanent pendant l'onboarding

Remplacer le rôle temporaire de `WebConfigActivity` par un `WebOnboardingActivity` minimal :

- démarre le serveur dès que le canal Web est choisi ;
- reste affichée pendant toute la session ;
- montre seulement langue, QR, URL/code, connexion du navigateur, étape distante et erreurs récupérables ;
- empêche l'accès au launcher tant que l'onboarding n'est ni terminé ni explicitement abandonné selon la règle produit ;
- ferme le serveur dès fin, sortie ou expiration.

Conserver le serveur lié à une activité est plus sûr qu'un service LAN permanent. Il faut toutefois survivre à une rotation/recréation sans invalider brutalement une session active (token dans un session holder à durée limitée, pas dans `savedInstanceState` en clair).

### 4. API Web à ajouter

Conserver le token en query string pour compatibilité initiale, puis le transmettre par en-tête ou cookie `SameSite=Strict` afin d'éviter sa propagation accidentelle.

Routes minimales :

| Route | Rôle |
|---|---|
| `GET /api/onboarding` | état, canal, étape, options, capacités, progression |
| `POST /api/onboarding/command` | commande typée avec `expectedRevision` |
| `GET /api/onboarding/events` | SSE pour progression, tests et actions locales |
| `POST /api/onboarding/complete` | validation finale et complétion atomique |
| `POST /api/onboarding/reset` | reset de progression après confirmation Web |
| `POST /api/device-action` | ouvre l'écran Android requis sur le panneau |

Ajouter une révision monotone évite qu'un ancien onglet écrase une décision récente. Une seule session navigateur est éditrice; les autres deviennent observatrices ou doivent reprendre explicitement la main.

### 5. Écrans Web desktop et mobile

Utiliser un seul DOM adaptatif :

- mobile : une colonne, CTA collé en bas, champs larges, aucun hover requis ;
- desktop : navigation/progression à gauche, contenu au centre, aperçu du panneau à droite quand utile ;
- mêmes étapes et mêmes libellés, sans dupliquer la logique JS ;
- sauvegarde/reprise à chaque transition et écran explicite si le panneau disparaît du réseau ;
- aucune dépendance CDN : `config.html` charge actuellement Tailwind depuis `cdn.tailwindcss.com`, ce qui est fragile sur un LAN sans Internet et contraire à un onboarding appliance fiable. Servir tout localement.

Le but d'activation est simple : arriver à un launcher utilisable. Home Assistant, MQTT et les optimisations doivent rester ignorables; les autorisations non essentielles ne bloquent pas la fin.

### 6. Reset depuis le navigateur

Supprimer l'action directe `OnboardingActivity.intent(reset = true)` des réglages et la remplacer par « Ouvrir la gestion Web » :

1. le panneau affiche QR/URL/code ;
2. le navigateur ouvre une page Gestion ;
3. « Recommencer l'onboarding » affiche ce qui sera conservé ;
4. confirmation explicite ;
5. `resetOnboarding()` + nouvelle session Web, sans effacer les réglages ;
6. le panneau revient au gate d'onboarding.

Ne jamais exposer une route reset sans token, ne pas accepter GET pour une mutation, ajouter protection anti-rejeu/révision, délai d'expiration et rate limiting local.

## Matrice de couverture à construire

| Domaine | Compose actuel | Web actuel | Cible Web | Particularité |
|---|---:|---:|---:|---|
| Langue app | oui | non | lecture seulement | reste sur le device |
| Capacités Android | oui | non | pilotage + polling | consentement visible sur device |
| Grille | oui | non | oui | aperçu adaptatif |
| Fond / opacité | oui | non | oui | upload éventuel à spécifier |
| Home Assistant | oui | oui | oui | tests communs |
| Pills | oui | non | oui | nécessite chargement des entités |
| MQTT | oui | oui | oui | tests communs |
| Apps masquées | oui | non | oui | exposer labels/icônes, pas seulement packages |
| App au tap | oui | non | oui | liste des apps installées |
| Gestes | oui | non | oui | explication, pas interaction simulée obligatoire |
| Complétion globale | oui | non | oui | commit atomique |
| Reset onboarding | device/ADB | non | oui | confirmation Web |

## Découpage d'implémentation recommandé

### Phase 1 — fondations et contrat

- extraire/tester `OnboardingChannelPolicy` ;
- définir snapshot, commandes, erreurs et révision du coordinator ;
- ajouter `onboarding_channel` et une migration non destructive ;
- rendre les transitions existantes consommables hors ViewModel.

Critère : le flux Compose existant passe toujours ses tests via le nouveau coordinator.

### Phase 2 — shell Web obligatoire sur petits écrans

- séparer la langue du reste de `WelcomeStep` ;
- router `< 6"` vers le shell QR obligatoire, `>= 6"` vers le choix de canal ;
- gérer métriques invalides, reprise process et expiration de session ;
- afficher connexion navigateur et état distant sur le panneau.

Critère : aucun appareil classé `< 6"` ne peut atteindre une étape Compose après la langue.

### Phase 3 — parité fonctionnelle Web

- remplacer le wizard HA/MQTT par le client du coordinator ;
- ajouter grille, fond, pills, apps, gestes, branches facultatives et résumé ;
- ajouter SSE/polling de secours et commandes d'actions Android ;
- embarquer tous les assets, desktop et mobile.

Critère : chaque transition du test de navigation Compose possède un scénario API équivalent.

### Phase 4 — complétion, reprise et reset Web

- complétion atomique et redirection automatique du panneau vers le launcher ;
- reprise après fermeture d'onglet, perte Wi-Fi, rotation et mort du process ;
- déplacer la relance d'onboarding des réglages vers la gestion Web ;
- ajouter confirmation, audit local minimal et invalidation de session après reset.

### Phase 5 — durcissement et validation

- tests API d'autorisation, secrets, concurrence, replay et expiration ;
- tests de frontière physique et faux DPI ;
- tests E2E navigateur aux largeurs mobile/desktop ;
- tests réels sur 5", exactement 6" et tablette ;
- accessibilité clavier, focus, lecteur d'écran et contraste ;
- métriques locales : canal choisi, étape atteinte, abandon, temps d'activation, sans URL/token/mot de passe.

## Fichiers principalement concernés

- `LauncherActivity.kt` : gate initial et redirection après complétion.
- `Prefs.kt` : canal, révision, progression et transaction de fin/reset.
- `ui/onboarding/OnboardingActivity.kt` et `WelcomeStep.kt` : langue, politique et shell minimal.
- `ui/onboarding/OnboardingViewModel.kt`, `OnboardingStep.kt`, `OnboardingState.kt` : extraction du coordinator partagé.
- `WebConfigActivity.kt`, `WebConfigServer.kt`, `WebConfigPage.kt` : transformation en hôte/API d'onboarding.
- `resources/webconfig/*` : nouveau client responsive autonome.
- `SettingsScreen.kt` : gestion/reset via navigateur.
- tests onboarding et serveur : parité de transitions, sécurité et reprise.

## Points à trancher avant codage

1. À exactement **6,00 pouces**, cette cartographie autorise device ou Web, conformément à « plus petit que 6 ».
2. Un « reset tout » est-il une relance non destructive de l'assistant, ou un factory reset réel ?
3. Le parcours Web peut-il être ignoré pour utiliser immédiatement les valeurs par défaut, surtout sous 6 pouces ? Recommandation : oui uniquement via une action Web confirmée, jamais par un bouton caché sur le petit écran.
4. Pour les actions Android impossibles à valider à distance, accepte-t-on une intervention tactile ponctuelle sur le panneau ? Techniquement, elle est obligatoire sauf appareil rooté/provisionné ADB.
5. Le fond personnalisé doit-il accepter l'upload d'une image depuis le navigateur dès cette version ?

---

# Audit UX complet et parcours de référence

## Verdict produit

Le Web doit devenir **le configurateur de référence**, et l'appareil **la surface de résultat et de consentement**. Le navigateur ne doit pas imiter un écran sans lien avec le matériel : chaque réglage visuel doit produire simultanément une prévisualisation locale dans le navigateur et une prévisualisation réelle sur le panneau. Le flux Compose reste temporairement disponible comme client secondaire du même moteur d'état, puis peut être retiré sans perdre de logique métier.

Le flux Web actuel n'est pas une base suffisante à étendre écran par écran. Il faut conserver son serveur local et ses tests de connexion, mais remplacer son modèle de navigation, son contrat d'état et sa couche visuelle.

## Revue d'interface

**Mode :** `full`  
**Périmètre :** premier lancement Compose, configuration Web, serveur local, réglages disponibles après onboarding, synchronisation navigateur/appareil.  
**Frameworks :** Jetpack Compose côté appareil ; HTML, JavaScript sans framework et Tailwind CDN côté navigateur ; CSS local complémentaire.  
**Convention cible :** interface utilitaire sombre, surfaces mates, bordures 1 px, rayons 4–6 px, texte 13–14 px, titre 18 px, une couleur d'accent, icônes seulement pour les providers, états et actions iconiques.  
**Limite :** inspection statique du code. Aucun navigateur contrôlable ni appareil Android n'était disponible pour une revue visuelle interactive.

| Catégorie | Éléments inspectés | Résultat |
|---|---|---|
| Typographie | `config.html`, titres et descriptions de `config.js`, libellés des étapes Compose | 2 constats |
| Surfaces | `webconfig.css`, structure desktop/mobile de `config.html`, tailles de cible | 2 constats ; cibles tactiles correctes |
| Animations | transitions CSS, progression mobile, `scrollTo`, transitions Compose déclarées | 1 constat ; inspection à 10 % non vérifiée |
| Icônes | états finaux Web, cartes d'intégrations HA, icônes de providers Android | 2 constats |
| Performance | Tailwind CDN, fréquence et moment d'envoi des réglages, absence de canal événementiel | 3 constats |

### Vérité fonctionnelle et continuité

| Sévérité | Emplacement | Avant | Après | Pourquoi |
|---|---|---|---|---|
| HIGH | `resources/webconfig/config.js:11-17`, `ui/onboarding/OnboardingStep.kt:10-27` | Le Web expose 5 écrans quand le parcours device en expose 16. | Un graphe d'étapes unique génère le parcours des deux clients et insère les branches des providers sélectionnés. | Le Web ne peut pas devenir la référence s'il configure moins de la moitié du produit. |
| HIGH | `resources/webconfig/config.js:71`, `resources/webconfig/config.html:22-30` | Taille et fond ne sont envoyés qu'au clic sur « Continuer » ; le slider d'opacité ne met à jour qu'un pourcentage. | Commandes de prévisualisation éphémères pendant l'interaction, rendu simulé dans le navigateur, rendu réel sur le panneau, commit au relâchement ou à « Continuer ». | Le réglage principal ne montre actuellement aucun effet, donc l'utilisateur choisit à l'aveugle. |
| HIGH | `resources/webconfig/config.js:128`, `WebConfigServer.kt:179-183` | La fin envoie toujours `skip_app_cleanup: true` sans avoir présenté le nettoyage des apps. | L'étape « Applications » collecte explicitement visibilité, ordre, pack d'icônes et app au tap ; aucun choix n'est inventé à la fin. | Une configuration cachée et irréversible dans le parcours est trompeuse. |
| HIGH | `resources/webconfig/config.js:3-4,44-50,146-147`, `WebConfigServer.kt:134-147` | Le navigateur démarre toujours à l'étape 0 et maintient sa progression localement ; aucun numéro de révision. | Snapshot serveur avec `stepId`, `revision`, brouillon, propriétaire de session et reprise exacte ; chaque mutation porte `expectedRevision`. | Un refresh ou un ancien onglet peut afficher un état faux et écraser des choix plus récents. |
| HIGH | `resources/webconfig/config.js:139` | Ignorer Home Assistant ignore aussi MQTT et saute directement à la fin. | MQTT est un provider indépendant : il peut exposer écran, luminosité, volume, présence et notifications sans Home Assistant. | La dépendance actuelle supprime une fonction valide sans rapport nécessaire avec HA. |

### Copywriting et hiérarchie

| Sévérité | Emplacement | Avant | Après | Pourquoi |
|---|---|---|---|---|
| MEDIUM | `resources/webconfig/config.js:12-16`, `resources/webconfig/config.html:19` | « Adaptez l'affichage », « Connectez votre maison », « Donnez une voix au panneau », descriptions systématiques. | « Affichage », « Home Assistant », « MQTT », « Gemini », sans sous-titre sauf contrainte ou statut. | Les titres décrivent un bénéfice comme une landing page au lieu de nommer l'outil. |
| LOW | `resources/webconfig/config.html:39,48-49` | Le caractère facultatif de MQTT est un paragraphe, celui de Gemini un bouton en bas. | Badge texte monochrome `Facultatif`, visible dans la navigation et dans l'en-tête ; action `Ignorer` stable. | L'optionalité doit être comprise avant la lecture du formulaire et rester visible dans la progression. |

### Surfaces et densité

| Sévérité | Emplacement | Avant | Après | Pourquoi |
|---|---|---|---|---|
| MEDIUM | `resources/webconfig/webconfig.css:4,6,21-23`, `resources/webconfig/config.html:53` | Rayons 12–18 px, `rounded-xl/2xl`, ombres de 20–50 px et boutons de 48 px. | Rayons 4–6 px, bordures mates, aucune ombre hors popover, contrôles desktop de 36–40 px et touch de 44 px. | Le rendu actuel reste proche d'une interface promotionnelle et gaspille de la hauteur utile. |
| MEDIUM | `resources/webconfig/config.html:9,19` | Colonne centrale limitée à 720–760 px avec titre jusqu'à 36 px ; aucun espace réservé à l'appareil. | Shell 3 colonnes : navigation 220 px, formulaire 520–640 px, aperçu 320–420 px ; titre 18 px. | L'espace desktop disponible n'est pas utilisé pour montrer la conséquence des réglages. |

### Icônes et états

| Sévérité | Emplacement | Avant | Après | Pourquoi |
|---|---|---|---|---|
| MEDIUM | `resources/webconfig/config.html:55-56` | Gros symboles `✓` et `!` dans des conteneurs circulaires colorés. | Ligne d'état compacte avec icône 16 px, libellé et détail ; même structure pour succès, erreur et attente. | Les icônes géantes sont décoratives et rompent le langage utilitaire. |
| MEDIUM | `ui/screens/HaIntegrationsSettingsPage.kt:107-183` | Intégrations HA en mosaïque de grandes cartes avec icône 62 px et rayon 24 px. | Table/liste dense avec icône officielle 20 px, nom, domaine, appareils, entités, statut et switch. | Une liste technique doit permettre comparaison, filtre et sélection en masse. |

### Mouvement et performance

| Sévérité | Emplacement | Avant | Après | Pourquoi |
|---|---|---|---|---|
| HIGH | `resources/webconfig/config.html:6` | Tailwind est chargé depuis `cdn.tailwindcss.com`. | CSS compilé et assets servis par l'appareil, sans accès Internet requis. | Un onboarding LAN ne doit pas devenir inutilisable ou non stylé quand Internet est absent. |
| MEDIUM | `resources/webconfig/config.js:46-50` | Changement d'étape suivi d'un scroll animé de la page ; progression à 300 ms. | Transition d'opacité/couleur ≤150 ms, interrompable, aucun mouvement de layout ; `prefers-reduced-motion` conservé. | Le mouvement fréquent ne doit pas attirer l'attention ni retarder la navigation. |
| HIGH | `WebConfigServer.kt:121-131`, `WebConfigServer.kt:56-58` | Les secrets configurés sont renvoyés en clair au navigateur ; le serveur fonctionne en HTTP LAN avec token dans l'URL. | Ne jamais relire un secret : retourner `configured: true`; échanger le code de jumelage contre une session courte et éviter le token dans les logs/historiques. | Une clé Gemini, un token HA et un mot de passe MQTT ne doivent pas être réexposés après stockage. |

## Principes non négociables du nouveau parcours

1. **Une seule source d'état.** Le navigateur et Compose consomment les mêmes snapshots et commandes.
2. **Deux prévisualisations, une vérité.** Le navigateur simule immédiatement ; le panneau confirme le rendu réel et renvoie un accusé de réception.
3. **Brouillon réversible.** Revenir en arrière restaure le dernier état validé de l'étape, pas une valeur partiellement manipulée.
4. **Providers indépendants.** Home Assistant, MQTT, Gemini et Immich sont activés séparément. Une dépendance réelle est affichée au niveau de la fonction concernée.
5. **Gemini est un supplément.** La navigation, le résumé et les erreurs le marquent `Facultatif`. Son échec ne bloque jamais l'activation de Portal.
6. **Le device ne devient pas passif.** Il affiche le résultat, les demandes de permission Android, l'état du navigateur et une sortie de secours locale.
7. **Pas de secret en lecture.** L'API expose `configured`, `lastTestAt` et `status`, jamais la valeur stockée.
8. **Tout est reprenable.** Fermeture d'onglet, rotation, perte Wi-Fi et redémarrage de process ramènent à l'étape et au brouillon serveur.

## Architecture de l'écran Web

```text
┌──────────────────┬──────────────────────────────────┬────────────────────────┐
│ Portal           │ Affichage                        │ Panneau                │
│                  │                                  │ Connecté · 18 ms       │
│ Appareil         │ Taille des icônes                │ ┌────────────────────┐ │
│ Interface        │ [Compact] [Équilibré] [Confort]  │ │  grille réelle     │ │
│ Services         │ ───────●────────────  100 %      │ │  mise à jour live  │ │
│ Maison           │ 0,70                         1,30 │ └────────────────────┘ │
│ Finalisation     │                                  │ 6 colonnes · 24 apps  │
│                  │ Colonnes calculées       6       │ Appliqué il y a 0,2 s │
│ 9 / 14           │ Lignes visibles          4       │                        │
│ Connecté         │                                  │                        │
│                  │ [Précédent]          [Continuer] │                        │
└──────────────────┴──────────────────────────────────┴────────────────────────┘
```

- La navigation gauche affiche des **chapitres** et un compteur, pas quinze pastilles décoratives.
- Le formulaire central est une liste de groupes bordés, hauteurs 36–40 px sur desktop.
- L'aperçu droit reste sticky. Il porte clairement deux états : `Simulation` et `Panneau appliqué`.
- Sur navigateur mobile, l'aperçu simulé devient un panneau repliable. Le panneau physique reste la prévisualisation principale.
- L'action primaire reste en bas à droite. `Ignorer` est un bouton secondaire uniquement sur les étapes facultatives.
- Le statut de connexion est toujours visible : `Connecté`, `Reconnexion`, `Hors ligne`, avec dernière réception relative et timestamp UTC en tooltip.

## Parcours cible détaillé

Le nombre visible s'adapte aux providers retenus. Les identifiants d'étape restent stables pour la reprise et les métriques.

| ID | Écran navigateur | Affichage simultané sur le device | Données et actions | Sortie |
|---|---|---|---|---|
| `locale` | Aucun formulaire Web. | Choix `Français` / `English`, puis QR, URL courte et code. | `appLanguage`, disponibilité réseau, adresse locale. | Obligatoire, local uniquement. |
| `pairing` | `Appareil` : nom, modèle, version, résolution, diagonale détectée, IP, qualité du lien. | QR remplacé par `Navigateur connecté`, nom de session et bouton local `Déconnecter`. | Acquisition de session éditrice, diagnostic non sensible. | Obligatoire. |
| `system-access` | Liste `Launcher par défaut`, `Contrôle de l'écran`, `Luminosité`, `Notifications`, `Microphone` si Gemini sélectionné. | Ouvre le panneau système demandé et montre l'autorisation en cours. | Polling de `CapabilityStatus`, commande `open_device_action`. | Les permissions essentielles bloquent ; les autres sont ignorables. |
| `display` | `Affichage` : slider 0,70–1,30, presets, nombre calculé de colonnes/lignes, reset. | Vraie grille du launcher avec apps installées, sans navigation possible. | `gridScale`; preview 15 Hz maximum, commit au relâchement. | Obligatoire, valeur par défaut utilisable. |
| `background` | `Fond` : Neutre, Système, Image, Immich ; grille de miniatures ; opacité 0–60 %. | Fond réel avec horloge, pills et icônes superposés. | `backgroundMode`, `bgOverlayOpacity`, upload, Immich URL/clé/albums/shuffle/refresh/cadence. | Obligatoire pour le mode ; provider Immich facultatif. |
| `clock` | `Horloge` : police, graisse, taille, espacement, teinte, 12/24 h, date, espacement des blocs. | Écran horloge réel, heure courante et date courante. | Toutes les propriétés de `clockTheme`. | Valeurs par défaut préchargées. |
| `apps` | `Applications` : table searchable, visible/masquée, ordre, pack d'icônes, pastilles de notification, app au tap. | Grille réelle ; sélection d'une ligne fait pulser une fois l'app correspondante sans mouvement répété. | `hiddenApps`, `appOrder/appPlacements`, `iconPack`, `notificationDots`, `homeAssistantPackage`, `tapAppPackage`. | Aucune app protégée ne peut être masquée. |
| `providers` | `Services` : lignes denses Home Assistant, MQTT, Gemini, Immich avec icône officielle, état et switch. | Liste compacte des services retenus et statut `À configurer`. | Sélection des branches ; Gemini et Immich portent `Facultatif`. | Home Assistant n'active pas automatiquement MQTT. |
| `ha-connection` | `Home Assistant` : instances mDNS, URL, jeton, test adresse/auth/API. | Journal des trois tests et nombre d'entités reçues. | `haUrl`, secret `haToken`, `HomeCandidate`, résumé par domaine. | Facultatif ; secret non relisible. |
| `ha-integrations` | `Intégrations` : table filtrable par nom/domaine, appareils, entités, statut ; activer/désactiver en masse. | Aperçu de la page Maison recalculée après chaque commit. | `disabledHaIntegrations`, marques HA proxifiées par le device. | Conditionnel à HA connecté. |
| `ha-entities` | `Entités` : recherche, filtres pièce/type/intégration/disponibilité, sélection multiple, pins primaires/secondaires, ordre, groupes manuels. | Pills réelles ; choix d'une entité ouvre son panneau de contrôle en mode lecture seule. | `pillRules`, `homePillPreferences`, ordre des sections, regroupement type/pièce, groupes manuels. | Conditionnel ; état vide, erreur et données stale gérés. |
| `ha-cameras` | `Caméras` : visibilité, ordre, caméra principale, mode principal/grille, pill générale. | Centre caméras réel avec flux ou poster ; aucune commande PTZ involontaire. | `cameraPreferences`, pin `PillSpecials.CAMERAS`. | Étape injectée uniquement si une caméra existe. |
| `mqtt` | `MQTT` : broker détecté, hôte, port, TLS si supporté, authentification, utilisateur, mot de passe, nom du panneau. | Tests connexion, publication et round-trip ; topics exposés. | `brokerHost/port`, credentials, `deviceName`, activation bridge. | Facultatif et indépendant de HA. |
| `gemini` | `Gemini` : clé, modèle Live retourné par API, voix, prompt, interruption, wake word, seuil, délai d'inactivité, limite/jour. | Demande micro, niveau live, score wake word, calibration bruit/haut-parleur, test de conversation. | Tous les champs `voice*`, résultat `GeminiProbe`, capacité d'annulation d'écho. | Facultatif ; `Ignorer` toujours disponible ; l'échec ne bloque pas. |
| `behavior` | `Comportement` : écran toujours actif, veille et délai, retour auto et délai, gestes. | Démonstration contextuelle exacte du geste ou timer configuré. | `powerMode`, `screenTimeoutEnabled/minutes`, `autoReturnEnabled/delay`, `gestureHintsSeen`. | Permissions manquantes signalées. |
| `review` | `Vérification` : tableau par domaine avec état `Prêt`, `Ignoré`, `Action requise`, lien `Modifier`. | Checklist identique puis transition vers le launcher après commit. | Validation, révision finale, commit atomique, invalidation session. | Seuls les éléments réellement bloquants empêchent `Activer Portal`. |

## Interactions de prévisualisation

### Taille des icônes

- Slider continu `0,70` à `1,30`, pas `0,01`; valeur numérique en chiffres tabulaires.
- Presets `Compact 0,82`, `Équilibré 1,00`, `Confort 1,18` sous forme de boutons de choix sobres.
- Le navigateur recalcule immédiatement la grille avec le ratio exact du panneau et des icônes réellement installées.
- Pendant le drag, les valeurs sont coalescées à 15 commandes/seconde maximum et envoyées avec `mode: preview`.
- Le device applique la valeur sans écrire `Prefs`, répond avec `previewRevision` et affiche la grille.
- Au `pointerup`, à `Enter` ou à `Continuer`, une commande `commit` écrit la valeur. `Escape` ou `Annuler` restaure la valeur validée.
- Le navigateur affiche `Simulation` tant que l'ACK du panneau n'est pas reçu, puis `Appliqué sur le panneau` avec la latence.

### Fond d'écran

- `Neutre` montre une couleur mate réelle ; aucune fausse image décorative.
- `Système` demande au device une miniature si Android permet de la lire. Sinon le navigateur affiche `Aperçu disponible sur le panneau` — il ne fabrique pas une miniature.
- `Image` accepte JPEG/PNG/WebP, annonce avant envoi taille maximale, ratio et recadrage. L'image est uploadée au panneau, qui produit une miniature locale.
- `Immich` est un provider de fond : URL et clé, test, albums, shuffle, rafraîchissement et cadence. Les miniatures sont proxifiées par le panneau pour éviter CORS et fuite de clé.
- Le slider d'assombrissement met à jour la même composition horloge + pills + icônes dans les deux previews.

### Home Assistant, entités et intégrations

- Après le test HA, le Web ne doit pas envoyer directement à un simple écran de fin.
- La table `Intégrations` agrège domaine, nombre d'appareils et nombre d'entités. L'icône officielle HA est légitime ici car elle distingue un provider/type réel.
- La table `Entités` contient au minimum : sélection, nom, `entity_id`, pièce, type, intégration, état, disponibilité et action.
- Filtres persistés dans l'URL du navigateur ; sélection multiple pour activer/désactiver.
- Un panneau latéral configure l'entité ou le groupe. Pas de modale longue.
- Les zones de pins montrent exactement 3 emplacements primaires et 6 secondaires, puis l'overflow. Le drag Web et les boutons clavier produisent la même commande d'ordre.
- Les groupes automatiques par type/pièce et les groupes manuels restent distingués ; le déplacement d'un membre entre groupes est transactionnel.

### Gemini

- Libellé de navigation : `Gemini` avec badge neutre `Facultatif`.
- L'écran ne promet pas que Gemini « donne une voix » : il expose une configuration et un test.
- La clé API est saisie une fois. Une clé existante affiche `Configurée`, jamais sa valeur.
- Le modèle est un select alimenté par `GeminiProbe`; fallback local si Google est indisponible, avec statut explicite.
- `Barge-in` est désactivé par défaut. Si aucune annulation d'écho n'est détectée, la contrainte reste affichée près du switch.
- La permission micro et la calibration nécessitent une action sur le panneau ; le Web suit `bruit ambiant`, `lecture du signal`, `terminé/échec`.
- Le test final vérifie clé, modèle Live, micro, wake word et sortie audio. Chaque erreur nomme l'étape et l'action corrective.
- Le prompt, le seuil, le wake word, l'inactivité et la limite quotidienne font partie du parcours avancé repliable, mais restent accessibles avant validation.
- Les faits mémorisés, actions planifiées et journaux d'outils sont des vues d'exploitation après activation, pas des réglages d'onboarding.

## Audit de tous les réglages persistés

| Domaine | Inclus dans l'onboarding Web | Conservé dans les réglages après activation |
|---|---|---|
| Langue | Oui, choisie sur le device puis affichée en lecture seule sur le Web | Oui |
| Canal/version/progression | Automatique, non exposé comme préférence | Gestion/reprise/reset uniquement |
| Grille | Échelle, ordre initial, visibilité, app au tap | Placement avancé, dossiers, widgets, raccourcis |
| Icônes | Pack d'icônes, pastilles de notification | Oui |
| Fond | Mode, image, opacité, Immich complet | Oui, remplacement/suppression compris |
| Horloge | Police, graisse, taille, tracking, teinte, format heure/date, espacements | Oui |
| Home Assistant | URL, token, application compagnon | Oui, reconnexion et rotation du token |
| Intégrations HA | Domaines activés/désactivés | Oui |
| Entités/Pills | Disponibilité, règles, pins, sections, regroupement, groupes manuels | Oui |
| Caméras | Visibilité, ordre, principale, mode, pill générale | Oui |
| MQTT | Hôte, port, credentials, nom device | Oui |
| Gemini | Activation, clé, modèle, voix, prompt, barge-in, wake word, seuil, calibration, limites | Oui ; journaux/faits/actions planifiées restent hors onboarding |
| Appareil | mode alimentation, veille, retour auto, permissions nécessaires | Oui |
| Sessions d'apps | Non par défaut ; seulement si la fonction est activée dans une version qui l'explique | Allowlist et classifications dans les réglages |
| Import/export layout | Action de migration optionnelle au jumelage | Oui |
| Debug, mises à jour, reboot, `devKeepScreenOn` | Non | Réglages développeur/informations uniquement |

## Contrat temps réel navigateur → panneau

### Snapshot

```json
{
  "sessionId": "ob_01K4V8M6X2",
  "revision": 42,
  "stepId": "display",
  "owner": true,
  "device": {
    "name": "Panneau cuisine",
    "resolution": { "width": 1280, "height": 800 },
    "connected": true,
    "lastSeenAt": "2026-09-11T01:42:18Z"
  },
  "providers": { "homeAssistant": true, "mqtt": false, "gemini": false, "immich": true },
  "secrets": { "haTokenConfigured": true, "geminiKeyConfigured": false }
}
```

### Commande

```json
{
  "commandId": "cmd_01K4V8PC3F",
  "expectedRevision": 42,
  "stepId": "display",
  "mode": "preview",
  "patch": { "gridScale": 0.94 }
}
```

### Événements

- `snapshot.changed` : nouvelle révision validée ;
- `preview.applied` : valeur éphémère rendue sur le panneau et latence ;
- `device.action_required` : consentement Android à effectuer ;
- `device.action_result` : accordé, refusé ou indisponible ;
- `test.progress` : phase d'un test HA/MQTT/Gemini/Immich ;
- `catalog.changed` : apps, entités, intégrations ou caméras rechargées ;
- `session.replaced` : un autre navigateur a repris l'édition ;
- `device.disconnected` / `device.reconnected`.

SSE suffit pour les événements serveur → navigateur et garde une implémentation simple avec l'hôte HTTP actuel. Les commandes restent des `POST` idempotents avec `commandId`. Le polling à 2 s n'est qu'un fallback. Un WebSocket n'est justifié que si la mesure montre que la coalescence HTTP du slider ne tient pas la latence cible.

### Latence et cohérence attendues

- retour visuel local : moins de 16 ms ;
- envoi preview : au plus 15 Hz ;
- ACK du panneau sur LAN : cible p95 < 150 ms ;
- aucune écriture de préférence pendant un drag ;
- commit atomique par étape ;
- conflit de révision : HTTP 409 avec snapshot courant, jamais écrasement silencieux ;
- preview sans activité : expiration et restauration après 10 s ;
- déconnexion : formulaire encore lisible, mutations suspendues, bouton `Réessayer`, aucune fausse confirmation.

## États obligatoires par vue

Chaque table, catalogue et test doit fournir :

- squelette compact pendant le chargement ;
- état vide factuel et action (`Aucune caméra.`, `Actualiser`) ;
- état vide après filtre (`Aucun résultat pour ces filtres.`, `Réinitialiser`) ;
- erreur avec cause actionnable et `Réessayer` ;
- données mises en cache avec statut `Obsolètes` ;
- permission insuffisante avec `Ouvrir sur le panneau` ;
- device hors ligne sans perdre les valeurs déjà saisies ;
- lecture seule quand la session n'est pas propriétaire.

## Sécurité de jumelage minimale

1. Le QR contient un code à usage unique, pas le bearer token final.
2. Le navigateur échange ce code contre une session courte et le code devient invalide.
3. Le token n'est ni dans les query strings suivantes, ni dans les logs, ni dans le DOM après échange.
4. `GET /api/config` renvoie `haTokenConfigured`, `mqttPasswordConfigured`, `geminiKeyConfigured`, jamais les secrets.
5. Une seule session est éditrice. La reprise de contrôle est visible sur le device.
6. Reset et complétion exigent session propriétaire, révision courante et `POST`.
7. Toute session expire après inactivité et à la fermeture de l'activité hôte.

## Découpage de réalisation révisé

### Lot 1 — moteur et session

- `OnboardingCoordinator` partagé, snapshot, commandes, révisions et brouillons ;
- reprise d'étape serveur ;
- session éditrice unique ;
- secrets redacted ;
- suppression de la dépendance MQTT → HA.

### Lot 2 — shell utilitaire et preview

- CSS local compilé ;
- shell trois colonnes desktop / une colonne mobile ;
- aperçu device persistant ;
- commandes `preview`, `commit`, `revert` ;
- SSE, état de connexion et latence.

### Lot 3 — configuration du launcher

- accès système ;
- grille live ;
- fonds système/custom/Immich et opacité ;
- horloge ;
- apps, pack, visibilité, ordre et gestes.

### Lot 4 — providers et Maison

- sélection indépendante des providers ;
- HA connexion, intégrations, entités, pins/groupes et caméras ;
- MQTT complet ;
- Gemini complet, clairement facultatif, permissions et calibration.

### Lot 5 — finalisation et retrait progressif de Compose

- vérification et commit global ;
- scénarios de reprise/conflit/perte réseau ;
- instrumentation locale sans secret ;
- Compose branché au coordinator ;
- suppression progressive des écrans Compose devenus redondants après parité vérifiée.

## Critères d'acceptation

- Modifier le slider d'icônes change le navigateur en moins d'une frame et le panneau en p95 < 150 ms sur LAN.
- Le fond et l'opacité montrent la même composition dans le navigateur et sur le panneau ; toute différence de capacité est nommée.
- Un refresh reprend la bonne étape et les valeurs validées.
- Deux onglets ne peuvent pas écraser leurs réglages silencieusement.
- MQTT reste configurable quand Home Assistant est ignoré.
- Gemini peut être ignoré avant saisie et après échec de test ; Portal reste activable.
- Une connexion HA réussie mène aux intégrations, entités, groupes et caméras disponibles, pas au résumé final.
- Aucun secret stocké n'est renvoyé par une route GET.
- Le parcours reste entièrement utilisable sans Internet, hors fonctions qui en dépendent explicitement (Gemini/Immich distant).
- Aucun `gradient`, `shadow-md+`, rayon > 6 px, emoji ou titre marketing dans le client Web.
- Tous les boutons iconiques ont `aria-label` et tooltip ; toute cible fait au moins 40 px desktop ou 44 px touch.
- Chargement, vide, filtre vide, erreur, stale, permission et hors-ligne sont testés.

## Options considérées puis rejetées

| Option | Motif du rejet |
|---|---|
| Une seule longue page de réglages | Impossible de distinguer les dépendances, validations et actions Android ; reprise et progression trop fragiles. |
| Prévisualisation uniquement simulée dans le navigateur | Ne révèle pas les différences réelles de DPI, ratio, pack d'icônes, wallpaper et performances du panneau. |
| Une grande card par provider | Faible densité et comparaison lente ; une ligne typée avec icône officielle, état et switch est plus adaptée. |
| WebSocket obligatoire dès le premier lot | SSE + POST coalescé couvre le besoin avec moins de surface technique ; décision à réévaluer sur mesure de latence. |
| Suppression immédiate de tout l'onboarding Compose | Risque de perdre les actions système et le fallback avant que le coordinator partagé et la parité Web soient éprouvés. |

## Vérification de cet audit

- Inspection de `OnboardingStep`, `OnboardingState`, `OnboardingScreen`, `OnboardingViewModel` et des écrans Compose : 16 étapes device et paramètres réellement disponibles identifiés.
- Inspection de `config.html`, `config.js`, `webconfig.css` et `WebConfigServer` : 5 étapes Web, sauvegarde au changement d'étape, absence de preview/event/revision et secrets relus confirmés.
- Inspection de `Prefs`, `HomePillPreferences`, `CameraPreferences`, `HomeSettingsReducer`, `PillsSettingsPage`, `HaIntegrationsSettingsPage` et `VoiceAssistantSettingsPage` : inventaire des réglages et des branches avancées confirmé.
- Inspection de `git diff` : l'onboarding Web et Gemini sont déjà modifiés dans le worktree ; aucune de ces modifications applicatives n'a été écrasée par cet audit.
- **Non vérifié :** parcours visuel navigateur, responsive, clavier, lecteur d'écran, contraste, device physique et animations à 10 % ; aucune surface navigateur/device n'était disponible dans la session.

**Verdict : `Block`.** Les constats HIGH sur la parité fonctionnelle, la preview réelle, la reprise concurrente, la dépendance CDN et la réexposition des secrets empêchent de considérer le Web actuel comme onboarding de référence.
