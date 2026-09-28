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
 *
 * Inclut une étape de pression GC : si le pont natif (NativeApi) n'est pas
 * retenu par une référence forte côté Java, le GC le supprime et tous les
 * appels JS→Java deviennent muets — c'était le bug des boutons « qui ne
 * font rien » de la v1.4.4.
 */
object UiTest {

    private val results = ArrayList<String>()

    /** Code spécial : plutôt qu'exécuter du JS, force des cycles de GC complets. */
    private const val GC_STEP = "!GC!"

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
                """(function(){try{
                    document.getElementById('f_name').value='UITEST-' + Date.now() + ' 😀 "v2"';
                    document.getElementById('dlgOk').click();
                    const closed = document.getElementById('modalBack').style.display==='none';
                    const err = (document.getElementById('formErr')||{}).textContent||'';
                    return (closed?'CLOSED':'STILL_OPEN') + (err?' ERR='+err : '')
                }catch(e){return 'EXC:'+e.message}})()""",
                "enregistrer crée le paquet (nom avec emoji + guillemets)") { it.startsWith("CLOSED") },
            Step(1300.0,
                """(function(){const n=[...document.querySelectorAll('.card .name')].find(e=>e.textContent.startsWith('UITEST-'));
                    return n? (n.textContent.indexOf('😀')>=0 && n.textContent.indexOf('"v2"')>=0 ? 'FOUND:'+n.textContent : 'BAD_NAME:'+n.textContent) : 'NOT_FOUND'})()""",
                "paquet UITEST visible, nom préservé (emoji + guillemets)") { it.startsWith("FOUND:") },
            Step(1450.0,
                """(function(){try{
                    const deck=[...document.querySelectorAll('.card')].find(e=>e.querySelector('.name')?.textContent.startsWith('UITEST-'));
                    if(!deck)return 'NO_DECK';
                    deck.click();
                    const add=document.getElementById('fab'), study=document.getElementById('fabStudy');
                    if(getComputedStyle(add).display==='none'||getComputedStyle(study).display==='none')return 'HIDDEN';
                    const a=add.getBoundingClientRect(), s=study.getBoundingClientRect();
                    if(!(s.bottom<=a.top||a.bottom<=s.top))return 'OVERLAP';
                    add.click();
                    const opened=document.getElementById('modalBack').style.display!=='none';
                    if(opened)document.getElementById('dlgCancel').click();
                    return opened?'ADD_CARD_OK|separated':'ADD_CARD_NO_MODAL';
                }catch(e){return 'EXC:'+e.message}})()""",
                "bouton Ajouter une carte visible, séparé de Réviser et fonctionnel") { it.startsWith("ADD_CARD_OK") },
            Step(1600.0, GC_STEP,
                "pression GC (3 cycles complets)") { it.startsWith("PASS") },
            Step(1950.0,
                """(function(){try{
                    const d = N.decks();
                    if (typeof d !== 'string') return 'DEAD:typeof ' + typeof d;
                    return 'ALIVE:' + JSON.parse(d).length;
                }catch(e){return 'EXC:'+e.message}})()""",
                "pont natif toujours vivant après le GC") { it.startsWith("ALIVE:") },
            Step(2400.0,
                """(function(){try{
                    document.getElementById('btnStats').click();
                    const b = document.getElementById('errBanner');
                    return document.getElementById('title').textContent + '|banner:' + (b.style.display||'none')
                }catch(e){return 'EXC:'+e.message}})()""",
                "stats sans erreur") { it.startsWith("Statistiques|banner:none") },
            Step(2800.0,
                "(function(){try{document.getElementById('btnBack').click();return document.getElementById('title').textContent}catch(e){return 'EXC:'+e.message}})()",
                "retour vers Mes paquets") { it == "Mes paquets" },
            Step(3000.0,
                """(function(){try{
                    window.__updResult = null;
                    window.alert = function(m){ window.__lastAlert = m };
                    document.getElementById('btnRefresh').click();
                    return 'CLICKED'
                }catch(e){return 'EXC:'+e.message}})()""",
                "clic bouton vérification de mise à jour") { it == "CLICKED" },
            Step(6500.0,
                """(function(){
                    let out = window.__updResult ? 'RESULT:' + window.__updResult
                        : (window.__lastAlert ? 'ALERT:' + window.__lastAlert : 'NO_RESULT');
                    out += '|Nchk:' + typeof N.checkUpdatesAsync + '|Nimp:' + typeof N.importFile + '|Ndel:' + typeof N.deleteDeck;
                    const b = document.getElementById('errBanner');
                    out += '|banner:' + (b.style.display||'none') + '#' + document.getElementById('errMsg').textContent.slice(0,70);
                    return out
                })()""",
                "réponse de la vérification de mise à jour reçue") { it.startsWith("RESULT:") || it.startsWith("ALERT:") },
            Step(7000.0,
                """(function(){try{
                    window.__importAck = undefined;
                    document.getElementById('btnImport').click();
                    return 'CLICKED'
                }catch(e){return 'EXC:'+e.message}})()""",
                "clic bouton Importer") { it == "CLICKED" },
            Step(7400.0,
                """(function(){
                    let out = window.__importAck === undefined ? 'NO_ACK' : 'ACK:' + window.__importAck;
                    const b = document.getElementById('errBanner');
                    out += '|banner:' + (b.style.display||'none') + '#' + document.getElementById('errMsg').textContent.slice(0,70);
                    return out
                })()""",
                "appel natif import parcours l'aller-retour") { it.startsWith("ACK:") },
            Step(7800.0,
                """(function(){try{
                    window.confirm = function(){return true};
                    let n = 0;
                    while (n < 10) {
                        const row=[...document.querySelectorAll('.card')].find(c=>c.querySelector('.name').textContent.startsWith('UITEST-'));
                        if (!row) break;
                        row.querySelector('[data-act="del"]').click();
                        n++;
                    }
                    return n > 0 ? 'CLICKED:' + n : 'NONE_LEFT'
                }catch(e){return 'EXC:'+e.message}})()""",
                "clic supprimer les paquets UITEST") { it.startsWith("CLICKED") || it == "NONE_LEFT" },
            Step(8200.0,
                """(function(){
                    const n = [...document.querySelectorAll('.card .name')].find(e=>e.textContent.startsWith('UITEST-'));
                    let out = n ? 'STILL_THERE' : 'GONE';
                    const b = document.getElementById('errBanner');
                    out += '|banner:' + (b.style.display||'none') + '#' + document.getElementById('errMsg').textContent.slice(0,70);
                    return out
                })()""",
                "paquet UITEST supprimé") { it.startsWith("GONE") }
        )

        // Une seule Timeline : chaque étape est un KeyFrame daté depuis le départ.
        val frames = steps.map { s ->
            KeyFrame(Duration.millis(s.delayFromStartMs), EventHandler<ActionEvent> {
                if (s.code == GC_STEP) {
                    try {
                        repeat(3) { System.gc(); Thread.sleep(60) }
                        val line = "PASS ${s.label} -> OK"
                        results.add(line)
                        println(line)
                    } catch (e: Exception) {
                        val line = "FAIL ${s.label} -> exception ${e.message}"
                        results.add(line)
                        println(line)
                    }
                    return@EventHandler
                }
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
        } + KeyFrame(Duration.millis(8700.0), EventHandler<ActionEvent> {
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
