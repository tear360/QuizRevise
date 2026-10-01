package com.leov.quizrevise

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.google.android.material.textfield.TextInputEditText

class StudyActivity : AppCompatActivity() {

    companion object {
        /** Un test type professeur compte 10 questions, notées 2 points chacune. */
        private const val TEST_LENGTH = 10
    }

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

    // État du mode Test : questions mixtes, pas de retour en arrière, note sur 20.
    private var testMode = false
    private var testAbandoned = false
    private var currentQuestionIsQcm = false

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var studyArea: View
    private lateinit var cardContainer: MaterialCardView
    private lateinit var cardSideLabel: TextView
    private lateinit var cardText: TextView
    private lateinit var tapHint: TextView
    private lateinit var flashButtons: LinearLayout
    private lateinit var quizButtons: LinearLayout
    private lateinit var writeButtons: LinearLayout
    private lateinit var writeInput: TextInputEditText
    private lateinit var writeFeedback: TextView
    private lateinit var answerButtons: List<Button>
    private lateinit var resultView: LinearLayout
    private lateinit var resultScore: TextView
    private lateinit var testResultView: LinearLayout
    private lateinit var testResultScore: TextView
    private lateinit var testResultMention: TextView
    private lateinit var testResultDetail: TextView

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
        writeButtons = findViewById(R.id.writeButtons)
        writeInput = findViewById(R.id.writeInput)
        writeFeedback = findViewById(R.id.writeFeedback)
        answerButtons = listOf(findViewById(R.id.answer0), findViewById(R.id.answer1), findViewById(R.id.answer2), findViewById(R.id.answer3))
        resultView = findViewById(R.id.resultView)
        resultScore = findViewById(R.id.resultScore)
        testResultView = findViewById(R.id.testResultView)
        testResultScore = findViewById(R.id.testResultScore)
        testResultMention = findViewById(R.id.testResultMention)
        testResultDetail = findViewById(R.id.testResultDetail)

