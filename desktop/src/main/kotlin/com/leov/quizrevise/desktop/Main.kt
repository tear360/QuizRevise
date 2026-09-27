package com.leov.quizrevise.desktop

fun main(args: Array<String>) {
    // Mode self-test : tests de la base et de la logique updater, sans interface graphique.
    if (args.contains("--selftest")) {
        SelfTest.run()
        return
    }
    // Interface = page HTML/CSS/JS rendue dans un WebView JavaFX (voir DesktopUi).
    System.setProperty("prism.lcdtext", "false")
    System.setProperty("prism.text", "t2k")
    DesktopUi.launch(uitest = args.contains("--uitest"))
}
