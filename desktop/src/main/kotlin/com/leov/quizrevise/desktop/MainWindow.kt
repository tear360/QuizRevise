package com.leov.quizrevise.desktop

import java.awt.*
import javax.swing.*

/** Fenêtre principale : liste des paquets, création, renommage, suppression, stats et mises à jour. */
class MainWindow : JFrame("QuizRévise") {

    private val db = DesktopDb()
    private val listModel = DefaultListModel<Deck>()
    private val list = JList(listModel)
    private val renderer = DeckRenderer()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(560, 640)
        minimumSize = Dimension(420, 480)
        setLocationRelativeTo(null)

        val toolbar = JToolBar().apply { isFloatable = false }
        val newBtn = JButton("＋ Nouveau paquet")
        val statsBtn = JButton("📊 Statistiques")
        val checkBtn = JButton("🔄 Rechercher les mises à jour")
        val aboutBtn = JButton("ℹ️ À propos")
        toolbar.add(newBtn)
        toolbar.add(statsBtn)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(checkBtn)
        toolbar.add(aboutBtn)

        list.cellRenderer = renderer
        list.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        list.fixedCellHeight = 52

        val scroll = JScrollPane(list)
        layout = BorderLayout()
        add(toolbar, BorderLayout.NORTH)
        add(scroll, BorderLayout.CENTER)

        newBtn.addActionListener { askNewDeck() }
        statsBtn.addActionListener { showStats() }
        checkBtn.addActionListener { checkUpdates(showError = true) }
        aboutBtn.addActionListener { showAbout() }

        list.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) openSelected()
            }
        })
        list.addKeyListener(object : java.awt.event.KeyAdapter() {
            override fun keyPressed(e: java.awt.event.KeyEvent) {
                if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) openSelected()
            }
        })

        refresh()
        // Vérification discrète des mises à jour à l'ouverture
        SwingUtilities.invokeLater { checkUpdates(showError = false) }
    }

    private fun refresh() {
        val selected = selectedDeck()?.id
        listModel.clear()
        db.decks().forEach { listModel.addElement(it) }
        if (selected != null) {
            for (i in 0 until listModel.size()) {
                if (listModel[i].id == selected) { list.selectedIndex = i; break }
            }
        }
        renderer.emptyVisible = listModel.isEmpty
        list.repaint()
    }

    private fun selectedDeck(): Deck? = list.selectedValue

    private fun openSelected() {
        val deck = selectedDeck() ?: return
        DeckWindow(this, db, deck).isVisible = true
        refresh()
    }

    private fun askNewDeck() {
        val name = JOptionPane.showInputDialog(
            this, "Nom du paquet (ex. : Anglais – Vocabulaire) :", "Nouveau paquet",
            JOptionPane.PLAIN_MESSAGE
        )?.trim() ?: return
        if (name.isNotEmpty()) {
            val colors = listOf(
                0xFF6750A4.toInt(), 0xFF1B873B.toInt(), 0xFFB3261E.toInt(),
                0xFF0B57D0.toInt(), 0xFFE8590C.toInt(), 0xFF7A1FA2.toInt()
            )
            db.createDeck(name, colors[db.decks().size % colors.size])
            refresh()
        }
    }

    private fun showStats() {
        val (reviews, correct) = db.todayStats()
        val accuracy = if (reviews > 0) correct * 100 / reviews else 0
        JOptionPane.showMessageDialog(
            this,
            "Cartes revues aujourd'hui : $reviews\n\n" +
                "Série actuelle : ${db.currentStreak()} jours\n" +
                "Meilleure série : ${db.bestStreak()} jours\n" +
                "Précision du jour : $accuracy%",
            "Statistiques", JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun showAbout() {
        JOptionPane.showMessageDialog(
            this,
            "QuizRévise ${GitHubUpdater.currentVersion()}\n\n" +
                "Alternative libre à Quizlet : flashcards, QCM et statistiques.\n" +
                "Fonctionne hors-ligne, données stockées en local.\n\n" +
                "Mises à jour : https://github.com/${GitHubUpdater.REPO}/releases/latest",
            "À propos", JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun checkUpdates(showError: Boolean) {
        Thread {
            val release = GitHubUpdater.fetchLatest()
            val current = GitHubUpdater.currentVersion()
            SwingUtilities.invokeLater {
                when {
                    release == null -> if (showError) {
                        JOptionPane.showMessageDialog(this, "Impossible de vérifier les mises à jour (connexion ?).", "Mises à jour", JOptionPane.WARNING_MESSAGE)
                    }
                    GitHubUpdater.isNewer(release.version, current) -> {
                        val assetHint = if (release.downloadUrl != null) "\n\nUn fichier pour votre système est disponible." else ""
                        val choice = JOptionPane.showConfirmDialog(
                            this,
                            "La version ${release.version} est disponible (vous avez la $current).$assetHint\n\nOuvrir la page de téléchargement ?",
                            "Mise à jour disponible", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
                        )
                        if (choice == JOptionPane.YES_OPTION) GitHubUpdater.openDownloadPage(release.pageUrl)
                    }
                    showError -> JOptionPane.showMessageDialog(this, "Vous avez déjà la dernière version ($current).", "Mises à jour", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        }.start()
    }

    private class DeckRenderer : DefaultListCellRenderer() {
        var emptyVisible = false
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, selected: Boolean, focused: Boolean
        ): Component {
            val deck = value as Deck
            val panel = JPanel(BorderLayout(12, 0))
            panel.isOpaque = true
            panel.background = if (selected) Color(0xEADDFF) else Color.WHITE
            val dot = JPanel()
            dot.preferredSize = Dimension(28, 28)
            dot.background = Color(deck.color)
            panel.add(dot, BorderLayout.WEST)
            val labels = JPanel(GridLayout(2, 1))
            val name = JLabel(deck.name).apply { font = font.deriveFont(Font.BOLD, 15f) }
            val count = JLabel("${deck.cardCount} cartes").apply { font = font.deriveFont(12f); foreground = Color(0x49454F) }
            labels.isOpaque = false
            labels.add(name)
            labels.add(count)
            panel.add(labels, BorderLayout.CENTER)
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(0xEEEEEE)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            )
            return panel
        }
    }
}
