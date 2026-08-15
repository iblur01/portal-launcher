# Prochaine version : scènes et centre caméra — Spécification

**Créée le :** 2026-08-15  
**Score d’ambiguïté :** 0,15 (seuil : ≤ 0,20)  
**Exigences verrouillées :** 12

## Objectif

Permettre de retrouver et d’épingler les scènes et les caméras Home Assistant sous forme de pills, d’activer une scène d’un toucher et d’ouvrir les caméras dans un centre plein écran configurable proposant le flux en direct, le son et les commandes PTZ réellement disponibles.

## Contexte

Portal Launcher sait déjà découvrir, afficher, ordonner et épingler plusieurs types d’entités Home Assistant. Les préférences d’accueil conservent un ordre de pills épinglées et l’interface réserve trois positions principales puis six positions secondaires.

Le type interne `PillKind.SCENE` et ses libellés existent, mais le domaine Home Assistant `scene` n’est pas découvert comme entité utilisable, les scènes sont masquées par le moteur de pills et aucune action ne leur est associée. Le domaine `camera` est explicitement exclu du catalogue. Il n’existe ni type de pill caméra, ni écran de lecture vidéo, ni préférences de centre caméra.

## Exigences

1. **Découverte des scènes** : chaque entité Home Assistant disponible du domaine `scene` figure dans la liste des pills configurables.
   - État actuel : le type `SCENE` existe partiellement, mais les entités `scene.*` ne sont pas retenues comme pills utilisables.
   - Cible : les scènes disponibles apparaissent avec leur nom et leur icône Home Assistant, sans être confondues avec une pill d’état persistante.
   - Validation : avec deux scènes disponibles et une scène indisponible, les deux scènes disponibles apparaissent dans la liste et peuvent être sélectionnées ; l’indisponibilité de la troisième est indiquée.

2. **Activation immédiate d’une scène** : toucher une pill de scène appelle immédiatement le service d’activation de cette scène, sans ouvrir de panneau ni demander de confirmation.
   - État actuel : aucune action n’est routée pour `PillKind.SCENE`.
   - Cible : un toucher sur `scene.<id>` déclenche exactement une activation et fournit un retour visuel temporaire de demande en cours, de réussite ou d’échec.
   - Validation : un toucher produit un seul appel Home Assistant visant l’entité choisie ; un échec réseau ne produit pas de réussite trompeuse et la pill redevient utilisable.

3. **Épinglage des scènes** : une scène peut être épinglée, désépinglée et ordonnée sur l’écran principal avec les mécanismes existants.
   - État actuel : les références et préférences d’épinglage existent, mais aucune scène ne peut entrer dans le catalogue correspondant.
   - Cible : une scène épinglée occupe les positions principales ou secondaires selon son ordre sauvegardé, comme les autres pills.
   - Validation : après épinglage, réordonnancement puis redémarrage de l’application, la scène reste à la position enregistrée et demeure activable.

4. **Découverte des caméras** : chaque entité Home Assistant disponible du domaine `camera` figure comme pill caméra configurable.
   - État actuel : `camera` est explicitement exclu du support des pills.
   - Cible : les caméras apparaissent avec leur nom, leur icône et leur état de disponibilité ; une caméra indisponible reste identifiable mais ne tente pas d’afficher un faux flux actif.
   - Validation : les caméras connues de Home Assistant sont présentes dans les réglages et leur disponibilité correspond à l’état reçu.

5. **Pills caméra individuelles** : chaque caméra sélectionnée peut être épinglée sur l’écran principal et ouvre le centre directement sur cette caméra.
   - État actuel : aucun type ni routage de pill caméra n’existe.
   - Cible : l’épinglage, le désépinglage, l’ordre et la persistance suivent les règles communes des pills ; toucher la pill ouvre la caméra correspondante même si une autre caméra principale est configurée.
   - Validation : toucher successivement deux pills de caméras différentes ouvre à chaque fois le flux correspondant ; leur ordre survit au redémarrage.

6. **Pill générale Caméras** : une pill générale permet d’ouvrir le centre caméra dans sa vue par défaut.
   - État actuel : aucune entrée globale pour les caméras n’existe.
   - Cible : la pill « Caméras » est configurable et épinglable ; elle ouvre le mode et la caméra principale enregistrés dans les réglages.
   - Validation : après modification du mode par défaut et de la caméra principale, toucher la pill générale ouvre exactement cette configuration.

