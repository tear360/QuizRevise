package com.leov.quizrevise.desktop

import java.io.File

private fun tmp2(): String = java.io.File.createTempFile("quizrevise-t2", ".db").let { it.delete(); it.absolutePath }

/** Tests de la logique métier (DB SQLite, comparaison de versions) exécutables sans interface. */
object SelfTest {

    fun run() {
        val failures = ArrayList<String>()

        fun check(label: String, cond: Boolean) {
            if (cond) println("OK   $label") else { println("FAIL $label"); failures.add(label) }
        }

        // --- DesktopDb : opération de base sur une DB temporaire ---
        val tmp = File.createTempFile("quizrevise-selftest", ".db")
        tmp.delete()
        try {
            val db = DesktopDb(tmp.absolutePath)
            check("decks vide au départ", db.decks().isEmpty())
            val deckId = db.createDeck("Test", 0xFF6750A4.toInt())
            check("deck créé", db.deckName(deckId) == "Test")
            val card1 = db.addCard(deckId, "cat", "chat")
            val card2 = db.addCard(deckId, "dog", "chien")
            check("2 cartes ajoutées", db.cards(deckId).size == 2)
            db.updateCard(card1, "kitten", "chaton")
            check("carte mise à jour", db.cards(deckId).first { it.id == card1 }.question == "kitten")
            db.deleteCard(card2)
            check("carte supprimée", db.cards(deckId).size == 1)
            db.recordSession(2, 1)
            db.recordSession(3, 3)
            val (reviews, correct) = db.todayStats()
            check("stats cumulées", reviews == 5 && correct == 4)
            check("streak >= 1", db.currentStreak() >= 1)
            check("best streak >= 1", db.bestStreak() >= 1)
        } catch (e: Exception) {
            failures.add("exception DB: ${e.message}")
            e.printStackTrace()
        } finally {
            tmp.delete()
        }

        // --- Transfert .qrevise : export puis import dans une autre base (round-trip) ---
        try {
            val db2 = DesktopDb(tmp2())
            val d2 = db2.createDeck(" Voyage ".trim(), 0xFF0B57D0.toInt())
            db2.addCard(d2, "hello", "bonjour")
            db2.addCard(d2, "world", "monde")
            val json = Transfer.exportDeckJson(db2, d2)
            check("qrevise: format/version", json.contains("\"format\": \"quizrevise\"") && json.contains("\"version\": 1"))
            check("qrevise: couleur hex", json.contains("#0B57D0"))

            val db3 = DesktopDb(tmp2())
            val imported = Transfer.importJson(db3, json)
            check("qrevise: import 1 paquet", imported == 1)
            val decks3 = db3.decks()
            check("qrevise: nom conservé", decks3.first().name == "Voyage")
            check("qrevise: couleur conservée", decks3.first().color == 0xFF0B57D0.toInt())
            val cards3 = db3.cards(decks3.first().id)
            check("qrevise: cartes conservées", cards3.map { it.question to it.answer } == listOf("hello" to "bonjour", "world" to "monde"))
            check("qrevise: json invalide rejeté", Transfer.importJson(db3, "{}") == 0)
        } catch (e: Exception) {
            failures.add("exception qrevise: ${e.message}")
            e.printStackTrace()
        }

        // --- Comparaison de versions ---
        check("1.0.1 > 1.0.0", GitHubUpdater.isNewer("1.0.1", "1.0.0"))
        check("1.1.0 > 1.0.9", GitHubUpdater.isNewer("1.1.0", "1.0.9"))
        check("1.0.0 = 1.0.0", !GitHubUpdater.isNewer("1.0.0", "1.0.0"))
        check("0.9.9 < 1.0.0", !GitHubUpdater.isNewer("0.9.9", "1.0.0"))

        if (failures.isEmpty()) {
            println("SELFTEST OK")
        } else {
            println("SELFTEST FAILED: $failures")
            kotlin.system.exitProcess(1)
        }
    }
}
