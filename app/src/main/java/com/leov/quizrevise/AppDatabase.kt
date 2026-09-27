package com.leov.quizrevise

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AppDatabase(context: Context) :
    SQLiteOpenHelper(context, "quizrevise.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE decks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "color INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE cards (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE, " +
                "question TEXT NOT NULL, " +
                "answer TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE stats (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "day TEXT NOT NULL UNIQUE, " +
                "reviews INTEGER NOT NULL DEFAULT 0, " +
                "correct INTEGER NOT NULL DEFAULT 0, " +
                "best INTEGER NOT NULL DEFAULT 0)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 : rien à migrer
    }

    // ---------- Paquets ----------

    fun decks(): List<Deck> {
        val out = mutableListOf<Deck>()
        readableDatabase.rawQuery(
            "SELECT d.id, d.name, d.color, (SELECT COUNT(*) FROM cards c WHERE c.deck_id = d.id) " +
                "FROM decks d ORDER BY d.id DESC", null
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Deck(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
            }
        }
        return out
    }

    fun createDeck(name: String, color: Int): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("color", color)
        }
        return writableDatabase.insert("decks", null, cv)
    }

    fun renameDeck(id: Long, name: String) {
        val cv = ContentValues().apply { put("name", name) }
        writableDatabase.update("decks", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteDeck(id: Long) {
        writableDatabase.delete("decks", "id = ?", arrayOf(id.toString()))
    }

    fun deckName(id: Long): String =
        readableDatabase.rawQuery("SELECT name FROM decks WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getString(0) else ""
        }

    fun deckColor(id: Long): Int =
        readableDatabase.rawQuery("SELECT color FROM decks WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0xFF6750A4.toInt()
        }

    // ---------- Cartes ----------

    fun cards(deckId: Long): List<Card> {
        val out = mutableListOf<Card>()
        readableDatabase.rawQuery(
            "SELECT id, deck_id, question, answer FROM cards WHERE deck_id = ? ORDER BY id",
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out.add(Card(c.getLong(0), c.getLong(1), c.getString(2), c.getString(3)))
            }
        }
        return out
    }

    fun addCard(deckId: Long, question: String, answer: String): Long {
        val cv = ContentValues().apply {
            put("deck_id", deckId)
            put("question", question)
            put("answer", answer)
        }
        return writableDatabase.insert("cards", null, cv)
    }

    fun updateCard(id: Long, question: String, answer: String) {
        val cv = ContentValues().apply {
            put("question", question)
            put("answer", answer)
        }
        writableDatabase.update("cards", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteCard(id: Long) {
        writableDatabase.delete("cards", "id = ?", arrayOf(id.toString()))
    }

    // ---------- Statistiques ----------

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** Enregistre le résultat d'une session de révision (aujourd'hui). */
    fun recordSession(total: Int, correct: Int) {
        val db = writableDatabase
        val day = today()
        db.beginTransaction()
        try {
            val existing = db.rawQuery(
                "SELECT reviews, correct FROM stats WHERE day = ?", arrayOf(day)
            ).use { c -> if (c.moveToFirst()) Pair(c.getInt(0), c.getInt(1)) else null }

            if (existing == null) {
                db.execSQL(
                    "INSERT INTO stats(day, reviews, correct, best) VALUES(?, ?, ?, 0)",
                    arrayOf(day, total, correct)
                )
            } else {
                db.execSQL(
                    "UPDATE stats SET reviews = ?, correct = ? WHERE day = ?",
                    arrayOf(existing.first + total, existing.second + correct, day)
                )
            }

            // Met à jour la meilleure série si nécessaire
            val streak = currentStreak()
            db.execSQL("UPDATE stats SET best = MAX(best, ?) WHERE day = ?", arrayOf(streak, day))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Nombre de jours consécutifs étudiés (aujourd'hui inclus, sinon à partir d'hier). */
    fun currentStreak(): Int {
        val days = HashSet<String>()
        readableDatabase.rawQuery("SELECT day FROM stats WHERE reviews > 0", null).use { c ->
            while (c.moveToNext()) days.add(c.getString(0))
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        if (!days.contains(fmt.format(cal.time))) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        var streak = 0
        while (days.contains(fmt.format(cal.time))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun bestStreak(): Int =
        readableDatabase.rawQuery("SELECT MAX(best) FROM stats", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    /** (cartes revues aujourd'hui, cartes correctes aujourd'hui) */
    fun todayStats(): Pair<Int, Int> =
        readableDatabase.rawQuery(
            "SELECT reviews, correct FROM stats WHERE day = ?", arrayOf(today())
        ).use { c ->
            if (c.moveToFirst()) Pair(c.getInt(0), c.getInt(1)) else Pair(0, 0)
        }
}
