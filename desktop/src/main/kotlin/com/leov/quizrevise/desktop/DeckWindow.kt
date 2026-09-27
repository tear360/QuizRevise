package com.leov.quizrevise.desktop

import java.awt.*
import javax.swing.*

/** Fenêtre de gestion des cartes d'un paquet (ajout, édition, suppression) + bouton Réviser. */
class DeckWindow(
    owner: JFrame,
    private val db: DesktopDb,
    private val deck: Deck
) : JDialog(owner, deck.name, true) {

    private val listModel = DefaultListModel<Card>()
    private val list = JList(listModel)

    init {
        size = Dimension(560, 620)
        setLocationRelativeTo(owner)
        layout = BorderLayout()

        val toolbar = JToolBar().apply { isFloatable = false }
        val addBtn = JButton("＋ Ajouter une carte")
        val studyBtn = JButton("▶ Réviser")
        toolbar.add(addBtn)
        toolbar.add(studyBtn)

        list.cellRenderer = CardRenderer()
        list.fixedCellHeight = 64
        val scroll = JScrollPane(list)
        val emptyLabel = JLabel("Ce paquet est vide.\nAjoute ta première carte !", SwingConstants.CENTER)
        emptyLabel.verticalAlignment = SwingConstants.CENTER

        add(toolbar, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)

        addBtn.addActionListener { askCard(null) }
        studyBtn.addActionListener {
            val cards = db.cards(deck.id)
            if (cards.size < 2) {
                JOptionPane.showMessageDialog(this, "Il faut au moins 2 cartes pour lancer une session.", "Réviser", JOptionPane.WARNING_MESSAGE)
            } else {
                StudyWindow(this, db, deck).isVisible = true
                refresh()
            }
        }
        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val idx = list.locationToIndex(e.point)
                    if (idx >= 0) askCard(listModel[idx])
                }
            }
        })

        refresh()
    }

    private fun refresh() {
        val selected = list.selectedValue?.id
        listModel.clear()
        db.cards(deck.id).forEach { listModel.addElement(it) }
        if (selected != null) {
            for (i in 0 until listModel.size()) {
                if (listModel[i].id == selected) { list.selectedIndex = i; break }
            }
        }
        list.repaint()
    }

    private fun askCard(existing: Card?) {
        val qField = JTextField(if (existing != null) existing.question else "")
        val aField = JTextField(if (existing != null) existing.answer else "")
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
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

    private class CardRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, selected: Boolean, focused: Boolean
        ): Component {
            val card = value as Card
            val panel = JPanel(BorderLayout(10, 0))
            panel.isOpaque = true
            panel.background = if (selected) Color(0xEADDFF) else Color.WHITE
            val labels = JPanel(GridBagLayout())
            labels.isOpaque = false
            val gbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
            gbc.gridx = 0; gbc.gridy = 0
            labels.add(JLabel(card.question).apply { font = font.deriveFont(Font.BOLD, 14f) }, gbc)
            gbc.gridx = 0; gbc.gridy = 1
            labels.add(JLabel(card.answer).apply { font = font.deriveFont(13f); foreground = Color(0x49454F) }, gbc)
            panel.add(labels, BorderLayout.CENTER)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(0xEEEEEE)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
            )
            return panel
        }
    }
}
