# Podkids

Application Android de podcasts pour enfants : simple, colorée, pensée pour être utilisée par un enfant.

## Fonctionnalités

- **Accueil** : la grille des podcasts ajoutés, suivie de la carte « Catalogue » qui défile avec elle.
- **Catalogue** : sélection de podcasts pour enfants, avec un sélecteur d'âge à choix multiples (à partir
  de 3 à 12 ans), une recherche, et l'ajout d'un podcast à partir de l'adresse de son flux RSS.
- **Écoute** : un appui sur un épisode le lit en streaming ; la lecture continue en arrière-plan avec
  notification et commandes sur l'écran verrouillé.
- **Épisodes lus** : un épisode écouté jusqu'au bout est marqué comme lu et rangé en bas de la liste, dans
  une section « Épisodes lus ». Glisser un épisode vers la gauche ou la droite le marque lu ou non lu
  (toujours possible en mode adulte, sur réglage en mode enfant).
- **Ordre des épisodes** : une icône de tri, sur la page d'un podcast, bascule entre l'ordre chronologique
  (du plus ancien au plus récent, par défaut) et l'ordre inverse. L'ordre par défaut se règle dans les
  réglages.
- **Téléchargement** : épisode par épisode ou tous les épisodes d'un podcast, pour l'écoute hors connexion.
  Un épisode téléchargé est lu depuis le fichier local.
- **Lecture en cours et file d'attente** : lecteur volontairement simple (lecture / pause et curseur
  de position) et file d'attente (ajout, réorganisation, suppression). La file et la position d'écoute
  sont conservées d'un lancement à l'autre.
- **Mini-lecteur** : toujours visible en bas de l'écran dès qu'un épisode est chargé, avec la pochette à gauche
  et un bouton lecture / pause.
- **Réglages** (bouton en haut à gauche de l'accueil) : ordre des épisodes par défaut
  (anciens d'abord ou récents d'abord), mode enfant, verrouillage par mot de passe et, tout en bas,
  apparence claire / sombre / automatique.
- **Mode enfant / mode adulte** : une icône en haut à droite de l'accueil bascule entre les deux modes. En
  mode enfant, les réglages de la carte « Mode enfant » s'appliquent : masquer le catalogue de l'accueil (il
  ne s'ouvre alors que par un bouton des réglages) et n'afficher que les épisodes téléchargés (sans boutons
  de téléchargement ni de suppression), et autoriser ou non le glissement pour marquer un épisode lu / non
  lu. En mode adulte, tout est visible.
- **Verrouillage des réglages** : en mode enfant, un mot de passe défini dans les réglages est demandé pour
  les ouvrir et pour passer en mode adulte.
  Seule une empreinte salée est enregistrée. En cas d'oubli, effacer les données de l'application dans les
  paramètres d'Android.

## Catalogue et contenus

Le catalogue est une sélection manuelle embarquée dans l'application : `app/src/main/assets/catalog.json`.
Il ne contient que des podcasts francophones destinés aux enfants. Chaque entrée porte :

- `minAge` : l'âge minimal **annoncé par l'éditeur** du podcast : dans la description de son flux, à
  défaut dans le livret « podcasts enfants » ou l'étiquette d'âge de la page officielle (Radio France), ou
  dans une déclaration publique de sa production. Un podcast sans âge annoncé n'est pas retenu.
- `feedUrl` et `artworkUrl` : le flux RSS public et la pochette. Pour Radio France (France Inter, France
  Musique, France Culture, franceinfo, FIP, ICI), il s'agit des flux officiels `radiofrance-podcast.net`
  référencés sur la page de chaque émission.

Les podcasts sont rangés du plus jeune âge au plus élevé. Le sélecteur d'âge affiche ceux dont l'âge minimal
correspond à l'un des âges cochés ; les podcasts annoncés pour les moins de 3 ans sont rattachés au choix
« 3 ans ». La pastille de chaque pochette indique l'âge exact. Rien de coché : tous les âges.

Pour ajouter ou retirer un podcast, il suffit de modifier ce fichier ; un test unitaire en vérifie la
cohérence. Tout autre podcast peut être ajouté par un adulte via son flux RSS.

## Architecture

Application native Kotlin, mono-module, sans framework d'injection.

| Domaine | Choix technique |
|---|---|
| Interface | Jetpack Compose, Material 3, Navigation Compose |
| Lecture audio | Media3 (ExoPlayer + `MediaSessionService`) |
| Données locales | Room (podcasts, épisodes, file d'attente) |
| Réglages | DataStore Preferences |
| Téléchargements | WorkManager + OkHttp (reprise par en-tête HTTP `Range`) |
| Flux RSS | Analyseur SAX maison (`RssParser`) |
| Images | Coil 3 |

```
app/src/main/java/fr/podkids/app/
├── PodkidsApp.kt          Application + conteneur de dépendances
├── MainActivity.kt        Activité unique
├── data/                  Room, flux RSS, catalogue, téléchargements, réglages
├── playback/              Service de lecture et pont vers l'interface
└── ui/                    Écrans Compose, thème, mini-lecteur
```

## Compiler

Prérequis : JDK 17 ou plus récent et le SDK Android (plateforme 37). Le plus simple est d'ouvrir le
dossier dans Android Studio.

```bash
./gradlew assembleDebug        # APK : app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests unitaires (analyse RSS, catalogue, adresses de flux)
```

Chaque `push` déclenche aussi le workflow GitHub Actions `Build`, qui exécute les tests et publie l'APK
de debug en tant qu'artefact du run.

Version minimale d'Android : 8.0 (API 26).
