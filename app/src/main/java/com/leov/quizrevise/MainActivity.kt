package com.leov.quizrevise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: DecksAdapter
    private var exportDeckId: Long = 0

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val json = if (exportDeckId > 0) Transfer.exportDeckJson(db, exportDeckId)
            else Transfer.exportAllJson(db)
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                toast("Paquet exporté ✅")
            } catch (_: Exception) {
                toast("Échec de l'export")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        val mode = when (getSharedPreferences("settings", MODE_PRIVATE).getString("theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
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
                if (item.itemId == R.id.action_settings) {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                } else false
            }

        // Vérification discrète des mises à jour à l'ouverture (1 fois par lancement)
        UpdateManager.check(this, silent = true)

        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Reprend une installation en attente (retour des réglages d'autorisation)
        // et relance l'app si l'APK a été remplacé pendant que le processus vivait.
        UpdateManager.onAppResumed(this)
        refresh()
    }

    private fun refresh() {
        val decks = db.decks()
        adapter.submit(decks)
        findViewById<TextView>(R.id.emptyState).visibility =
            if (decks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun askNewDeck() {
        // Dialogue Material (TextInputLayout) : l'EditText brut paddé en pixels
        // collait le texte à la ligne de soulignement.
        val view = layoutInflater.inflate(R.layout.dialog_deck_name, null)
        val input = view.findViewById<TextInputEditText>(R.id.deckNameInput)
        AlertDialog.Builder(this)
            .setTitle(R.string.new_deck)
            .setView(view)
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
            val view = layoutInflater.inflate(R.layout.dialog_deck_name, null)
            val input = view.findViewById<TextInputEditText>(R.id.deckNameInput)
            input.setText(deck.name)
            AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.rename)
                .setView(view)
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

}

private class DecksVH(view: android.view.View) : RecyclerView.ViewHolder(view) {
    val dot: android.view.View = view.findViewById(R.id.colorDot)
    val title: TextView = view.findViewById(R.id.deckTitle)
    val count: TextView = view.findViewById(R.id.deckCount)
}
