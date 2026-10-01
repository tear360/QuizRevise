package com.leov.quizrevise

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import java.util.Random

/**
 * Écran posé par-dessus l'application ciblée : une question tirée des paquets
 * choisis doit être résolue (QCM ou réécriture) pour accéder à l'appli.
 */
class BlockerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE = "package"
        /** Petite temporisation avant de libérer l'appli (feedback visuel). */
        private const val GRANT_DELAY_MS = 500L
    }

    private lateinit var db: AppDatabase
    private var targetPackage: String? = null
    private var challenge: AppLockManager.Challenge? = null
    private val handler = Handler(Looper.getMainLooper())
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocker)

        db = AppDatabase(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE)

        findViewById<MaterialToolbar>(R.id.blockerToolbar).setTitle(R.string.app_block_question_title)

        challenge = buildChallenge()
        if (challenge == null) {
            // Aucune question disponible : on affiche l'écran avec un bouton retour,
            // l'utilisateur pourra régler les paquets depuis les paramètres.
            findViewById<View>(R.id.blockerCard).visibility = View.GONE
            findViewById<Button>(R.id.btnGiveUp).visibility = View.VISIBLE
        } else {
            showChallenge()
        }

        findViewById<Button>(R.id.btnGiveUp).setOnClickListener {
            AppLockManager.challengeShowing = false
            goHome()
        }
    }

    private fun buildChallenge(): AppLockManager.Challenge? {
        val deckIds = AppLockManager.questionDeckIds(this)
        val pool = if (deckIds.isEmpty()) {
            // Aucun paquet choisi : on pioche dans tous les paquets assez remplis.
            db.decks().filter { it.cardCount >= 4 }.map { it.id }
        } else deckIds
        val cards = pool.flatMap { db.cards(it) }
        return AppLockManager.buildChallenge(cards)
    }

    private fun showChallenge() {
        val ch = challenge ?: return
        findViewById<TextView>(R.id.blockerAppLabel).text =
            getString(R.string.app_block_question_for, appName(targetPackage ?: ""))

        if (ch.qcm) {
            findViewById<View>(R.id.blockerWriteArea).visibility = View.GONE
            val quizArea = findViewById<View>(R.id.blockerQuizArea)
            quizArea.visibility = View.VISIBLE
            findViewById<TextView>(R.id.blockerQuestion).text = ch.question
            val ids = listOf(R.id.blockerAnswer0, R.id.blockerAnswer1, R.id.blockerAnswer2, R.id.blockerAnswer3)
            ids.forEachIndexed { i, id ->
                val btn = findViewById<Button>(id)
                if (i < ch.options.size) {
                    btn.visibility = View.VISIBLE
                    btn.text = ch.options[i]
                    btn.setOnClickListener { onAnswer(ch, ch.options[i] == ch.answer) }
                } else btn.visibility = View.GONE
            }
        } else {
            findViewById<View>(R.id.blockerQuizArea).visibility = View.GONE
            findViewById<View>(R.id.blockerWriteArea).visibility = View.VISIBLE
            findViewById<TextView>(R.id.blockerQuestion).text = ch.question
            val input = findViewById<TextInputEditText>(R.id.blockerInput)
            input.setText("")
            input.isEnabled = true
            input.requestFocus()
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val ok = AppLockManager.normalized(input.text?.toString().orEmpty()) == AppLockManager.normalized(ch.answer)
                    onAnswer(ch, ok)
                    true
                } else false
            }
            findViewById<Button>(R.id.btnBlockerValidate).setOnClickListener {
                val ok = AppLockManager.normalized(input.text?.toString().orEmpty()) == AppLockManager.normalized(ch.answer)
                onAnswer(ch, ok)
            }
        }
    }

    private fun onAnswer(ch: AppLockManager.Challenge, ok: Boolean) {
        val feedback = findViewById<TextView>(R.id.blockerFeedback)
        if (ok) {
            feedback.text = getString(R.string.app_block_correct)
            feedback.setTextColor(ContextCompat.getColor(this, R.color.correct))
            handler.postDelayed({ grantAccess() }, GRANT_DELAY_MS)
        } else {
            feedback.text = getString(R.string.app_block_wrong) + " → " + ch.answer
            feedback.setTextColor(ContextCompat.getColor(this, R.color.wrong))
            // Nouvelle question après un court délai de lecture.
            handler.postDelayed({
                challenge = buildChallenge()
                feedback.text = ""
                showChallenge()
            }, 1600)
        }
    }

    /** Bonne réponse : on ramène l'utilisateur vers l'application demandée. */
    private fun grantAccess() {
        val pkg = targetPackage
        AppLockManager.challengeShowing = false
        if (pkg != null) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivity(launch)
            } else {
                goHome()
            }
        } else {
            goHome()
        }
        finish()
    }

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
    }

    private fun appName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').ifEmpty { pkg }
    }

    override fun onResume() {
        super.onResume()
        // Reprise depuis l'accueil (pas le retour de l'appli accordée) : nouvelle question.
        if (resumedOnce) {
            challenge = buildChallenge()
            showChallenge()
        }
        resumedOnce = true
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) {
            // L'écran est quitté autrement que par une réussite (accueil, écran éteint…)
            // : on réarme le verrou, une nouvelle question sera posée au retour.
            AppLockManager.challengeShowing = false
        }
    }

    override fun onBackPressed() {
        // Impossible d'esquiver par « Retour » : on renvoie à l'accueil.
        AppLockManager.challengeShowing = false
        goHome()
        finish()
    }
}
