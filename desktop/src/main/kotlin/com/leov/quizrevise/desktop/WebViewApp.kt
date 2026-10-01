package com.leov.quizrevise.desktop

import javafx.application.Platform
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.control.TextInputDialog
import javafx.scene.image.Image
import javafx.stage.FileChooser
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import netscape.javascript.JSObject
import org.json.JSONArray
import org.json.JSONObject

/** Petit utilitaire d'échappement JSON pour injecter des chaînes sûres dans le JS. */
object JSONizer {
    fun str(s: String): String = "\"" +
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}

/**
 * Application desktop : interface en HTML/CSS/JS (Material 3) rendue par le WebView JavaFX.
 * Le JS appelle l'API native via window.QuizReviseNative.
 * (Pas de classe javafx.application.Application : compatible classpath/jpackage.)
 */
object DesktopUi {

    private val db = DesktopDb()
    internal var lastEngine: WebEngine? = null

    /**
     * RÉFÉRENCE FORTE vers l'API exposée au JavaScript.
     *
     * La Javadoc de WebEngine est explicite : setMember(), JSObject.setSlot() et
     * JSObject.call() ne retiennent l'objet Java que par référence FAIBLE. Sans ce
     * champ, le garbage collector supprime NativeApi après quelques minutes
     * d'utilisation et TOUS les appels JS→Java échouent silencieusement :
     * boutons « qui ne font rien » (import, mise à jour), erreurs
     * « JSON Parse error: Unexpected identifier "undefined" » et « Script error. ».
     * C'était le bug central de la v1.4.4.
     */
    private var nativeApi: NativeApi? = null

    fun launch(uitest: Boolean = false) {
        Platform.startup {
            show()
            if (uitest) UiTest.run()
        }
        // Le thread JavaFX (non-daemon) maintient le processus en vie.
    }

    private fun show() {
        val stage = Stage()
        NativeApi.stageRef = stage
        val webView = WebView()
        webView.isContextMenuEnabled = false
        val engine = webView.engine

        try {
            DesktopUi::class.java.getResourceAsStream("/quizrevise.png")?.let { stage.icons.add(Image(it)) }
        } catch (_: Exception) { }

        engine.setOnError { err -> System.err.println("QuizRevise WebEngine error: ${err.message}") }

        engine.loadWorker.stateProperty().addListener(
            object : ChangeListener<Worker.State> {
                override fun changed(
                    obs: ObservableValue<out Worker.State>,
                    old: Worker.State,
                    state: Worker.State
                ) {
                    if (state == Worker.State.SUCCEEDED) {
                        try {
                            // nativeApi est conservé dans un champ static : voir la doc du champ.
                            nativeApi = NativeApi(engine)
                            (engine.executeScript("window") as JSObject)
                                .setMember("QuizReviseNative", nativeApi!!)
                            // La page n'affiche rien tant que nativeReady() n'est pas appelé.
                            engine.executeScript("nativeReady && nativeReady()")
                            val st = engine.executeScript(
                                "(function(){try{return document.getElementById('main').innerHTML.length>0?'RENDER_OK':'RENDER_EMPTY'}catch(e){return 'JS_ERR: '+e.message}})()"
                            )
                            println("QuizRevise UI: $st")
                        } catch (e: Exception) {
                            System.err.println("QuizRevise nativeReady error: ${e.message}")
                        }
                    }
                }
            }
        )
        engine.load(DesktopUi::class.java.getResource("/web/index.html").toExternalForm())

        // alert() / confirm() / prompt() du JS : dialogs natifs
        engine.setOnAlert { ev ->
            Platform.runLater {
                Alert(Alert.AlertType.INFORMATION, ev.data).apply { headerText = "QuizRévise" }.showAndWait()
            }
        }
        engine.setConfirmHandler { msg ->
            val r = Alert(Alert.AlertType.CONFIRMATION, msg).apply { headerText = "QuizRévise" }.showAndWait()
            r.orElse(ButtonType.CANCEL) == ButtonType.OK
        }
        engine.setPromptHandler { data ->
            val r = TextInputDialog(data.defaultValue ?: "").apply { headerText = data.message }.showAndWait()
            r.orElse("")
        }

        stage.title = "QuizRévise"
        stage.scene = Scene(webView, 900.0, 680.0)
        stage.minWidth = 640.0
        stage.minHeight = 520.0
        stage.show()
        lastEngine = engine
    }

