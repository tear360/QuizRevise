# Changelog

Toutes les évolutions notables de QuizRévise sont documentées ici.
Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et respecte le [versionnement sémantique](https://semver.org/lang/fr/).
À chaque release, la section correspondante est publiée automatiquement dans les notes de la GitHub Release.

## [1.3.0] - 2026-09-27

### Ajouts
- 🪟 **Vrai installateur Windows `.exe`** : installe l'application dans le système, crée les raccourcis **Bureau** et **menu Démarrer**, avec désinstallation depuis les réglages Windows (JRE embarqué, rien d'autre à installer)
- 🐧 Le paquet `.deb` Linux installe désormais l'**icône** de l'app dans le menu des applications
- Les futures versions Windows se remplacent automatiquement (produit d'installation stable)

### Technique
- `jpackage --type exe` avec WiX 3.14 en CI ; UUID produit fixe pour les mises à jour par-dessus l'existant
- Icônes `.ico` / `.png` générées depuis le même design que l'app Android (`tools/GenIco.java`)

## [1.2.0] - 2026-09-27

### Améliorations
- 🖥️ **Interface desktop refonte** (Windows/Linux) fidèle au design Material 3 d'Android : header violet, cartes arrondies, boutons pill, FAB violet, look FlatLaf
- 🃏 **Retournement illimité des flashcards** : appuie autant de fois que tu veux pour alterner question ↔ réponse
- ✨ **Vraie animation de retournement** (rotation 3D) sur Android, iOS et desktop — fini le simple changement de texte

## [1.1.1] - 2026-09-27

### Améliorations
- Le **changelog est désormais intégré aux notes de release** : chaque version publie sa section ici automatiquement
- Garantie **un jeu d'assets par version** : un asset par plateforme, nommé avec le numéro de version, écrasé proprement en cas de re-publication

## [1.1.0] - 2026-09-27

### Ajouts
- 🪟 Version **Windows 10/11** : zip autonome avec JRE embarqué, rien à installer (`jpackage`)
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
