# QuizRévise 🎴

Application de révision type **Quizlet**, multi-plateformes : créez vos paquets de cartes, révisez avec des **flashcards** ou des **QCM**, et suivez vos **statistiques** — avec des mises à jour automatiques depuis GitHub sur Android.

| Plateforme | Paquet publié | Mises à jour |
|---|---|---|
| 🤖 **Android 5.0 → 16** | `QuizRevise-Android-vX.apk` (universel : arm32/64, x86) | ✅ **Automatiques in-app** via GitHub Releases |
| 🪟 **Windows 10/11** | `QuizRevise-Windows-Setup-vX.exe` (installateur : raccourcis, désinstallation, JRE embarqué) | ✅ **Téléchargement + lancement de l'installateur depuis l'application** |
| 🐧 **Linux** Debian/Ubuntu/Mint | `QuizRevise-Linux-deb-vX.deb` | 🔎 Vérification intégrée |
| 🐧 **Linux** Fedora & autres | `QuizRevise-Linux-vX.tar.gz` | 🔎 Vérification intégrée |
| 🍎 **iOS 15+** | `QuizRevise-iOS-unsigned-vX.ipa` (non signée, à sideloader) | 🔎 Vérification intégrée → ouvre la page (contrainte Apple) |

> Version d'essai **v1.4.0** — fonctionnelle, hors-ligne, sans publicité.

## ✨ Fonctionnalités (identiques sur toutes les plateformes)

- 📚 Paquets de cartes illimités, avec couleur et renommage
- 🃏 **Mode Flashcards** : retournement illimité avec animation 3D, puis « Je savais » / « Pas su »
- ❓ **Mode QCM** : 4 choix générés automatiquement à partir des autres cartes
- 📊 **Statistiques** : série de jours consécutifs (streak), précision, cartes revues du jour
- 📦 **Import / Export** de paquets au format `.qrevise`, compatible entre tous les appareils
- 🇫🇷 Interface 100 % en français (Material 3 sur Android, interface web intégrée sur desktop)
- 📴 100 % hors-ligne (SQLite / JSON local)

## 📥 Installation

### 🤖 Android
Téléchargez l'APK depuis la page [Releases](https://github.com/tear360/QuizRevise/releases/latest), puis ouvrez-le sur l'appareil (autorisez l'installation d'applications inconnues si demandé). Les mises à jour suivantes se font **directement dans l'application** : menu ⋮ → « Rechercher les mises à jour » (une vérification silencieuse a aussi lieu à chaque ouverture).