        findViewById<Button>(R.id.btnKnew).setOnClickListener { answer(true) }
        findViewById<Button>(R.id.btnDidntKnow).setOnClickListener { answer(false) }
        findViewById<Button>(R.id.btnAgain).setOnClickListener { startSession(mode) }
        findViewById<Button>(R.id.btnBackToDeck).setOnClickListener { finish() }
        answerButtons.forEach { btn -> btn.setOnClickListener { onQuizAnswer(btn) } }
        findViewById<Button>(R.id.btnValidate).setOnClickListener { checkWritten() }
        writeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { checkWritten(); true } else false
        }
        cardContainer.setOnClickListener { if (mode == "flash") flipCard() }
        // Perspective plus prononcée pour l'animation de retournement
        cardContainer.cameraDistance = 8000f * resources.displayMetrics.density

        chooseMode()
    }

    private fun chooseMode() {
        AlertDialog.Builder(this)
            .setTitle(R.string.study)
            .setItems(arrayOf(getString(R.string.flashcards), getString(R.string.quiz), getString(R.string.rewrite), getString(R.string.test))) { _, which ->
                startSession(when (which) {
                    0 -> "flash"
                    1 -> "quiz"
                    2 -> "write"
                    else -> "test"
                })
            }
            .setCancelable(false)
            .show()
    }

    private fun startSession(m: String) {
        mode = m
        testMode = (m == "test")
        testAbandoned = false
        order = deckCards.indices.shuffled()
        if (testMode && order.size > TEST_LENGTH) order = order.take(TEST_LENGTH)
        pos = 0
        correctCount = 0
        progressBar.max = order.size
        progressBar.progress = 0
        resultView.visibility = View.GONE
        testResultView.visibility = View.GONE
        studyArea.visibility = View.VISIBLE
        if (testMode) {
            AlertDialog.Builder(this)
                .setTitle(R.string.test)
                .setMessage(R.string.test_forbidden_hint)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        showQuestion()
    }

    /**
     * Comparaison tolérante pour le mode « Réécrire le mot » : casse, accents,
     * ponctuation et espaces multiples ignorés — on juge le mot, pas la frappe.
     */
    private fun normalized(s: String): String = s.trim()
        .replace(Regex("[\\p{Punct}\\p{Space}]+"), " ")
        .replace(Regex("[àâä]"), "a").replace(Regex("[éèêë]"), "e")
        .replace(Regex("[îï]"), "i").replace(Regex("[ôö]"), "o")
        .replace(Regex("[ùûü]"), "u").replace("ç", "c").replace("œ", "oe")
        .lowercase()

    private fun showQuestion() {
        val card = deckCards[order[pos]]
        currentAnswer = card.answer
        revealed = false
        everRevealed = false
        animating = false
        cardContainer.rotationY = 0f

        progressText.text = if (testMode) {
            getString(R.string.test_progress, pos + 1, order.size)
        } else {
            getString(R.string.progress, pos + 1, order.size)
        }
        progressBar.setProgressCompat(pos, true)
        setSide(false)

        flashButtons.visibility = View.GONE
        writeButtons.visibility = View.GONE
        writeInput.setText("")
        writeFeedback.visibility = View.GONE

        when (mode) {
            "quiz" -> {
                quizButtons.visibility = View.VISIBLE
                setupQuizOptions()
            }
            "write" -> {
                writeButtons.visibility = View.VISIBLE
                writeInput.requestFocus()
            }
            "test" -> {
                // Alternance aléatoire QCM / réécriture : une épreuve mixte, comme un vrai sujet.
                currentQuestionIsQcm = (pos + order.size) % 2 == 1 || Math.random() < 0.5
                if (currentQuestionIsQcm) {
                    quizButtons.visibility = View.VISIBLE
                    setupQuizOptions()
                } else {
                    writeButtons.visibility = View.VISIBLE
                    writeInput.requestFocus()
                }
            }
            else -> { /* flash : les boutons apparaissent après retournement */ }
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
            if (!everRevealed) {
                flashButtons.visibility = View.GONE
                tapHint.text = getString(R.string.fold_hint)
            }
        }
    }

    private fun checkWritten() {
        val isWriteQuestion = mode == "write" || (testMode && !currentQuestionIsQcm)
        if (!isWriteQuestion || writeFeedback.visibility == View.VISIBLE) return
        val given = writeInput.text?.toString()?.trim().orEmpty()
        if (given.isEmpty()) return
        val ok = normalized(given) == normalized(currentAnswer)
        score(ok)
        writeFeedback.text = if (ok) getString(R.string.correct)
        else getString(R.string.wrong) + " → " + currentAnswer
        writeFeedback.setTextColor(ContextCompat.getColor(this, if (ok) R.color.correct else R.color.wrong))
        writeFeedback.visibility = View.VISIBLE
        writeInput.isEnabled = false
        findViewById<Button>(R.id.btnValidate).isEnabled = false
        pos++
        progressBar.setProgressCompat(pos, true)
        handler.postDelayed({
            writeInput.isEnabled = true
            findViewById<Button>(R.id.btnValidate).isEnabled = true
            if (pos >= order.size) finishSession() else showQuestion()
        }, 1200)
    }

    /** Compte le point dans tous les modes ; en Test, chaque bonne réponse vaut 2 points sur 20. */
    private fun score(ok: Boolean) {
        if (ok) correctCount++
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
        score(ok)
        pos++
        progressBar.setProgressCompat(pos, true)
        handler.postDelayed({
            if (pos >= order.size) finishSession() else showQuestion()
        }, if (mode == "quiz" || (testMode && currentQuestionIsQcm)) 900 else 150)
    }

    private fun finishSession() {
        db.recordSession(order.size, correctCount)
        studyArea.visibility = View.GONE
        flashButtons.visibility = View.GONE
        quizButtons.visibility = View.GONE
        writeButtons.visibility = View.GONE
        if (testMode) {
            // Note ramenée sur 20 (2 points par question quand le test est complet).
            val note = Math.round(correctCount * 20.0 / maxOf(order.size, 1)).toInt()
            resultView.visibility = View.GONE
            testResultView.visibility = View.VISIBLE
            testResultScore.text = getString(R.string.test_score, note.toString())
            testResultMention.text = when {
                note >= 16 -> getString(R.string.mention_very_good)
                note >= 12 -> getString(R.string.mention_good)
                note >= 10 -> getString(R.string.mention_fair)
                else -> getString(R.string.mention_insufficient)
            }
            testResultDetail.text = getString(R.string.score_label, correctCount, order.size, correctCount * 100 / maxOf(order.size, 1))
        } else {
            testResultView.visibility = View.GONE
            resultView.visibility = View.VISIBLE
            val pct = correctCount * 100 / order.size
            resultScore.text = getString(R.string.score_label, correctCount, order.size, pct)
        }
    }

    override fun onPause() {
        super.onPause()
        // Anti-triche : quitter l'écran pendant l'épreuve (bouton accueil, autre app…)
        // interrompt le test ; la note ne sera pas comptée au retour.
        if (testMode && pos < order.size) testAbandoned = true
    }

    override fun onResume() {
        super.onResume()
        if (testMode && testAbandoned && pos < order.size) {
            AlertDialog.Builder(this)
                .setTitle(R.string.test)
                .setMessage(R.string.test_interrupted)
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        }
    }
}
