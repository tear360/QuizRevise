package com.leov.quizrevise

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Système de mise à jour automatique via GitHub Releases.
 *
 * Principe : à chaque tag `v*` poussé sur GitHub, un workflow compile l'APK signé
 * et le publie en release. L'application interroge `releases/latest`, compare la
 * version, télécharge le nouvel APK (DownloadManager) puis propose l'installation.
 */
object UpdateManager {

    /** Repo GitHub source des releases (doit correspondre au repo publié). */
    const val REPO = "tear360/QuizRevise"

    private fun latestReleaseUrl() = "https://api.github.com/repos/$REPO/releases/latest"

    data class Release(val version: String, val apkUrl: String, val notes: String)

    /** Vérifie les mises à jour. [silent] = aucun message si tout est à jour. */
    fun check(activity: Activity, silent: Boolean) {
        Thread {
            var handled = false
            try {
                val conn = URL(latestReleaseUrl()).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "QuizRevise-App")
                val code = conn.responseCode
                if (code == 200) {
                    val body = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val release = JSONObject(body)
                    val latest = release.optString("tag_name", "").removePrefix("v")
                    val current = BuildConfig.VERSION_NAME
                    val apkUrl = findApk(release)

                    when {
                        apkUrl == null -> if (!silent) toast(activity, R.string.update_error)
                        isNewer(latest, current) -> {
                            handled = true
                            activity.runOnUiThread { confirm(activity, Release(latest, apkUrl, release.optString("body", "")), current) }
                        }
                        else -> {
                            handled = true
                            activity.runOnUiThread {
                                toast(activity, activity.getString(R.string.update_none, current))
                            }
                        }
                    }
                } else {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // pas de réseau / JSON inattendu
            }
            if (!handled) {
                activity.runOnUiThread {
                    if (!silent) toast(activity, R.string.update_error)
                }
            }
        }.start()
    }

    private fun findApk(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name", "")
            if (name.endsWith(".apk")) return a.optString("browser_download_url")
        }
        return null
    }

    /** Comparaison sémantique simple : "1.2.10" > "1.2.9". */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun confirm(activity: Activity, release: Release, current: String) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_available_title)
            .setMessage(activity.getString(R.string.update_available_msg, release.version, current))
            .setPositiveButton(R.string.update_download) { _, _ -> download(activity, release) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun download(activity: Activity, release: Release) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.update_channel)
            .setMessage(activity.getString(R.string.update_downloading, 0))
            .setCancelable(false)
            .create()
        dialog.show()

        // Téléchargement via DownloadManager : natif, reprise automatique, filet de sécurité.
        val dest = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "quizrevise-update.apk")
        dest.delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("QuizRévise ${release.version}")
            .setDescription("Mise à jour de QuizRévise")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(dest))
        val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = dm.enqueue(request)

        Thread {
            var running = true
            while (running) {
                var finished = false
                val q = DownloadManager.Query().setFilterById(id)
                dm.query(q).use { cursor ->
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val pct = if (total > 0) (downloaded * 100 / total).toInt() else 0
                        activity.runOnUiThread {
                            try {
                                dialog.setMessage(activity.getString(R.string.update_downloading, pct))
                            } catch (_: Exception) { }
                        }
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                running = false
                                finished = true
                                activity.runOnUiThread {
                                    try { dialog.dismiss() } catch (_: Exception) { }
                                    readyToInstall(activity)
                                }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                running = false
                                finished = true
                                activity.runOnUiThread {
                                    try { dialog.dismiss() } catch (_: Exception) { }
                                    toast(activity, R.string.update_failed)
                                }
                            }
                        }
                    }
                }
                if (!finished) Thread.sleep(500)
            }
        }.start()
    }

    private fun readyToInstall(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.update_channel)
            .setMessage(R.string.update_downloaded)
            .setPositiveButton(R.string.update_install) { _, _ -> install(activity) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    /** Lance l'installation de l'APK téléchargé (demande l'autorisation si besoin). */
    fun install(activity: Activity) {
        val file = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "quizrevise-update.apk")
        if (!file.exists()) {
            toast(activity, R.string.update_failed)
            return
        }
        // La permission « installer des apps inconnues » n'existe qu'à partir d'Android 8 (API 26).
        // Sur Android 5/6/7, l'installation inconnue passe par le réglage global de l'appareil.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
            toast(activity, R.string.update_downloaded)
            return
        }
        try {
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            toast(activity, R.string.update_failed)
        }
    }

    private fun toast(activity: Activity, resId: Int) =
        Toast.makeText(activity, resId, Toast.LENGTH_LONG).show()

    private fun toast(activity: Activity, msg: String) =
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
}
