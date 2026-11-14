# Podkids

Application Android de podcasts pour enfants : simple, colorée, pensée pour être utilisée par un enfant.

## Fonctionnalités

- **Accueil** : deux grandes cartes (« Catalogue » et « Lecture en cours »), puis la grille « Mes podcasts ».
- **Catalogue** : classements par rubrique (Les préférés, Histoires, Apprendre, Animaux), recherche,
  et ajout d'un podcast à partir de l'adresse de son flux RSS.
- **Écoute** : un appui sur un épisode le lit en streaming ; la lecture continue en arrière-plan avec
  notification et commandes sur l'écran verrouillé.
- **Téléchargement** : épisode par épisode ou tous les épisodes d'un podcast, pour l'écoute hors connexion.
  Un épisode téléchargé est lu depuis le fichier local.
- **Lecture en cours et file d'attente** : lecteur complet (pause, −10 s, +30 s, épisode suivant, curseur
  de position) et file d'attente (ajout, réorganisation, suppression). La file et la position d'écoute
  sont conservées d'un lancement à l'autre.
- **Mini-lecteur** : toujours visible en bas de l'écran dès qu'un épisode est chargé, avec la pochette à gauche.
- **Réglages** (bouton en haut à gauche de l'accueil) : prénom de l'enfant, couleur du thème, apparence
  claire / sombre / automatique, taille du texte, taille des pochettes.

## Catalogue et contenus

Le catalogue interroge l'annuaire public Apple Podcasts (iTunes Search API, sans clé ni compte). Ne sont
retenus que les podcasts classés « Enfants et parents », non explicites, hors rubrique « Parentalité ».
Tout autre podcast peut être ajouté par un adulte via son flux RSS.

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
