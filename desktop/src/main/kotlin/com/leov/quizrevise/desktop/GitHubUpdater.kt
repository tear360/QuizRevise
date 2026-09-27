package com.leov.quizrevise.desktop

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Vérification des mises à jour via GitHub Releases (lecture publique, sans token). */
object GitHubUpdater {

    const val REPO = "tear360/QuizRevise"

    data class Release(val version: String, val downloadUrl: String?, val pageUrl: String)

    fun currentVersion(): String {
        // La version est injectée à la compilation (voir build.gradle.kts).
        val v = try {
            val clazz = Class.forName("com.leov.quizrevise.desktop.BuildConfig")
            clazz.getField("VERSION").get(null) as String
        } catch (_: Exception) {
            "dev"
        }
        return v
    }

    private fun latestUrl() = "https://api.github.com/repos/$REPO/releases/latest"

    /** Renvoie la dernière release publiée, ou null si indisponible (pas de réseau, etc.). */
    fun fetchLatest(): Release? = try {
        val conn = URL(latestUrl()).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "QuizRevise-Desktop")
        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
            val json = org.json.JSONTokener(body).nextValue() as org.json.JSONObject
            val tag = json.optString("tag_name", "").removePrefix("v")
            val page = json.optString("html_url", "https://github.com/$REPO/releases/latest")
            var apkLike: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                val os = System.getProperty("os.name").lowercase()
                val isWin = os.contains("win")
                val isLinux = os.contains("linux")
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    val n = a.optString("name", "").lowercase()
                    val url = a.optString("browser_download_url", "")
                    val match = (isWin && n.contains("windows")) || (isLinux && n.contains("linux"))
                    if (match) { apkLike = url; break }
                }
                if (apkLike == null && assets.length() > 0) {
                    apkLike = assets.getJSONObject(0).optString("browser_download_url")
                }
            }
            Release(tag, apkLike, page)
        } else null
    } catch (_: Exception) {
        null
    }

    /** Comparaison sémantique "1.2.10" > "1.2.9". */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Ouvre la page des releases dans le navigateur par défaut. */
    fun openDownloadPage(pageUrl: String) {
        try {
            Desktop.browse(URI(pageUrl))
        } catch (_: Exception) {
            // Environnement sans navigateur : rien de plus à faire
        }
    }

    private object Desktop {
        fun browse(uri: URI) {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("win") ->
                    Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", uri.toString()))
                os.contains("mac") ->
                    Runtime.getRuntime().exec(arrayOf("open", uri.toString()))
                else ->
                    Runtime.getRuntime().exec(arrayOf("xdg-open", uri.toString()))
            }
        }
    }
}
