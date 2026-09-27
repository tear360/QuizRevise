package com.leov.quizrevise.desktop

fun main(args: Array<String>) {
    // Mode self-test : tests de la base et de la logique updater, sans interface graphique.
    if (args.contains("--selftest")) {
        SelfTest.run()
        return
    }
    // Interface = page HTML/CSS/JS rendue dans un WebView JavaFX (voir DesktopUi).
    val uitest = args.contains("--uitest")
    if (uitest) {
        // Import/export sans fenêtre de fichier pour le test automatisé.
        System.setProperty("quizrevise.uitest", "true")
    }
    // Les polices sont rendues par DirectWrite (défaut Windows) : le rendu T2K
    // forcé affichait des carrés □ à la place des pictogrammes SVG/emoji.
    DesktopUi.launch(uitest = uitest)
}
