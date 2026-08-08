# Pills personnalisables et page Maison — Spécification technique

**Statut :** contrat fonctionnel et technique prêt pour planification

**Date :** 2026-08-08

**Portée :** composition dynamique du plateau de pills, épinglage, page Maison, groupes et réglages

**Ordre du document :** logique métier d'abord, interface ensuite

## 1. Objectif

Faire évoluer le plateau actuel, limité à neuf pills classées automatiquement, vers un système qui :

- conserve une sélection dynamique des états domestiques les plus pertinents ;
- permet d'épingler, ordonner et retrouver des pills sans supprimer la priorité des alertes ;
- expose tous les appareils pilotables dans une page Maison optionnelle ;
- affiche par défaut une pill par appareil et permet des regroupements automatiques ou manuels ;
- réutilise tous les panels de contrôle existants et le langage visuel de `PlaygroundScreen`.

Le résultat doit rester un lanceur calme : l'accueil continue de montrer au maximum trois pills au repos et neuf après développement. La page Maison absorbe la découverte et l'organisation avancées sans transformer l'accueil en dashboard dense.

## 2. État actuel et delta

### 2.1 Existant à préserver

- `PillSupport` découvre les entités Home Assistant compatibles et les transforme en `PillCandidate`/`PillRule`.
- `PillPriorityEngine.select` fabrique les `LauncherChip`, les classe par priorité décroissante puis applique `take(9)`.
- Plusieurs familles sont agrégées globalement avant classement : ouvertures, températures, lumières, médias, purificateurs, air, scènes, présence et énergie.
- `ClockTray` affiche trois pills replié et neuf développé, en lignes de trois.
- Un toucher route vers le service direct ou le panel via `ChipMapper` ; le panel ouvert est géré par `PanelState`.
- L'appui long ouvre actuellement directement le panel, sauf pour Média.
- `Prefs.pillRules` conserve l'activation des entités, mais aucun ordre, pin ou groupe manuel.
- `LauncherPager` représente actuellement `[Accueil, Applications…]`, avec `PAGE_CLOCK = 0`.
- `PillsSettingsPage` permet d'activer ou désactiver les entités candidates.

### 2.2 Delta demandé

- Ne plus confondre « catalogue de pills disponibles », « sélection dynamique » et « neuf positions visibles ».
- Ajouter une préférence d'épinglage persistante et un ordre persistant.
- Ajouter un catalogue individuel par appareil en plus des agrégats existants.
- Ajouter des groupes automatiques par pièce et type, ainsi que des groupes manuels.
- Ajouter une page Maison avant l'accueil dans le pager.
- Remplacer l'action d'appui long des pills par un menu contextuel donnant encore accès au panel.
- Étendre les réglages pour organiser des pills absentes du plateau courant.

# Partie I — Logique métier

## 3. Vocabulaire et identités stables

### 3.1 Définitions

- **Appareil pilotable** : entité ou appareil Home Assistant disponible pour lequel Portal Launcher sait construire un `PanelKind` opérationnel.
- **Pill individuelle** : pill représentant un seul appareil pilotable.
- **Pill de groupe** : pill représentant plusieurs appareils et ouvrant un navigateur de groupe avant les sous-panels.
- **Catalogue** : ensemble non tronqué de toutes les pills individuelles et de groupe actuellement résolubles.
- **Pill dynamique** : pill non épinglée, admise sur l'accueil uniquement si son score de pertinence courant le justifie.
- **Pill épinglée** : choix utilisateur persistant ; elle reste candidate même si son état est calme, tant qu'elle est disponible.
- **Alerte critique** : pill dont l'état requiert de dépasser temporairement l'ordre utilisateur.
- **Plateau principal** : les trois pills toujours visibles au repos.
- **Plateau secondaire** : les six pills supplémentaires visibles après « Voir plus ».
- **Favoris Maison** : toutes les pills épinglées disponibles, y compris celles qui débordent des neuf positions d'accueil.

### 3.2 Identifiants

Toute référence persistée utilise une clé typée stable, jamais le libellé localisé :

```kotlin
sealed interface PillRef {
    data class Device(val entityId: String) : PillRef
    data class AreaGroup(val areaId: String) : PillRef
    data class KindGroup(val kind: PillKind) : PillRef
    data class ManualGroup(val groupId: String) : PillRef
}
```

Les clés sérialisées sont versionnées, par exemple `device:light.salon`, `area:kitchen`, `kind:LIGHTS`, `manual:<uuid>`. Les noms, icônes et états sont résolus à chaque snapshot.