    /**
     * API exposée au JavaScript. ATTENTION : la classe DOIT être publique,
     * sinon le pont JSObject échoue silencieusement (page bloquée sur « Chargement… »),
     * et l'instance DOIT être retenue par [nativeApi] sinon le GC la supprime
     * après quelques minutes (voir la doc de [nativeApi]).
     *
     * Aucune méthode ne laisse exception traverser le pont : [guard] capture tout,
     * affiche la cause réelle dans le bandeau de l'interface et renvoie un repli
     * toujours exploitable par le JavaScript.
     */
    class NativeApi(private val engine: WebEngine) {
        companion object {
            var stageRef: Stage? = null

            /** Palette des paquets — identique sur Android, PC et iPhone. */
            val DECK_COLORS = intArrayOf(
                0xFF6750A4.toInt(), 0xFF1B873B.toInt(), 0xFFB3261E.toInt(), 0xFF0B57D0.toInt(),
                0xFFE8590C.toInt(), 0xFF7A1FA2.toInt(), 0xFF00897B.toInt(), 0xFFC2185B.toInt(),
                0xFFF9A825.toInt(), 0xFF5D4037.toInt(), 0xFF3949AB.toInt(), 0xFF7CB342.toInt()
            )

            /** Analyse "#RRGGBB" (ou "RRGGBB") ; renvoie null si invalide. */
            fun colorFromHexOrNull(hex: String): Int? = try {
                0xFF000000.toInt() or Integer.parseInt(hex.trim().removePrefix("#"), 16)
            } catch (_: Exception) {
                null
            }
        }

        /** Entoure chaque appel natif : échec visible + valeur de repli, jamais d'exception. */
        private fun <T> guard(label: String, fallback: T, block: () -> T): T = try {
            block()
        } catch (t: Throwable) {
            val msg = label + " : " + (t.message ?: t.javaClass.simpleName)
            System.err.println("QuizRevise natif — $msg")
            Platform.runLater { callJsSafe("showError(" + JSONizer.str(msg) + ")") }
            fallback
        }

        fun decks(): String = guard("Liste des paquets indisponible", "[]") {
            val arr = JSONArray()
            db.decks().forEach { d ->
                arr.put(
                    JSONObject().put("id", d.id).put("name", d.name)
                        .put("color", d.color).put("cardCount", d.cardCount)
                )
            }
            arr.toString()
        }

        fun cards(deckId: Long): String = guard("Cartes indisponibles", "[]") {
            val arr = JSONArray()
            db.cards(deckId).forEach { c ->
                arr.put(JSONObject().put("id", c.id).put("question", c.question).put("answer", c.answer))
            }
            arr.toString()
        }

        fun createDeck(name: String, colorHex: String): Unit = guard("Création du paquet impossible", Unit) {
            val fallback = DECK_COLORS[db.decks().size % DECK_COLORS.size]
            db.createDeck(name, colorFromHexOrNull(colorHex) ?: fallback)
        }

        fun renameDeck(id: Long, name: String): Int = guard("Renommage impossible", -1) { db.renameDeck(id, name) }

        fun setDeckColor(id: Long, colorHex: String): Int = guard("Couleur du paquet impossible", -1) {
            val color = colorFromHexOrNull(colorHex)
            if (color == null) -1 else db.updateDeckColor(id, color)
        }
        fun deleteDeck(id: Long): Int = guard("Suppression impossible", -1) { db.deleteDeck(id) }
        fun addCard(deckId: Long, q: String, a: String): Long = guard("Ajout de carte impossible", -1L) { db.addCard(deckId, q, a) }
        fun updateCard(id: Long, q: String, a: String): Int = guard("Modification impossible", -1) { db.updateCard(id, q, a) }
        fun deleteCard(id: Long): Int = guard("Suppression impossible", -1) { db.deleteCard(id) }

