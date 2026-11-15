# Podkids

Application Android de podcasts pour enfants : simple, colorée, pensée pour être utilisée par un enfant.

## Fonctionnalités

- **Accueil** : la carte « Lecture en cours », la grille des podcasts ajoutés, et le bouton « Catalogue »
  fixé tout en bas de l'écran.
- **Catalogue** : sélection de podcasts pour enfants, classée par rubrique (Histoires du soir, Contes et
  légendes, Histoires et aventures, Découvertes), avec un sélecteur d'âge à choix multiples (à partir de
  3, 4, 5, 6 ou 7 ans), une recherche, et l'ajout d'un podcast à partir de l'adresse de son flux RSS.
- **Écoute** : un appui sur un épisode le lit en streaming ; la lecture continue en arrière-plan avec
  notification et commandes sur l'écran verrouillé.
- **Téléchargement** : épisode par épisode ou tous les épisodes d'un podcast, pour l'écoute hors connexion.
  Un épisode téléchargé est lu depuis le fichier local.
- **Lecture en cours et file d'attente** : lecteur complet (pause, −10 s, +30 s, épisode suivant, curseur
  de position) et file d'attente (ajout, réorganisation, suppression). La file et la position d'écoute
  sont conservées d'un lancement à l'autre.
- **Mini-lecteur** : toujours visible en bas de l'écran dès qu'un épisode est chargé, avec la pochette à gauche.
- **Réglages** (bouton en haut à gauche de l'accueil) : couleur du thème, apparence
  claire / sombre / automatique, taille du texte, taille des pochettes.

## Catalogue et contenus

Le catalogue est une sélection manuelle embarquée dans l'application : `app/src/main/assets/catalog.json`.
Il ne contient que des podcasts francophones destinés aux enfants. Chaque entrée porte :

- `minAge` : l'âge minimal **annoncé par l'éditeur** du podcast (dans la description de son flux ou, à
  défaut, dans une déclaration publique de sa production). Un podcast sans âge annoncé n'est pas retenu.
- `category` : la rubrique (`BEDTIME`, `TALES`, `STORIES`, `DISCOVERY`).
- `feedUrl` et `artworkUrl` : le flux RSS public et la pochette.

Le sélecteur d'âge affiche les podcasts dont l'âge minimal correspond à l'un des âges cochés ; les podcasts
annoncés pour les moins de 3 ans sont rattachés au choix « 3 ans ». Rien de coché : tous les âges.

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