### 🪟 Windows 10/11
1. Téléchargez `QuizRevise-Windows-Setup-vX.exe` et lancez-le : l'application **s'installe dans le système** (répertoire au choix), crée un **raccourci sur le Bureau** et une entrée dans le **menu Démarrer**
2. Désinstallez-la comme n'importe quelle application (Paramètres → Applications)
3. Un JRE est **embarqué** : rien d'autre à installer. L'installateur n'étant pas signé, SmartScreen peut afficher un avertissement — cliquez « Exécuter quand même »
> Les versions futures se remplacent automatiquement (même produit d'installation). Les données (dans `%APPDATA%\QuizRevise`) sont conservées.

### 🐧 Linux
**Debian / Ubuntu / Mint :**
```bash
sudo apt install ./QuizRevise-Linux-deb-vX.deb
/opt/quizrevise/bin/QuizRevise   # ou depuis le menu des applications
```
**Fedora / Arch / autres :**
```bash
tar -xzf QuizRevise-Linux-vX.tar.gz
./QuizRevise/bin/QuizRevise
```
Données stockées dans `~/.local/share/QuizRevise/`.

### 🍎 iOS (sideload — compte Apple gratuit suffisant)
Apple interdisant l'installation hors App Store, l'IPA est **non signée** : installez-la avec [AltStore](https://altstore.io) ou [Sideloadly](https://sideloadly.io) (compte Apple ID gratuit, re-signature tous les 7 jours) — ou avec un compte développeur (99 €/an, validité 1 an). L'application vérifie les nouveautés sur GitHub et ouvre la page de téléchargement, mais ne peut pas s'auto-mettre à jour (contrainte Apple).

## 🏗️ Structure du projet

```
app/        Android (Kotlin, Material 3) — APK signée + auto-update
desktop/    Windows/Linux (Kotlin + WebView JavaFX, interface HTML/CSS) — installateur exe / deb
ios/        iOS (SwiftUI, xcodegen) — IPA non signée
tools/      Générateurs d'icônes
.github/workflows/release.yml   4 builds en parallèle + release unique
```

## 📦 Import / Export de paquets (format `.qrevise`)

Partagez vos paquets entre tous vos appareils : **exporté sur Windows → importé sur Android, iPhone ou un autre PC**, sans perte (nom, couleur, cartes).

- **Android** : appui long sur un paquet → « Exporter », ou menu ⋮ → « Importer / Exporter tous »
- **Windows/Linux** : bouton « Exporter » sur chaque paquet, ou icônes import/export dans l'en-tête
- **iOS** : menu ⋮ → « Importer / Exporter » (partage via Fichiers, AirDrop, e-mail…)

Le format est un JSON lisible et versionné :
```json
{
  "format": "quizrevise",
  "version": 1,
  "exported": "2026-09-27T18:30:00+02:00",
  "decks": [
    {
      "name": "Anglais – Vocabulaire",
      "color": "#6750A4",
      "cards": [ { "q": "cat", "a": "chat" } ]
    }
  ]
}
```
Les futures versions de l'application resteront compatibles (champs additionnels ignorés proprement, `version` permettant les migrations).

## 🔄 Mises à jour automatiques

- **Android** : l'application interroge `api.github.com/repos/tear360/QuizRevise/releases/latest`, compare les versions, télécharge l'APK (DownloadManager) et lance l'installation. Le keystore `app/quizrevise.keystore` (versionné) garantit la même signature entre les versions — indispensable pour que la mise à jour s'installe par-dessus l'existant. ⚠️ Pour un projet sérieux, déplacez-le en *GitHub Secrets*.
- **Windows/Linux** : vérification intégrée, **téléchargement automatique** de l'installateur adapté au système avec barre de progression, puis lancement de l'installation (l'application se ferme pour laisser l'installateur remplacer les fichiers). Sur Windows, confirmez simplement l'élévation UAC.
- **iOS** : vérification de version intégrée + ouverture de la page des releases (pas d'auto-installation possible sur cette plateforme).

## 🚀 Publier une nouvelle version (les 4 plateformes d'un coup)

Ajouter une section au `CHANGELOG.md` pour la version, puis :

```bash
git tag v1.4.1
git push origin v1.4.1
```

Les builds Android, Windows, Linux et iOS tournent **en parallèle** (~10 min), puis une release unique est publiée avec les notes extraites du changelog et un asset par plateforme.

## 🛠️ Compiler en local

```bash
# Android (JDK 17 + SDK 36)
./gradlew :app:assembleRelease

# Desktop : jar autonome + tests
./gradlew :desktop:jar
java -jar desktop/build/libs/desktop-1.4.0.jar --selftest

# iOS (macOS + xcodegen)
cd ios && xcodegen generate && xcodebuild -project QuizRevise.xcodeproj -scheme QuizRevise build
```

## 🧭 Inspiration

- [QuizFlow](https://github.com/douxxtech/QuizFlow) — style Quizlet, flashcards et modes de révision
- [anki (zlatanpham)](https://github.com/zlatanpham/anki) — concept de répétition espacée

## 🗺️ Pistes pour la suite

- Répétition espacée (algorithme type SM-2) et mode écrit (écrire la réponse)
- Import du format CSV Quizlet en complément du format `.qrevise`
- Mode sombre automatique (déjà prêt côté Android via Material 3 DayNight)
