package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.blinkng.shared.update.BlinkReleaseInfo
import com.blinkng.shared.update.BlinkReleasePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * User-initiated website APK updater. Downloaded files are private to BLINK,
 * capped in size, compared with authenticated GitHub Release metadata, and
 * checked against the package/signing certificate already installed on Android.
 *
 * This phase deliberately does NOT install silently, run a hidden background
 * worker, or ask for access to all storage. Android's installer must approve.
 * Future pause/resume/background downloads belong in a separate tested phase.
 */
class WebsiteApkDownloadCoordinator(private val context: Context) {

    sealed interface State {
        data object Idle : State
        data class Downloading(val bytes: Long, val total: Long) : State
        data class Ready(val release: BlinkReleaseInfo, val file: File) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private var downloadJob: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(false) // Never downgrade the official HTTPS APK URL.
            .build()
    }

    fun start(scope: CoroutineScope, release: BlinkReleaseInfo, installedVersionCode: Int) {
        if (downloadJob?.isActive == true) return
        if (BlinkReleasePolicy.availableUpdate(release, installedVersionCode) == null) {
            _state.value = State.Failed("This BLINK version is already installed.")
            return
        }
        _state.value = State.Downloading(0L, -1L)
        downloadJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val current = WebsiteApkUpdateCoordinator.checkForUpdate(installedVersionCode)
                    require(current == release) { "The available release changed. Check for a new update." }
                    downloadAndVerify(release, installedVersionCode)
                }
                _state.value = State.Ready(release, file)
            } catch (cancelled: CancellationException) {
                _state.value = State.Idle
                throw cancelled
            } catch (_: Exception) {
                _state.value = State.Failed(
                    "This update could not be verified or downloaded. Please retry on a stable connection."
                )
            }
        }
    }

    fun cancel() {
        downloadJob?.cancel()
        downloadJob = null
        _state.value = State.Idle
    }

    fun close() {
        cancel()
    }

    private suspend fun downloadAndVerify(release: BlinkReleaseInfo, installedCode: Int): File {
        val folder = File(context.cacheDir, "blink-updates").apply { mkdirs() }
        require(folder.isDirectory) { "No private update storage available" }
        val part = File.createTempFile("pending-", ".tmp", folder)
        val verified = File(folder, "blink-verified.apk")
        try {
            val request = Request.Builder()
                .url(APK_URL)
                .header("Accept", "application/vnd.android.package-archive,application/octet-stream")
                .header("Cache-Control", "no-store")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful) { "Official APK download unavailable" }
                val effective = response.request.url
                require(effective.isHttps && (
                    effective.host == "github.com" ||
                        effective.host.endsWith(".githubusercontent.com")
                    )) { "APK was redirected outside official GitHub delivery" }

                val body = requireNotNull(response.body) { "Missing APK data" }
                val contentLength = body.contentLength()
                require(contentLength in -1L..MAX_APK_BYTES) { "APK is oversized" }
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                body.byteStream().use { stream ->
                    part.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = stream.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            count += read
                            require(count <= MAX_APK_BYTES) { "APK exceeds download limit" }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            _state.value = State.Downloading(count, contentLength)
                        }
                    }
                }
                require(count > 0 && (contentLength == -1L || count == contentLength)) {
                    "Incomplete APK download"
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                require(sha == release.apkSha256) { "Official APK hash mismatch" }
            }

            // Parse from a .apk filename. This is a temporary private pathname,
            // never exposed to another app before the checks finish.
            if (verified.exists()) require(verified.delete()) { "Unable to clear old update" }
            require(part.renameTo(verified)) { "Unable to prepare verified update" }
            verifyPackageIdentityAndSigner(verified, release, installedCode)
            coroutineContext.ensureActive()
            return verified
        } catch (failure: Exception) {
            verified.delete()
            throw failure
        } finally {
            part.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackageIdentityAndSigner(
        file: File,
        release: BlinkReleaseInfo,
        installedVersionCode: Int,
    ) {
        val manager = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flag)) {
            "Invalid Android package archive"
        }
        require(candidate.packageName == context.packageName &&
            candidate.packageName == release.applicationId) { "Incorrect BLINK application ID" }
        val candidateCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.longVersionCode
        } else {
            candidate.versionCode.toLong()
        }
        require(candidateCode == release.versionCode.toLong() &&
            candidateCode > installedVersionCode.toLong()) { "Incorrect or older APK version" }

        val installed = manager.getPackageInfo(context.packageName, flag)
        val candidateCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else candidate.signatures?.toList().orEmpty()
        val installedCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else installed.signatures?.toList().orEmpty()
        require(candidateCerts.size == 1 && installedCerts.size == 1) {
            "BLINK update requires one verified signing certificate"
        }
        fun certificateHash(value: android.content.pm.Signature): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        val candidateHash = certificateHash(candidateCerts.single())
        require(candidateHash == release.signingCertificateSha256 &&
            candidateHash == certificateHash(installedCerts.single())) {
            "BLINK update signing identity does not match the installed app"
        }
    }

    /**
     * Launch the Android OS installer only after successful download validation.
     * Unknown-apps permission, where required, is granted in Android Settings by
     * the user; on returning to BLINK they can tap "Install" again.
     */
    fun install(activity: Activity): Boolean {
        val ready = _state.value as? State.Ready ?: return false
        if (!ready.file.isFile) {
            _state.value = State.Failed("Update file is missing. Download it again.")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            )
            activity.startActivity(settings)
            return false
        }
        // Never give another application a raw filesystem path.
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.update.fileprovider", ready.file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        return true
    }

    companion object {
        private const val MAX_APK_BYTES = 100L * 1024 * 1024
        private const val APK_URL =
            "https://github.com/Glimmar-code/Blinkng/releases/latest/download/Blink-latest.apk"

        /** Play-installed copies keep Google's own flexible in-app update path. */
        @Suppress("DEPRECATION")
        fun isPlayStoreInstall(context: Context): Boolean =
            runCatching {
                val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
                installer == "com.android.vending"
            }.getOrDefault(false)
    }
}
