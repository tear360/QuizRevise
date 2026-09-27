# QuizRévise 🎴

Application de révision type **Quizlet**, multi-plateformes : crée tes paquets de cartes, révise avec des **flashcards** ou des **QCM**, suis tes **statistiques**.

| Plateforme | Paquet publié | Mise à jour |
|---|---|---|
| 🤖 **Android 5.0 → 16** | `QuizRevise-Android-vX.apk` (universel : arm32/64, x86) | ✅ **Automatique in-app** via GitHub Releases |
| 🪟 **Windows 10/11** | `QuizRevise-Setup-vX.exe` (installateur natif : raccourcis, désinstallation, JRE embarqué) | 🔎 Vérification intégrée → ouvre la page des releases |
| 🐧 **Linux** Debian/Ubuntu/Mint | `QuizRevise-Linux-deb-vX.deb` | 🔎 Vérification intégrée |
| 🐧 **Linux** Fedora & autres | `QuizRevise-Linux-vX.tar.gz` | 🔎 Vérification intégrée |
| 🍎 **iOS 15+** | `QuizRevise-iOS-unsigned-vX.ipa` (non signée, à sideloader) | 🔎 Vérification intégrée → ouvre la page (contrainte Apple) |

> Version d'essai **v1.1.0** — fonctionnelle, hors-ligne, sans publicité.

## ✨ Fonctionnalités (identiques sur toutes les plateformes)

- 📚 Paquets de cartes illimités, avec couleur et renommage
- 🃏 **Mode Flashcards** : retourne la carte, puis « Je savais » / « Pas su »
- ❓ **Mode QCM** : 4 choix générés automatiquement à partir des autres cartes
- 📊 **Statistiques** : série de jours consécutifs (streak), précision, cartes revues du jour
- 🇫🇷 Interface 100 % en français
- 📴 100 % hors-ligne (SQLite / JSON local)

## 📥 Installation

### 🤖 Android
Télécharge l'APK depuis la page [Releases](https://github.com/tear360/QuizRevise/releases/latest) et ouvre-le sur ton téléphone (autorise l'installation d'apps inconnues si demandé). Les mises à jour suivantes se font **directement dans l'app** : menu ⋮ → « Rechercher les mises à jour » (vérification silencieuse aussi à chaque ouverture).

### 🪟 Windows 10/11
1. Télécharge `QuizRevise-Setup-vX.exe` et lance-le : l'app **s'installe dans le système** (répertoire au choix), crée un **raccourci sur le Bureau** et une entrée dans le **menu Démarrer**
2. Désinstalle-la comme n'importe quelle application (Paramètres → Applications)
3. Un JRE est **embarqué** : rien d'autre à installer. L'installateur n'étant pas signé, SmartScreen peut afficher un avertissement — clique « Exécuter quand même »
> Les versions futures se remplacent automatiquement (même produit d'installation). Tes données (dans `%APPDATA%\QuizRevise`) sont conservées.

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

### 🍎 iOS (sideload — compte Apple gratuit OK)
Apple interdisant l'installation hors App Store, l'IPA est **non signée** : installe-la avec [AltStore](https://altstore.io) ou [Sideloadly](https://sideloadly.io) (compte Apple ID gratuit, re-signature tous les 7 jours) — ou un compte développeur (99 €/an, validité 1 an). L'app vérifie les nouveautés sur GitHub et ouvre la page de téléchargement, mais ne peut pas s'auto-mettre à jour (contrainte Apple).

## 🏗️ Structure du projet

```
app/        Android (Kotlin, Material 3) — APK signée + auto-update
desktop/    Windows/Linux (Kotlin + Swing, SQLite) — jar gras packagé via jpackage
ios/        iOS (SwiftUI, xcodegen) — IPA non signée
tools/      Générateur d'icônes
.github/workflows/release.yml   4 builds en parallèle + release unique
```

## 🔄 Mises à jour automatiques

- **Android** : l'app interroge `api.github.com/repos/tear360/QuizRevise/releases/latest`, compare les versions, télécharge l'APK (DownloadManager) et lance l'installation. Le keystore `app/quizrevise.keystore` (versionné) garantit la même signature entre les versions — indispensable pour que la mise à jour s'installe par-dessus l'existant. ⚠️ Pour un projet sérieux, déplace-le en *GitHub Secrets*.
- **Desktop/iOS** : vérification de version intégrée + ouverture de la page des releases (pas d'auto-installation possible sur ces plateformes).

## 🚀 Publier une nouvelle version (les 4 plateformes d'un coup)

```bash
git tag v1.1.1
git push origin v1.1.1
```

Les builds Android, Windows, Linux et iOS tournent **en parallèle** (~10 min), puis une release unique est publiée avec tous les fichiers.

## 🛠️ Compiler en local

```bash
# Android (JDK 17 + SDK 36)
./gradlew :app:assembleRelease

# Desktop : jar autonome + tests
./gradlew :desktop:jar
java -jar desktop/build/libs/desktop-1.1.0.jar --selftest

# iOS (macOS + xcodegen)
cd ios && xcodegen generate && xcodebuild -project QuizRevise.xcodeproj -scheme QuizRevise build
```

## 🧭 Inspiration

- [QuizFlow](https://github.com/douxxtech/QuizFlow) — style Quizlet, flashcards et modes de révision
- [anki (zlatanpham)](https://github.com/zlatanpham/anki) — concept de répétition espacée

## 🗺️ Pistes pour la suite

- Répétition espacée (algorithme type SM-2) et mode écrit (écrire la réponse)
- Import/export de paquets (CSV, format Quizlet)
- Mode sombre automatique (déjà prêt côté Android via Material 3 DayNight)
