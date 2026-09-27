# QuizRévise 🎴

Application Android de révision type **Quizlet** : crée tes paquets de cartes, révise avec des **flashcards** ou des **QCM**, suis tes **statistiques** — et mets l'application à jour **automatiquement depuis GitHub**.

> Version d'essai **v1.0.0** — fonctionnelle, hors-ligne, sans publicité.

## ✨ Fonctionnalités

- 📚 **Paquets de cartes** illimités, avec couleur et renommage
- 🃏 **Mode Flashcards** : retourne la carte, puis « Je savais » / « Pas su »
- ❓ **Mode QCM** : 4 choix générés automatiquement à partir des autres cartes
- 📊 **Statistiques** : série de jours consécutifs (streak), précision, cartes revues du jour
- 🔄 **Mise à jour automatique** : l'app interroge les GitHub Releases, télécharge le nouvel APK et propose l'installation
- 🇫🇷 Interface 100 % en français, Material 3
- 📴 100 % hors-ligne (données en SQLite local)

## 📥 Installation

Télécharge le dernier APK depuis la page [Releases](https://github.com/tear360/QuizRevise/releases/latest), puis ouvre-le sur ton téléphone (autorise l'installation d'apps inconnues si Android le demande). Android 8.0+ requis.

Les mises à jour suivantes se font **directement dans l'app** : menu ⋮ → « Rechercher les mises à jour » (une vérification silencieuse a aussi lieu à chaque ouverture).

## 🏗️ Structure du projet

```
app/src/main/java/com/leov/quizrevise/
├── MainActivity.kt     Liste des paquets + stats + mises à jour
├── DeckActivity.kt     Gestion des cartes d'un paquet
├── StudyActivity.kt    Modes Flashcards et QCM
├── AppDatabase.kt      SQLite local (paquets, cartes, stats)
├── UpdateManager.kt    Auto-update via GitHub Releases
└── Models.kt           Deck, Card, utilitaires JSON

.github/workflows/release.yml   Build APK signé + release automatique à chaque tag v*
```

## 🔄 Comment fonctionne la mise à jour automatique

1. **Côté dépôt** : quand un tag `v*` est poussé (ex. `v1.0.1`), GitHub Actions compile l'APK en mode release, le signe avec le keystore du dépôt et publie une **GitHub Release** contenant `QuizRevise-v1.0.1.apk`.
2. **Côté app** : `UpdateManager` appelle `https://api.github.com/repos/tear360/QuizRevise/releases/latest`, compare le `tag_name` à la version installée (comparaison sémantique), puis télécharge l'APK via le **DownloadManager** natif et lance l'installation (avec la permission *installer des apps inconnues* demandée proprement).

⚠️ Pour qu'Android accepte une mise à jour par-dessus l'existant, l'APK doit être signé avec **la même clé** : c'est garanti ici par le keystore versionné `app/quizrevise.keystore` (alias `quizrevise`). Pour un projet sérieux, déplace ce keystore hors du dépôt et passe les mots de passe en *secrets* GitHub.

## 🚀 Publier une nouvelle version

```bash
git tag v1.0.1
git push origin v1.0.1
```

C'est tout : le workflow s'occupe du reste. La release apparaît quelques minutes plus tard avec l'APK signé, et toutes les apps installées la proposeront à l'utilisateur.

## 🛠️ Compiler en local

Prérequis : JDK 17+, Android SDK (API 36), ou simplement Android Studio.

```bash
./gradlew :app:assembleRelease
# APK : app/build/outputs/apk/release/app-release.apk
```

## 🧭 Inspiration

- [QuizFlow](https://github.com/douxxtech/QuizFlow) — style Quizlet, flashcards et modes de révision
- [anki (zlatanpham)](https://github.com/zlatanpham/anki) — concept de répétition espacée

## 🗺️ Pistes pour la suite

- Répétition espacée (algorithme type SM-2) et mode écrit (écrire la réponse)
- Import/export de paquets (CSV, format Quizlet)
- Partage de paquets par fichier/liens
- Mode sombre automatique (déjà prêt via Material 3 DayNight)
