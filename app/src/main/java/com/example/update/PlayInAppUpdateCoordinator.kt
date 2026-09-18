package com.example.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android-only Google Play update adapter.
 *
 * GitHub/sideload builds remain fully usable: if Google Play cannot provide update
 * information, this coordinator simply no-ops. Once BLINK is distributed through
 * Google Play, the same app can use flexible in-app updates.
 */
class PlayInAppUpdateCoordinator(
    activity: ComponentActivity,
    private val updateLauncher: ActivityResultLauncher<IntentSenderRequest>
) {
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)

    private val _updateReadyToInstall = MutableStateFlow(false)
    val updateReadyToInstall: StateFlow<Boolean> = _updateReadyToInstall.asStateFlow()

    private var flexibleFlowRequestedThisProcess = false
    private var listenerRegistered = true

    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            _updateReadyToInstall.value = true
        }
    }

    init {
        appUpdateManager.registerListener(installStateListener)
    }

    fun onResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    _updateReadyToInstall.value = true
                    return@addOnSuccessListener
                }

                if (
                    !flexibleFlowRequestedThisProcess &&
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    flexibleFlowRequestedThisProcess = true
                    val started = runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            updateLauncher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    }.getOrDefault(false)

                    if (!started) {
                        flexibleFlowRequestedThisProcess = false
                    }
                }
            }
            .addOnFailureListener {
                // Expected for GitHub/sideload builds or devices without Play support.
                // Never block BLINK startup because the Play update service is unavailable.
            }
    }

    fun completeUpdate() {
        if (!_updateReadyToInstall.value) return
        _updateReadyToInstall.value = false
        appUpdateManager.completeUpdate()
    }

    fun close() {
        if (!listenerRegistered) return
        appUpdateManager.unregisterListener(installStateListener)
        listenerRegistered = false
    }
}