7. **Centre caméra plein écran** : le centre caméra est une page superposée occupant toute la surface utile, quelle que soit la taille du téléphone, de la tablette ou de l’écran mural.
   - État actuel : les entités pilotables ouvrent des panneaux adaptés aux appareils, sans page vidéo dédiée.
   - Cible : le centre masque les pages du launcher derrière lui, adapte son contenu aux dimensions disponibles et fournit une action de fermeture explicite ainsi que la navigation Retour Android.
   - Validation : sur les tailles de référence compactes et larges du projet, aucun élément essentiel n’est hors écran ; Retour ferme le centre et restitue la page précédemment affichée.

8. **Modes caméra principale et grille** : le centre propose un mode « caméra principale » et un mode « grille », sélectionnables dans les réglages et commutables depuis le centre.
   - État actuel : aucun mode d’affichage caméra n’existe.
   - Cible : le mode caméra principale privilégie un grand flux et permet de choisir une autre caméra ; le mode grille affiche toutes les caméras rendues visibles dans les réglages. Sélectionner une caméra dans la grille l’ouvre comme caméra principale.
   - Validation : avec au moins trois caméras sélectionnées, les deux modes affichent le bon ensemble ; choisir une vignette de grille ouvre le bon flux principal.

9. **Flux en direct robuste** : chaque caméra sélectionnée affiche son flux Home Assistant avec des états explicites de chargement, d’indisponibilité et d’erreur récupérable.
   - État actuel : aucun lecteur caméra ni cycle de connexion vidéo n’existe.
   - Cible : le lecteur s’authentifie avec la connexion Home Assistant existante, ne divulgue pas le jeton, arrête les ressources lorsque le centre est fermé et propose une nouvelle tentative après erreur.
   - Validation : un flux compatible devient visible ; une URL invalide ou une caméra indisponible affiche une erreur et permet de réessayer ; fermer le centre arrête la lecture et l’activité réseau associée.

10. **Son du flux** : lorsqu’un flux possède une piste audio, l’utilisateur peut l’écouter et la couper depuis le centre caméra.
    - État actuel : aucun média caméra n’est lu.
    - Cible : un contrôle muet/sonore est disponible pour les flux audio ; l’absence de piste audio ne provoque pas d’erreur et n’affiche pas un contrôle mensonger.
    - Validation : le contrôle change effectivement l’état audio d’un flux avec son ; un flux sans son reste lisible sans échec.

11. **PTZ conditionnel** : les commandes panoramique, inclinaison et zoom ne sont affichées que lorsqu’elles sont réellement supportées par la caméra active.
    - État actuel : aucune capacité ni commande PTZ n’est gérée.
    - Cible : les capacités exposées par Home Assistant déterminent les contrôles proposés ; chaque action vise uniquement la caméra active et une caméra fixe n’affiche aucun contrôle PTZ.
    - Validation : une caméra PTZ de test expose uniquement ses commandes prises en charge et reçoit l’action choisie ; une caméra fixe n’affiche aucune commande PTZ.

12. **Configuration du centre caméra** : les réglages permettent de choisir les caméras visibles, leur ordre, la caméra principale, le mode par défaut et les pills caméra épinglées.
    - État actuel : aucune préférence caméra n’existe.
    - Cible : ces choix sont modifiables sans éditer Home Assistant, persistent après redémarrage et tolèrent la disparition ou l’indisponibilité d’une caméra enregistrée.
    - Validation : une configuration complète survit au redémarrage ; retirer une caméra de Home Assistant ne bloque ni les réglages ni l’ouverture des autres flux.

## Limites

### Inclus

- Découverte et pills individuelles des scènes Home Assistant.
- Activation immédiate et retour d’état des scènes.
- Découverte, sélection, ordre et pills individuelles des caméras.
- Pill générale « Caméras ».
- Centre caméra plein écran sur toutes les tailles prises en charge.
- Modes caméra principale et grille.
- Choix d’une caméra principale.
- Flux vidéo en direct, son et commandes PTZ selon les capacités.
- Gestion des chargements, erreurs, indisponibilités et nouvelles tentatives.
- Persistance de tous les réglages caméra et d’épinglage.
- Libellés français et anglais ainsi que l’accessibilité des nouvelles actions.

### Exclus

- Enregistrement vidéo et consultation des enregistrements — cette version est centrée sur le direct.
- Historique et chronologie des caméras — à traiter avec les enregistrements.
- Création ou téléchargement de snapshots — non nécessaire pour consulter le direct.
- Détection, affichage et notification des événements — fonctionnalité distincte du visionnage.
- Interphone, microphone et audio bidirectionnel — le son inclus est uniquement celui reçu avec le flux.
- Création, édition ou suppression de scènes — ces opérations restent gérées dans Home Assistant.
- Gestion des utilisateurs et permissions Home Assistant — les droits existants sont respectés tels quels.