## 4. Modèle de données cible

Le modèle persistant est séparé du modèle UI live.

```kotlin
data class HomePillPreferences(
    val schemaVersion: Int,
    val homePageEnabled: Boolean,
    val pinnedOrder: List<PillRef>,
    val homeSections: List<HomeSectionPreference>,
    val manualGroups: List<ManualPillGroup>,
)

data class HomeSectionPreference(
    val sectionId: String,
    val visible: Boolean,
    val order: Int,
    val itemOrder: List<PillRef>,
)

data class ManualPillGroup(
    val id: String,
    val name: String,
    val icon: String?,
    val members: List<PillRef.Device>,
)
```

Le snapshot live expose au minimum :

```kotlin
data class PillCatalogSnapshot(
    val devices: Map<PillRef.Device, LauncherChip>,
    val groups: Map<PillRef, PillGroupSnapshot>,
    val availability: Map<PillRef, Availability>,
    val dynamicCandidates: List<ScoredPill>,
)

data class HomeComposition(
    val primary: List<ResolvedPill>,
    val secondary: List<ResolvedPill>,
    val favoriteOverflow: List<ResolvedPill>,
)
```

`PillPriorityEngine` ne doit plus tronquer le catalogue. Le `take(9)` appartient au nouveau compositeur d'accueil, après résolution des pins, alertes et indisponibilités.

## 5. Éligibilité et disponibilité

### 5.1 Éligibilité

Une pill peut entrer dans le catalogue si et seulement si :

1. son entité est admise par les règles de support ;
2. Portal Launcher sait router son toucher vers un panel ou une commande existante ;
3. pour un groupe, au moins un membre pilotable est résolu ;
4. la référence possède une identité stable.

Les entités purement informatives sans panel compatible ne deviennent pas épinglables dans cette phase. Les entités inconnues ne reçoivent pas automatiquement un panel générique.

### 5.2 Disponibilité

- Une pill `unavailable`, `unknown`, absente du snapshot HA ou dont aucun membre n'est résolu est indisponible.
- Une pill indisponible ne peut pas être épinglée depuis les réglages ou un menu.
- Si une pill déjà épinglée devient indisponible, son pin reste persisté mais la pill disparaît temporairement de l'accueil et de la section Favoris rendue.
- La première favorite disponible en overflow remonte automatiquement ; à défaut, le moteur dynamique remplit la place.
- Au retour du device, la pill restaurée récupère automatiquement sa position logique dans `pinnedOrder`; la remplaçante retourne en overflow ou au classement dynamique.
- Un groupe reste disponible tant qu'au moins un membre compatible est disponible. Son panel indique implicitement la liste résolue ; les membres indisponibles ne sont pas actionnables.

## 6. Composition des neuf positions d'accueil

### 6.1 Capacité

- Capacité nominale : 9 pills.
- Positions 0 à 2 : plateau principal, rendu en permanence.
- Positions 3 à 8 : plateau secondaire, rendu uniquement lorsque le plateau est développé.
- La capacité visuelle exceptionnelle peut dépasser temporairement trois pills principales pour une alerte si la largeur et les contraintes de fenêtre permettent un rendu lisible.
- Si la fenêtre ne permet pas une pill supplémentaire, l'alerte remplace temporairement la pill non critique de plus faible précédence visuelle.

La capacité responsive est décidée par une fonction pure recevant la largeur disponible, l'échelle d'affichage et les largeurs mesurées/estimées des pills. Elle ne doit jamais réduire une cible tactile sous les minimums du projet.

### 6.2 Ordre des sources

Le compositeur applique cette précédence :

1. alertes critiques disponibles ;
2. pills épinglées disponibles, dans `pinnedOrder` ;
3. pills dynamiques pertinentes, par score décroissant puis clé stable ;
4. aucune pill de remplissage calme non épinglée.

Une même `PillRef` ne peut apparaître qu'une fois dans le plateau, même si elle est à la fois critique, épinglée et dynamique.

### 6.3 Pins

