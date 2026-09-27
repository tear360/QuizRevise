package com.leov.quizrevise.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.Timer

/** Palette Material 3 identique à l'app Android. */
object M3 {
    val PRIMARY = Color(0x6750A4)
    val PRIMARY_DARK = Color(0x381E72)
    val PRIMARY_CONTAINER = Color(0xEADDFF)
    val BACKGROUND = Color(0xFAF8FF)
    val SURFACE = Color.WHITE
    val ON_SURFACE = Color(0x1D1B20)
    val ON_SURFACE_VARIANT = Color(0x49454F)
    val OUTLINE = Color(0x79747E)
    val CORRECT = Color(0x1B873B)
    val WRONG = Color(0xB3261E)

    private val base = Font("Segoe UI", Font.PLAIN, 14)
    val title: Font = base.deriveFont(Font.BOLD, 17f)
    val body: Font = base
    val bodyBold: Font = base.deriveFont(Font.BOLD, 14f)
    val caption: Font = base.deriveFont(12f)
    val cardText: Font = base.deriveFont(Font.BOLD, 26f)
}

private fun darker(c: Color, f: Float) = Color((c.red * f).toInt(), (c.green * f).toInt(), (c.blue * f).toInt())

/** Coin arrondi générique (cartes, pastilles). */
open class RoundedPanel(
    private val radius: Int,
    private val bg: Color,
    layout: java.awt.LayoutManager? = GridBagLayout()
) : JPanel(layout) {
    var borderColor: Color? = null

    init { isOpaque = false }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bg
        g2.fill(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), radius.toDouble(), radius.toDouble()))
        borderColor?.let {
            g2.color = it
            g2.draw(RoundRectangle2D.Double(0.0, 0.0, width - 1.0, height - 1.0, radius.toDouble(), radius.toDouble()))
        }
        g2.dispose()
        super.paintComponent(g)
    }
}

/** Bouton pill Material (plein ou tonal). */
class PillButton(text: String, private val bg: Color, fg: Color, onClick: (() -> Unit)? = null) : JButton(text) {
    init {
        onClick?.let { cb -> addActionListener { cb() } }
        isContentAreaFilled = false
        isFocusPainted = false
        isBorderPainted = false
        isOpaque = false
        foreground = fg
        font = M3.bodyBold
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = if (model.isRollover) darker(bg, 0.9f) else bg
        g2.fill(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 999.0, 999.0))
        g2.dispose()
        super.paintComponent(g)
    }

    override fun getPreferredSize(): Dimension {
        val d = super.getPreferredSize()
        return Dimension(d.width + 40, maxOf(46, d.height + 16))
    }
}

/** Header Material : bande violette avec titre blanc (comme la toolbar Android). */
class Header(title: String) : JPanel(BorderLayout()) {
    private val label = JLabel(title).apply {
        foreground = Color.WHITE
        font = M3.title
        border = javax.swing.border.EmptyBorder(0, 24, 0, 24)
    }

    init {
        background = M3.PRIMARY
        border = javax.swing.border.EmptyBorder(8, 0, 8, 0)
        add(label, BorderLayout.CENTER)
    }

    override fun getPreferredSize(): Dimension = Dimension(0, 56)

    fun setTitle(t: String) { label.text = t }
}

/** FAB violet rond avec « + » (comme le FloatingActionButton Android). */
class Fab(onClick: () -> Unit) : JPanel(null) {
    init {
        isOpaque = false
        preferredSize = Dimension(56, 56)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        val plus = JLabel("＋").apply {
            foreground = Color.WHITE
            font = Font("Segoe UI", Font.PLAIN, 26)
            bounds = java.awt.Rectangle(0, 0, 56, 56)
            horizontalAlignment = SwingConstants.CENTER
        }
        add(plus)
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) = onClick()
            override fun mouseEntered(e: MouseEvent?) { background = M3.PRIMARY; repaint() }
        })
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = M3.PRIMARY
        g2.fillOval(0, 0, width, height)
        g2.dispose()
    }
}

/**
 * Carte à retourner : animation de retournement (compression horizontale façon rotation 3D),
 * bascule question ↔ réponse illimitée à chaque clic.
 */
class FlipCard(private val onSideChanged: (Boolean) -> Unit) : RoundedPanel(24, M3.SURFACE) {
    var clickable = true
    private var showingAnswer = false
    private var everRevealed = false
    private var animating = false
    private var faceQuestion = ""
    private var faceAnswer = ""

    private val sideLabel = JLabel("QUESTION", SwingConstants.CENTER).apply {
        foreground = M3.PRIMARY
        font = M3.caption.deriveFont(Font.BOLD)
    }
    private val textLabel = JLabel("", SwingConstants.CENTER).apply {
        foreground = M3.ON_SURFACE
        font = M3.cardText
    }
    private val hintLabel = JLabel("Touche la carte pour la retourner", SwingConstants.CENTER).apply {
        foreground = M3.OUTLINE
        font = M3.caption
    }
    private val inner = JPanel(GridBagLayout()).apply {
        isOpaque = false
        border = javax.swing.border.EmptyBorder(32, 28, 32, 28)
        layout = GridBagLayout()
        val gbc = java.awt.GridBagConstraints().apply {
            gridx = 0; gridy = 0; weightx = 1.0; fill = java.awt.GridBagConstraints.HORIZONTAL
        }
        add(sideLabel, gbc)
        gbc.gridy = 1; gbc.insets = Insets(18, 0, 0, 0); add(textLabel, gbc)
        gbc.gridy = 2; gbc.insets = Insets(26, 0, 0, 0); add(hintLabel, gbc)
    }

    init {
        layout = BorderLayout(0, 0)
        borderColor = M3.OUTLINE
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(inner, BorderLayout.CENTER)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) { if (clickable) flip() }
        })
    }

    fun setCard(question: String, answer: String) {
        setFaces(question, answer)
        showingAnswer = false
        everRevealed = false
        animating = false
        applySide(notify = false)
    }

    /** Retournement illimité : compression → bascule du contenu → expansion. */
    fun flip() {
        if (animating || !isVisible) return
        animating = true
        inner.isVisible = false
        val fullW = width
        val timer = Timer(14, null)
        var step = 0
        var phase = 0
        timer.addActionListener {
            step++
            if (phase == 0) {
                val w = (fullW * (1f - step / 8f)).toInt().coerceIn(2, fullW)
                inner.setBounds((width - w) / 2, 0, w, height)
            } else {
                val w = (2 + (fullW - 2) * (step / 8f)).toInt().coerceIn(2, fullW)
                inner.setBounds((width - w) / 2, 0, w, height)
            }
            repaint()
            if (step >= 8) {
                step = 0
                if (phase == 0) {
                    showingAnswer = !showingAnswer
                    if (showingAnswer && !everRevealed) everRevealed = true
                    applySide()
                    phase = 1
                } else {
                    inner.isVisible = true
                    inner.setBounds(0, 0, fullW, height)
                    timer.stop()
                    animating = false
                    revalidate(); repaint()
                }
            }
        }
        timer.start()
    }

    private fun applySide(notify: Boolean = true) {
        if (showingAnswer) {
            sideLabel.text = "RÉPONSE"
            textLabel.text = faceAnswer
        } else {
            sideLabel.text = "QUESTION"
            textLabel.text = faceQuestion
        }
        hintLabel.isVisible = !everRevealed
        if (notify) onSideChanged(showingAnswer)
    }

    fun setFaces(question: String, answer: String) {
        faceQuestion = question
        faceAnswer = answer
    }
}
