package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.blinkng.shared.notification.BlinkNotificationQuickActionPolicy
import java.util.concurrent.TimeUnit

/**
 * Only explicit, non-exported Android notification PendingIntents reach here.
 * Requests are revalidated against the current signed-in account by the Worker.
 */
class NotificationQuickActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(NotificationQuickActionWorker.KEY_ACTION).orEmpty()
        val expectedUser = intent.getStringExtra(NotificationQuickActionWorker.KEY_USER_ID).orEmpty()
        val targetId = intent.getStringExtra(NotificationQuickActionWorker.KEY_TARGET_ID).orEmpty()
        val actorUsername = intent.getStringExtra(NotificationQuickActionWorker.KEY_USERNAME).orEmpty()
        val notificationId = intent.getIntExtra(NotificationQuickActionWorker.KEY_NOTIFICATION_ID, -1)

        if (!BlinkNotificationQuickActionPolicy.isId(expectedUser) || notificationId < 0) return
        val allowed = when (action) {
            NotificationQuickActionWorker.ACTION_FOLLOW,
            NotificationQuickActionWorker.ACTION_LIKE_POST,
            NotificationQuickActionWorker.ACTION_LIKE_COMMENT -> BlinkNotificationQuickActionPolicy.isId(targetId)
            NotificationQuickActionWorker.ACTION_MARK_READ -> actorUsername.isNotBlank()
            else -> false
        }
        if (!allowed) return

        val request = OneTimeWorkRequestBuilder<NotificationQuickActionWorker>()
            .setInputData(workDataOf(
                NotificationQuickActionWorker.KEY_ACTION to action,
                NotificationQuickActionWorker.KEY_USER_ID to expectedUser,
                NotificationQuickActionWorker.KEY_TARGET_ID to targetId,
                NotificationQuickActionWorker.KEY_USERNAME to actorUsername,
                NotificationQuickActionWorker.KEY_NOTIFICATION_ID to notificationId,
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "blink_quick_${expectedUser}_${action}_${notificationId}",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
