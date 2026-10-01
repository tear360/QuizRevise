package com.leov.quizrevise

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * Réglages du verrouillage d'applications (Android uniquement) :
 * choix des applis à bloquer, des paquets de questions, activation
 * du service d'accessibilité et test du verrou.
 */
class AppLockSettingsActivity : AppCompatActivity() {

    private val settings by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private lateinit var db: AppDatabase

    private lateinit var lockSwitch: SwitchMaterial
    private lateinit var statusText: TextView
    private lateinit var appsSummary: TextView
    private lateinit var decksSummary: TextView

    private val selectedApps = LinkedHashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_applock)
        db = AppDatabase(this)

        findViewById<MaterialToolbar>(R.id.appLockToolbar).setNavigationOnClickListener { finish() }

        lockSwitch = findViewById(R.id.appLockSwitch)
        statusText = findViewById(R.id.appLockStatus)
        appsSummary = findViewById(R.id.appLockAppsSummary)
        decksSummary = findViewById(R.id.appLockDecksSummary)

        lockSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (!isAccessibilityEnabled()) {
                    lockSwitch.isChecked = false
                    askAccessibilityPermission()
                } else {
                    AppLockManager.setEnabled(this, true)
                }
            } else {
                AppLockManager.setEnabled(this, false)
            }
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.btnPickApps).setOnClickListener { pickApps() }
        findViewById<MaterialButton>(R.id.btnPickDecks).setOnClickListener { pickDecks() }
        findViewById<MaterialButton>(R.id.btnTestLock).setOnClickListener {
            // Auto-test : QuizRévise se verrouille lui-même (sans risque, on est dedans).
            startActivity(
                Intent(this, BlockerActivity::class.java)
                    .putExtra(BlockerActivity.EXTRA_PACKAGE, packageName)
            )
        }

        reloadSelection()
    }

    override fun onResume() {
        super.onResume()
        // Le retour des réglages système peut avoir changé l'état du service.
        if (isAccessibilityEnabled() && !lockSwitch.isChecked) {
            lockSwitch.isChecked = true
            AppLockManager.setEnabled(this, true)
        }
        refreshStatus()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${AppLockService::class.java.name}", ignoreCase = true) ||
            enabled.contains(packageName, ignoreCase = true)
    }

    private fun askAccessibilityPermission() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_block_accessibility_title)
            .setMessage(R.string.app_block_accessibility_msg)
            .setPositiveButton(R.string.app_block_open_settings) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun refreshStatus() {
        val serviceOn = isAccessibilityEnabled() && AppLockManager.enabled(this)
        statusText.text = getString(
            if (serviceOn) R.string.app_block_status_on else R.string.app_block_status_off
        )
        appsSummary.text = getString(
            R.string.app_block_selected_count,
            AppLockManager.blockedApps(this).size
        )
        decksSummary.text = getString(
            R.string.app_block_decks_count,
            AppLockManager.questionDeckIds(this).size
        )
    }

    private fun reloadSelection() {
        selectedApps.clear()
        selectedApps.addAll(AppLockManager.blockedApps(this))
        lockSwitch.isChecked = isAccessibilityEnabled() && AppLockManager.enabled(this)
        refreshStatus()
    }

    /** Liste des applications lançables installées (hors QuizRévise), triées par nom. */
    private data class AppEntry(val label: String, val pkg: String)

    private fun launchableApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName != packageName }
            .distinctBy { it.packageName }
            .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun pickApps() {
        val apps = launchableApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.app_block_no_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].pkg in selectedApps }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_block_apps_section)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val pkg = apps[which].pkg
                if (isChecked) {
                    if (selectedApps.size >= AppLockManager.MAX_APPS) {
                        Toast.makeText(
                            this, getString(R.string.app_block_max_apps, AppLockManager.MAX_APPS),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Réaffiche la liste avec l'état corrigé.
                        runOnUiThread { refreshStatus() }
                    } else {
                        selectedApps.add(pkg)
                    }
                } else {
                    selectedApps.remove(pkg)
                }
            }
            .setPositiveButton(R.string.save) { _, _ ->
                AppLockManager.setBlockedApps(this, selectedApps.toSet())
                refreshStatus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickDecks() {
        val decks = db.decks()
        if (decks.isEmpty()) {
            Toast.makeText(this, R.string.app_block_needs_cards, Toast.LENGTH_SHORT).show()
            return
        }
        val chosen = AppLockManager.questionDeckIds(this).toMutableSet()
        val labels = decks.map { "${it.name} (${it.cardCount} cartes)" }.toTypedArray()
        val checked = BooleanArray(decks.size) { decks[it].id in chosen }

        AlertDialog.Builder(this)
            .setTitle(R.string.app_block_decks_section)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) {
                    if (chosen.size >= AppLockManager.PREF_MAX_DECKS) {
                        Toast.makeText(
                            this, getString(R.string.app_block_max_decks, AppLockManager.PREF_MAX_DECKS),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        chosen.add(decks[which].id)
                    }
                } else {
                    chosen.remove(decks[which].id)
                }
            }
            .setPositiveButton(R.string.save) { _, _ ->
                AppLockManager.setQuestionDeckIds(this, chosen)
                refreshStatus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyTheme() {
        val mode = when (settings.getString("theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