        fun recordSession(total: Int, correct: Int): Unit = guard("Enregistrement de session impossible", Unit) {
            db.recordSession(total, correct)
        }

        /**
         * Exporte un paquet (deckId>0) ou tout (deckId<=0). Asynchrone : les boîtes de
         * dialogue ne doivent pas être ouvertes depuis le callback JS du WebView,
         * sinon le résultat est faux. Le résultat arrive au JS via exportDone(ok).
         */
        fun exportFile(deckId: Long, suggestedName: String) {
            Platform.runLater {
                try {
                    val chooser = FileChooser().apply {
                        title = "Exporter le paquet"
                        extensionFilters.add(FileChooser.ExtensionFilter("Paquet QuizRévise (*.qrevise)", "*.qrevise"))
                        initialFileName = suggestedName.ifBlank { "paquet" }
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".qrevise"
                    }
                    val file = chooser.showSaveDialog(stageRef)
                    if (file == null) { callJsSafe("exportDone(false)"); return@runLater }
                    val json = if (deckId > 0) Transfer.exportDeckJson(db, deckId) else Transfer.exportAllJson(db)
                    val ok = try {
                        file.writeText(json, Charsets.UTF_8); true
                    } catch (_: Exception) { false }
                    callJsSafe("exportDone($ok)")
                } catch (t: Throwable) {
                    System.err.println("QuizRevise export — $t")
                    callJsSafe("exportDone(false)")
                    callJsSafe("showError(" + JSONizer.str("Export impossible : " + (t.message ?: t)) + ")")
                }
            }
        }

        /** Importe des fichiers .qrevise (asynchrone) — résultat au JS via importDone(n) ou importFailed(msg). */
        fun importFile() {
            Platform.runLater {
                try {
                    // Mode test automatisé : pas de fenêtre de fichier, accusé direct.
                    if (System.getProperty("quizrevise.uitest") == "true") {
                        println("QuizRevise uitest: import sans dialogue")
                        callJsSafe("importDone(-1)")
                        return@runLater
                    }
                    val chooser = FileChooser().apply {
                        title = "Importer des paquets QuizRévise"
                        extensionFilters.add(FileChooser.ExtensionFilter("Paquets QuizRévise (*.qrevise)", "*.qrevise"))
                    }
                    val files = chooser.showOpenMultipleDialog(stageRef)
                    if (files == null) { callJsSafe("importDone(-1)"); return@runLater }
                    var total = 0
                    for (f in files) {
                        try { total += Transfer.importJson(db, f.readText(Charsets.UTF_8)) } catch (_: Exception) { }
                    }
                    callJsSafe("importDone($total)")
                } catch (t: Throwable) {
                    System.err.println("QuizRevise import — $t")
                    callJsSafe("importFailed(" + JSONizer.str(t.message ?: t.javaClass.simpleName) + ")")
                }
            }
        }

        fun stats(): String = guard(
            "Statistiques indisponibles",
            "{\"todayReviews\":0,\"accuracy\":0,\"streak\":0,\"best\":0}"
        ) {
            val (reviews, correct) = db.todayStats()
            JSONObject()
                .put("todayReviews", reviews)
                .put("accuracy", if (reviews > 0) correct * 100 / reviews else 0)
                .put("streak", db.currentStreak())
                .put("best", db.bestStreak())
                .toString()
        }

        fun version(): String = BuildConfig.VERSION