- Toute pill individuelle, groupe de pièce, groupe de type ou groupe manuel peut être épinglé.
- L'utilisateur peut épingler sans limite stricte depuis Maison ou les réglages.
- Les neuf premières pills épinglées disponibles alimentent l'accueil ; les suivantes restent dans Favoris Maison.
- Quand une place se libère, la première favorite disponible en overflow remonte automatiquement.
- Le premier épinglage insère automatiquement la pill à la fin des pins visibles, puis de l'overflow.
- L'utilisateur peut ensuite réordonner les pins par glisser-déposer.
- Déplacer un pin entre les trois premières et les six suivantes change explicitement son niveau visuel.
- Désépingler supprime la référence de `pinnedOrder` immédiatement. La pill peut rester visible uniquement si son score dynamique le justifie.

### 6.4 Sélection dynamique

Les places non occupées par une alerte ou un pin sont alimentées par le classement dynamique existant, étendu :

- sécurité et danger ;
- accès anormal, serrure déverrouillée, ouverture active ;
- appareil en cours, terminé récemment ou nécessitant une action ;
- média actif, minuteur actif, batterie faible et autres états pertinents existants ;
- ordre alphabétique stable seulement comme dernier départage.

Une pill dynamique calme ne doit pas être affichée uniquement pour remplir un emplacement. Une pill épinglée, elle, reste visible dans un état calme.

Le score doit rester calculé dans une fonction pure et testable. Le système conserve les règles de récence existantes pour les événements transitoires et évite les permutations visuelles à chaque push HA sans changement de priorité réel.

Le comparateur dynamique est déterministe : score décroissant, libellé normalisé, puis clé stable. Un avertissement non critique ne déplace jamais une favorite ; il remplit seulement une place libre. La projection finale est émise atomiquement afin qu'aucune frame intermédiaire ne montre un doublon ou un ordre partiel.

### 6.5 Alertes critiques

Les alertes critiques incluent au minimum les états déjà reconnus comme critiques par le domaine sécurité/alarme. Leur liste exacte doit être centralisée dans une politique pure, sans comparaison de libellés localisés.

Règles :

- une alerte disponible est toujours présentée devant les pins et les candidates dynamiques ;
- sur grand écran, elle peut s'ajouter temporairement aux trois principales si toutes restent lisibles ;
- sinon elle remplace visuellement la dernière pill principale non critique, sans modifier `pinnedOrder` ;
- quand l'alerte disparaît, la composition nominale est restaurée automatiquement ;
- plusieurs alertes sont triées par sévérité, récence, puis clé stable ;
- la présence de l'alerte sur le plateau ne modifie pas la précédence `PanelSource.ALERT` existante.

Chaque pill résolue expose les ids des entités à l'origine de son état critique. Si un même incident remonte à la fois dans une pill individuelle et plusieurs agrégats, il n'est montré qu'une fois sur l'accueil ; la représentation la plus spécifique gagne. Maison peut continuer à exposer les autres chemins de navigation.

## 7. Catalogue individuel et agrégats

Le nouveau catalogue produit simultanément :

- une pill par appareil compatible ;
- les groupes automatiques par pièce ;
- les groupes automatiques par type ;
- les groupes manuels ;
- les agrégats historiques nécessaires à la compatibilité, tant qu'ils sont représentés comme des groupes typés et non comme des ids spéciaux dispersés.

Un appareil peut apparaître simultanément :

- comme pill individuelle ;
- dans sa pièce ;
- dans son groupe de type ;
- dans zéro, un ou plusieurs groupes manuels ;
- dans Favoris s'il est épinglé.

Cette duplication est volontaire. L'identité du device reste unique et les états sont dérivés du même snapshot HA.

## 8. Groupes

### 8.1 Groupes automatiques par pièce

- Source : métadonnées `areaId` exposées par Home Assistant et déjà accessibles via `areaByEntity`/`LocalAreas`.
- L'identité persistée est obligatoirement le `area_id`, jamais le nom traduit ou renommable. Le repository doit exposer séparément `areaIdByEntity` et `areaNameById` ; l'aire de l'entité est utilisée en priorité, puis celle de son device.
- Un groupe est créé pour chaque pièce ayant au moins un appareil compatible.
- Son appartenance n'est pas modifiable dans Portal Launcher.
- Changer une entité de pièce nécessite une modification dans Home Assistant ; Portal Launcher reflète ensuite le changement.
- L'utilisateur peut masquer le groupe, le réordonner et réordonner localement ses membres sans modifier Home Assistant.

### 8.2 Groupes automatiques par type

- Source : `PillKind` ou une famille de domaine stable, jamais le nom affiché.
- Un groupe est créé pour chaque type ayant au moins un appareil compatible.
- Son appartenance est recalculée automatiquement lorsque les capacités HA changent.
- L'utilisateur peut masquer et réordonner le groupe et ses membres.

