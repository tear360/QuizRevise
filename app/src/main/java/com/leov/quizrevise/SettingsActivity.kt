package com.leov.quizrevise

import android.net.Uri
import android.os.Bundle
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar

class SettingsActivity : AppCompatActivity() {
    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var db: AppDatabase
    private var exportDeckId = 0L
    private var settingsLoaded = false

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = if (exportDeckId > 0) Transfer.exportDeckJson(db, exportDeckId)
                else Transfer.exportAllJson(db)
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Paquet exporté ✅", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Échec de l'export", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        var total = 0
        uris.forEach { uri ->
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                total += Transfer.importJson(db, json)
            } catch (_: Exception) { }
        }
        Toast.makeText(this, if (total > 0) "✅ $total paquet(s) importé(s)" else "Aucun paquet importé (fichier invalide ?)", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        db = AppDatabase(this)

        findViewById<MaterialToolbar>(R.id.settingsToolbar).setNavigationOnClickListener { finish() }
        findViewById<RadioGroup>(R.id.themeOptions).apply {
            check(when (settings.getString("theme", "system")) {
                "light" -> R.id.themeLight
                "dark" -> R.id.themeDark
                else -> R.id.themeSystem
            })
            setOnCheckedChangeListener { _, id ->
                if (!settingsLoaded) return@setOnCheckedChangeListener
                val choice = when (id) {
                    R.id.themeLight -> "light"
                    R.id.themeDark -> "dark"
                    else -> "system"
                }
                if (choice != settings.getString("theme", "system")) {
                    settings.edit().putString("theme", choice).apply()
                    applyTheme()
                    recreate()
                }
            }
        }
        findViewById<android.view.View>(R.id.settingsImport).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }
        findViewById<android.view.View>(R.id.settingsExport).setOnClickListener {
            exportDeckId = 0
            exportLauncher.launch("mes-paquets")
        }
        findViewById<android.view.View>(R.id.settingsStats).setOnClickListener { showStats() }
        findViewById<android.view.View>(R.id.settingsUpdates).setOnClickListener {
            Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
            UpdateManager.check(this, silent = false)
        }
        findViewById<android.view.View>(R.id.settingsAbout).setOnClickListener { showAbout() }
        settingsLoaded = true
    }

    private fun applyTheme() {
        val mode = when (settings.getString("theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showStats() {
        val (reviews, correct) = db.todayStats()
        val accuracy = if (reviews > 0) correct * 100 / reviews else 0
        val message = """
            ${getString(R.string.studied_today, reviews)}

            ${getString(R.string.streak)} : ${db.currentStreak()} ${getString(R.string.days)}
            ${getString(R.string.best_streak)} : ${db.bestStreak()} ${getString(R.string.days)}
            ${getString(R.string.accuracy)} : $accuracy%
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle(R.string.stats_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Version ${BuildConfig.VERSION_NAME}\n\nAlternative libre à Quizlet : flashcards, QCM et statistiques.\n\nMises à jour automatiques via GitHub.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
