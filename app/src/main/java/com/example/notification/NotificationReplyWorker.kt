package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.ChatRepository
import com.example.data.supabase.SupabaseService

/**
 * Persists a notification quick-reply to Supabase without opening the UI.
 * WorkManager gives the reply a durable execution path if the process is stopped
 * after the user presses Send or connectivity changes during the request.
 */
class NotificationReplyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val receiverUsername = inputData.getString(KEY_RECEIVER_USERNAME)?.trim().orEmpty()
        val receiverName = inputData.getString(KEY_RECEIVER_NAME).orEmpty()
        val replyText = inputData.getString(KEY_REPLY_TEXT)?.trim().orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 0)

        if (receiverUsername.isBlank() || replyText.isBlank()) return Result.failure()

        SupabaseService.initialize(applicationContext)
        val repository = ChatRepository()
        val sendResult = repository.sendMessage(receiverUsername, replyText)

        return sendResult.fold(
            onSuccess = { message ->
                // The DB trigger now dispatches push server-side. Keep the existing client
                // trigger as a deduplicated fallback for environments where request headers
                // cannot be forwarded by pg_net.
                repository.triggerMessagePushBestEffort(message.id)
                InstantChatNotification.showReplySent(
                    context = applicationContext,
                    notificationId = notificationId,
                    senderName = receiverName,
                    replyText = replyText
                )
                Result.success()
            },
            onFailure = {
                if (runAttemptCount < MAX_ATTEMPTS) {
                    InstantChatNotification.showReplyWaiting(
                        context = applicationContext,
                        notificationId = notificationId,
                        senderName = receiverName,
                        replyText = replyText
                    )
                    Result.retry()
                } else {
                    // Keep the reply visible rather than silently discarding it. The user can
                    // open the chat and resend if authentication was revoked or the server stays unavailable.
                    InstantChatNotification.showReplyWaiting(
                        context = applicationContext,
                        notificationId = notificationId,
                        senderName = receiverName,
                        replyText = replyText
                    )
                    Result.failure()
                }
            }
        )
    }

    companion object {
        const val KEY_RECEIVER_USERNAME = "receiver_username"
        const val KEY_RECEIVER_NAME = "receiver_name"
        const val KEY_REPLY_TEXT = "reply_text"
        const val KEY_NOTIFICATION_ID = "notification_id"
        private const val MAX_ATTEMPTS = 6
    }
}
