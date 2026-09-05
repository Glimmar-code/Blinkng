package com.example.data.views

import android.content.Context
import com.example.data.local.OfflineMutationStore
import com.example.data.supabase.ContentViewService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val VIEW_REFLECTION_DELAY_MS = 10_000L

data class ContentViewDisplayState(
    val revealedCounts: Map<String, Int> = emptyMap(),
    val suppressedBaselines: Map<String, Int> = emptyMap(),
    val pendingByContent: Map<String, Int> = emptyMap()
) {
    fun displayedCount(contentId: String, modelCount: Int): Int {
        val cleanModel = modelCount.coerceAtLeast(0)
        val revealed = revealedCounts[contentId]?.coerceAtLeast(0) ?: 0
        return if ((pendingByContent[contentId] ?: 0) > 0) {
            maxOf(suppressedBaselines[contentId] ?: cleanModel, revealed)
        } else {
            maxOf(cleanModel, revealed)
        }
    }
}

private data class PendingDisplay(
    val postId: String,
    val createdAt: Long
)

/** One app-wide view pipeline for posts and reels. */
object ContentViewCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingEvents = ConcurrentHashMap<String, PendingDisplay>()
    private val _displayState = MutableStateFlow(ContentViewDisplayState())
    val displayState: StateFlow<ContentViewDisplayState> = _displayState.asStateFlow()

    fun recordExposure(context: Context, postId: String, currentlyDisplayedCount: Int) {
        if (postId.isBlank()) return
        val eventId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val pending = PendingDisplay(postId, createdAt)
        pendingEvents[eventId] = pending
        beginSuppression(postId, currentlyDisplayedCount)

        // The display gate starts when the exposure happens, not after the network returns.
        scope.launch {
            val remaining = (createdAt + VIEW_REFLECTION_DELAY_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            delay(remaining)
            finishPending(eventId, null)
        }

        scope.launch {
            val result = ContentViewService.record(postId, eventId)
            if (result != null) {
                scheduleAuthoritativeReveal(eventId, postId, result.viewCount, createdAt)
            } else {
                OfflineMutationStore(context.applicationContext)
                    .enqueueContentView(postId = postId, eventId = eventId)
            }
        }
    }

    /** Called by the online path and by WorkManager after an offline retry succeeds. */
    fun scheduleAuthoritativeReveal(
        eventId: String,
        postId: String,
        authoritativeViewCount: Int,
        createdAt: Long
    ) {
        scope.launch {
            val remaining = (createdAt + VIEW_REFLECTION_DELAY_MS - System.currentTimeMillis())
                .coerceAtLeast(0L)
            delay(remaining)
            finishPending(eventId, authoritativeViewCount.coerceAtLeast(0), postId)
        }
    }

    private fun beginSuppression(postId: String, modelCount: Int) {
        _displayState.update { current ->
            val baseline = current.displayedCount(postId, modelCount)
            val pending = (current.pendingByContent[postId] ?: 0) + 1
            current.copy(
                suppressedBaselines = current.suppressedBaselines +
                    (postId to (current.suppressedBaselines[postId] ?: baseline)),
                pendingByContent = current.pendingByContent + (postId to pending)
            )
        }
    }

    private fun finishPending(eventId: String, authoritativeCount: Int?, fallbackPostId: String? = null) {
        val pending = pendingEvents.remove(eventId)
        val postId = pending?.postId ?: fallbackPostId ?: return

        _displayState.update { current ->
            val revealed = if (authoritativeCount != null) {
                current.revealedCounts +
                    (postId to maxOf(current.revealedCounts[postId] ?: 0, authoritativeCount))
            } else {
                current.revealedCounts
            }

            if (pending == null) {
                // A WorkManager retry may finish in a newly-created process where the original
                // in-memory pending marker no longer exists. The authoritative result is still safe.
                return@update current.copy(revealedCounts = revealed)
            }

            val left = ((current.pendingByContent[postId] ?: 1) - 1).coerceAtLeast(0)
            if (left == 0) {
                current.copy(
                    revealedCounts = revealed,
                    suppressedBaselines = current.suppressedBaselines - postId,
                    pendingByContent = current.pendingByContent - postId
                )
            } else {
                current.copy(
                    revealedCounts = revealed,
                    pendingByContent = current.pendingByContent + (postId to left)
                )
            }
        }
    }
}
