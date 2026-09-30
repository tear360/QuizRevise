package com.leov.quizrevise

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class StudyActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var deckCards: List<Card>
    private var order: List<Int> = emptyList()
    private var pos = 0
    private var correctCount = 0
    private var revealed = false
    private var everRevealed = false
    private var animating = false
    private var mode = "flash"
    private var currentAnswer = ""
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var studyArea: View
    private lateinit var cardContainer: MaterialCardView
    private lateinit var cardSideLabel: TextView
    private lateinit var cardText: TextView
    private lateinit var tapHint: TextView
    private lateinit var flashButtons: LinearLayout
    private lateinit var quizButtons: LinearLayout
    private lateinit var answerButtons: List<Button>
    private lateinit var resultView: LinearLayout
    private lateinit var resultScore: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study)

        db = AppDatabase(this)
        val deckId = intent.getLongExtra("deckId", -1)
        deckCards = db.cards(deckId)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitle(db.deckName(deckId))
        toolbar.setNavigationOnClickListener { finish() }

        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        studyArea = findViewById(R.id.studyArea)
        cardContainer = findViewById(R.id.cardContainer)
        cardSideLabel = findViewById(R.id.cardSideLabel)
        cardText = findViewById(R.id.cardText)
        tapHint = findViewById(R.id.tapHint)
        flashButtons = findViewById(R.id.flashButtons)
        quizButtons = findViewById(R.id.quizButtons)
        answerButtons = listOf(findViewById(R.id.answer0), findViewById(R.id.answer1), findViewById(R.id.answer2), findViewById(R.id.answer3))
        resultView = findViewById(R.id.resultView)
        resultScore = findViewById(R.id.resultScore)

        findViewById<Button>(R.id.btnKnew).setOnClickListener { answer(true) }
        findViewById<Button>(R.id.btnDidntKnow).setOnClickListener { answer(false) }
        findViewById<Button>(R.id.btnAgain).setOnClickListener { startSession(mode) }
        findViewById<Button>(R.id.btnBackToDeck).setOnClickListener { finish() }
        answerButtons.forEach { btn -> btn.setOnClickListener { onQuizAnswer(btn) } }
        cardContainer.setOnClickListener { if (mode == "flash") flipCard() }
        // Perspective plus prononcée pour l'animation de retournement
        cardContainer.cameraDistance = 8000f * resources.displayMetrics.density

        chooseMode()
    }

    private fun chooseMode() {
        AlertDialog.Builder(this)
            .setTitle(R.string.study)
            .setItems(arrayOf(getString(R.string.flashcards), getString(R.string.quiz))) { _, which ->
                startSession(if (which == 0) "flash" else "quiz")
            }
            .setCancelable(false)
            .show()
    }

    private fun startSession(m: String) {
        mode = m
        order = deckCards.indices.shuffled()
        pos = 0
        correctCount = 0
        progressBar.max = order.size
        progressBar.progress = 0
        resultView.visibility = View.GONE
        studyArea.visibility = View.VISIBLE
        showQuestion()
    }

    private fun showQuestion() {
        val card = deckCards[order[pos]]
        currentAnswer = card.answer
        revealed = false
        everRevealed = false
        animating = false
        cardContainer.rotationY = 0f

        progressText.text = getString(R.string.progress, pos + 1, order.size)
        progressBar.setProgressCompat(pos, true)
        setSide(false)

        if (mode == "quiz") {
            quizButtons.visibility = View.VISIBLE
            setupQuizOptions()
        } else {
            quizButtons.visibility = View.GONE
        }
    }

    private fun setupQuizOptions() {
        val distractors = deckCards.map { it.answer }
            .filter { it != currentAnswer }
            .distinct()
            .shuffled()
            .take(3)
        val options = (distractors + currentAnswer).shuffled()

        answerButtons.forEachIndexed { i, btn ->
            if (i < options.size) {
                btn.visibility = View.VISIBLE
                btn.text = options[i]
                btn.isEnabled = true
                btn.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.quiz_option_bg)
                )
                btn.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            } else {
                btn.visibility = View.GONE
            }
        }
    }

    /** Retournement illimité avec animation 3D : chaque appui alterne question ↔ réponse. */
    private fun flipCard() {
        if (mode != "flash" || animating) return
        animating = true
        val showAnswerNext = !revealed
        if (showAnswerNext) everRevealed = true
        val first = ValueAnimator.ofFloat(0f, 90f)
        first.duration = 150
        first.interpolator = AccelerateInterpolator()
        first.addUpdateListener { cardContainer.rotationY = it.animatedValue as Float }
        first.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                setSide(showAnswerNext)
                cardContainer.rotationY = -90f
                val second = ValueAnimator.ofFloat(-90f, 0f)
                second.duration = 150
                second.interpolator = DecelerateInterpolator()
                second.addUpdateListener { cardContainer.rotationY = it.animatedValue as Float }
                second.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { animating = false }
                })
                second.start()
            }
        })
        first.start()
    }

    private fun setSide(showAnswer: Boolean) {
        revealed = showAnswer
        if (showAnswer) {
            cardSideLabel.text = getString(R.string.answer_hint)
            cardText.text = currentAnswer
            tapHint.visibility = View.GONE
            flashButtons.visibility = View.VISIBLE
        } else {
            cardSideLabel.text = getString(R.string.question_hint)
            cardText.text = deckCards[order[pos]].question
            tapHint.visibility = if (mode == "flash" && !everRevealed) View.VISIBLE else View.GONE
            if (!everRevealed) flashButtons.visibility = View.GONE
        }
    }

    private fun onQuizAnswer(btn: Button) {
        if (btn.text.toString() == currentAnswer) {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.correct))
            answer(true)
        } else {
            btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.wrong_button))
            answerButtons.filter { it.text.toString() == currentAnswer }.forEach {
                it.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.correct))
            }
            answer(false)
        }
        answerButtons.forEach { it.isEnabled = false }
    }

    private fun answer(ok: Boolean) {
        if (ok) correctCount++
        pos++
        progressBar.setProgressCompat(pos, true)
        handler.postDelayed({
            if (pos >= order.size) finishSession() else showQuestion()
        }, if (mode == "quiz") 900 else 150)
    }

    private fun finishSession() {
        db.recordSession(order.size, correctCount)
        studyArea.visibility = View.GONE
        flashButtons.visibility = View.GONE
        quizButtons.visibility = View.GONE
        resultView.visibility = View.VISIBLE
        val pct = correctCount * 100 / order.size
        resultScore.text = getString(R.string.score_label, correctCount, order.size, pct)
    }
}
