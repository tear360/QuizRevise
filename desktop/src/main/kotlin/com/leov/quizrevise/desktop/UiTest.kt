package com.leov.quizrevise.desktop

import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.application.Platform
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.util.Duration

/**
 * Test d'interface automatisé sur la VRAIE application (--uitest) :
 * clique réellement via le DOM et vérifie chaque étape.
 * Sortie console PASS/FAIL ; code de retour 0 (tout OK) ou 1 (échec).
 */
object UiTest {

    private val results = ArrayList<String>()

    private class Step(val delayFromStartMs: Double, val code: String, val label: String, val verify: (String) -> Boolean)

    fun run() {
        val engine = DesktopUi.lastEngine
        if (engine == null) {
            println("FAIL aucun WebView disponible")
            Platform.exit()
            return
        }

        val steps = listOf(
            Step(100.0,
                "(function(){return document.getElementById('main').innerHTML.indexOf('Chargement')===-1 && (document.querySelector('.card')!==null || document.querySelector('.empty')!==null) ? 'OK' : 'KO'})()",
                "écran paquets rendu") { it == "OK" },
            Step(400.0,
                "(function(){try{document.getElementById('fab').click();return document.getElementById('modalBack').style.display!=='none'?'OPEN':'CLOSED'}catch(e){return 'EXC:'+e.message}})()",
                "modal nouveau paquet ouvert") { it == "OPEN" },
            Step(900.0,
                "(function(){try{" +
                    "document.getElementById('f_name').value='UITEST-' + Date.now();" +
                    "document.getElementById('dlgOk').click();" +
                    "const closed = document.getElementById('modalBack').style.display==='none';" +
                    "const err = (document.getElementById('formErr')||{}).textContent||'';" +
                    "return (closed?'CLOSED':'STILL_OPEN') + (err?' ERR='+err : '')" +
                "}catch(e){return 'EXC:'+e.message}})()",
                "enregistrer crée le paquet") { it.startsWith("CLOSED") },
            Step(1300.0,
                "(function(){const n=[...document.querySelectorAll('.card .name')].find(e=>e.textContent.startsWith('UITEST-'));return n?'FOUND:'+n.textContent:'NOT_FOUND'})()",
                "paquet UITEST visible dans la liste") { it.startsWith("FOUND") },
            Step(1700.0,
                "(function(){try{document.getElementById('btnStats').click();return document.getElementById('title').textContent + '|' + document.getElementById('btnBack').style.display}catch(e){return 'EXC:'+e.message}})()",
                "stats + bouton retour") { it.startsWith("Statistiques|") },
            Step(2100.0,
                "(function(){try{document.getElementById('btnBack').click();return document.getElementById('title').textContent}catch(e){return 'EXC:'+e.message}})()",
                "retour vers Mes paquets") { it == "Mes paquets" },
            Step(2500.0,
                "(function(){try{" +
                    "window.confirm = function(){return true};" +
                    "const row=[...document.querySelectorAll('.card')].find(c=>c.querySelector('.name').textContent.startsWith('UITEST-'));" +
                    "if(!row) return 'ROW_NOT_FOUND';" +
                    "row.querySelector('[data-act=\"del\"]').click();" +
                    "return 'CLICKED'}catch(e){return 'EXC:'+e.message}})()",
                "clic supprimer le paquet UITEST") { it == "CLICKED" },
            Step(2900.0,
                "(function(){const n=[...document.querySelectorAll('.card .name')].find(e=>e.textContent.startsWith('UITEST-'));return n?'STILL_THERE':'GONE'})()",
                "paquet UITEST supprimé") { it == "GONE" }
        )

        // Une seule Timeline : chaque étape est un KeyFrame daté depuis le départ.
        val frames = steps.map { s ->
            KeyFrame(Duration.millis(s.delayFromStartMs), EventHandler<ActionEvent> {
                try {
                    val res = engine.executeScript(s.code)?.toString() ?: "null"
                    val ok = s.verify(res)
                    val line = (if (ok) "PASS " else "FAIL ") + s.label + " -> " + res.take(120)
                    results.add(line)
                    println(line)
                } catch (e: Exception) {
                    val line = "FAIL ${s.label} -> exception ${e.message}"
                    results.add(line)
                    println(line)
                }
            })
        } + KeyFrame(Duration.millis(3300.0), EventHandler<ActionEvent> {
            val fails = results.count { it.startsWith("FAIL") }
            println("==========================================")
            println("UITEST : ${results.size - fails}/${results.size} PASS")
            println("==========================================")
            Platform.exit()
            kotlin.system.exitProcess(if (fails == 0) 0 else 1)
        })

        Timeline(*frames.toTypedArray()).play()
    }
}
