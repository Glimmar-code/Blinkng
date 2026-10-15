package com.example.game

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Native-code-free on-demand WebAssembly game pack for website/sideload users.
 * DownloadManager owns the HTTP transfer, including when BLINK is backgrounded.
 * JS and WASM are served to a sandboxed WebView, never loaded as Android dex/so.
 *
 * The expected SHA is embedded in the signed release APK, NOT read from a mutable
 * network manifest. This prevents silently replacing a signed game version.
 */
object BlinkLifeWebPack {
    private const val TAG = "BlinkLifeWebPack"
    private const val PREF = "blink_life_web_delivery"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val MAX_ARCHIVE_BYTES = 160L * 1024 * 1024
    private const val MAX_EXTRACTED_BYTES = 320L * 1024 * 1024
    private val required = setOf("index.html", "index.js", "index.wasm", "index.pck")
    private val allowed = setOf("html", "js", "wasm", "pck", "png", "ico", "svg", "webp", "json")

    enum class Phase { UNAVAILABLE, NOT_INSTALLED, DOWNLOADING, VERIFYING, INSTALLED, FAILED }
    data class Snapshot(val phase: Phase, val downloaded: Long = 0, val total: Long = 0, val message: String = "")

    val expectedSha: String get() = BuildConfig.BLINK_LIFE_WEB_SHA256.lowercase()
    private fun validConfig(): Boolean = expectedSha.matches(Regex("[a-f0-9]{64}"))

    fun gameDirectory(context: Context): File = File(context.filesDir, "blink_life_web")
    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    private fun downloadManager(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private fun packageUrl(): String =
        "https://github.com/Glimmar-code/Blinkng/releases/download/v${BuildConfig.VERSION_NAME}/blink-life-web.zip"

    fun isInstalled(context: Context): Boolean {
        if (!validConfig()) return false
        val root = gameDirectory(context)
        return File(root, ".verified-sha256").takeIf { it.isFile }?.readText() == expectedSha &&
            required.all { File(root, it).isFile }
    }

    fun snapshot(context: Context): Snapshot {
        if (!validConfig()) return Snapshot(Phase.UNAVAILABLE, message = "Downloadable game pack is not published for this build.")
        if (isInstalled(context)) return Snapshot(Phase.INSTALLED)
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1)
        if (id < 0) return Snapshot(Phase.NOT_INSTALLED)
        return runCatching {
            downloadManager(context).query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return@use Snapshot(Phase.NOT_INSTALLED)
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> Snapshot(Phase.DOWNLOADING, downloaded, total,
                        if (status == DownloadManager.STATUS_PAUSED) "Waiting for network…" else "")
                    DownloadManager.STATUS_SUCCESSFUL -> Snapshot(Phase.VERIFYING, downloaded, total)
                    else -> Snapshot(Phase.FAILED, message = "Download was interrupted. Retry.")
                }
            }
        }.getOrElse { Snapshot(Phase.FAILED, message = "Download status unavailable.") }
    }

    /** The system notification and transfer survive leaving BLINK. */
    fun download(context: Context) {
        require(validConfig()) { "No signed game pack is configured for this APK" }
        cancel(context)
        val filename = "blink-life-${expectedSha.take(16)}.zip"
        val request = DownloadManager.Request(Uri.parse(packageUrl()))
            .setTitle("BLINK LIFE 3D")
            .setDescription("Downloading optional game files")
            .setMimeType("application/zip")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename)
        val id = downloadManager(context).enqueue(request)
        prefs(context).edit().putLong(KEY_DOWNLOAD_ID, id).apply()
    }

    fun cancel(context: Context) {
        val id = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)
        if (id >= 0) runCatching { downloadManager(context).remove(id) }
        prefs(context).edit().remove(KEY_DOWNLOAD_ID).apply()
    }

    /** Slow file operations: call ONLY off the UI thread (or from a background Worker). */
    fun verifyAndInstall(context: Context): Boolean {
        if (isInstalled(context)) return true
        if (snapshot(context).phase != Phase.VERIFYING) return false
        val filename = "blink-life-${expectedSha.take(16)}.zip"
        val archive = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), filename)
        val parent = gameDirectory(context).parentFile ?: return false
        val stage = File(parent, "blink_life_web_stage")
        return try {
            if (!archive.isFile || archive.length() !in 1..MAX_ARCHIVE_BYTES) error("Archive missing or too large")
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(archive).use { input ->
                val block = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(block)
                    if (read < 0) break
                    digest.update(block, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expectedSha) error("Archive SHA-256 differs from signed APK")
            stage.deleteRecursively()
            check(stage.mkdirs()) { "Unable to create game directory" }
            var extracted = 0L
            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                val names = mutableSetOf<String>()
                while (entries.hasMoreElements()) {
                    val item = entries.nextElement()
                    val name = item.name
                    if (item.isDirectory || name.contains('/') || name.contains('\\') ||
                        name.startsWith('.') || name.substringAfterLast('.', "").lowercase() !in allowed ||
                        !names.add(name)) error("Invalid file in game archive")
                    if (item.size < 0 || item.size > MAX_EXTRACTED_BYTES ||
                        extracted + item.size > MAX_EXTRACTED_BYTES) error("Game pack exceeds storage limit")
                    val target = File(stage, name)
                    zip.getInputStream(item).use { input ->
                        FileOutputStream(target).use { output ->
                            val written = input.copyTo(output)
                            if (written != item.size) error("Game asset length differs from ZIP entry")
                        }
                    }
                    extracted += item.size
                }
                check(required.all(names::contains)) { "Game package is incomplete" }
            }
            File(stage, ".verified-sha256").writeText(expectedSha)
            // Swap only after every file is validated; otherwise leave prior version.
            val destination = gameDirectory(context)
            if (destination.exists()) check(destination.deleteRecursively())
            check(stage.renameTo(destination)) { "Could not activate verified game files" }
            cancel(context)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Downloaded game pack rejected", e)
            stage.deleteRecursively()
            cancel(context)
            false
        }
    }

    fun remove(context: Context) {
        cancel(context)
        gameDirectory(context).deleteRecursively()
    }
}
