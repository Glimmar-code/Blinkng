package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Receives Android's notification RemoteInput without opening MainActivity. */
class NotificationReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(InstantChatNotification.KEY_TEXT_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        val receiverUsername = intent.getStringExtra(InstantChatNotification.EXTRA_SENDER_USERNAME)
            ?.trim()
            .orEmpty()
        val senderName = intent.getStringExtra(InstantChatNotification.EXTRA_SENDER_NAME)
            .orEmpty()
        val notificationId = intent.getIntExtra(
            InstantChatNotification.EXTRA_NOTIFICATION_ID,
            receiverUsername.hashCode() and Int.MAX_VALUE
        )

        if (replyText.isBlank() || receiverUsername.isBlank()) return

        // Immediately close the RemoteInput spinner and show that the reply is queued.
        // The actual network send happens in WorkManager, so Android is free to kill this
        // short-lived BroadcastReceiver without losing the reply.
        InstantChatNotification.showReplyQueued(
            context = context,
            notificationId = notificationId,
            senderName = senderName,
            replyText = replyText
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<NotificationReplyWorker>()
            .setInputData(
                workDataOf(
                    NotificationReplyWorker.KEY_RECEIVER_USERNAME to receiverUsername,
                    NotificationReplyWorker.KEY_RECEIVER_NAME to senderName,
                    NotificationReplyWorker.KEY_REPLY_TEXT to replyText,
                    NotificationReplyWorker.KEY_NOTIFICATION_ID to notificationId
                )
            )
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag("blink_inline_reply")
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "blink_inline_reply_${request.id}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