### 8.3 Groupes manuels

- Créés localement avec un nom obligatoire et un identifiant immuable.
- Peuvent contenir des appareils de pièces et de types différents.
- Les membres peuvent être ajoutés, retirés et réordonnés localement.
- Supprimer un groupe manuel ne supprime ni ne désactive ses appareils.
- Un groupe manuel vide n'est pas rendu sur Maison ; il reste éditable dans les réglages ou est supprimé après confirmation.

### 8.4 Résumé et commande collective

La pill de groupe expose un résumé déterministe : nombre d'éléments actifs ou état le plus important, selon le type. Toucher la pill ouvre un panel navigateur similaire au flux Média :

1. en-tête et résumé du groupe ;
2. commande collective si une intersection sûre de capacités existe ;
3. liste des appareils disponibles ;
4. toucher un appareil remplace le contenu par son sous-panel ;
5. Retour revient à la liste du groupe ; Fermer ferme toute la pile.

Une commande collective n'est affichée que si elle est définie explicitement pour le groupe. Aucun service HA n'est deviné à partir d'un libellé. Pour les groupes hétérogènes, seules les actions compatibles avec tous les membres ciblés sont permises ; sinon aucune commande collective n'est montrée.

## 9. Persistance, migration et synchronisation

- Les nouvelles préférences sont enregistrées dans `Prefs` via un codec JSON versionné, sur le modèle de `PillRuleCodec`.
- Les écritures sont atomiques au niveau `SharedPreferences.edit()` et dédupliquées pour éviter les refresh inutiles.
- La première migration conserve les `pillRules` existantes et crée : page Maison activée, aucun pin explicite, sections automatiques visibles dans l'ordre par défaut, aucun groupe manuel.
- Une référence devenue inconnue reste persistée pendant une période de grâce ou jusqu'à suppression explicite, afin de survivre à une indisponibilité HA ; elle n'est pas rendue.
- Les ids de pièce/type/manual sont sérialisés indépendamment de la langue.
- Toute modification depuis l'accueil, Maison ou les réglages publie via le mécanisme de changement existant afin que l'UI active se recompose sans redémarrage.

## 10. Flux et invariants

### 10.1 Pipeline cible

```text
HA snapshots + device/area metadata + pillRules
    -> catalogue complet individuel et groupé
    -> résolution de disponibilité
    -> scoring dynamique

Prefs pins + ordre
    + catalogue + scoring + politique d'alerte + capacité responsive
    -> HomeComposition(primary, secondary, overflow)

Prefs sections + catalogue
    -> HomePageModel(sections ordonnées, items ordonnés)
```

### 10.2 Invariants obligatoires

- Une référence n'apparaît jamais deux fois dans les neuf positions.
- Une alerte ne modifie jamais l'ordre persistant des pins.
- Une indisponibilité ne supprime jamais silencieusement un pin.
- Une restauration remet la pill à son rang logique sans action utilisateur.
- Le catalogue n'est jamais tronqué à neuf.
- Une entité sans panel compatible n'est jamais proposée à l'épinglage.
- Les groupes automatiques ne modifient jamais Home Assistant.
- L'édition d'un groupe manuel ne modifie jamais les groupes automatiques.
- Le rendu UI ne contient aucune logique de priorité métier.
- Les libellés localisés ne servent jamais d'identifiants ou de règles de routage.

# Partie II — Interface utilisateur

## 11. Plateau de l'accueil

### 11.1 Affichage

- État replié : les trois premières pills de `HomeComposition.primary`.
- État développé : principales puis secondaires, neuf au maximum hors ajout critique responsive.
- Le contrôle « Voir plus » est affiché uniquement lorsqu'au moins une pill secondaire existe.
- Les pills conservent `StatusChip`, leurs états, icônes, valeurs et style sélectionné.
- Une indication discrète de pin est autorisée dans le menu et le mode édition ; elle ne doit pas surcharger la pill au repos.

### 11.2 Toucher et appui long

- Toucher simple : comportement actuel conservé (`OpenPanel` ou commande directe selon `ChipAction`).
- Appui long : ouvre désormais un menu contextuel au lieu d'ouvrir directement le panel.
- Menu minimal sur l'accueil :
  - Épingler ou Désépingler ;
  - Déplacer / Réorganiser ;
  - Ouvrir les commandes.
- « Ouvrir les commandes » route vers le panel actuel et remplace donc l'ancien raccourci d'appui long.
- Le menu doit préserver les nœuds sémantiques de chaque action et se fermer sur Back ou toucher extérieur.

