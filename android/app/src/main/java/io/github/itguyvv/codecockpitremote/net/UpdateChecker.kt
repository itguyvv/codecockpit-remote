package io.github.itguyvv.codecockpitremote.net

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Latest GitHub release, when it is newer than the running build */
data class UpdateInfo(val tag: String, val apkUrl: String)

sealed interface UpdateResult {
    data class Available(val info: UpdateInfo) : UpdateResult
    data object Latest : UpdateResult
    data object Failed : UpdateResult
}

/**
 * Self-update against GitHub releases. Check is a plain blocking HTTPS call (run it off the
 * main thread); download goes through the system DownloadManager, and installing hands the
 * APK to the platform installer — the unknown-sources consent is the platform's, not ours.
 */
object UpdateChecker {
    private const val RELEASES_LATEST =
        "https://api.github.com/repos/itguyvv/codecockpit-remote/releases/latest"

    /** Blocking network call — call from a background coroutine */
    fun check(currentVersion: String): UpdateResult {
        val conn = URL(RELEASES_LATEST).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) return UpdateResult.Failed
            val body = conn.inputStream.bufferedReader().readText()
            val obj = Json.parseToJsonElement(body).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content?.removePrefix("v") ?: return UpdateResult.Failed
            val apkUrl = obj["assets"]?.jsonArray
                ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                ?.firstOrNull { (it["name"]?.jsonPrimitive?.content ?: "").endsWith(".apk") }
                ?.let { it["browser_download_url"]?.jsonPrimitive?.content }
                ?: return UpdateResult.Failed
            if (isNewer(tag, currentVersion)) UpdateResult.Available(UpdateInfo(tag, apkUrl)) else UpdateResult.Latest
        } catch (e: Exception) {
            UpdateResult.Failed
        } finally {
            conn.disconnect()
        }
    }

    /** Numeric segment compare: 0.2.10 > 0.2.9; malformed versions never report newer */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: return false }
        val b = current.split('.').map { it.toIntOrNull() ?: return false }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Enqueue the APK into the system Downloads list; progress shows as a system notification */
    fun download(context: Context, info: UpdateInfo): Long {
        val name = "CodeCockpitRemote-${info.tag}.apk"
        val req = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return dm.enqueue(req)
    }

    /** Hand the finished APK to the platform installer (unknown-sources consent is the platform's) */
    fun openApk(context: Context, tag: String) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = File(dir, "CodeCockpitRemote-$tag.apk")
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
