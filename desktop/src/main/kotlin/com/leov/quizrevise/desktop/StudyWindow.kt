package com.leov.quizrevise.desktop

import java.awt.*
import javax.swing.*

/** Fenêtre de révision : flashcards (je savais / pas su) et QCM (4 choix), puis écran de score. */
class StudyWindow(
    owner: JDialog,
    private val db: DesktopDb,
    private val deck: Deck
) : JDialog(owner, "Révision — ${deck.name}", true) {

    private val deckCards = db.cards(deck.id)
    private var order: List<Int> = emptyList()
    private var pos = 0
    private var correctCount = 0
    private var mode = "flash"
    private var currentAnswer = ""
    private var quizOptionButtons: List<JButton> = emptyList()

    private val cardPanel = JPanel(GridBagLayout()).apply {
        background = Color.WHITE
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(0x79747E), 1, true),
            BorderFactory.createEmptyBorder(24, 24, 24, 24)
        )
    }
    private val sideLabel = JLabel("QUESTION", SwingConstants.CENTER).apply { foreground = Color(0x6750A4) }
    private val cardText = JLabel("", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD, 24f) }
    private val hintLabel = JLabel("Touche la carte pour la retourner", SwingConstants.CENTER).apply { foreground = Color(0x79747E) }
    private val flashButtons = JPanel(FlowLayout(FlowLayout.CENTER, 12, 8))
    private val quizButtons = JPanel(GridLayout(2, 2, 8, 8))
    private val progress = JProgressBar()
    private val progressText = JLabel("", SwingConstants.CENTER)
    private val modeButtons = JPanel(FlowLayout(FlowLayout.CENTER, 12, 16))

    init {
        size = Dimension(600, 520)
        setLocationRelativeTo(owner)
        layout = BorderLayout()

        cardPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints().apply {
            gridx = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0
        }
        gbc.gridy = 0; cardPanel.add(sideLabel, gbc)
        gbc.gridy = 1; gbc.insets = Insets(16, 0, 0, 0); cardPanel.add(cardText, gbc)
        gbc.gridy = 2; gbc.insets = Insets(20, 0, 0, 0); cardPanel.add(hintLabel, gbc)
        cardPanel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (mode == "flash" && sideLabel.text == "QUESTION") reveal()
            }
        })

        val knew = JButton("Je savais").apply { background = Color(0x1B873B); foreground = Color.WHITE; isOpaque = true; isBorderPainted = false }
        val didnt = JButton("Pas su").apply { background = Color(0xB3261E); foreground = Color.WHITE; isOpaque = true; isBorderPainted = false }
        knew.addActionListener { answer(true) }
        didnt.addActionListener { answer(false) }
        flashButtons.add(didnt)
        flashButtons.add(knew)
        flashButtons.isVisible = false

        quizButtons.isVisible = false

        val flashMode = JButton("🃏 Flashcards")
        val quizMode = JButton("❓ QCM")
        modeButtons.add(flashMode)
        modeButtons.add(quizMode)
        flashMode.addActionListener { startSession("flash") }
        quizMode.addActionListener { startSession("quiz") }

        val center = JPanel(BorderLayout())
        val southStack = JPanel(BorderLayout())
        southStack.add(flashButtons, BorderLayout.NORTH)
        southStack.add(quizButtons, BorderLayout.CENTER)
        center.add(cardPanel, BorderLayout.CENTER)
        center.add(southStack, BorderLayout.SOUTH)

        add(modeButtons, BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        add(buildSouthPanel(), BorderLayout.SOUTH)
    }

    private fun buildSouthPanel(): JPanel {
        val south = JPanel(BorderLayout())
        south.add(progressText, BorderLayout.NORTH)
        south.add(progress, BorderLayout.SOUTH)
        return south
    }

    private fun startSession(m: String) {
        mode = m
        order = deckCards.indices.shuffled()
        pos = 0
        correctCount = 0
        progress.maximum = order.size
        progress.value = 0
        modeButtons.isVisible = false
        showQuestion()
    }

    private fun showQuestion() {
        val card = deckCards[order[pos]]
        currentAnswer = card.answer
        sideLabel.text = "QUESTION"
        cardText.text = card.question
        hintLabel.text = if (mode == "flash") "Touche la carte pour la retourner" else ""
        progressText.text = "${pos + 1} / ${order.size}"
        flashButtons.isVisible = false
        if (mode == "quiz") {
            quizButtons.removeAll()
            val distractors = deckCards.map { it.answer }.filter { it != currentAnswer }.distinct().shuffled().take(3)
            val options = (distractors + currentAnswer).shuffled()
            quizOptionButtons = options.map { opt ->
                JButton(opt).apply {
                    isOpaque = true; isBorderPainted = false
                    background = Color(0x6750A4); foreground = Color.WHITE
                    addActionListener { onQuizAnswer(this, opt) }
                }
            }
            quizOptionButtons.forEach { quizButtons.add(it) }
            quizButtons.revalidate()
            quizButtons.repaint()
            quizButtons.isVisible = true
        } else {
            quizButtons.isVisible = false
        }
        centerPanel().revalidate()
        centerPanel().repaint()
    }

    private fun centerPanel(): JPanel = contentPane.getComponent(1) as JPanel

    private fun reveal() {
        sideLabel.text = "RÉPONSE"
        cardText.text = currentAnswer
        hintLabel.text = ""
        flashButtons.isVisible = true
    }

    private fun onQuizAnswer(button: JButton, choice: String) {
        if (choice == currentAnswer) {
            button.background = Color(0x1B873B)
            answer(true)
        } else {
            button.background = Color(0xB3261E)
            quizOptionButtons.filter { it.text == currentAnswer }.forEach { it.background = Color(0x1B873B) }
            answer(false)
        }
        quizOptionButtons.forEach { it.isEnabled = false }
    }

    private fun answer(ok: Boolean) {
        if (ok) correctCount++
        pos++
        progress.value = pos
        javax.swing.Timer(700) {
            if (pos >= order.size) finishSession() else showQuestion()
        }.apply { isRepeats = false }.start()
    }

    private fun finishSession() {
        db.recordSession(order.size, correctCount)
        cardPanel.removeAll()
        val gbc = GridBagConstraints().apply { gridx = 0; fill = GridBagConstraints.HORIZONTAL; weightx = 1.0 }
        gbc.gridy = 0; cardPanel.add(JLabel("Session terminée !", SwingConstants.CENTER).apply { font = font.deriveFont(Font.BOLD, 24f) }, gbc)
        gbc.gridy = 1; gbc.insets = Insets(16, 0, 0, 0)
        cardPanel.add(JLabel("Score : $correctCount / ${order.size} (${correctCount * 100 / order.size}%)", SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 20f); foreground = Color(0x6750A4)
        }, gbc)
        gbc.gridy = 2; gbc.insets = Insets(24, 0, 0, 0)
        cardPanel.add(JButton("Recommencer").apply { addActionListener { startSession(mode) } }, gbc)
        cardPanel.revalidate()
        cardPanel.repaint()
        flashButtons.isVisible = false
        quizButtons.isVisible = false
        progressText.text = ""
    }
}