### 11.3 Réorganisation

- Accessible depuis « Déplacer » et depuis les réglages.
- Drag & drop entre les trois principales, les six secondaires et l'overflow Favoris.
- Les emplacements dynamiques ne sont pas des objets persistants : déposer un pin à un index insère sa référence dans `pinnedOrder`.
- Une annulation laisse l'ordre inchangé ; un drop valide persiste immédiatement.
- Le pager horizontal est désactivé pendant un drag, comme pour les icônes d'applications.

## 12. Navigation Maison

### 12.1 Structure du pager

Lorsque Maison est activée :

```text
[ Maison ] <- [ Accueil principal ] -> [ Application 1 ] -> [ Application 2 ] ...
```

L'accueil principal devient la page initiale, même si son index interne n'est plus zéro. Les constantes et helpers doivent distinguer explicitement `PAGE_HOME`, `PAGE_CLOCK` et `PAGE_FIRST_APP` au lieu de déduire qu'une page antérieure à Applications est nécessairement l'horloge.

La navigation doit être modélisée par identité logique (`House`, `Clock`, `Apps(index)`) et seulement ensuite convertie en index physique. Quand Maison est activée, les index nominaux sont `House = 0`, `Clock = 1`, `Apps = 2+n`; quand elle est désactivée, ils redeviennent `Clock = 0`, `Apps = 1+n`. Une activation ou désactivation à chaud remappe la page courante par identité afin de ne jamais transformer silencieusement une page Application en une autre.

Le calcul de repli de l'en-tête devient relatif à l'index de l'accueil, et non à zéro. Maison possède son propre en-tête : le grand en-tête Horloge ne doit pas rester superposé au-dessus d'elle. Le parallaxe, le scrim, les indicateurs, le drag inter-pages et les helpers `appPageOf` sont recalculés depuis ce modèle logique.

Lorsque Maison est désactivée, le pager se comporte comme aujourd'hui et aucun emplacement vide n'est conservé.

### 12.2 Icône Maison

- Une petite icône Maison est placée en bas de l'accueil et ouvre la page Maison.
- Elle n'est visible que si la page est activée.
- Elle dispose d'une cible tactile accessible même si le glyphe est visuellement petit.
- Son libellé d'accessibilité annonce « Ouvrir Maison ».
- Le swipe vers la gauche et l'icône produisent la même navigation.
- HOME, Back depuis une page, extinction/réveil et auto-retour ramènent toujours à l'accueil principal, pas à Maison.

## 13. Page Maison

### 13.1 Composition visuelle

La page reprend le motif de `HaCapabilityLab` dans `PlaygroundScreen` :

- titre de page « Maison » ;
- sections verticales ordonnées ;
- titre par section ;
- texte secondaire seulement s'il apporte une information utile ;
- une ou deux rangées de `StatusChip` ;
- défilement horizontal indépendant par section ;
- espacement et tokens Apple existants ;
- fond cohérent avec le launcher et scrim suffisant sur photo.

Exemple nominal :

```text
Maison

Favoris
[Serrure] [Salon] [Machine] [Soirée] ->

Pièces
[Salon] [Cuisine] [Chambre] [Bureau] ->

Lumières
[Lampe salon] [Suspension] [Chevet] ->

Volets
[Salon] [Chambre] ->

Mes groupes
[Soirée] [Départ maison] ->
```

### 13.2 Sections par défaut

- Favoris, si au moins un pin disponible existe.
- Pièces automatiques.
- Types automatiques.
- Groupes manuels, si au moins un groupe rendu existe.
- Les deux familles automatiques, pièces et types, sont visibles par défaut.
- L'utilisateur peut masquer, afficher et réordonner toute section.
- Un même appareil peut être rendu dans plusieurs sections.

### 13.3 Une ou deux rangées

- Le nombre de rangées est automatique selon la largeur disponible, le nombre de pills, la hauteur de fenêtre, l'échelle de police et la densité.
- Une petite section reste sur une rangée.
- Une section dense peut utiliser deux rangées avant de continuer horizontalement.
- Le parcours horizontal doit conserver un ordre déterministe en lecture : remplissage défini et testé, sans changement aléatoire après recomposition.
- Une section ne dépasse jamais deux rangées.
- Chaque section conserve son propre état de scroll pendant la session, sous une clé stable.

### 13.4 Interaction

