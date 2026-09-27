package com.leov.quizrevise.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.Timer

/** Fenêtre de révision Material 3 : choix de mode, flashcards animées, QCM, score. */
class StudyWindow(
    owner: java.awt.Window,
    private val db: DesktopDb,
    private val deck: Deck
) : JDialog(owner as? java.awt.Frame, "Révision — ${deck.name}", true) {

    private val deckCards = db.cards(deck.id)
    private var order: List<Int> = emptyList()
    private var pos = 0
    private var correctCount = 0
    private var mode = "flash"
    private var currentAnswer = ""
    private var quizOptionButtons: List<JButton> = emptyList()

    private val header = Header("Révision — ${deck.name}")
    private val progress = JProgressBar(0, 100).apply {
        foreground = M3.PRIMARY
        border = BorderFactory.createEmptyBorder()
        preferredSize = Dimension(100, 6)
    }
    private val progressText = JLabel("", SwingConstants.CENTER).apply {
        font = M3.caption; foreground = M3.ON_SURFACE_VARIANT
    }

    private val cardArea = JPanel()
    private val flipCard = FlipCard { showFlashButtons -> flashButtons.isVisible = showFlashButtons }
    private val modeChoice = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(48, 40, 48, 40)
    }
    private val quizQuestion = JLabel("", SwingConstants.CENTER).apply {
        font = M3.cardText.deriveFont(22f); foreground = M3.ON_SURFACE
    }
    private val quizButtons = JPanel(GridLayout(2, 2, 8, 8)).apply { isOpaque = false }
    private val quizPanel = JPanel(BorderLayout()).apply {
        isOpaque = false
        add(quizQuestion, BorderLayout.CENTER)
        add(quizButtons, BorderLayout.SOUTH)
    }
    private val flashButtons = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 12, 8)).apply {
        isOpaque = false
        add(PillButton("Pas su", M3.WRONG, Color.WHITE) { answer(false) })
        add(PillButton("Je savais", M3.CORRECT, Color.WHITE) { answer(true) })
        isVisible = false
    }

    init {
        size = Dimension(680, 620)
        setLocationRelativeTo(owner)
        layout = BorderLayout()
        background = M3.BACKGROUND

        val back = PillButton("←", M3.PRIMARY_DARK, Color.WHITE).apply {
            preferredSize = Dimension(46, 36)
            font = Font("Segoe UI", Font.PLAIN, 14)
            addActionListener { dispose() }
        }
        header.add(back, BorderLayout.WEST)
        add(header, BorderLayout.NORTH)

        cardArea.layout = java.awt.CardLayout()
        cardArea.isOpaque = false
        cardArea.add(modeChoice, "mode")
        cardArea.add(flipCard, "flip")
        cardArea.add(quizPanel, "quiz")

        val center = JPanel(BorderLayout()).apply {
            background = M3.BACKGROUND
            border = BorderFactory.createEmptyBorder(16, 24, 12, 24)
            add(progressText, BorderLayout.NORTH)
            add(cardArea, BorderLayout.CENTER)
        }
        add(center, BorderLayout.CENTER)

        val south = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 24, 16, 24)
            add(flashButtons, BorderLayout.NORTH)
            add(progress, BorderLayout.SOUTH)
        }
        add(south, BorderLayout.SOUTH)

        showModeChoice()
    }

    private fun switchTo(name: String) {
        (cardArea.layout as java.awt.CardLayout).show(cardArea, name)
    }

    private fun showModeChoice() {
        modeChoice.removeAll()
        modeChoice.add(JLabel("Comment veux-tu réviser ?").apply { font = M3.title; alignmentX = CENTER_ALIGNMENT })
        modeChoice.add(Box.createVerticalStrut(28))
        modeChoice.add(PillButton("🃏 Flashcards", M3.PRIMARY, Color.WHITE) { startSession("flash") }.apply { alignmentX = CENTER_ALIGNMENT })
        modeChoice.add(Box.createVerticalStrut(14))
        modeChoice.add(PillButton("❓ QCM", M3.PRIMARY, Color.WHITE) { startSession("quiz") }.apply { alignmentX = CENTER_ALIGNMENT })
        modeChoice.isVisible = true
        switchTo("mode")
        progress.isVisible = false
        progressText.isVisible = false
        flashButtons.isVisible = false
        quizButtons.isVisible = false
    }

    private fun startSession(m: String) {
        mode = m
        order = deckCards.indices.shuffled()
        pos = 0
        correctCount = 0
        progress.maximum = order.size
        progress.value = 0
        progress.isVisible = true
        progressText.isVisible = true
        showQuestion()
    }

    private fun showQuestion() {
        val card = deckCards[order[pos]]
        currentAnswer = card.answer
        progressText.text = "${pos + 1} / ${order.size}"
        progress.value = pos

        if (mode == "flash") {
            flashButtons.isVisible = false
            quizButtons.isVisible = false
            flipCard.setCard(card.question, card.answer)
            switchTo("flip")
        } else {
            quizQuestion.text = card.question
            quizButtons.removeAll()
            val distractors = deckCards.map { it.answer }.filter { it != currentAnswer }.distinct().shuffled().take(3)
            val options = (distractors + currentAnswer).shuffled()
            quizOptionButtons = options.map { opt ->
                PillButton(opt, M3.PRIMARY, Color.WHITE).also { btn ->
                    btn.addActionListener { onQuizAnswer(btn, opt) }
                    btn.horizontalAlignment = SwingConstants.CENTER
                }
            }
            quizOptionButtons.forEach { quizButtons.add(it) }
            quizButtons.isVisible = true
            quizButtons.revalidate(); quizButtons.repaint()
            switchTo("quiz")
        }
    }

    private fun onQuizAnswer(button: JButton, choice: String) {
        if (choice == currentAnswer) {
            button.background = M3.CORRECT
            answer(true)
        } else {
            button.background = M3.WRONG
            quizOptionButtons.filter { it.text == currentAnswer }.forEach { it.background = M3.CORRECT }
            answer(false)
        }
        quizOptionButtons.forEach { it.isEnabled = false }
    }

    private fun answer(ok: Boolean) {
        if (ok) correctCount++
        pos++
        progress.value = pos
        Timer(650) {
            if (pos >= order.size) finishSession() else showQuestion()
        }.apply { isRepeats = false }.start()
    }

    private fun finishSession() {
        db.recordSession(order.size, correctCount)
        flashButtons.isVisible = false
        quizButtons.isVisible = false
        progress.isVisible = false
        progressText.text = "Session terminée !"
        modeChoice.removeAll()
        modeChoice.add(JLabel("Session terminée !").apply { font = M3.title; alignmentX = CENTER_ALIGNMENT })
        modeChoice.add(Box.createVerticalStrut(12))
        modeChoice.add(JLabel("Score : $correctCount / ${order.size} (${correctCount * 100 / order.size}%)").apply {
            font = M3.cardText.deriveFont(20f); foreground = M3.PRIMARY; alignmentX = CENTER_ALIGNMENT
        })
        modeChoice.add(Box.createVerticalStrut(24))
        modeChoice.add(PillButton("Recommencer", M3.PRIMARY, Color.WHITE) { startSession(mode) }.apply { alignmentX = CENTER_ALIGNMENT })
        modeChoice.add(Box.createVerticalStrut(10))
        modeChoice.add(PillButton("Retour au paquet", M3.PRIMARY_CONTAINER, M3.PRIMARY_DARK) { dispose() }.apply { alignmentX = CENTER_ALIGNMENT })
        modeChoice.isVisible = true
        switchTo("mode")
        modeChoice.revalidate(); modeChoice.repaint()
    }
}
