# Notifications et alarme (JSON)

Deux topics, deux couches indépendantes :

| topic | pour quoi | rendu |
|-------|-----------|-------|
| `portal/<deviceId>/notification` | tout le reste (colis, lave-linge, minuteurs) | bandeau, remplacé par la notification suivante |
| `portal/<deviceId>/alarm` | l'état du système d'alarme | couche **au-dessus**, jamais déplacée par une notification |
| `portal/<deviceId>/action_lock/set` | mode invité (`ON`/`OFF`) | rien à l'écran, les commandes cessent de répondre |

**Pourquoi deux.** Une notification remplace ce qui est à l'écran : sur un topic unique, une
livraison de colis effacerait l'écran d'intrusion, inacceptable pour la seule chose qui ne doit pas
pouvoir être écartée. Et une alarme est un *état*, pas un événement : publiée en `retain`, un
panneau qui redémarre au milieu d'une alarme la retrouve.

**Règle 1 : le portal ne synthétise jamais de voix.** C'est Home Assistant qui rend l'audio. Le
payload porte soit `audio` (un lien déjà prêt), soit `tts` (les mots) : dans ce cas le portal
demande l'URL à HA via `/api/tts_get_url` avec le jeton qu'il possède déjà, puis lit le fichier.
Aucun `rest_command`, aucun secret en double, aucun redémarrage de HA.

Rétro-compatibilité : si le payload ne commence pas par `{`, il est traité comme avant
(texte brut = message, tonalité `alert`).

## Schéma

```json
{
  "message": "Un colis vient d'être livré à la porte",
  "title": "Livraison",
  "icon": "mdi:package-variant-closed",
  "tts": "Un colis vient d'être livré à la porte d'entrée",
  "engine": "tts.google_ai_tts",
  "language": "fr-FR",
  "tone": "chime",
  "level": "warning",
  "color": "#30D158",
  "duration": 8000,
  "wake": true
}
```

