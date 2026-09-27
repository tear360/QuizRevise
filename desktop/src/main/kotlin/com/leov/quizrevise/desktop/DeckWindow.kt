package com.leov.quizrevise.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.SwingUtilities

/** Fenêtre de gestion des cartes d'un paquet, style Material 3. */
class DeckWindow(
    owner: java.awt.Window,
    private val db: DesktopDb,
    private val deck: Deck
) : JDialog(owner as? java.awt.Frame, deck.name, true) {

    private val rowsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 16, 8, 16)
    }
    private val emptyLabel = JLabel(
        "<html><div style='text-align:center'>Ce paquet est vide.<br>Ajoute ta première carte !</div></html>",
        javax.swing.SwingConstants.CENTER
    ).apply { foreground = M3.ON_SURFACE_VARIANT }

    init {
        size = Dimension(640, 680)
        setLocationRelativeTo(owner)
        layout = BorderLayout()

        val header = Header(deck.name)
        val back = PillButton("←", M3.PRIMARY_DARK, Color.WHITE).apply {
            preferredSize = Dimension(46, 36)
            font = Font("Segoe UI", Font.PLAIN, 14)
            addActionListener { dispose() }
        }
        header.add(back, BorderLayout.WEST)
        add(header, BorderLayout.NORTH)

        val scroll = JScrollPane(rowsPanel).apply {
            border = null
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            viewport.background = M3.BACKGROUND
            background = M3.BACKGROUND
        }
        val center = JPanel(BorderLayout()).apply { background = M3.BACKGROUND }
        center.add(emptyLabel, BorderLayout.NORTH)
        center.add(scroll, BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)

        val south = JPanel(FlowLayout(FlowLayout.TRAILING, 28, 20)).apply { isOpaque = false }
        south.add(PillButton("▶ Réviser", M3.PRIMARY, Color.WHITE) {
            if (db.cards(deck.id).size < 2) {
                JOptionPane.showMessageDialog(this, "Il faut au moins 2 cartes pour lancer une session.", "Réviser", JOptionPane.WARNING_MESSAGE)
            } else {
                StudyWindow(this, db, deck).isVisible = true
                refresh()
            }
        })
        south.add(Fab { askCard(null) })
        add(south, BorderLayout.SOUTH)

        refresh()
    }

    private fun refresh() {
        rowsPanel.removeAll()
        val cards = db.cards(deck.id)
        emptyLabel.isVisible = cards.isEmpty()
        cards.forEach { rowsPanel.add(cardRow(it)); rowsPanel.add(javax.swing.Box.createVerticalStrut(10)) }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun cardRow(card: Card): JPanel {
        val row = RoundedPanel(14, M3.SURFACE, BorderLayout(10, 0)).apply {
            borderColor = Color(0xEEEEEE)
        }
        val labels = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(JLabel(card.question).apply { font = M3.body.deriveFont(Font.BOLD, 14f) })
            add(JLabel(card.answer).apply { font = M3.body; foreground = M3.ON_SURFACE_VARIANT })
        }
        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        buttons.add(PillButton("Modifier", M3.PRIMARY_CONTAINER, M3.PRIMARY_DARK) { askCard(card) })
        buttons.add(PillButton("Supprimer", Color(0xFBE9E7), M3.WRONG) { confirmDelete(card) })
        row.add(labels, BorderLayout.CENTER)
        row.add(buttons, BorderLayout.EAST)
        row.border = BorderFactory.createEmptyBorder(12, 16, 12, 12)
        return row
    }

    private fun confirmDelete(card: Card) {
        val choice = JOptionPane.showConfirmDialog(
            this, "Supprimer cette carte ?", "Supprimer",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        )
        if (choice == JOptionPane.YES_OPTION) { db.deleteCard(card.id); refresh() }
    }

    private fun askCard(existing: Card?) {
        val qField = JTextField(if (existing != null) existing.question else "")
        val aField = JTextField(if (existing != null) existing.answer else "")
        val panel = JPanel(java.awt.GridBagLayout())
        val gbc = java.awt.GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6); fill = java.awt.GridBagConstraints.HORIZONTAL; weightx = 1.0
        }
        gbc.gridx = 0; gbc.gridy = 0; panel.add(JLabel("Question / Recto :"), gbc)
        gbc.gridx = 0; gbc.gridy = 1; panel.add(qField, gbc)
        gbc.gridx = 0; gbc.gridy = 2; panel.add(JLabel("Réponse / Verso :"), gbc)
        gbc.gridx = 0; gbc.gridy = 3; panel.add(aField, gbc)
        val result = JOptionPane.showConfirmDialog(
            this, panel,
            if (existing == null) "Ajouter une carte" else "Modifier la carte",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result == JOptionPane.OK_OPTION) {
            val q = qField.text.trim()
            val a = aField.text.trim()
            if (q.isNotEmpty() && a.isNotEmpty()) {
                if (existing == null) db.addCard(deck.id, q, a) else db.updateCard(existing.id, q, a)
                refresh()
            }
        }
    }
}
