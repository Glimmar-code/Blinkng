package com.example.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blinkng.shared.notification.BlinkNotificationQuickActionPolicy
import com.example.data.repository.FollowStateStore
import com.example.data.supabase.SupabaseService

/** Perform an explicit quick action through existing RLS-protected Supabase APIs. */
class NotificationQuickActionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION).orEmpty()
        val expectedAccount = inputData.getString(KEY_USER_ID).orEmpty()
        val targetId = inputData.getString(KEY_TARGET_ID).orEmpty()
        val username = inputData.getString(KEY_USERNAME).orEmpty()
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, -1)

        if (!BlinkNotificationQuickActionPolicy.isId(expectedAccount) || notificationId < 0) return Result.failure()
        if (action != ACTION_MARK_READ && !BlinkNotificationQuickActionPolicy.isId(targetId)) return Result.failure()
        if (action == ACTION_MARK_READ && username.isBlank()) return Result.failure()

        val outcome = runCatching {
            SupabaseService.initialize(applicationContext)
            val service = SupabaseService()
            // A background action scheduled for account A must NEVER mutate account B.
            if (service.getCurrentUserId() != expectedAccount) return Result.failure()
            when (action) {
                ACTION_FOLLOW -> targetId != expectedAccount && FollowStateStore.setFollowing(targetId, true)
                ACTION_LIKE_POST -> service.togglePostLike(targetId, liked = true, newLikeCount = 0)
                ACTION_LIKE_COMMENT -> service.toggleCommentLike(targetId, liked = true)
                ACTION_MARK_READ -> service.markMessagesRead(username)
                else -> return Result.failure()
            }
        }.getOrDefault(false)

        return if (outcome) {
            // Only dismiss after the server confirms an actual mutation.
            NotificationManagerCompat.from(applicationContext).cancel(notificationId)
            Result.success()
        } else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_ACTION = "quick_action"
        const val KEY_USER_ID = "quick_expected_user"
        const val KEY_TARGET_ID = "quick_target_id"
        const val KEY_USERNAME = "quick_sender_username"
        const val KEY_NOTIFICATION_ID = "quick_notification_id"
        const val ACTION_FOLLOW = "follow_back"
        const val ACTION_LIKE_POST = "like_post"
        const val ACTION_LIKE_COMMENT = "like_comment"
        const val ACTION_MARK_READ = "mark_message_read"
    }
}
