package com.example.items

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * A user can always turn off step tracking, including from the notification itself.
 * Android 14+ may also permit swiping ongoing notifications away: respect that choice.
 */
class BlinkStepNotificationControlsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            "com.example.items.STOP_STEP_NOTIFICATION",
            "com.example.items.DISMISS_STEP_NOTIFICATION" ->
                BlinkStepNotificationService.stop(context)
        }
    }
}
