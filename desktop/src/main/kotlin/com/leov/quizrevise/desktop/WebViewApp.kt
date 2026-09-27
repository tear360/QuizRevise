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

/**
 * Application desktop : interface en HTML/CSS/JS (Material 3) rendue par le WebView JavaFX.
 * Le JS appelle l'API native via window.QuizReviseNative.
 * (Pas de classe javafx.application.Application : compatible classpath/jpackage.)
 */
object DesktopUi {

    private val db = DesktopDb()

    fun launch() {
        Platform.startup { show() }
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

        engine.loadWorker.stateProperty().addListener(
            object : ChangeListener<Worker.State> {
                override fun changed(
                    obs: ObservableValue<out Worker.State>,
                    old: Worker.State,
                    state: Worker.State
                ) {
                    if (state == Worker.State.SUCCEEDED) {
                        (engine.executeScript("window") as JSObject)
                            .setMember("QuizReviseNative", NativeApi(engine))
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
    }

    /** API exposée au JavaScript (synchrone, appelée depuis le thread FX). */
    private class NativeApi(private val engine: WebEngine) {
        companion object {
            var stageRef: Stage? = null
        }

        fun decks(): String {
            val arr = JSONArray()
            db.decks().forEach { d ->
                arr.put(
                    JSONObject().put("id", d.id).put("name", d.name)
                        .put("color", d.color).put("cardCount", d.cardCount)
                )
            }
            return arr.toString()
        }

        fun cards(deckId: Long): String {
            val arr = JSONArray()
            db.cards(deckId).forEach { c ->
                arr.put(JSONObject().put("id", c.id).put("question", c.question).put("answer", c.answer))
            }
            return arr.toString()
        }

        fun createDeck(name: String) {
            val colors = intArrayOf(
                0xFF6750A4.toInt(), 0xFF1B873B.toInt(), 0xFFB3261E.toInt(),
                0xFF0B57D0.toInt(), 0xFFE8590C.toInt(), 0xFF7A1FA2.toInt()
            )
            db.createDeck(name, colors[db.decks().size % colors.size])
        }

        fun renameDeck(id: Long, name: String) = db.renameDeck(id, name)
        fun deleteDeck(id: Long) = db.deleteDeck(id)
        fun addCard(deckId: Long, q: String, a: String) = db.addCard(deckId, q, a)
        fun updateCard(id: Long, q: String, a: String) = db.updateCard(id, q, a)
        fun deleteCard(id: Long) = db.deleteCard(id)

        fun recordSession(total: Int, correct: Int) = db.recordSession(total, correct)

        /** Exporte un paquet (deckId>0) ou tout (deckId<=0) vers un fichier .qrevise choisi par l'utilisateur. Renvoie true si exporté. */
        fun exportFile(deckId: Long, suggestedName: String): Boolean {
            val chooser = FileChooser().apply {
                title = "Exporter le paquet"
                extensionFilters.add(FileChooser.ExtensionFilter("Paquet QuizRévise (*.qrevise)", "*.qrevise"))
                initialFileName = suggestedName.ifBlank { "paquet" }
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".qrevise"
            }
            val file = chooser.showSaveDialog(stageRef) ?: return false
            val json = if (deckId > 0) Transfer.exportDeckJson(db, deckId) else Transfer.exportAllJson(db)
            return try {
                file.writeText(json, Charsets.UTF_8)
                true
            } catch (_: Exception) { false }
        }

        /** Importe un ou plusieurs fichiers .qrevise, renvoie le nombre de paquets ajoutés (ou -1 si annulé). */
        fun importFile(): Int {
            val chooser = FileChooser().apply {
                title = "Importer des paquets QuizRévise"
                extensionFilters.add(FileChooser.ExtensionFilter("Paquets QuizRévise (*.qrevise)", "*.qrevise"))
            }
            val files = chooser.showOpenMultipleDialog(stageRef) ?: return -1
            var total = 0
            for (f in files) {
                try { total += Transfer.importJson(db, f.readText(Charsets.UTF_8)) } catch (_: Exception) { }
            }
            return total
        }

        fun stats(): String {
            val (reviews, correct) = db.todayStats()
            return JSONObject()
                .put("todayReviews", reviews)
                .put("accuracy", if (reviews > 0) correct * 100 / reviews else 0)
                .put("streak", db.currentStreak())
                .put("best", db.bestStreak())
                .toString()
        }

        fun version(): String = BuildConfig.VERSION

        /** Renvoie la nouvelle version dispo, "up_to_date", ou "error". */
        fun checkUpdates(): String {
            val release = GitHubUpdater.fetchLatest() ?: return "error"
            return if (GitHubUpdater.isNewer(release.version, BuildConfig.VERSION)) release.version else "up_to_date"
        }

        fun openReleases() =
            GitHubUpdater.openDownloadPage("https://github.com/${GitHubUpdater.REPO}/releases/latest")
    }
}
