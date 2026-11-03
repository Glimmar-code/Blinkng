package com.example.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.supabase.ContentViewService
import com.example.data.supabase.SupabaseService
import com.example.data.views.ContentViewCoordinator
import org.json.JSONObject

class OfflineMutationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        SupabaseService.initialize(applicationContext)
        val service = SupabaseService()
        val restored = runCatching { service.restoreSession() }.getOrDefault(false)
        if (!restored && SupabaseService.accessToken().isNullOrBlank()) return Result.retry()

        val store = OfflineMutationStore(applicationContext)
        val items = store.pending(60)
        if (items.isEmpty()) return Result.success()

        var hadRetryableFailure = false
        items.forEach { item ->
            val success = runCatching {
                val payload = JSONObject(item.payloadJson)
                when (item.operation) {
                    OfflineMutationStore.OP_POST_LIKE -> service.togglePostLike(
                        postId = item.entityId,
                        liked = payload.optBoolean("liked"),
                        newLikeCount = payload.optInt("count").coerceAtLeast(0)
                    )
                    OfflineMutationStore.OP_BOOKMARK -> service.togglePostBookmark(
                        postId = item.entityId,
                        bookmarked = payload.optBoolean("bookmarked")
                    )
                    OfflineMutationStore.OP_SHARE -> service.sharePost(item.entityId, "share")
                    OfflineMutationStore.OP_CONTENT_VIEW -> {
                        val eventId = payload.optString("event_id")
                        val result = if (eventId.isBlank()) null else
                            ContentViewService.record(item.entityId, eventId)
                        if (result != null) {
                            ContentViewCoordinator.scheduleAuthoritativeReveal(
                                eventId = eventId,
                                postId = item.entityId,
                                authoritativeViewCount = result.viewCount,
                                createdAt = item.createdAt
                            )
                            true
                        } else false
                    }
                    else -> true
                }
            }.getOrDefault(false)

            if (success) {
                store.complete(item.id)
            } else {
                hadRetryableFailure = true
                store.fail(item, "Mutation could not be synced")
            }
        }

        if (store.pendingCount() > 0 && hadRetryableFailure) {
            OfflineMutationStore.schedule(applicationContext)
        }
        return Result.success()
    }
}