| champ      | type   | défaut | rôle |
|------------|--------|--------|------|
| `message`  | string | :      | corps affiché. Requis sauf si `tts` ou `audio` seul. |
| `title`    | string | i18n `alert_overlay_title` | surtitre. |
| `icon`     | string | cloche | icône HA (`mdi:`, `phu:`, `hue:`…), résolue par `IconRef.parse` + `HaIcon`. |
| `tts`      | string | :      | mots à faire dire. Le portal demande l'URL à HA, HA synthétise. |
| `engine`   | string | premier `tts.*` de l'instance | moteur TTS, ex. `tts.google_ai_tts`. |
| `language` | string | celle du moteur | ex. `fr-FR`. Refusée par le moteur → réessai sans langue. |
| `audio`    | url    | :      | clip déjà rendu, joué tel quel. Prioritaire sur `tts`. |
| `tone`     | string | `alert` si ni `tts` ni `audio` | carillon (voir plus bas). Sert aussi de repli si la voix échoue. |
| `level`    | enum   | `info` | `info` / `warning` / `critical` → couleur et durée (5 s / 8 s / jusqu'au tap). |
| `color`    | hex    | :      | accent explicite (`#RGB`, `#RRGGBB`, `#AARRGGBB`), prioritaire sur `level`. |
| `duration` | int ms | selon `level`, ou fin de l'audio + 1500 ms si voix (plafond 60 s) | affichage. |
| `wake`     | bool   | `true` | réveille l'écran avant affichage. |
| `countdown`| durée  | :      | minuteur : `20s`, `10m`, `1h30m`, `1:30`, `00:10:00`, ou un nombre de secondes. |
| `until`    | ISO-8601 | :    | instant de fin, prioritaire sur `countdown` (ex. `finishes_at` d'un `timer.*`). |
| `end_message` | string | :   | remplace `message` quand le compteur atteint zéro. |
| `end_tone` | string | `alert` | carillon joué à zéro. `none` pour rien. |
| `tick`     | string | :      | carillon joué **chaque seconde** du décompte (bips de temporisation). |
| `tick_urgent_at` | durée | `10s` | secondes restantes sous lesquelles les bips doublent de cadence. |
| `repeat`   | durée  | :      | rejoue `tone` à cet intervalle tant que la notification est là (sirène, plafond 5 min). |
| `dismiss`  | bool   | :      | payload à part : retire la notification en cours, tout le reste est ignoré. |
| `badge`    | string | :      | mot court à côté du titre : `IGNORÉ`, `BYPASS`, `HORS LIGNE`. |
| `items`    | liste  | :      | lignes de détail sous le message (voir plus bas). |
| `keypad`   | entité | :      | `alarm_control_panel` à désarmer : écran plein, clavier au centre. |
| `blackout` | bool   | vrai si `keypad` | fond noir opaque, message centré, avec ou sans clavier. |
| `dismissible` | bool | faux si `keypad` | si faux, un tap ne referme plus rien. |

Payload sans `message`, sans `tts` et sans `audio` → ignoré.

### Carillons

`alert` (trois bips) · `doorbell` (ding-dong) · `chime` (quatre notes descendantes) ·
`success` (accord montant) · `error` (deux notes graves) · `ping` (note unique) ·
`beep` (bip court, pour `tick`) · `siren` (deux tons alternés) · `none` / `off` / `silent` (rien).

## Minuteurs et alarmes

`countdown` ou `until` transforme la notification en minuteur : le compteur s'affiche à droite du
message (`m:ss`, ou `h:mm:ss` au-delà de l'heure) et **rien ne la referme avant zéro** : ni la durée
du niveau, ni la fin de la voix. À zéro : `end_tone` sonne, `end_message` remplace le message, puis
la notification s'efface selon `duration` / `level` (en `critical`, elle attend le tap).

Le compteur se recale sur l'horloge à chaque tick, donc il ne dérive pas sur un minuteur long.
`until` l'emporte sur `countdown` : Home Assistant sait quand son propre `timer` finit, une durée ne
sait que depuis quand le panneau l'a entendue.

```yaml
- action: script.portal_notify
  data:
    panel: <appareil>
    title: Cuisine
    message: Minuteur du thé
    icon: mdi:tea
    level: warning
    until: "{{ state_attr('timer.the', 'finishes_at') }}"
    end_message: Le thé est prêt.
    end_tone: chime
```

## Lignes de détail

`items` porte ce que le message ne doit pas allonger : quels capteurs sont en cause, et ce que le
système en a fait. Chaque entrée est une chaîne, ou un objet `{text, icon, note}` : `note` s'écrit
dans la couleur d'accent, c'est le verdict de la ligne.

```json
{
  "message": "Armement demandé : ouvertures détectées",
  "badge": "ignoré",
  "level": "warning",
  "countdown": "25s",
  "tick": "beep",
  "end_tone": "none",
  "items": [
    {"text": "Fenêtre cuisine", "icon": "mdi:window-open", "note": "ignoré"},
    {"text": "Porte terrasse",  "icon": "mdi:door-open",   "note": "ignoré"}
  ],
  "tts": "La fenêtre de la cuisine est ouverte, elle sera ignorée pendant l'armement."
}
```

Trois lignes au plus sont affichées : au-delà le panneau compte le reste (« +2 autre(s) »), une
liste plus longue ne se lit pas à trois mètres. Une entrée malformée est jetée, pas la
notification.

Côté Home Assistant, la liste se construit à partir des capteurs réellement ouverts :

```jinja
{% set ouverts = states.binary_sensor
   | selectattr('attributes.device_class', 'in', ['door','window','opening','garage_door'])
   | selectattr('state', 'eq', 'on') | list %}
{{ ouverts | map(attribute='name') | list }}
```

## Verrouillage des commandes

`switch.<panneau>_controls_locked` : une entité MQTT discovery, donc elle apparaît toute seule dans
Home Assistant. Allumée, le panneau continue d'afficher l'état de la maison exactement pareil, mais
**aucun appui n'atteint Home Assistant** : un toast dit que c'est verrouillé, et rien ne bouge.
Pensé pour un logement vide : le panneau devient un écran d'information.

Verrouiller l'*appel de service* plutôt que les contrôles est délibéré : l'écran reste honnête sur
l'état du logement, ce qui est tout l'intérêt d'un panneau devant lequel personne ne se tient.
Le garde-fou est posé sur `LocalCallService`, seul chemin entre un appui et Home Assistant, donc il
ne peut pas être oublié sur un écran.

**Le domaine `alarm_control_panel` n'est jamais bloqué.** Le code est déjà l'authentification, et un
panneau verrouillé qui ne peut plus désarmer enferme celui qui rentre.

L'état est persisté (`Prefs.actionLocked`) et republié en retained : un logement absent reste
verrouillé au redémarrage, plutôt que de se rouvrir tout seul.

## Topic alarme

Publier l'**état**, pas une notification composée : le panneau décide seul de ce que chaque état
mérite. Un état nu suffit (`triggered`), l'objet complet affine.

```json
{
  "state": "pending",
  "entity": "alarm_control_panel.alarmo",
  "mode": "Absence",
  "delay": 60,
  "sensors": [{"text": "Porte entrée", "icon": "mdi:door-open"}],
  "speak": "Alarme, désarmez le système."
}
```

> **Moteur TTS.** Une clé Gemini gratuite plafonne à 10 requêtes : une fois vidée, `tts_get_url`
> répond encore une URL mais le fichier renvoie `429` : donc silence, sans erreur visible côté
> panneau. Repli sûr : `tts.google_translate_fr_fr`, illimité.
>
> **Le cache de Home Assistant amortit ça.** Le rendu est mémorisé par (texte, moteur, options) :
> mesuré à 3,0 s à la première demande puis 10 ms, contenu identique : le jeton d'URL change à
> chaque appel, pas le fichier derrière. Les six phrases d'alarme coûtent donc six générations, une
> fois pour toutes. `HaApiClient.ttsUrl` passe `cache: true` explicitement plutôt que de se fier au
> défaut de l'instance.

`message`, `title`, `speak`, `engine`, `language` remplacent ce que le panneau dirait de lui-même.
Un payload vide efface la couche : c'est aussi ce que fait la purge du topic retained.

**Chaque état a sa phrase**, dite sans qu'on ait à l'écrire : « Système armé, mode Absence. »,
« Système désarmé. », « Alarme, intrusion détectée. », « Armement refusé, Fenêtre cuisine est encore
ouvert. » Le texte dit n'est pas le texte affiché : l'écran peut se permettre un tiret et un nom de
mode, une phrase lue à trois mètres non. `"speak": "…"` impose une autre phrase, `"speak": false`
(ou `"silent": true`) fait taire cet état. Seul le moteur (`engine`, `language`) vient de
l'automation.

| état | plein écran | tap le ferme | son | durée |
|------|-------------|--------------|-----|-------|
| `arming` (sortie) | non | oui | bips chaque seconde | jusqu'à l'état suivant |
| `pending` (entrée) | **oui + clavier** | **non** | bips, accélérés en fin | jusqu'à l'état suivant |
| `triggered` | **oui + clavier** | **non** | sirène toutes les 3 s | jusqu'à l'état suivant |
| `armed_*` | non | oui | accord montant | 6 s |
| `disarmed` | non | oui | accord montant | 5 s |
| `failed_to_arm` | non | oui | deux notes graves | 20 s |

La temporisation de **sortie** n'est délibérément pas en plein écran : on marche vers la porte, il
n'y a rien à saisir, et noircir le panneau d'un logement encore occupé n'aide personne. Celle
d'**entrée** l'est, parce qu'il faut taper un code, et vite.

Cette matrice vit dans `AlarmPhase` (Kotlin), pas dans l'automation : qu'un état mérite l'écran
entier est une propriété de l'état, pas de qui écrit le YAML.

## Écran d'alarme

`keypad` change la nature de la notification : plus de bandeau en haut d'un fond d'écran, mais un
écran noir dédié : titre, badge, message, capteurs en cause, compteur, et le clavier de désarmement
au milieu, celui-là même que sert le panneau alarme (`AlarmKeypad`, donc code vérifié par Home
Assistant et secousse iOS sur code faux).

Il emmène deux défauts avec lui, chacun débrayable :

- `blackout` : le fond d'écran disparaît. Une horloge qui transparaît sous une alarme se lit comme
  un bug d'affichage.
- `dismissible: false` : **un tap ne coupe plus rien**. C'est la protection, sans elle, n'importe
  qui atteignant le panneau fait taire la sirène d'un doigt. Il ne reste que le code, ou un
  `{"dismiss": true}` de Home Assistant : en pratique le passage à `disarmed`.

C'est `blackout` qui commande la pleine page, pas `keypad` : un message seul, sans clavier, est
centré de la même façon. Et un `keypad` dont l'entité est inconnue de ce panneau ne dessine aucun
clavier : un code qui partirait dans le vide vaut moins que pas de clavier du tout, mais garde
l'écran noir, le message et le compteur.

La mise en page suit l'écran : panneau à la verticale (800×1280) → bloc en haut, clavier agrandi
(touches 128 dp) centré dessous ; panneau court en paysage (960×480) → deux colonnes, incident à
gauche, clavier à droite, sinon la dernière rangée tombe hors de l'écran.

## Panneau d'alarme

Les quatre briques d'un panneau d'alarme se combinent dans le même payload :

| état | payload |
|------|---------|
| temporisation de sortie | `countdown` + `tick: beep`, `end_tone: none` |
| temporisation d'entrée | `level: critical` + `countdown` + `tick`, `end_tone: none` |
| intrusion | `level: critical`, `tone: siren`, `repeat: 3s` : tient jusqu'au désarmement |
| armé / désarmé | notification courte, `tone: success` |

Deux règles rendent l'ensemble sûr : **toute nouvelle notification remplace la précédente** (donc
passer à « Système désarmé » coupe le décompte, les bips et la sirène), et `{"dismiss": true}`
efface sans rien afficher. Sans cela, un désarmement pendant la temporisation d'entrée laisserait
le panneau compter dans le vide.

**Un décompte qui atteint zéro ne conclut rien.** « Armé », « déclenchée », « désarmé » sont des
états que Home Assistant pousse ; le panneau ne les déduit pas de son propre compteur. Sinon il
annoncerait une intrusion alors que quelqu'un vient de désarmer à la dernière seconde, ou un
armement qu'Alarmo a refusé. À zéro, le compteur disparaît, le message reste, et l'état suivant le
remplace : d'où `end_tone: none` sur les deux temporisations.

`end_message` / `end_tone` gardent leur intérêt pour un vrai minuteur (le thé, la cuisson), là où
zéro *est* l'événement.

`tools/ha/automation_alarme_portal.yaml` publie l'état d'Alarmo sur le topic alarme de chaque
panneau (liste `panneaux`), en `retain`, en y joignant la temporisation (`delay`), le mode et les
ouvertures encore ouvertes. C'est un seul `mqtt.publish` par panneau : aucune décision d'affichage
dans le YAML.

## Sécurité

Un champ `audio` n'est joué que si l'URL est `http(s)` **et** que son host correspond à
`Prefs.haUrl` : n'importe qui pouvant publier sur le broker sinon ferait fetcher une URL arbitraire
par le panneau. L'URL obtenue via `tts` échappe à ce contrôle : elle vient de la réponse
authentifiée que le portal a lui-même demandée à HA, pas du broker.

## Flux

```
HA script ── mqtt.publish portal/<id>/notification {message, icon, tts, tone, ...}
                │
        MqttBridgeService.showNotification
                │  AlertPayload.parse(raw)        <- pur, testé
                ├─ ScreenControl.wake (si wake)
                ├─ audio ? → garde d'host → AudioUrlPlayer
                ├─ tts ?   → overlay tout de suite, puis HaApiClient.ttsUrl en tâche de fond
                │            → AudioUrlPlayer, ou carillon si HA ne rend rien
                └─ sinon   → TonePlayer
                            │
                     AlertOverlay (glow coloré, HaIcon, titre, message)
```

## Code

- `AlertPayload.kt` : schéma + `parse` tolérant + `playableAudio(haUrl)` + `accentArgb()`.
  Testé dans `AlertPayloadTest`.
- `HaApiClient.ttsUrl(message, engine, language)` : `/api/tts_get_url`, moteur deviné et mémoïsé
  si absent, réessai sans langue si le moteur la refuse.
- `AudioUrlPlayer.kt` : `MediaPlayer` en streaming, focus audio `TRANSIENT_MAY_DUCK`, un clip à la
  fois, `onDone` garanti une fois (fin, erreur ou remplacement). **Le lecteur est construit sur le
  thread principal** : `MediaPlayer` lie ses callbacks au `Looper` du thread qui le construit, et
  une URL de TTS arrive naturellement sur un thread de travail : sans ça, `onPrepared` n'est jamais
  livré et le clip ne démarre jamais, sans erreur.
- `TonePlayer` : carillons `chime` / `success` / `error` / `ping` en plus, et un silence explicite.
- `AlertOverlayState` : porte un `AlertPayload`; `show(alert, awaitAudio)`, `onAudioFinished()`.
- `AlertOverlay` : titre, icône et couleur issus du payload, plus le compteur (`formatRemaining`,
  testé) qui se recale sur l'horloge à chaque seconde.
- `AlertOverlayState` : un minuteur garde l'overlay jusqu'à zéro, y sonne `end_tone` et y bascule
  sur `end_message`; il porte aussi les bips (`tick`, cadence doublée en fin de compte) et la
  sirène (`repeat`, plafonnée à 5 min).
- `MdiCodepoints` : l'`AssetFileDescriptor` de l'index MDI est retenu, il était finalisé aussitôt,
  le `FileChannel` échouait en « Bad file descriptor » et **toutes** les icônes `mdi:` retombaient
  silencieusement sur leur vecteur de repli.

## Script Home Assistant

`tools/ha/script_portal_notify.yaml`, installé sous `script.portal_notify` : un `mqtt.publish` et
rien d'autre. Le panneau se choisit dans une liste (sélecteur d'appareil filtré sur l'intégration
MQTT, modèle « Meta Portal ») et son `deviceId` est lu dans les identifiants MQTT :

```yaml
panel_id: >-
  {% set ids = device_attr(panel, 'identifiers') | default([], true) | list %}
  {{ ids[0][1] if ids else panel }}
```

Deux pièges : `device_id` est un nom réservé dans un appel de service (d'où `panel`), et
`false | default(true, true)` vaut `true` en Jinja : `speak: false` partait quand même en TTS.

## Script en ligne de commande

```bash
export HA_URL=https://ha.example.org HA_TOKEN=eyJ... PORTAL_DEVICE_ID=117daa67dd009788
tools/portal_notify.py \
  --message "Le lave-linge a terminé son cycle." --title Buanderie \
  --icon mdi:washing-machine --color "#30D158" --speak
```

`--speak "autre texte"` dissocie voix et affichage, `--tone`, `--level`, `--duration`, `--engine`,
`--language`, `--no-wake`, `--dry-run`, `--list-devices`, et pour les minuteurs `--countdown 10m`,
`--until <ISO>`, `--end-message`, `--end-tone`. Le `deviceId` se lit sur le panneau :
`adb shell run-as com.iblu01.portallauncher cat shared_prefs/portal_launcher.xml`.

## Hors périmètre

File d'attente multi-notifs (aujourd'hui une notif remplace la précédente), actions/boutons dans
l'overlay, image distante.
