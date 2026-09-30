package com.leov.quizrevise

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class DeckActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: CardsAdapter
    private var deckId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck)

        db = AppDatabase(this)
        deckId = intent.getLongExtra("deckId", -1)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(db.deckName(deckId))
        toolbar.setNavigationOnClickListener { finish() }

        adapter = CardsAdapter()
        val list = findViewById<RecyclerView>(R.id.cardsList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabStudy).setOnClickListener {
            if (db.cards(deckId).size >= 2) {
                startActivity(Intent(this, StudyActivity::class.java).putExtra("deckId", deckId))
            } else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.not_enough_cards)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

        findViewById<ExtendedFloatingActionButton>(R.id.fabAddCard)
            .setOnClickListener { askAddCard() }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.onAppResumed(this)
        refresh()
    }

    private fun refresh() {
        val cards = db.cards(deckId)
        adapter.submit(cards)
        findViewById<TextView>(R.id.emptyState).visibility =
            if (cards.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun askAddCard(existing: Card? = null) {
        val view = layoutInflater.inflate(R.layout.dialog_card, null)
        val q = view.findViewById<EditText>(R.id.questionInput)
        val a = view.findViewById<EditText>(R.id.answerInput)
        existing?.let { q.setText(it.question); a.setText(it.answer) }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.add_card else R.string.rename)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val question = q.text.toString().trim()
                val answer = a.text.toString().trim()
                if (question.isNotEmpty() && answer.isNotEmpty()) {
                    if (existing == null) db.addCard(deckId, question, answer)
                    else db.updateCard(existing.id, question, answer)
                    refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class CardsAdapter : RecyclerView.Adapter<CardsVH>() {
        private val items = mutableListOf<Card>()

        fun submit(cards: List<Card>) {
            items.clear()
            items.addAll(cards)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardsVH =
            CardsVH(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: CardsVH, position: Int) {
            val card = items[position]
            holder.question.text = card.question
            holder.answer.text = card.answer
            // Carte pliée par défaut : un appui déplie (réponse + actions).
            holder.answer.visibility = View.GONE
            holder.actions.visibility = View.GONE
            holder.arrow.text = "▸"
            val toggle = View.OnClickListener {
                val expanded = holder.answer.visibility == View.VISIBLE
                holder.answer.visibility = if (expanded) View.GONE else View.VISIBLE
                holder.actions.visibility = if (expanded) View.GONE else View.VISIBLE
                holder.arrow.text = if (expanded) "▸" else "▾"
            }
            holder.body.setOnClickListener(toggle)
            holder.arrow.setOnClickListener(toggle)
            holder.edit.setOnClickListener { askAddCard(card) }
            holder.delete.setOnClickListener {
                AlertDialog.Builder(this@DeckActivity)
                    .setMessage(R.string.confirm_delete_card)
                    .setPositiveButton(R.string.delete) { _, _ -> db.deleteCard(card.id); refresh() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

}

private class CardsVH(view: View) : RecyclerView.ViewHolder(view) {
    val body: View = view.findViewById(R.id.cardBody)
    val arrow: TextView = view.findViewById(R.id.foldArrow)
    val question: TextView = view.findViewById(R.id.cardQuestion)
    val answer: TextView = view.findViewById(R.id.cardAnswer)
    val actions: View = view.findViewById(R.id.cardActions)
    val edit: View = view.findViewById(R.id.btnEdit)
    val delete: View = view.findViewById(R.id.btnDelete)
}
