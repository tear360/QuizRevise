package com.leov.quizrevise

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import java.util.Random

/**
 * Verrouillage d'applications (Android uniquement) :
 * un service d'accessibilité détecte l'ouverture d'une appli choisie par
 * l'utilisateur et affiche par-dessus un écran de question (QCM ou réécriture).
 * Bonne réponse → l'appli s'ouvre normalement ; sinon on ramène sur QuizRévise
 * (ou l'écran d'accueil) et l'utilisateur peut retenter.
 */
class AppLockService : AccessibilityService() {

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return
        if (!AppLockManager.enabled(this)) return
        val pkg = event.packageName.toString()
        if (pkg == packageName) return
        val blocked = AppLockManager.blockedApps(this)
        if (pkg !in blocked) return

        // Un écran de déverrouillage déjà affiché ? On ne rempile pas la pile.
        if (AppLockManager.challengeShowing) return

        AppLockManager.challengeShowing = true
        val intent = Intent(this, BlockerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(BlockerActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() { /* rien à faire */ }
}

/** Réglages et tirage des questions pour le verrouillage d'applications. */
object AppLockManager {

    const val PREF_MAX_DECKS = 6
    /** Nombre maximum d'applications verrouillables (lisibilité de la liste). */
    const val MAX_APPS = 12

    /** Vrai tant qu'un écran de question bloque l'appli détectée (anti-remplissage). */
    @Volatile
    var challengeShowing = false
        internal set

    private fun prefs(c: android.content.Context): SharedPreferences =
        c.getSharedPreferences("applock", android.content.Context.MODE_PRIVATE)

    /** Interrupteur maître côté QuizRévise (le service Android reste enregistré). */
    fun enabled(c: android.content.Context): Boolean = prefs(c).getBoolean("enabled", false)

    fun setEnabled(c: android.content.Context, value: Boolean) {
        prefs(c).edit().putBoolean("enabled", value).apply()
    }

    /** Liste des applications à verrouiller (noms de paquets Android). */
    fun blockedApps(c: android.content.Context): Set<String> =
        prefs(c).getStringSet("blocked", emptySet()) ?: emptySet()

    fun setBlockedApps(c: android.content.Context, pkgs: Set<String>) {
        prefs(c).edit().putStringSet("blocked", pkgs).apply()
    }

    /** Paquets (deckId) autorisés à fournir les questions du verrouillage. */
    fun questionDeckIds(c: android.content.Context): Set<Long> =
        prefs(c).getStringSet("decks", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    fun setQuestionDeckIds(c: android.content.Context, ids: Set<Long>) {
        prefs(c).edit().putStringSet("decks", ids.map { it.toString() }.toSet()).apply()
    }

    class Challenge(val qcm: Boolean, val question: String, val answer: String, val options: List<String>)

    /**
     * Compose une question à partir des paquets choisis :
     * ~50 % de QCM (3 mauvaises réponses + la bonne, mélangées),
     * sinon une question à réécrire (comparaison tolérante).
     */
    fun buildChallenge(cards: List<Card>, rng: Random = Random()): Challenge? {
        if (cards.isEmpty()) return null
        val card = cards[rng.nextInt(cards.size)]
        val qcm = rng.nextBoolean() && cards.count { it.answer != card.answer && it.answer.isNotBlank() } >= 3
        return if (qcm) {
            val distractors = cards.map { it.answer }
                .filter { it != card.answer && it.isNotBlank() }
                .distinct()
                .shuffled(rng)
                .take(3)
            Challenge(true, card.question, card.answer, (distractors + card.answer).shuffled(rng))
        } else {
            Challenge(false, card.question, card.answer, emptyList())
        }
    }

    /**
     * Le verrouillage est utilisable s'il y a au moins un paquet avec 4 cartes
     * pour composer des questions variées.
     */
    fun isEligible(deckCardCounts: Collection<Int>): Boolean = deckCardCounts.any { it >= 4 }

    /** Comparaison tolérante (casse, accents, ponctuation, espaces) — même règle que le mode Réécrire. */
    fun normalized(s: String): String = s.trim()
        .replace(Regex("[\\p{Punct}\\p{Space}]+"), " ")
        .replace(Regex("[àâä]"), "a").replace(Regex("[éèêë]"), "e")
        .replace(Regex("[îï]"), "i").replace(Regex("[ôö]"), "o")
        .replace(Regex("[ùûü]"), "u").replace("ç", "c").replace("œ", "oe")
        .lowercase()
}