- Toucher une pill individuelle ouvre son panel existant.
- Toucher une pill de groupe ouvre le navigateur de groupe puis les sous-panels.
- Appui long ouvre un menu proposant toutes les actions applicables :
  - Épingler/Désépingler sur l'accueil ;
  - Ajouter à un groupe manuel ;
  - Réorganiser ;
  - Ouvrir le panel.
- Aucun champ de recherche n'est fourni.
- Aucun déplacement entre pièces/types automatiques n'est fourni.

## 14. Mode Modifier de Maison

Le mode est accessible par :

- bouton « Modifier » dans l'en-tête ;
- appui long sur une pill ou un groupe.

Il permet :

- réordonner les sections ;
- masquer/afficher les sections ;
- réordonner les pills au sein d'une section ;
- créer, renommer, réordonner et supprimer un groupe manuel ;
- ajouter/retirer des membres d'un groupe manuel ;
- gérer les pins et leur ordre.

Il ne permet pas :

- déplacer un appareil vers une autre pièce HA ;
- changer son type HA ;
- rendre compatible une entité sans panel ;
- modifier les membres calculés d'un groupe automatique.

Le déplacement d'une pill automatique reste limité à sa ligne. Pour modifier son appartenance, l'UI explique que cela se gère dans Home Assistant.

Une alternative accessible au drag est obligatoire : déplacer avant/après, placer en premier/dernier et, pour les groupes manuels, déplacer vers un autre groupe. L'ordre TalkBack suit exactement l'ordre visuel, y compris dans les rails à deux rangées.

## 15. Panel navigateur de groupe

Le navigateur généralise le flux actuel du panel Média sans dupliquer un état local ad hoc par domaine.

### 15.1 Niveau groupe

- bouton Fermer ;
- icône, nom et résumé du groupe ;
- commande collective conditionnelle ;
- liste de pills/appareils disponibles ;
- état vide si tous les membres deviennent indisponibles pendant l'ouverture.

### 15.2 Niveau appareil

- sous-panel de contrôle existant ;
- Retour revient au niveau groupe sans fermer le side panel ;
- Fermer ferme toute la navigation ;
- si l'appareil devient indisponible, afficher le traitement standard « Appareil indisponible » puis permettre le retour au groupe.

### 15.3 Routage

`PanelRequest` doit pouvoir représenter une pile ou une destination groupée stable. Le routage de panel reste typé ; aucun `when` fondé sur un id de string supplémentaire ne doit être introduit dans `LauncherActivity`.

## 16. Réglages

La page Pills existante est étendue ou divisée en sous-sections cohérentes :

1. **Accueil**
   - aperçu des trois positions principales et six secondaires ;
   - liste ordonnée des pins et overflow ;
   - drag & drop ;
   - slots restants indiqués « Automatique ».
2. **Page Maison**
   - toggle d'activation ;
   - visibilité et ordre des sections ;
   - gestion des groupes manuels.
3. **Appareils disponibles**
   - liste des appareils compatibles et disponibles ;
   - activation actuelle conservée ;
   - action Épingler/Désépingler même si la pill n'est pas dans le top neuf courant.

Contraintes :

- un appareil indisponible n'offre pas l'action Épingler ;
- un appareil sans panel compatible n'apparaît pas comme cible d'épinglage ;
- la liste peut être regroupée par famille existante, mais aucun champ de recherche n'est ajouté ;
- toute modification est reflétée immédiatement sur le launcher via `SettingsChangeBus` ou son successeur.

## 17. Responsive, accessibilité et mouvement

- Support Android 9+ et tailles déjà prises en charge par le projet.
- Aucune réduction de la taille tactile sous 48 dp.
- Les pills restent lisibles avec police agrandie ; le nombre de rangées et la capacité critique s'adaptent avant de compresser le texte.
- D-pad/clavier : ordre focus vertical entre sections, horizontal dans une section, actions du menu atteignables.
- TalkBack annonce nom, état, caractère épinglé si utile, appartenance à un groupe et action disponible.
- L'état critique n'est jamais communiqué uniquement par couleur.
- Les animations de réorganisation, ouverture de panel et navigation respectent reduced motion.
- Les `LazyRow`/grilles paresseuses sont préférées aux `Row.horizontalScroll` pour les catalogues potentiellement longs.
- Le pager parent ne doit pas voler un geste horizontal déjà engagé dans une ligne Maison ; la stratégie d'arbitrage gestuel doit être testée sur bord de ligne.

