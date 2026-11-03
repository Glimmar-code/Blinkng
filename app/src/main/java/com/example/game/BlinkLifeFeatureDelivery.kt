package com.example.game

import android.app.Activity
import android.content.Context
import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallSessionState
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener

/**
 * Play Feature Delivery controls the optional Godot runtime and assets.
 * The base APK deliberately contains neither native Godot libraries nor scenes.
 *
 * This mechanism works for Play App Bundle installs and bundletool local-testing
 * installs. A signed universal website APK includes the game split fused at build time.
 */
class BlinkLifeFeatureDelivery(private val applicationContext: Context) {
    companion object {
        const val MODULE = "blinkLifeFeature"
    }

    enum class Phase { NOT_INSTALLED, REQUESTING, DOWNLOADING, INSTALLING, INSTALLED, REMOVING, FAILED }

    data class Snapshot(
        val phase: Phase,
        val downloaded: Long = 0L,
        val total: Long = 0L,
        val message: String = "",
    )

    private val handler = Handler(Looper.getMainLooper())
    private val manager: SplitInstallManager =
        SplitInstallManagerFactory.create(applicationContext)

    // The website release uses bundletool's fused universal APK: the native
    // game Activity is already in that single APK even without a Play install.
    private fun isInstalled(): Boolean {
        if (manager.installedModules.contains(MODULE)) return true
        return runCatching {
            applicationContext.packageManager.getActivityInfo(
                ComponentName(applicationContext.packageName, "com.example.game.BlinkLifeGodotActivity"),
                0,
            )
            true
        }.getOrDefault(false)
    }

