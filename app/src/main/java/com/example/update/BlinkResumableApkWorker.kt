package com.example.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Intent
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
import com.blinkng.shared.update.BlinkApkResumePolicy
import com.blinkng.shared.update.BlinkApkUpdatePolicy
import com.blinkng.shared.update.BlinkReleaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Persistent, resumable, user-initiated APK download. WorkManager provides
 * network constraints, process-recreation recovery and retry/backoff. Partial
 * bytes stay inaccessible to the installation FileProvider.
 */
class BlinkResumableApkWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(6, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    override suspend fun doWork(): Result {
        val release = BlinkApkWorkContract.release(inputData) ?: return Result.failure()
        val installedCode = inputData.getInt(BlinkApkWorkContract.INSTALLED_CODE, 0)
        if (release.versionCode <= installedCode) return Result.failure()
        val partial = BlinkApkWorkContract.partialFile(applicationContext, release)
        val verified = BlinkApkWorkContract.verifiedFile(applicationContext, release)
        try {
            // Before using any old partial data, ensure this SHA is still current.
            val latest = WebsiteApkUpdateCoordinator.checkForUpdate(installedCode)
            if (latest == null) {
                // An offline/temporarily unreachable GitHub metadata endpoint is
                // not proof of a revoked update: wait and retry, keeping bytes.
                return if (runAttemptCount < 5) Result.retry() else Result.failure()
            }
            if (latest != release) {
                partial.delete()
                return Result.failure()
            }

            setForeground(notification(0L, -1L))
            verified.parentFile?.mkdirs()
            partial.parentFile?.mkdirs()
            if (verified.isFile) {
                if (sha256(verified) == release.apkSha256) {
                    BlinkApkPackageVerifier.verify(applicationContext, verified, release, installedCode)
                    return Result.success()
                }
                verified.delete()
            }

            download(release, partial)
            coroutineContext.ensureActive()
            if (sha256(partial) != release.apkSha256) {
                partial.delete()
                return Result.failure()
            }
            BlinkApkPackageVerifier.verify(applicationContext, partial, release, installedCode)
            // Never deliver an old version if the official latest moved meanwhile.
            val latestAfterDownload = WebsiteApkUpdateCoordinator.checkForUpdate(installedCode)
            if (latestAfterDownload == null) throw IOException("Release endpoint temporarily unreachable")
            if (latestAfterDownload != release) {
                partial.delete()
                return Result.failure()
            }
            coroutineContext.ensureActive()
            if (verified.exists() && !verified.delete()) return Result.failure()
            if (!partial.renameTo(verified)) return Result.failure()
            return Result.success()
        } catch (cancelled: CancellationException) {
            // Pauses/cancellation keep complete written bytes for the next run.
            throw cancelled
        } catch (transient: IOException) {
            // Offline, lost connections and bounded network timeouts can be retried.
            return if (runAttemptCount < 5) Result.retry() else Result.failure()
        } catch (_: Exception) {
            // Corrupt/tampered/invalid APK must never be retried or installed.
            partial.delete()
            verified.delete()
            return Result.failure()
        }
    }

    private suspend fun download(release: BlinkReleaseInfo, partial: File) {
        var existing = if (partial.isFile) partial.length() else 0L
        if (existing < 0 || existing > BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES) {
            partial.delete()
            existing = 0L
        }
        // A completed partial transfer may have been cancelled before final validation.
        if (existing > 0 && sha256(partial) == release.apkSha256) return
        val builder = Request.Builder()
            .url(BlinkApkWorkContract.APK_URL)
            .header("Accept", "application/octet-stream")
            .header("Cache-Control", "no-store")
        if (existing > 0L) builder.header("Range", "bytes=${existing}-")

        client.newCall(builder.build()).execute().use { response ->
            if (response.code == 416 && existing > 0L) {
                // The server rejected a saved range (e.g. stale or corrupt
                // partial). Drop it; the next bounded retry starts from zero.
                partial.delete()
                throw IOException("Saved APK byte range is no longer valid")
            }
            val effective = response.request.url
            require(BlinkApkUpdatePolicy.isOfficialAssetOrigin(effective.scheme, effective.host)) {
                "APK must come from official GitHub delivery"
            }
            // 200 after a Range request means restart, never append mismatched content.
            val isPartial = response.code == 206 && existing > 0L
            val body = response.body ?: throw IOException("Missing APK bytes")
            val length = body.contentLength()
            val range = if (isPartial) BlinkApkResumePolicy.acceptedRange(
                response.header("Content-Range"), existing, length,
            ) else null
            if (BlinkApkResumePolicy.restartFromZero(response.code)) {
                existing = 0L
            } else if (isPartial) {
                requireNotNull(range) { "Invalid or mismatched HTTP Content-Range" }
            } else {
                throw IOException("Official server did not accept download/resume request")
            }
            val total = range?.total ?: length
            require(total in -1L..BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES &&
                length in -1L..BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES &&
                (total == -1L || total >= existing)) { "APK exceeds maximum allowed size" }

            RandomAccessFile(partial, "rw").use { output ->
                output.setLength(existing)
                output.seek(existing)
                var bytes = existing
                var segmentBytes = 0L
                var lastReported = -1L
                val buffer = ByteArray(64 * 1024)
                body.byteStream().use { stream ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val amount = stream.read(buffer)
                        if (amount < 0) break
                        if (amount == 0) continue
                        bytes += amount
                        segmentBytes += amount
                        require(bytes <= BlinkApkUpdatePolicy.MAX_DOWNLOAD_BYTES &&
                            (total < 0 || bytes <= total)) { "Downloaded size exceeds bound" }
                        output.write(buffer, 0, amount)
                        if (bytes - lastReported >= 1024 * 1024 || lastReported < 0) {
                            lastReported = bytes
                            setProgress(workDataOf(
                                BlinkApkWorkContract.PROGRESS_BYTES to bytes,
                                BlinkApkWorkContract.PROGRESS_TOTAL to total,
                            ))
                            setForeground(notification(bytes, total))
                        }
                    }
                }
                output.fd.sync()
                if (length >= 0 && segmentBytes != length) throw IOException("Truncated APK transfer")
                if (total >= 0 && bytes != total) throw IOException("Incomplete APK transfer")
                if (bytes <= 0) throw IOException("Empty APK transfer")
                setProgress(workDataOf(
                    BlinkApkWorkContract.PROGRESS_BYTES to bytes,
                    BlinkApkWorkContract.PROGRESS_TOTAL to total,
                ))
            }
        }
    }

    private fun notification(bytes: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "BLINK app downloads", NotificationManager.IMPORTANCE_LOW,
            ))
        }
        val percent = if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 100) else 0
        val openApp = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_blink)
            .setContentIntent(openApp)
            .setContentTitle("BLINK update")
            .setContentText(if (total > 0) "Downloading update • ${percent}%" else "Downloading update")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, total <= 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "blink_apk_updates"
        private const val NOTIFICATION_ID = 6277
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

/** Package verification before exposing the APK to Android's system installer. */
internal object BlinkApkPackageVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File, release: BlinkReleaseInfo, installedCode: Int) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else PackageManager.GET_SIGNATURES
        val candidate = requireNotNull(pm.getPackageArchiveInfo(apk.absolutePath, flags)) {
            "Invalid Android package archive"
        }
        val installed = pm.getPackageInfo(context.packageName, flags)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) candidate.longVersionCode
                   else candidate.versionCode.toLong()
        val candidateCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            candidate.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else candidate.signatures?.toList().orEmpty()
        val installedCerts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installed.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else installed.signatures?.toList().orEmpty()
        require(candidateCerts.size == 1 && installedCerts.size == 1) {
            "Unexpected APK signing identity"
        }
        fun hash(signature: android.content.pm.Signature) =
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        require(BlinkApkUpdatePolicy.matchesInstalledApp(
            release = release,
            installedPackage = context.packageName,
            installedCode = installedCode,
            installedSignerSha256 = hash(installedCerts.single()),
            archivePackage = candidate.packageName,
            archiveCode = code,
            archiveSignerSha256 = hash(candidateCerts.single()),
        )) { "APK identity or signing certificate mismatch" }
    }
}