Arbitrage attendu : un geste commencé sur une pill ou dans un rail défile d'abord le rail ; un geste commencé sur l'en-tête, un titre, l'espace entre sections ou la marge latérale navigue dans le pager. Au bord droit d'un rail, l'excédent peut être transmis au pager après le touch slop. Une zone de bord de 24 dp garantit le retour Maison -> Accueil. Pendant une réorganisation, pager et scroll vertical sont verrouillés.

## 18. États vides et erreurs

- Maison activée sans appareils compatibles : titre, explication courte et accès aux réglages Home Assistant ; aucune ligne vide répétée.
- Aucune favorite : section Favoris masquée.
- Pièce/type vide après une mise à jour : section correspondante supprimée du rendu, préférence d'ordre conservée.
- Déconnexion HA avec dernier snapshot : conserver l'état stale selon la politique existante, interdire les nouvelles actions risquées et ne pas effacer les pins.
- Une déconnexion globale HA n'est pas équivalente à un device `unavailable` : les dernières pills restent visibles comme données figées/stale et ne déclenchent pas une promotion massive de l'overflow.
- Échec de décodage des préférences : fallback sûr vers les valeurs migrées, journalisation, aucune suppression immédiate du JSON source.
- Groupe manuel dont tous les membres sont indisponibles : masqué dans le rendu normal, conservé dans les réglages.

## 19. Architecture et points d'intégration recommandés

Les noms définitifs restent à confirmer pendant le plan, mais les responsabilités sont verrouillées :

- `PillSupport` : découverte et compatibilité, sans décision d'affichage.
- `PillCatalogBuilder` nouveau : pills individuelles, groupes et disponibilité.
- `PillPriorityEngine` : score dynamique non tronqué et politique de pertinence.
- `HomePillComposer` nouveau et pur : pins + alertes + capacité -> 3/6/overflow.
- `HomePageBuilder` nouveau et pur : sections et ordre de Maison.
- `Prefs` + codec versionné : persistance.
- `LauncherViewModel` : combine les flows et expose des modèles UI immuables.
- `LauncherActivity` : collecte et délègue, sans logique de composition.
- `ClockTray` : rendu 3/6 et entrée menu/drag.
- `HomePage` nouveau : sections titrées et lignes 1–2 rangées.
- `GroupBrowserPanel` nouveau : liste et sous-panels.
- `PillsSettingsPage` : édition complète.
- `LauncherPager` : page Maison optionnelle à gauche et index d'accueil explicite.

La spec interdit d'ajouter une seconde source de vérité Compose locale pour les pins, groupes ou navigation de groupe. Les données persistantes vivent dans le store ; les snapshots live et compositions vivent dans le ViewModel/domaine.

## 20. Stratégie de tests

### 20.1 Tests unitaires obligatoires

`HomePillComposerTest` couvre au minimum :

- zéro pin, trois pins, neuf pins, plus de neuf pins ;
- mélange pins/dynamiques sans doublon ;
- pin calme visible ;
- dynamique calme absente ;
- alerte avec place responsive et sans place ;
- alerte disparue restaurant exactement l'ordre antérieur ;
- pin indisponible remplacé puis restauré ;
- overflow promu après désépinglage ou indisponibilité ;
- départage stable entre scores égaux.

`PillCatalogBuilderTest` couvre :

- une pill par appareil compatible ;
- exclusion sans panel ;
- groupes par pièce/type ;
- device dans plusieurs groupes ;
- groupes manuels hétérogènes ;
- groupe partiellement et totalement indisponible.

Les codecs couvrent : round-trip, migration depuis absence de préférence, ids inconnus, JSON corrompu et conservation de l'ordre.

### 20.2 Tests de reducer/navigation

- ouverture groupe -> appareil -> retour groupe -> fermeture ;
- priorité `ALERT` conservée ;
- disparition d'une pill n'arrache pas un panel déjà ouvert sans règle explicite ;
- activation/désactivation de Maison recalcule les index sans page invalide ;
- HOME et auto-retour ciblent toujours l'accueil.

### 20.3 Tests Compose/UI

- trois pills repliées, jusqu'à neuf développées ;
- menu long-press complet et sémantique ;
- page Maison absente lorsque désactivée ;
- sections dans l'ordre configuré ;
- bascule automatique une/deux rangées ;
- scroll horizontal indépendant et pager parent ;
- drag & drop avec persistance ;
- grandes polices, TalkBack, D-pad ;
- compact portrait, tablette paysage et grand panel mural.

