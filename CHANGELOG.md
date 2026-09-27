# Changelog

Toutes les évolutions notables de QuizRévise sont documentées ici.
Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et respecte le [versionnement sémantique](https://semver.org/lang/fr/).
À chaque release, la section correspondante est publiée automatiquement dans les notes de la GitHub Release.

## [1.4.2] - 2026-09-27

### Corrections
- 🖥️ **Interface desktop réparée** : plus aucune donnée de démonstration ne peut apparaître dans l'application (l'interface n'affiche rien tant que le moteur n'est pas prêt), boîtes de dialogue « Nouveau paquet / Ajouter une carte » à nouveau fonctionnelles, et tous les boutons répondent — y compris les choix du QCM contenant une apostrophe
- ↩️ Bouton **Retour présent sur tous les écrans** (statistiques, révision, paquet)
- ⚡ La vérification de mise à jour ne **gèle plus l'interface** : elle s'effectue en arrière-plan
- 🪟 Flux d'installation des mises à jour rendu plus robuste, avec messages d'erreur précis et repli vers la page GitHub si besoin

## [1.4.1] - 2026-09-27

### Améliorations
- 🪟🐧 **Mise à jour automatique complète sur Windows et Linux** : l'application télécharge elle-même le bon installateur depuis GitHub (barre de progression intégrée), le lance, puis se ferme pour laisser l'installation se faire — plus besoin de retourner sur la page des releases
- Détection automatique du fichier adapté au système (`.exe` sur Windows, `.deb` sur Debian/Ubuntu/Mint)

## [1.4.0] - 2026-09-27

### Ajouts
- 🖥️ **Interface desktop entièrement repensée en HTML/CSS** (rendue par un WebView intégré) : même design Material 3 que l'application Android, flashcard en **vraie 3D CSS**, boutons pill, FAB, dialogues modernes
- 📦 **Format d'échange `.qrevise`** : exportez/importez vos paquets entre **tous vos appareils** (Windows ↔ Android ↔ iPhone ↔ Linux) sans perte — nom, couleur et cartes conservés, JSON versionné et lisible
- 📱 Android : export par appui long sur un paquet + import/export global dans le menu ⋮
- 🍎 iOS : import via le sélecteur Fichiers, export via la feuille de partage (AirDrop, e-mail…)

### Technique
- Desktop : migration Swing → WebView JavaFX (WebKit embarqué), pont JS ↔ Kotlin synchrone, tests aller-retour `.qrevise` dans le selftest

## [1.3.0] - 2026-09-27

### Ajouts
- 🪟 **Véritable installateur Windows `.exe`** : installe l'application dans le système, crée les raccourcis **Bureau** et **menu Démarrer**, avec désinstallation depuis les réglages Windows (JRE embarqué, rien d'autre à installer)
- 🐧 Le paquet `.deb` Linux installe désormais l'**icône** de l'application dans le menu des applications
- Les futures versions Windows se remplacent automatiquement (produit d'installation stable)

### Technique
- `jpackage --type exe` avec WiX 3.14 en CI ; UUID produit fixe pour les mises à jour par-dessus l'existant
- Icônes `.ico` / `.png` générées depuis le même design que l'application Android (`tools/GenIco.java`)

## [1.2.0] - 2026-09-27

### Améliorations
- 🖥️ **Interface desktop repensée** (Windows/Linux) fidèle au design Material 3 d'Android : en-tête violet, cartes arrondies, boutons pill, FAB violet
- 🃏 **Retournement illimité des flashcards** : chaque appui alterne question ↔ réponse, autant de fois que souhaité
- ✨ **Vraie animation de retournement** (rotation 3D) sur Android, iOS et desktop

## [1.1.1] - 2026-09-27

### Améliorations
- Le **changelog est désormais intégré aux notes de release** : chaque version publie sa section ici automatiquement
- Garantie **un jeu d'assets par version** : un asset par plateforme, nommé avec le numéro de version, écrasé proprement en cas de re-publication

## [1.1.0] - 2026-09-27

### Ajouts
- 🪟 Version **Windows 10/11** : archive autonome avec JRE embarqué, rien à installer (`jpackage`)
- 🐧 Versions **Linux** : paquet `.deb` (Debian/Ubuntu/Mint) et archive générique (Fedora & autres)
- 🍎 Version **iOS** (SwiftUI) : IPA non signée à sideloader (AltStore/Sideloadly), vérification de mises à jour ouvrant la page GitHub
- 🤖 Compatibilité **Android élargie : 5.0 → 16** (icônes legacy générées, une seule APK universelle tous processeurs)
- Vérification de mise à jour intégrée côté desktop/iOS

### Technique
- CI multi-plateformes : 4 jobs parallèles (android, windows, linux, ios) + release unique multi-assets
- Self-test desktop (12 assertions : base SQLite, comparaison de versions)
- Librairies Android alignées pour support API 21+ (Material 1.12, AppCompat 1.6.1)

## [1.0.0] - 2026-09-27

### Ajouts
- Première version d'essai Android
- Paquets de cartes illimités (couleurs, renommage, suppression)
- Modes de révision : **Flashcards** (je savais / pas su) et **QCM** (4 choix générés)
- Statistiques : série de jours consécutifs, précision, cartes revues du jour
- **Mise à jour automatique** via GitHub Releases (vérification à l'ouverture + menu)
- Interface 100 % française, Material 3, hors-ligne (SQLite)
