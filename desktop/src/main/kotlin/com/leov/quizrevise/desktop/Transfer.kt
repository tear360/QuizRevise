package com.leov.quizrevise.desktop

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Format d'échange .qrevise — compatible entre toutes les versions et plateformes
 * de l'app (Windows, Linux, Android, iOS). JSON versionné et lisible :
 *
 * {
 *   "format": "quizrevise",
 *   "version": 1,
 *   "exported": "2026-09-27T18:30:00+02:00",
 *   "decks": [ { "name": "...", "color": "#6750A4", "cards": [ { "q": "...", "a": "..." } ] } ]
 * }
 */
object Transfer {
    const val FORMAT = "quizrevise"
    const val VERSION = 1

    fun colorToHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

    fun colorFromHex(hex: String?): Int = try {
        0xFF000000.toInt() or Integer.parseInt(hex?.trim()?.removePrefix("#") ?: "", 16)
    } catch (_: Exception) {
        0xFF6750A4.toInt()
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())

    private fun root(decks: JSONArray): JSONObject =
        JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exported", now())
            .put("decks", decks)

    fun exportDeckJson(db: DesktopDb, deckId: Long): String {
        val deck = JSONObject()
            .put("name", db.deckName(deckId))
            .put("color", colorToHex(db.deckColor(deckId)))
            .put("cards", JSONArray().apply {
                db.cards(deckId).forEach { put(JSONObject().put("q", it.question).put("a", it.answer)) }
            })
        return root(JSONArray().put(deck)).toString(2)
    }

    fun exportAllJson(db: DesktopDb): String {
        val arr = JSONArray()
        db.decks().forEach { d ->
            arr.put(
                JSONObject()
                    .put("name", d.name)
                    .put("color", colorToHex(d.color))
                    .put("cards", JSONArray().apply {
                        db.cards(d.id).forEach { put(JSONObject().put("q", it.question).put("a", it.answer)) }
                    })
            )
        }
        return root(arr).toString(2)
    }

    /** Importe les paquets du JSON, renvoie le nombre de paquets ajoutés. */
    fun importJson(db: DesktopDb, json: String): Int {
        val rootObj = JSONObject(json)
        if (rootObj.optString("format") != FORMAT) return 0
        val decks = rootObj.optJSONArray("decks") ?: return 0
        var added = 0
        for (i in 0 until decks.length()) {
            val d = decks.optJSONObject(i) ?: continue
            val name = d.optString("name").trim()
            if (name.isEmpty()) continue
            val color = colorFromHex(d.optString("color", null))
            val id = db.createDeck(name, color)
            val cards = d.optJSONArray("cards") ?: JSONArray()
            for (j in 0 until cards.length()) {
                val c = cards.optJSONObject(j) ?: continue
                val q = c.optString("q").trim()
                val a = c.optString("a").trim()
                if (q.isNotEmpty() && a.isNotEmpty()) db.addCard(id, q, a)
            }
            added++
        }
        return added
    }
}