### 20.4 Tests de non-régression

- toucher `SWITCH`/`FAN` conserve son comportement direct ;
- tous les autres panels existants restent accessibles ;
- panel Média auto-ouvert et `PanelSource` inchangés ;
- présence flottante et traitement Énergie explicitement revus lors de la migration des groupes ;
- auto-return, Back, HOME et grille d'applications restent fonctionnels.

## 21. Critères d'acceptation pass/fail

- [ ] L'accueil affiche au repos au plus trois pills, choisies par alertes, pins puis pertinence dynamique.
- [ ] « Voir plus » affiche au plus six pills supplémentaires et disparaît s'il n'y en a aucune.
- [ ] L'utilisateur peut épingler et désépingler une pill depuis l'accueil, Maison et les réglages.
- [ ] L'utilisateur peut épingler un device, une pièce, un type ou un groupe manuel.
- [ ] Les neuf premiers pins disponibles remplissent l'accueil ; les suivants restent dans Favoris Maison.
- [ ] Une place libérée promeut automatiquement la première favorite overflow.
- [ ] Un pin indisponible disparaît sans être oublié, puis revient automatiquement à son rang.
- [ ] Une alerte critique dépasse visuellement les pins sans modifier leur ordre persistant.
- [ ] Le catalogue Maison contient une pill individuelle pour chaque appareil compatible et disponible.
- [ ] Les sections automatiques par pièce et type sont visibles par défaut.
- [ ] Un appareil peut apparaître individuellement et dans plusieurs groupes.
- [ ] Les groupes manuels sont créables, renommables, ordonnables et supprimables.
- [ ] Une commande collective apparaît seulement lorsqu'une action sûre est définie.
- [ ] Un groupe ouvre un navigateur puis les sous-panels existants.
- [ ] Maison est située à gauche de l'accueil et accessible par swipe ou icône.
- [ ] Désactiver Maison supprime la page et masque l'icône sans casser les index du pager.
- [ ] Chaque section utilise automatiquement une ou deux rangées et reste horizontalement scrollable.
- [ ] L'appui long ouvre le menu complet ; « Ouvrir les commandes » remplace l'ancien accès long-press.
- [ ] Les réorganisations persistent après redémarrage.
- [ ] Portal Launcher ne modifie jamais l'appartenance Home Assistant aux pièces ou types.
- [ ] Les appareils indisponibles ou sans panel ne peuvent pas être nouvellement épinglés.
- [ ] Tous les tests de non-régression des panels, médias, Back, HOME et auto-return passent.

## 22. Hors périmètre

- Modifier les pièces, types, appareils ou registres dans Home Assistant.
- Ajouter une recherche dans Maison ou dans la liste demandée par cette fonctionnalité.
- Créer automatiquement un panel générique pour une entité actuellement non compatible.
- Synchroniser la personnalisation entre plusieurs installations Portal Launcher.
- Ajouter de nouveaux domaines de devices ou de nouveaux contrôles métier non nécessaires au système de pills/groupes.
- Reconcevoir visuellement les panels de contrôle existants.
- Transformer Maison en dashboard libre avec cartes, graphiques, widgets ou mise en page arbitraire.

## 23. Décisions verrouillées issues de la discussion

- Les trois pills principales sont dynamiques par défaut, pas des favoris obligatoires.
- Les six pills secondaires restent automatiques mais personnalisables.
- Toutes les positions peuvent être occupées par des pins.
- Les alertes peuvent dépasser les pins ; leur rendu additionnel dépend de l'espace.
- Le placement initial d'un pin est automatique, puis son ordre est modifiable.
- Les favoris au-delà de neuf restent dans Maison et sont promus automatiquement.
- Un pin indisponible disparaît puis revient automatiquement.
- Maison affiche une pill par appareil par défaut.
- Regroupements automatiques par pièce et type, plus groupes manuels.
- Les groupes ouvrent un panel navigateur avec sous-panels et commande collective conditionnelle.
- Maison n'a pas de recherche, peut être désactivée et se situe à gauche de l'accueil.
- L'affichage reprend Playground avec titres, lignes horizontales et une/deux rangées automatiques.
- Réorganisation disponible par bouton Modifier et appui long.
- L'ordre est local ; l'appartenance automatique se modifie uniquement dans Home Assistant.

---

Cette spécification verrouille le comportement attendu. La planification doit découper l'implémentation en migrations compatibles et préserver les modifications locales présentes dans le worktree.
