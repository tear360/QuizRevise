package com.leov.quizrevise

data class Deck(
    val id: Long,
    val name: String,
    val color: Int,
    val cardCount: Int,
)

data class Card(
    val id: Long,
    val deckId: Long,
    val question: String,
    val answer: String,
)

/** Petit utilitaire JSON maison (clé/valeur simple) pour éviter une dépendance externe. */
object MiniJson {
    fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    fun unescape(s: String): String = s
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
}
