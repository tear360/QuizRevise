# Changelog

Toutes les évolutions notables de QuizRévise sont documentées ici.
Le format s'inspire de [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et respecte le [versionnement sémantique](https://semver.org/lang/fr/).
À chaque release, la section correspondante est publiée automatiquement dans les notes de la GitHub Release.

## [1.4.5] - 2026-09-27

### Corrections (desktop)
- 🔧 **Correction critique : après quelques minutes d'utilisation, les boutons cessaient de répondre** (création de paquet, import, statistiques, mise à jour). L'objet qui relie l'interface au moteur n'était retenu que par une référence faible : le ramasse-miettes le supprimait et tous les appels devenaient silencieusement muets — d'où les erreurs « JSON Parse error: Unexpected identifier "undefined" », « ⚠️ Erreur JS : Script error. » et les boutons « qui ne font rien ». Le pont est désormais conservé explicitement et chaque appel natif est protégé.
- ⚠️ **Les erreurs techniques affichent leur cause réelle** dans le bandeau en haut de l'écran, au lieu d'un message incompréhensible.
- 🔄 La vérification de mise à jour **annonce immédiatement qu'elle est en cours** et prévient si aucune réponse n'arrive sous 15 secondes : plus jamais de clic sans effet.
- 🖼️ **Plus de carrés □ dans l'en-tête** : tous les pictogrammes de l'interface sont désormais des icônes vectorielles (le moteur de police du WebView ne rendait pas les emoji), et le rendu de police natif Windows est rétabli.
- 🤖 Tests automatisés étendus à **14 vérifications**, dont une pression sur le ramasse-miettes qui reproduit exactement ce bug, l'aller-retour complet de la vérification de mise à jour et de l'import, et un nom de paquet contenant emoji + guillemets — toutes validées sur Windows.

## [1.4.4] - 2026-09-27

### Corrections (desktop)
- 📤 **Boutons d'import/export désormais explicites** : « 📥 Importer » et « 📤 Exporter tout » en clair dans l'en-tête (l'ancienne icône était illisible)
- ⚠️ **Plus aucune erreur silencieuse** : tout problème s'affiche désormais dans un bandeau rouge en haut de l'écran, et les formulaires signalent les champs manquants ou une erreur technique directement dans la fenêtre
- ⌨️ La touche **Entrée** valide les formulaires (« Enregistrer »)
- 🤖 Nouveau mode de test automatisé qui pilote la vraie application (clics réels) : 8 vérifications — création, enregistrement, affichage, statistiques, retour, suppression — toutes validées sur Windows

## [1.4.3] - 2026-09-27

### Corrections (desktop)
- 🚀 **Écran « Chargement… » infini résolu** : le pont entre l'interface et le moteur ne se monte plus en silence — diagnostique en console et message d'erreur clair à l'écran si un problème survient
- 📥 **L'import de fichiers `.qrevise` fonctionne à nouveau** : la fenêtre de sélection de fichier s'ouvre bien (elle était court-circuitée par le WebView), puis le nombre de paquets importés est annoncé
- 📤 L'export utilise le même mécanisme fiable

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
