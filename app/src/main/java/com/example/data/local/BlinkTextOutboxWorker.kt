package com.example.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.blinkng.shared.BlinkGroupRoute
import com.example.data.repository.ChatRepository
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.flow.first
import java.util.UUID

/**
 * Durable sender-bound text outbox. Safe after process death because every RPC
 * uses the original client UUID; server insertion is atomic and idempotent.
 */
class BlinkTextOutboxWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ownerId = inputData.getString("ownerId")?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val ownerName = inputData.getString("ownerUsername")?.trim()?.lowercase()
            ?.takeIf(String::isNotBlank) ?: return Result.failure()
        val service = SupabaseService()
        if (!service.restoreSession()) return Result.retry()
        if (service.getCurrentUserId() != ownerId) {
            // Never deliver an earlier account's queued texts as a newly signed-in user.
            return Result.failure()
        }
        val store = OfflineContentStore(applicationContext)
        if (!store.cachedOwnerUsername().equals(ownerName, true)) return Result.failure()
        val chat = ChatRepository(service)
        val due = store.pendingOutbox(100)
        for (item in due) {
            if (isStopped) return Result.retry()
            if (!item.ownerUsername.equals(ownerName, true) ||
                service.getCurrentUserId() != ownerId) return Result.failure()
            val clientId = item.localId.removePrefix("temp_group_").removePrefix("temp_")
            if (runCatching { UUID.fromString(clientId) }.isFailure) {
                store.markOutboxPermanent(item.localId, "Invalid message identifier.")
                continue
            }
            val groupId = BlinkGroupRoute.conversationId(item.receiverUsername)
            val sent = if (groupId != null)
                chat.sendGroupMessage(groupId, item.content, clientId)
            else
                chat.sendMessage(item.receiverUsername, item.content,
                    item.replyToMessageId, clientId)
            sent.fold(
                onSuccess = { remote ->
                    // Deleting the local queue is permitted only after server acknowledgement.
                    store.deleteOutbox(item.localId)
                    runCatching { chat.triggerMessagePushBestEffort(remote.id) }
                },
                onFailure = { failure ->
                    val detail = failure.message.orEmpty()
                    val permanent = listOf(
                        "RECIPIENT_NOT_FOUND", "USER_BLOCKED", "CANNOT_MESSAGE_SELF",
                        "NOT_A_GROUP_MEMBER", "MESSAGE_REQUIRED", "INVALID_MESSAGE",
                        "CLIENT_MESSAGE_ID_REUSED", "403", "401"
                    ).any { detail.contains(it, ignoreCase = true) }
                    if (permanent) store.markOutboxPermanent(item.localId,
                        detail.ifBlank { "Message could not be delivered." })
                    else store.markOutboxFailure(item, detail.ifBlank { "Network unavailable." })
                }
            )
        }
        // Pending rows can have a not-yet-due backoff; WorkManager will wake later,
        // even if BLINK's chat screen is closed.
        return if (store.pendingOutboxCount.first() > 0) Result.retry() else Result.success()
    }
}