## Contraintes

- Compatibilité Android 9 / API 28 minimum et respect de la cible Android actuelle du projet.
- Interface utilisable sur téléphone, tablette et écran mural, sans supposer une diagonale ni un ratio précis.
- Réutilisation des règles d’épinglage existantes : trois positions principales, six secondaires et débordement géré par l’accueil.
- Les identifiants et jetons Home Assistant ne doivent jamais apparaître dans l’interface, les journaux applicatifs ou une URL transmise à une application externe.
- Le protocole de flux retenu devra être compatible avec les formats réellement fournis par Home Assistant et permettre la libération déterministe des lecteurs.
- Une caméra sans son ou sans PTZ reste pleinement utilisable pour la vidéo.
- Les nouvelles chaînes visibles doivent exister en français et en anglais.

## Critères d’acceptation globaux

- [ ] Toutes les scènes Home Assistant disponibles apparaissent dans la liste des pills configurables.
- [ ] Toucher une scène envoie une seule demande d’activation sans confirmation ni panneau intermédiaire.
- [ ] Les scènes épinglées conservent leur position après redémarrage.
- [ ] Toutes les caméras Home Assistant apparaissent dans les réglages avec leur disponibilité correcte.
- [ ] Une pill caméra individuelle ouvre directement cette caméra.
- [ ] La pill générale « Caméras » ouvre le mode par défaut et la caméra principale configurés.
- [ ] Le centre occupe toute la surface utile et se ferme correctement par son action dédiée et par Retour Android.
- [ ] Le mode caméra principale et le mode grille fonctionnent avec au moins trois caméras.
- [ ] Le flux fournit des états distincts de chargement, lecture, indisponibilité et erreur avec nouvelle tentative.
- [ ] La fermeture du centre arrête les lecteurs et leurs connexions.
- [ ] Le son est contrôlable lorsqu’il existe et son absence n’empêche pas la vidéo.
- [ ] Seules les commandes PTZ prises en charge par la caméra active sont affichées et exécutables.
- [ ] Sélection, ordre, caméra principale, mode par défaut et épinglages persistent après redémarrage.
- [ ] Une caméra supprimée ou devenue indisponible ne bloque pas les autres caméras ni l’accès aux réglages.
- [ ] Les nouveaux écrans et actions possèdent des libellés français et anglais et des descriptions d’accessibilité.
- [ ] Les tests existants liés aux pills, à l’accueil et à la navigation restent au vert.

## Rapport d’ambiguïté

| Dimension | Score | Minimum | État | Notes |
|---|---:|---:|:---:|---|
| Clarté du but | 0,93 | 0,75 | ✓ | Parcours scènes et caméras explicités |
| Clarté du périmètre | 0,92 | 0,70 | ✓ | Direct inclus ; archives, événements et interphone exclus |
| Clarté des contraintes | 0,74 | 0,65 | ✓ | Capacités, écrans et compatibilité cadrés |
| Critères d’acceptation | 0,75 | 0,70 | ✓ | Parcours principaux vérifiables en réussite et en erreur |
| **Ambiguïté** | **0,15** | **≤ 0,20** | **✓** | Prête pour les décisions d’implémentation |

## Journal des décisions

| Tour | Perspective | Question résumée | Décision verrouillée |
|---:|---|---|---|
| 1 | État des lieux | Que fait un toucher sur une scène ? | Activation immédiate |
| 1 | État des lieux | Comment s’ouvre une caméra ? | Centre caméra plein écran ; présentation choisie dans les réglages |
| 1 | État des lieux | Quelles fonctions caméra ? | Flux requis ; son et PTZ également inclus |
| 2 | Simplification | Quels modes d’affichage ? | Caméra principale et grille, avec caméra principale configurable |
| 2 | Simplification | Pill individuelle ou entrée globale ? | Les deux : pill individuelle ciblée et pill générale configurée |
| 3 | Gardien du périmètre | Qu’est-ce qui est exclu ? | Enregistrements, historique, snapshots, événements et interphone |
| 3 | Gardien du périmètre | Comment exposer le PTZ ? | Uniquement selon les capacités réelles de chaque caméra |
| 3 | Gardien du périmètre | Que peut-on régler ? | Visibilité, ordre, principale, mode par défaut et épinglages |

---

*Étape suivante : décider l’architecture du flux Home Assistant, la détection des capacités audio/PTZ, le modèle de préférences et le découpage d’implémentation.*
