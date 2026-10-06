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
        if (conversationId.isBlank()) return Result.failure()

        if (ConversationNotificationMuteStore.isMuted(applicationContext, conversationId)) {
            val until = ConversationNotificationMuteStore.mutedUntil(applicationContext, conversationId)
            if (until != null && until.toEpochMilli() > System.currentTimeMillis() + 1_000L) {
                return Result.success()
            }
        }

        SupabaseService.initialize(applicationContext)
        val service = SupabaseService()
        if (!service.restoreSession()) return if (runAttemptCount < 3) Result.retry() else Result.success()

        val updated = runCatching {
            ChatRepository().setConversationMuted(conversationId, false)
        }.getOrDefault(false)

        return if (updated) {
            ConversationNotificationMuteStore.unmute(applicationContext, conversationId)
            Result.success()
        } else if (runAttemptCount < 3) {
            Result.retry()
        } else {
            ConversationNotificationMuteStore.unmute(applicationContext, conversationId)
            Result.success()
        }
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
    }
}