        /**
         * Vérification ASYNCHRONE (jamais sur le thread UI — c'était la cause des
         * freezes) : le résultat est poussé au JS via updateCheckResult(json).
         * Le thread est protégé : le JS reçoit TOUJOURS une réponse.
         */
        fun checkUpdatesAsync(manual: Boolean) {
            Thread {
                val result = try {
                    val release = GitHubUpdater.fetchLatest()
                    when {
                        release == null -> "error"
                        GitHubUpdater.isNewer(release.version, BuildConfig.VERSION) -> release.version
                        else -> "up_to_date"
                    }
                } catch (t: Throwable) {
                    System.err.println("QuizRevise màj — $t")
                    "error"
                }
                println("QuizRevise màj: réponse = $result (manuel=$manual)")
                Platform.runLater { callJsSafe("updateCheckResult('$result', $manual)") }
            }.start()
        }

        /**
         * Télécharge l'installateur de la dernière version depuis GitHub puis le lance.
         * Tout se passe en arrière-plan ; l'UI est pilotée via updateProgress/updateDone/updateFailed.
         */
        fun downloadAndInstall() {
            Thread {
                try {
                    val release = GitHubUpdater.fetchLatest()
                    if (release == null) {
                        Platform.runLater { callJsSafe("updateFailed('Version introuvable (connexion ?)')") }
                        return@Thread
                    }
                    if (!GitHubUpdater.isNewer(release.version, BuildConfig.VERSION)) {
                        Platform.runLater { callJsSafe("updateFailed('Vous avez déjà la dernière version.')") }
                        return@Thread
                    }
                    val url = release.downloadUrl
                    if (url == null) {
                        Platform.runLater { callJsSafe("updateFailed('Aucun installateur disponible pour votre système dans cette release.')") }
                        return@Thread
                    }

                    val os = System.getProperty("os.name").lowercase()
                    val ext = if (os.contains("win")) ".exe" else ".deb"
                    val dest = java.io.File(
                        System.getProperty("java.io.tmpdir"),
                        "QuizRevise-Windows-Setup-v${release.version}$ext".let {
                            if (os.contains("win")) it else "QuizRevise-Linux-deb-v${release.version}$ext"
                        }
                    )

                    val ok = GitHubUpdater.download(url, dest) { pct ->
                        Platform.runLater { callJsSafe("updateProgress($pct)") }
                    }
                    if (ok == null) {
                        Platform.runLater { callJsSafe("updateFailed('Échec du téléchargement.')") }
                        return@Thread
                    }
                    val launched = runInstaller(dest, os)
                    Platform.runLater {
                        if (launched) {
                            callJsSafe("updateDone(true)")
                            exitForUpgrade()
                        } else {
                            callJsSafe("updateFailed('Installateur lancé manuellement si besoin : ' + ${JSONizer.str(dest.absolutePath)})")
                        }
                    }
                } catch (t: Throwable) {
                    System.err.println("QuizRevise installation — $t")
                    Platform.runLater {
                        callJsSafe("updateFailed(" + JSONizer.str(t.message ?: "Erreur inattendue") + ")")
                    }
                }
            }.start()
        }

        /** Lance l'installateur téléchargé (élévation UAC / pkexec gérées par l'OS). */
        private fun runInstaller(file: java.io.File, os: String): Boolean = try {
            val cmd = if (os.contains("win"))
                arrayOf("cmd", "/c", "start", "", file.absolutePath)
            else
                arrayOf("bash", "-c", "pkexec dpkg -i '${file.absolutePath}' 2>/dev/null || x-terminal-emulator -e 'sudo dpkg -i \"${file.absolutePath}\"' 2>/dev/null || xterm -e 'sudo dpkg -i \"${file.absolutePath}\"'")
            Runtime.getRuntime().exec(cmd)
            true
        } catch (_: Exception) {
            false
        }

        /** Ferme l'application proprement pour laisser l'installateur remplacer les fichiers. */
        private fun exitForUpgrade() {
            Thread {
                Thread.sleep(1500)
                Platform.exit()
            }.start()
        }

        private fun callJsSafe(js: String) {
            try { engine.executeScript(js) } catch (_: Exception) { }
        }

        fun openReleases() =
            GitHubUpdater.openDownloadPage("https://github.com/${GitHubUpdater.REPO}/releases/latest")
    }
}
