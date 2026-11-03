package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.repository.ChatRepository
import com.example.data.supabase.SupabaseService

/** Automatically clears a timed conversation mute after its requested duration. */
class ConversationMuteExpiryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)?.trim().orEmpty()
        val ownerId = inputData.getString(KEY_OWNER_ID)?.trim().orEmpty()
        if (conversationId.isBlank() || ownerId.isBlank()) return Result.failure()

        if (ConversationNotificationMuteStore.isMutedForever(applicationContext, conversationId)) {
            return Result.success()
        }

        if (ConversationNotificationMuteStore.isMuted(applicationContext, conversationId)) {
            val until = ConversationNotificationMuteStore.mutedUntil(applicationContext, conversationId)
            if (until != null && until.toEpochMilli() > System.currentTimeMillis() + 1_000L) {
                return Result.success()
            }
        }

        SupabaseService.initialize(applicationContext)
        val service = SupabaseService()
        if (!service.restoreSession()) return Result.retry()

        // A WorkManager job can wake after the user switches accounts. Never mutate the
        // conversation under whichever account happens to be active at that moment.
        if (service.getCurrentUserId().orEmpty() != ownerId) return Result.retry()

        val updated = runCatching {
            ChatRepository().setConversationMuted(conversationId, false)
        }.getOrDefault(false)

        return if (updated) {
            ConversationNotificationMuteStore.unmute(applicationContext, conversationId)
            Result.success()
        } else {
            // Keep retrying with WorkManager backoff. Clearing local state while the server is
            // still muted would make the UI claim alerts are restored when delivery is not.
            Result.retry()
        }
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_OWNER_ID = "owner_id"
    }
}
