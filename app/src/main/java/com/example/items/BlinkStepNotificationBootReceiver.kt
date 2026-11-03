package com.example.items

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Restore an explicitly opted-in offline step tracker after boot/app updates
 * where Android permits background foreground-service startup.
 * Never re-enable a tracker dismissed or turned off by the user.
 */
class BlinkStepNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        if (!BlinkItemPreferences.stepsEnabled(context) ||
            !BlinkItemPreferences.stepNotificationEnabled(context)
        ) return
        val repository = BlinkStepRepository(context)
        if (!repository.isAvailable() || !repository.hasReadPermission()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        // OEM restrictions can block this launch. The service runs only if allowed.
        BlinkStepNotificationService.start(context)
    }
}
