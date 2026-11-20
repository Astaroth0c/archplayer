package br.archplayer.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject

object UpdateManager {

    private const val RELEASES_API = "https://api.github.com/repos/Astaroth0c/archplayer/releases/latest"

    fun currentVersion(activity: Activity): String =
        runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

    fun checkAndPrompt(activity: Activity) {
        Thread {
            runCatching {
                val connection = java.net.URL(RELEASES_API).openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "ArchPlayer/1.0")
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val latest = tagName.removePrefix("v")
                val current = currentVersion(activity)
                if (!isNewer(latest, current)) return@runCatching

                val asset = json.optJSONArray("assets")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                        .firstOrNull { it.optString("name", "").endsWith(".apk") }
                } ?: return@runCatching
                val apkUrl = asset.optString("browser_download_url")
                val name = asset.optString("name", "ArchPlayer-update.apk")

                val apkFile = File(activity.cacheDir, name)
                if (!apkFile.exists()) {
                    val dl = java.net.URL(apkUrl).openConnection() as java.net.HttpURLConnection
                    dl.connectTimeout = 15_000
                    dl.readTimeout = 60_000
                    dl.instanceFollowRedirects = true
                    dl.inputStream.use { input ->
                        apkFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                activity.runOnUiThread {
                    AlertDialog.Builder(activity)
                        .setTitle("Nova versao $latest")
                        .setMessage("Voce esta usando $current. Instalar a atualizacao?\n\nSe aparecer 'App nao instalado' ou 'conflito de pacotes', desinstale o ArchPlayer antigo antes de instalar.")
                        .setPositiveButton("Atualizar agora") { _, _ -> install(activity, apkFile) }
                        .setNegativeButton("Depois", null)
                        .show()
                }
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(intent) }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return false
    }
}
