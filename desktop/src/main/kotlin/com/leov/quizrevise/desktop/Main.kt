package com.leov.quizrevise.desktop

import javax.swing.SwingUtilities
import javax.swing.UIManager

fun main(args: Array<String>) {
    // Mode self-test : exécute des tests de la base et de la logique updater, puis quitte.
    // Utilisé par la CI pour valider le module desktop sans interface graphique.
    if (args.contains("--selftest")) {
        SelfTest.run()
        return
    }

    try {
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatIntelliJLaf")
    } catch (_: Exception) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) } catch (_: Exception) { }
    }

    SwingUtilities.invokeLater {
        MainWindow().isVisible = true
    }
}
