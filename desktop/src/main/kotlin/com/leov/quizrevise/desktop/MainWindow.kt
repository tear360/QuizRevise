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
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/** Fenêtre principale, style Material 3 : header violet, cartes arrondies, FAB. */
class MainWindow : JFrame("QuizRévise") {

    private val db = DesktopDb()
    private lateinit var header: Header
    private val rowsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = BorderFactory.createEmptyBorder(16, 16, 8, 16)
    }
    private val emptyLabel = JLabel(
        "<html><div style='text-align:center'>Aucun paquet pour l'instant.<br>Crée ton premier paquet pour commencer à réviser.</div></html>",
        SwingConstants.CENTER
    ).apply { foreground = M3.ON_SURFACE_VARIANT }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(620, 700)
        minimumSize = Dimension(460, 540)
        setLocationRelativeTo(null)
        layout = BorderLayout()

        header = Header("Mes paquets")
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        actions.add(smallPill("📊", "Statistiques") { showStats() })
        actions.add(smallPill("🔄", "Rechercher les mises à jour") { checkUpdates(showError = true) })
        actions.add(smallPill("ℹ️", "À propos") { showAbout() })
        header.add(actions, BorderLayout.EAST)
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
        south.add(Fab { askNewDeck() })
        add(south, BorderLayout.SOUTH)

        refresh()
        SwingUtilities.invokeLater { checkUpdates(showError = false) }
    }

    private fun smallPill(text: String, tip: String, onClick: () -> Unit) =
        PillButton(text, M3.PRIMARY_DARK, Color.WHITE).apply {
            toolTipText = tip
            font = Font("Segoe UI", Font.PLAIN, 13)
            putClientProperty("onClick", onClick)
            addActionListener { onClick() }
            preferredSize = Dimension(46, 36)
        }

    private fun refresh() {
        rowsPanel.removeAll()
        val decks = db.decks()
        emptyLabel.isVisible = decks.isEmpty()
        decks.forEach { deck -> rowsPanel.add(deckRow(deck)) ; rowsPanel.add(javax.swing.Box.createVerticalStrut(10)) }
        rowsPanel.revalidate()
        rowsPanel.repaint()
    }

    private fun deckRow(deck: Deck): JPanel {
        val row = RoundedPanel(16, M3.SURFACE, BorderLayout(16, 0)).apply {
            borderColor = Color(0xEEEEEE)
            cursor = java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
        }
        val dot = RoundedPanel(10, Color(deck.color), GridBagLayout()).apply {
            preferredSize = Dimension(42, 42)
        }
        val labels = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val name = JLabel(deck.name).apply { font = M3.body.deriveFont(Font.BOLD, 15f) }
            val count = JLabel("${deck.cardCount} cartes").apply { font = M3.caption; foreground = M3.ON_SURFACE_VARIANT }
            add(name); add(count)
        }
        row.add(dot, BorderLayout.WEST)
        row.add(labels, BorderLayout.CENTER)
        row.border = BorderFactory.createEmptyBorder(12, 16, 12, 16)
        row.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (SwingUtilities.isLeftMouseButton(e)) openDeck(deck)
                if (SwingUtilities.isRightMouseButton(e)) showRowMenu(e, deck)
            }
        })
        return row
    }

    private fun showRowMenu(e: MouseEvent, deck: Deck) {
        val menu = JPopupMenu()
        val rename = JMenuItem("Renommer")
        rename.addActionListener { renameDeck(deck) }
        val delete = JMenuItem("Supprimer")
        delete.addActionListener { confirmDelete(deck) }
        menu.add(rename)
        menu.add(delete)
        menu.show(e.component, e.x, e.y)
    }

    private fun openDeck(deck: Deck) {
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

    private fun renameDeck(deck: Deck) {
        val name = JOptionPane.showInputDialog(this, "Nouveau nom :", "Renommer", JOptionPane.PLAIN_MESSAGE, null, null, deck.name) as? String
        if (!name.isNullOrBlank()) { db.renameDeck(deck.id, name.trim()); refresh() }
    }

    private fun confirmDelete(deck: Deck) {
        val choice = JOptionPane.showConfirmDialog(
            this, "Supprimer « ${deck.name} » et toutes ses cartes ?", "Supprimer",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        )
        if (choice == JOptionPane.YES_OPTION) { db.deleteDeck(deck.id); refresh() }
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
                        val choice = JOptionPane.showConfirmDialog(
                            this,
                            "La version ${release.version} est disponible (vous avez la $current).\n\nOuvrir la page de téléchargement ?",
                            "Mise à jour disponible", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE
                        )
                        if (choice == JOptionPane.YES_OPTION) GitHubUpdater.openDownloadPage(release.pageUrl)
                    }
                    showError -> JOptionPane.showMessageDialog(this, "Vous avez déjà la dernière version ($current).", "Mises à jour", JOptionPane.INFORMATION_MESSAGE)
                }
            }
        }.start()
    }
}