    fun canRemoveGameFiles(): Boolean {
        val playInstaller = runCatching {
            if (Build.VERSION.SDK_INT >= 30) {
                applicationContext.packageManager
                    .getInstallSourceInfo(applicationContext.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                applicationContext.packageManager.getInstallerPackageName(applicationContext.packageName)
            }
        }.getOrNull()
        return playInstaller == "com.android.vending" && manager.installedModules.contains(MODULE)
    }

    var snapshot by mutableStateOf(
        Snapshot(if (isInstalled()) Phase.INSTALLED else Phase.NOT_INSTALLED)
    )
        private set

    private var subscribed = false
    private var installId = 0
    private var cancelRequested = false
    private var confirmationSessionId = 0
    private var playAfterInstall = false
    private var activity: Activity? = null
    var onReady: (() -> Unit)? = null

    private val listener = SplitInstallStateUpdatedListener { state ->
        if (state.moduleNames().contains(MODULE)) update(state)
    }

    fun start(activity: Activity?) {
        this.activity = activity
        if (!subscribed) {
            manager.registerListener(listener)
            subscribed = true
        }
        refresh()
    }

    fun stop() {
        if (subscribed) manager.unregisterListener(listener)
        subscribed = false
        activity = null
        playAfterInstall = false
    }

    fun refresh() {
        if (isInstalled()) {
            snapshot = Snapshot(Phase.INSTALLED)
            return
        }
        manager.sessionStates.addOnSuccessListener { sessions ->
            val session = sessions.firstOrNull { it.moduleNames().contains(MODULE) &&
                it.status() != SplitInstallSessionStatus.FAILED &&
                it.status() != SplitInstallSessionStatus.CANCELED }
            if (session != null) update(session)
            else if (snapshot.phase != Phase.REMOVING && snapshot.phase != Phase.FAILED) {
                snapshot = Snapshot(Phase.NOT_INSTALLED)
            }
        }.addOnFailureListener {
            if (!isInstalled() && snapshot.phase != Phase.REMOVING) {
                snapshot = Snapshot(Phase.FAILED, message = "Couldn't check the game download. Please retry.")
            }
        }
    }

    fun downloadAndPlay() {
        if (isInstalled()) {
            snapshot = Snapshot(Phase.INSTALLED)
            onReady?.invoke()
            return
        }
        if (snapshot.phase in listOf(Phase.REQUESTING, Phase.DOWNLOADING, Phase.INSTALLING)) return
        playAfterInstall = true
        cancelRequested = false
        installId = 0
        snapshot = Snapshot(Phase.REQUESTING, message = "Requesting secure download from Google Play…")
        val request = SplitInstallRequest.newBuilder().addModule(MODULE).build()
        manager.startInstall(request)
            .addOnSuccessListener {
                installId = it
                if (cancelRequested) cancel()
            }
            .addOnFailureListener {
                playAfterInstall = false
                cancelRequested = false
                snapshot = Snapshot(
                    Phase.FAILED,
                    message = "Could not download. Use the Google Play version of BLINK, " +
                        "check internet and free storage, then retry.",
                )
            }
    }

    fun cancel() {
        playAfterInstall = false
        if (installId == 0) {
            cancelRequested = true
            snapshot = Snapshot(Phase.REQUESTING, message = "Cancelling the game request…")
            return
        }
        manager.cancelInstall(installId)
            .addOnFailureListener {
                snapshot = Snapshot(Phase.FAILED, message = "Cannot cancel at this stage.")
            }
    }

    fun removeGameFiles() {
        if (!canRemoveGameFiles()) return
        manager.deferredUninstall(listOf(MODULE))
            .addOnSuccessListener {
                snapshot = Snapshot(
                    Phase.REMOVING,
                    message = "Google Play has scheduled BLINK LIFE for removal. " +
                        "It may finish after restarting BLINK.",
                )
            }
            .addOnFailureListener {
                snapshot = Snapshot(Phase.FAILED, message = "Removal wasn't scheduled. Try again later.")
            }
    }

    private fun update(state: SplitInstallSessionState) {
        installId = state.sessionId()
        handler.post {
            when (state.status()) {
                SplitInstallSessionStatus.PENDING -> snapshot = Snapshot(Phase.REQUESTING)
                SplitInstallSessionStatus.DOWNLOADING -> snapshot = Snapshot(
                    Phase.DOWNLOADING, state.bytesDownloaded(), state.totalBytesToDownload()
                )
                SplitInstallSessionStatus.DOWNLOADED,
                SplitInstallSessionStatus.INSTALLING -> snapshot = Snapshot(Phase.INSTALLING)
                SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION -> {
                    snapshot = Snapshot(Phase.REQUESTING, message = "Confirm the download in Google Play.")
                    val visibleActivity = activity
                    if (visibleActivity != null && confirmationSessionId != state.sessionId()) {
                        confirmationSessionId = state.sessionId()
                        runCatching {
                            manager.startConfirmationDialogForResult(state, visibleActivity, 4808)
                        }.onFailure {
                            snapshot = Snapshot(Phase.FAILED, message = "Confirm the download to continue.")
                        }
                    }
                }
                SplitInstallSessionStatus.INSTALLED -> {
                    SplitCompat.install(applicationContext)
                    snapshot = Snapshot(Phase.INSTALLED)
                    installId = 0
                    confirmationSessionId = 0
                    cancelRequested = false
                    if (playAfterInstall) {
                        playAfterInstall = false
                        onReady?.invoke()
                    }
                }
                SplitInstallSessionStatus.CANCELED -> {
                    playAfterInstall = false
                    installId = 0
                    cancelRequested = false
                    confirmationSessionId = 0
                    snapshot = Snapshot(Phase.NOT_INSTALLED, message = "Download cancelled.")
                }
                SplitInstallSessionStatus.FAILED -> {
                    playAfterInstall = false
                    installId = 0
                    cancelRequested = false
                    confirmationSessionId = 0
                    snapshot = Snapshot(
                        Phase.FAILED,
                        message = "Game download failed (" + state.errorCode() +
                            "). Check your connection and free storage.",
                    )
                }
                else -> Unit
            }
        }
    }
}
