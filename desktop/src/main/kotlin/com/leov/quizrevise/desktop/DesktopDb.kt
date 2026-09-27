package com.leov.quizrevise.desktop

import java.sql.Connection
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Deck(val id: Long, val name: String, val color: Int, val cardCount: Int)
data class Card(val id: Long, val deckId: Long, val question: String, val answer: String)

/** Base de données desktop : même schéma que l'app Android. */
class DesktopDb(private val dbPath: String = computeDbPath()) {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    init {
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute(
                "CREATE TABLE IF NOT EXISTS decks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, " +
                    "color INTEGER NOT NULL DEFAULT 0)"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS cards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE, " +
                    "question TEXT NOT NULL, " +
                    "answer TEXT NOT NULL)"
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS stats (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "day TEXT NOT NULL UNIQUE, " +
                    "reviews INTEGER NOT NULL DEFAULT 0, " +
                    "correct INTEGER NOT NULL DEFAULT 0, " +
                    "best INTEGER NOT NULL DEFAULT 0)"
            )
        }
    }

    companion object {
        /** Répertoire de données par OS : %APPDATA% (Windows), XDG_DATA_HOME (Linux), ~/Library (macOS). */
        fun computeDbPath(): String {
            val os = System.getProperty("os.name").lowercase()
            val dir = when {
                os.contains("win") -> {
                    val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
                    java.io.File(appData, "QuizRevise")
                }
                os.contains("mac") || os.contains("darwin") ->
                    java.io.File(System.getProperty("user.home"), "Library/Application Support/QuizRevise")
                else -> {
                    val xdg = System.getenv("XDG_DATA_HOME")
                        ?: java.io.File(System.getProperty("user.home"), ".local/share").absolutePath
                    java.io.File(xdg, "QuizRevise")
                }
            }
            dir.mkdirs()
            return java.io.File(dir, "quizrevise.db").absolutePath
        }
    }

    fun decks(): List<Deck> =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT d.id, d.name, d.color, (SELECT COUNT(*) FROM cards c WHERE c.deck_id = d.id) " +
                    "FROM decks d ORDER BY d.id DESC"
            ).useRows { Deck(it.getLong(1), it.getString(2), it.getInt(3), it.getInt(4)) }
        }

    fun createDeck(name: String, color: Int): Long =
        conn.prepareStatement("INSERT INTO decks(name, color) VALUES(?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS).useAndKey {
            it.setString(1, name); it.setInt(2, color); it.executeUpdate()
        }

    fun renameDeck(id: Long, name: String) =
        conn.prepareStatement("UPDATE decks SET name = ? WHERE id = ?").use {
            it.setString(1, name); it.setLong(2, id); it.executeUpdate()
        }

    fun deleteDeck(id: Long) =
        conn.prepareStatement("DELETE FROM decks WHERE id = ?").use {
            it.setLong(1, id); it.executeUpdate()
        }

    fun deckName(id: Long): String =
        conn.prepareStatement("SELECT name FROM decks WHERE id = ?").use {
            it.setLong(1, id)
            it.executeQuery().useRows { r -> r.getString(1) }.firstOrNull() ?: ""
        }

    fun cards(deckId: Long): List<Card> =
        conn.prepareStatement("SELECT id, deck_id, question, answer FROM cards WHERE deck_id = ? ORDER BY id").use {
            it.setLong(1, deckId)
            it.executeQuery().useRows { r -> Card(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4)) }
        }

    fun addCard(deckId: Long, question: String, answer: String): Long =
        conn.prepareStatement("INSERT INTO cards(deck_id, question, answer) VALUES(?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS).useAndKey {
            it.setLong(1, deckId); it.setString(2, question); it.setString(3, answer); it.executeUpdate()
        }

    fun updateCard(id: Long, question: String, answer: String) =
        conn.prepareStatement("UPDATE cards SET question = ?, answer = ? WHERE id = ?").use {
            it.setString(1, question); it.setString(2, answer); it.setLong(3, id); it.executeUpdate()
        }

    fun deleteCard(id: Long) =
        conn.prepareStatement("DELETE FROM cards WHERE id = ?").use {
            it.setLong(1, id); it.executeUpdate()
        }

    private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun recordSession(total: Int, correct: Int) {
        val day = today()
        val existing = conn.prepareStatement("SELECT reviews, correct FROM stats WHERE day = ?").use {
            it.setString(1, day)
            it.executeQuery().useRows { r -> Pair(r.getInt(1), r.getInt(2)) }.firstOrNull()
        }
        if (existing == null) {
            conn.prepareStatement("INSERT INTO stats(day, reviews, correct, best) VALUES(?, ?, ?, 0)").use {
                it.setString(1, day); it.setInt(2, total); it.setInt(3, correct); it.executeUpdate()
            }
        } else {
            conn.prepareStatement("UPDATE stats SET reviews = ?, correct = ? WHERE day = ?").use {
                it.setInt(1, existing.first + total); it.setInt(2, existing.second + correct)
                it.setString(3, day); it.executeUpdate()
            }
        }
        val streak = currentStreak()
        conn.prepareStatement("UPDATE stats SET best = MAX(best, ?) WHERE day = ?").use {
            it.setInt(1, streak); it.setString(2, day); it.executeUpdate()
        }
    }

    fun currentStreak(): Int {
        val days = HashSet<String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT day FROM stats WHERE reviews > 0").useRows { r -> days.add(r.getString(1)) }
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        if (!days.contains(fmt.format(cal.time))) cal.add(Calendar.DAY_OF_YEAR, -1)
        var streak = 0
        while (days.contains(fmt.format(cal.time))) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    fun bestStreak(): Int =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT MAX(best) FROM stats").useRows { r -> r.getInt(1) }.firstOrNull() ?: 0
        }

    fun todayStats(): Pair<Int, Int> =
        conn.prepareStatement("SELECT reviews, correct FROM stats WHERE day = ?").use {
            it.setString(1, today())
            it.executeQuery().useRows { r -> Pair(r.getInt(1), r.getInt(2)) }.firstOrNull() ?: Pair(0, 0)
        }
}

/** Petites extensions JDBC pour un code lisible. */
fun <T> java.sql.ResultSet.useRows(block: (java.sql.ResultSet) -> T): List<T> {
    val out = ArrayList<T>()
    while (next()) out.add(block(this))
    close()
    return out
}

fun java.sql.PreparedStatement.useAndKey(block: (java.sql.PreparedStatement) -> Int): Long {
    val count = block(this)
    return if (count > 0) generatedKeys.useRows { it.getLong(1) }.firstOrNull() ?: -1 else -1
}
