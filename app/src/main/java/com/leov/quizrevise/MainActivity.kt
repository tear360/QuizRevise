package com.leov.quizrevise

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    companion object {
        /** Palette des paquets — identique sur Android, PC et iPhone. */
        val DECK_COLORS = intArrayOf(
            0xFF6750A4.toInt(), 0xFF1B873B.toInt(), 0xFFB3261E.toInt(), 0xFF0B57D0.toInt(),
            0xFFE8590C.toInt(), 0xFF7A1FA2.toInt(), 0xFF00897B.toInt(), 0xFFC2185B.toInt(),
            0xFFF9A825.toInt(), 0xFF5D4037.toInt(), 0xFF3949AB.toInt(), 0xFF7CB342.toInt()
        )
    }

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

    /**
     * Construit une grille de pastilles de couleurs (2 rangées de 6) ; renvoie
     * la couleur choisie. La pastille active est entourée ; un appui sur une
     * autre la déplace. Toutes les couleurs sont visibles sans défilement.
     */
    private fun buildSwatches(container: LinearLayout, initial: Int): () -> Int {
        val selection = intArrayOf(initial)
        val density = resources.displayMetrics.density
        val size = (36 * density).toInt()
        val perRow = 6

        var index = 0
        while (index < DECK_COLORS.size) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val end = minOf(index + perRow, DECK_COLORS.size)
            for (i in index until end) {
                val color = DECK_COLORS[i]
                // Cellule pondérée : 6 pastilles se partagent la largeur, même
                // sur les petits écrans (rien ne sort jamais de la grille) ;
                // la pastille reste un carré centré, donc un vrai cercle.
                val cell = FrameLayout(this)
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                val dot = View(this)
                dot.tag = color
                dot.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
                dot.setOnClickListener {
                    selection[0] = color
                    repaintSwatches(container, color, density)
                }
                cell.addView(dot)
                row.addView(cell)
            }
            container.addView(row)
            index = end
        }
        repaintSwatches(container, initial, density)
        return { selection[0] }
    }

    /** Redessine toutes les pastilles : entourée pour la sélection, sinon discrète. */
    private fun repaintSwatches(container: LinearLayout, selected: Int, density: Float) {
        for (r in 0 until container.childCount) {
            val row = container.getChildAt(r) as? LinearLayout ?: continue
            for (i in 0 until row.childCount) {
                val cell = row.getChildAt(i) as? ViewGroup ?: continue
                if (cell.childCount == 0) continue
                val dot = cell.getChildAt(0)
                val color = dot.tag as? Int ?: continue
                val active = color == selected
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.OVAL
                gd.setColor(color)
                if (active) {
                    gd.setStroke((3 * density).toInt(), ContextCompat.getColor(this, R.color.on_surface))
                }
                dot.background = gd
                dot.alpha = if (active) 1f else 0.8f
            }
        }
    }

    private fun askNewDeck() {
        // Toute erreur d'affichage du dialogue ne doit JAMAIS fermer l'app :
        // au pire on retombe sur le formulaire sans couleurs.
        try {
            showDeckDialog(null)
        } catch (t: Throwable) {
            Log.e("QuizRevise", "Dialogue de création impossible", t)
            Toast.makeText(this, getString(R.string.dialog_error, t.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Dialogue unique création/renommage : nom + grille de couleurs.
     * Les pastilles sont un bonus : si elles échouent, le dialogue marche quand même.
     */
    private fun showDeckDialog(deck: Deck?) {
        val view = layoutInflater.inflate(R.layout.dialog_deck_name, null)
        val input = view.findViewById<TextInputEditText>(R.id.deckNameInput)
        if (deck != null) input.setText(deck.name)

        var getSelectedColor: () -> Int = {
            deck?.color ?: DECK_COLORS[db.decks().size % DECK_COLORS.size]
        }
        try {
            val colorRow = view.findViewById<LinearLayout>(R.id.deckColorRow)
            val initial = deck?.color ?: DECK_COLORS[db.decks().size % DECK_COLORS.size]
            getSelectedColor = buildSwatches(colorRow, initial)
        } catch (t: Throwable) {
            // Le dialogue reste utilisable sans couleurs plutôt que de planter.
            Log.e("QuizRevise", "Pastilles de couleur indisponibles", t)
        }

        AlertDialog.Builder(this)
            .setTitle(if (deck == null) R.string.new_deck else R.string.rename)
            .setView(view)
            .setPositiveButton(if (deck == null) R.string.create else R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (deck == null) db.createDeck(name, getSelectedColor())
                    else {
                        db.renameDeck(deck.id, name)
                        db.updateDeckColor(deck.id, getSelectedColor())
                    }
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
                    .setItems(arrayOf(getString(R.string.rename), getString(R.string.change_color), "Exporter (.qrevise)", getString(R.string.delete))) { _, which ->
                        when (which) {
                            0 -> renameDialog(deck)
                            1 -> colorDialog(deck)
                            2 -> exportDeck(deck)
                            else -> confirmDelete(deck)
                        }
                    }
                    .show()
                true
            }
        }

        private fun renameDialog(deck: Deck) {
            showDeckDialog(deck)
        }

        private fun colorDialog(deck: Deck) {
            try {
                val view = layoutInflater.inflate(R.layout.dialog_deck_color, null)
                val row = view.findViewById<LinearLayout>(R.id.colorSwatchesRow)
                val getSelectedColor = buildSwatches(row, deck.color)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.deck_color_title)
                    .setView(view)
                    .setPositiveButton(R.string.save) { _, _ ->
                        db.updateDeckColor(deck.id, getSelectedColor())
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } catch (t: Throwable) {
                Log.e("QuizRevise", "Dialogue de couleur impossible", t)
                Toast.makeText(this@MainActivity, getString(R.string.dialog_error, t.message ?: ""), Toast.LENGTH_LONG).show()
            }
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
