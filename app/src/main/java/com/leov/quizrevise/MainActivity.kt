package com.leov.quizrevise

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: DecksAdapter
    private var exportDeckId: Long = 0

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            if (uri != null) {
                val json = if (exportDeckId > 0) Transfer.exportDeckJson(db, exportDeckId)
                           else Transfer.exportAllJson(db)
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    toast("Paquet exporté ✅")
                } catch (e: Exception) { toast("Échec de l'export") }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            var total = 0
            for (uri in uris) {
                try {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                    total += Transfer.importJson(db, json)
                } catch (_: Exception) { }
            }
            refresh()
            toast(if (total > 0) "✅ $total paquet(s) importé(s)" else "Aucun paquet importé (fichier invalide ?)")
        }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase(this)
        adapter = DecksAdapter()

        val list = findViewById<RecyclerView>(R.id.decksList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabNewDeck).setOnClickListener { askNewDeck() }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_import -> { importDecks(); true }
                    R.id.action_export_all -> { exportAll(); true }
                    R.id.action_stats -> { showStats(); true }
                    R.id.action_check_updates -> {
                        toast(R.string.update_checking)
                        UpdateManager.check(this, silent = false)
                        true
                    }
                    R.id.action_about -> { showAbout(); true }
                    else -> false
                }
            }

        // Vérification discrète des mises à jour à l'ouverture (1 fois par lancement)
        UpdateManager.check(this, silent = true)

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val decks = db.decks()
        adapter.submit(decks)
        findViewById<TextView>(R.id.emptyState).visibility =
            if (decks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun askNewDeck() {
        val input = EditText(this).apply {
            hint = getString(R.string.deck_name_hint)
            setSingleLine(true)
            setPadding(56, 40, 56, 8)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.new_deck)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val colors = listOf(0xFF6750A4, 0xFF1B873B, 0xFFB3261E, 0xFF0B57D0, 0xFFE8590C, 0xFF7A1FA2)
                    db.createDeck(name, colors[(db.decks().size) % colors.size].toInt())
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showStats() {
        val (todayReviews, todayCorrect) = db.todayStats()
        val accuracy = if (todayReviews > 0) todayCorrect * 100 / todayReviews else 0
        val message = """
            ${getString(R.string.studied_today, todayReviews)}

            ${getString(R.string.streak)} : ${db.currentStreak()} ${getString(R.string.days)}
            ${getString(R.string.best_streak)} : ${db.bestStreak()} ${getString(R.string.days)}
            ${getString(R.string.accuracy)} : $accuracy%
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(R.string.stats_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Version ${BuildConfig.VERSION_NAME}\n\nAlternative libre à Quizlet : crée tes paquets de cartes, révise avec des flashcards ou des QCM.\n\nMises à jour automatiques via GitHub.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun toast(resId: Int) =
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()

    private inner class DecksAdapter : RecyclerView.Adapter<DecksVH>() {
        private val items = mutableListOf<Deck>()

        fun submit(decks: List<Deck>) {
            items.clear()
            items.addAll(decks)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DecksVH =
            DecksVH(LayoutInflater.from(parent.context).inflate(R.layout.item_deck, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: DecksVH, position: Int) {
            val deck = items[position]
            holder.title.text = deck.name
            holder.count.text = holder.itemView.context.getString(R.string.cards_count, deck.cardCount)
            holder.dot.setBackgroundColor(deck.color)
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, DeckActivity::class.java).putExtra("deckId", deck.id))
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(deck.name)
                    .setItems(arrayOf(getString(R.string.rename), "Exporter (.qrevise)", getString(R.string.delete))) { _, which ->
                        when (which) {
                            0 -> renameDialog(deck)
                            1 -> exportDeck(deck)
                            else -> confirmDelete(deck)
                        }
                    }
                    .show()
                true
            }
        }

        private fun renameDialog(deck: Deck) {
            val input = EditText(this@MainActivity).apply {
                setText(deck.name)
                setSingleLine(true)
                setPadding(56, 40, 56, 8)
            }
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.rename)
                .setView(input)
                .setPositiveButton(R.string.save) { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) { db.renameDeck(deck.id, name); refresh() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun confirmDelete(deck: Deck) {
            AlertDialog.Builder(this@MainActivity)
                .setMessage(R.string.confirm_delete_deck)
                .setPositiveButton(R.string.delete) { _, _ -> db.deleteDeck(deck.id); refresh() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun exportDeck(deck: Deck) {
        exportDeckId = deck.id
        exportLauncher.launch(deck.name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
    }

    private fun exportAll() {
        exportDeckId = 0
        exportLauncher.launch("mes-paquets")
    }

    private fun importDecks() {
        importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
    }

}

private class DecksVH(view: android.view.View) : RecyclerView.ViewHolder(view) {
    val dot: android.view.View = view.findViewById(R.id.colorDot)
    val title: TextView = view.findViewById(R.id.deckTitle)
    val count: TextView = view.findViewById(R.id.deckCount)
}
