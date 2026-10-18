package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.blinkng.shared.update.BlinkReleaseInfo
import com.blinkng.shared.update.BlinkReleasePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The app UI is just a WorkManager observer; downloads survive Activity loss.
 * Pause cancels the worker but preserves private .partial bytes. A subsequent
 * start creates a new constrained worker, which resumes by HTTP Range.
 */
class WebsiteApkDownloadCoordinator(context: Context) {
    private val app = context.applicationContext
    private val manager = WorkManager.getInstance(app)
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observation: Job? = null
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    sealed interface State {
        data object Idle : State
        data class Downloading(
            val bytes: Long,
            val total: Long,
            val waitingForNetwork: Boolean = false,
        ) : State
        data class Paused(val bytes: Long) : State
        data class Ready(val release: BlinkReleaseInfo, val file: File) : State
        data class Failed(val message: String) : State
    }

    init {
        BlinkApkWorkContract.workId(app)?.let(::observe)
    }

    fun start(release: BlinkReleaseInfo, installedVersionCode: Int, unmeteredOnly: Boolean = false) {
        if (BlinkReleasePolicy.availableUpdate(release, installedVersionCode) == null) {
            _state.value = State.Failed("This BLINK version is already installed.")
            return
        }
        val existing = BlinkApkWorkContract.selected(app)
        if (existing == release && _state.value is State.Downloading) return

        BlinkApkWorkContract.save(app, release, paused = false)
        BlinkApkWorkContract.pruneOld(app, release)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<BlinkResumableApkWorker>()
            .setConstraints(constraints)
            .setInputData(BlinkApkWorkContract.input(release, installedVersionCode))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("blink-user-approved-apk")
            .build()
        // Persist the work ID before enqueuing; process restoration observes it.
        BlinkApkWorkContract.saveWorkId(app, request.id)
        _state.value = State.Downloading(
            BlinkApkWorkContract.partialFile(app, release).length(), -1L,
            waitingForNetwork = true,
        )
        observe(request.id)
        manager.enqueueUniqueWork(
            BlinkApkWorkContract.UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request,
        )
    }

    fun pause() {
        val release = BlinkApkWorkContract.selected(app) ?: return
        BlinkApkWorkContract.pause(app, true)
        manager.cancelUniqueWork(BlinkApkWorkContract.UNIQUE_WORK)
        _state.value = State.Paused(BlinkApkWorkContract.partialFile(app, release).length())
    }

    /** Called when Activity is destroyed: STOP OBSERVING, NOT THE DOWNLOAD. */
    fun close() {
        observation?.cancel()
        observerScope.cancel()
    }

    private fun observe(id: UUID) {
        observation?.cancel()
        observation = observerScope.launch {
            manager.getWorkInfoByIdFlow(id).collect { info ->
                val release = BlinkApkWorkContract.selected(app) ?: return@collect
                val partialSize = BlinkApkWorkContract.partialFile(app, release).length()
                if (BlinkApkWorkContract.isPaused(app)) {
                    _state.value = State.Paused(partialSize)
                    return@collect
                }
                _state.value = when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        val verified = BlinkApkWorkContract.verifiedFile(app, release)
                        if (verified.isFile) State.Ready(release, verified)
                        else State.Failed("Downloaded APK is missing. Retry the update.")
                    }
                    WorkInfo.State.FAILED -> State.Failed(
                        "BLINK could not safely download this update. Check your connection and retry."
                    )
                    WorkInfo.State.CANCELLED -> State.Paused(partialSize)
                    WorkInfo.State.RUNNING -> State.Downloading(
                        info.progress.getLong(BlinkApkWorkContract.PROGRESS_BYTES, partialSize),
                        info.progress.getLong(BlinkApkWorkContract.PROGRESS_TOTAL, -1L),
                    )
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, null -> State.Downloading(
                        partialSize, -1L, waitingForNetwork = true,
                    )
                }
            }
        }
    }

    /** Android's own installer always makes the final approval decision. */
    fun install(activity: Activity): Boolean {
        val ready = _state.value as? State.Ready ?: return false
        val path = BlinkApkWorkContract.verifiedFile(activity, ready.release)
        if (!ready.file.isFile || ready.file != path) {
            _state.value = State.Failed("Update package is unavailable. Download it again.")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}"),
            ))
            return false
        }
        // Recheck the package signer against the *current* installed version.
        val installedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
        } else {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
        }
        require(installedCode in 1L..Int.MAX_VALUE.toLong()) { "Invalid installed version" }
        BlinkApkPackageVerifier.verify(
            activity, path, ready.release, installedCode.toInt(),
        )
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.update.fileprovider", path,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(intent)
        return true
    }

    companion object {
        /** Play installations are updated by the Google Play API, not sideloads. */
        @Suppress("DEPRECATION")
        fun isPlayStoreInstall(context: Context): Boolean =
            runCatching {
                val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else context.packageManager.getInstallerPackageName(context.packageName)
                installer == "com.android.vending"
            }.getOrDefault(false)
    }
}
