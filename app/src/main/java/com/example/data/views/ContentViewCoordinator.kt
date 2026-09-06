package com.example.data.views

import android.content.Context
import com.example.data.local.OfflineMutationStore
import com.example.data.supabase.ContentViewService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val VIEW_REFLECTION_DELAY_MS = 30_000L
private const val WINDOW_NETWORK_SETTLE_MS = 1_500L
private const val LATE_RETRY_COALESCE_MS = 250L

data class ContentViewDisplayState(
    val revealedCounts: Map<String, Int> = emptyMap(),
    val suppressedBaselines: Map<String, Int> = emptyMap(),
    val activeWindows: Set<String> = emptySet()
) {
    fun displayedCount(contentId: String, modelCount: Int): Int {
        val cleanModel = modelCount.coerceAtLeast(0)
        if (contentId in activeWindows) {
            return (suppressedBaselines[contentId] ?: cleanModel).coerceAtLeast(0)
        }
        val revealed = revealedCounts[contentId]?.coerceAtLeast(0) ?: 0
        return maxOf(cleanModel, revealed)
    }
}

private class DisplayWindow(
    val postId: String,
    val startedAt: Long,
    var latestAuthoritativeCount: Int? = null,
    var inFlightEvents: Int = 0
)

private class DeferredReveal(
    val postId: String,
    val deadlineAt: Long,
    var latestAuthoritativeCount: Int
)

/**
 * One app-wide view pipeline for posts and reels.
 *
 * Every genuine exposure is written immediately with its own idempotency UUID. The first
 * exposure for one content item opens a single 30-second presentation window. Additional
 * exposures during that window never restart the timer and never advance the visible counter.
 */
object ContentViewCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val windowLock = Any()
    private val displayWindows = mutableMapOf<String, DisplayWindow>()
    private val deferredReveals = mutableMapOf<String, DeferredReveal>()

    private val _displayState = MutableStateFlow(ContentViewDisplayState())
    val displayState: StateFlow<ContentViewDisplayState> = _displayState.asStateFlow()

    fun recordExposure(context: Context, postId: String, currentlyDisplayedCount: Int) {
        if (postId.isBlank()) return

        val eventId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()
        val window = attachExposureToWindow(postId, currentlyDisplayedCount, createdAt)

        scope.launch {
            try {
                val result = ContentViewService.record(postId, eventId)
                if (result != null) {
                    scheduleAuthoritativeReveal(
                        eventId = eventId,
                        postId = postId,
                        authoritativeViewCount = result.viewCount,
                        createdAt = createdAt
                    )
                } else {
                    OfflineMutationStore(context.applicationContext)
                        .enqueueContentView(postId = postId, eventId = eventId)
                }
            } finally {
                synchronized(windowLock) {
                    window.inFlightEvents = (window.inFlightEvents - 1).coerceAtLeast(0)
                }
            }
        }
    }

    /**
     * Called by the online path and by WorkManager after an offline retry succeeds.
     * Online results are accumulated inside the existing content window. A retry from a
     * recreated process is briefly coalesced so a batch of queued exposures does not visibly
     * tick through multiple counts one by one.
     */
    @Suppress("UNUSED_PARAMETER")
    fun scheduleAuthoritativeReveal(
        eventId: String,
        postId: String,
        authoritativeViewCount: Int,
        createdAt: Long
    ) {
        val cleanCount = authoritativeViewCount.coerceAtLeast(0)
        val mergedIntoActiveWindow = synchronized(windowLock) {
            val active = displayWindows[postId]
            if (active != null) {
                active.latestAuthoritativeCount = maxOf(
                    active.latestAuthoritativeCount ?: 0,
                    cleanCount
                )
                true
            } else {
                false
            }
        }

        if (!mergedIntoActiveWindow) {
            scheduleDeferredReveal(postId, cleanCount, createdAt)
        }
    }

    private fun attachExposureToWindow(
        postId: String,
        currentlyDisplayedCount: Int,
        createdAt: Long
    ): DisplayWindow {
        var createdWindow: DisplayWindow? = null

        val window = synchronized(windowLock) {
            val existing = displayWindows[postId]
            if (existing != null) {
                existing.inFlightEvents += 1
                existing
            } else {
                val created = DisplayWindow(
                    postId = postId,
                    startedAt = createdAt,
                    inFlightEvents = 1
                )

                deferredReveals.remove(postId)?.let { deferred ->
                    created.latestAuthoritativeCount = deferred.latestAuthoritativeCount
                }

                displayWindows[postId] = created
                beginSuppressionLocked(postId, currentlyDisplayedCount)
                createdWindow = created
                created
            }
        }

        createdWindow?.let { created ->
            scope.launch {
                val remaining = (
                    created.startedAt + VIEW_REFLECTION_DELAY_MS - System.currentTimeMillis()
                ).coerceAtLeast(0L)
                delay(remaining)
                closeDisplayWindow(postId, created)
            }
        }

        return window
    }

    private suspend fun closeDisplayWindow(postId: String, window: DisplayWindow) {
        // Give requests started immediately before the deadline a short grace period so the
        // single authoritative refetch includes them when the network is healthy.
        withTimeoutOrNull(WINDOW_NETWORK_SETTLE_MS) {
            while (true) {
                val hasPending = synchronized(windowLock) {
                    displayWindows[postId] === window && window.inFlightEvents > 0
                }
                if (!hasPending) break
                delay(50L)
            }
        }

        // One lightweight refetch per content window. The RPC writes were already durable;
        // this request is presentation-only and never controls whether a view is recorded.
        val fetchedCount = ContentViewService.fetchAuthoritativeCount(postId)

        synchronized(windowLock) {
            if (displayWindows[postId] !== window) return

            val authoritativeCount = listOfNotNull(
                fetchedCount,
                window.latestAuthoritativeCount
            ).maxOrNull()

            displayWindows.remove(postId)
            finishSuppressionLocked(postId, authoritativeCount)
        }
    }

    private fun scheduleDeferredReveal(postId: String, count: Int, createdAt: Long) {
        var created: DeferredReveal? = null

        synchronized(windowLock) {
            val active = displayWindows[postId]
            if (active != null) {
                active.latestAuthoritativeCount = maxOf(
                    active.latestAuthoritativeCount ?: 0,
                    count
                )
                return
            }

            val existing = deferredReveals[postId]
            if (existing != null) {
                existing.latestAuthoritativeCount = maxOf(existing.latestAuthoritativeCount, count)
            } else {
                DeferredReveal(
                    postId = postId,
                    deadlineAt = createdAt + VIEW_REFLECTION_DELAY_MS,
                    latestAuthoritativeCount = count
                ).also {
                    deferredReveals[postId] = it
                    created = it
                }
            }
        }

        created?.let { deferred ->
            scope.launch {
                val remaining = (deferred.deadlineAt - System.currentTimeMillis())
                    .coerceAtLeast(LATE_RETRY_COALESCE_MS)
                delay(remaining)
                finishDeferredReveal(postId, deferred)
            }
        }
    }

    private fun finishDeferredReveal(postId: String, deferred: DeferredReveal) {
        synchronized(windowLock) {
            if (deferredReveals[postId] !== deferred) return
            deferredReveals.remove(postId)

            val active = displayWindows[postId]
            if (active != null) {
                active.latestAuthoritativeCount = maxOf(
                    active.latestAuthoritativeCount ?: 0,
                    deferred.latestAuthoritativeCount
                )
                return
            }

            _displayState.update { current ->
                current.copy(
                    revealedCounts = current.revealedCounts + (
                        postId to maxOf(
                            current.revealedCounts[postId] ?: 0,
                            deferred.latestAuthoritativeCount
                        )
                    )
                )
            }
        }
    }

    private fun beginSuppressionLocked(postId: String, modelCount: Int) {
        _displayState.update { current ->
            val baseline = current.displayedCount(postId, modelCount)
            current.copy(
                suppressedBaselines = current.suppressedBaselines + (postId to baseline),
                activeWindows = current.activeWindows + postId
            )
        }
    }

    private fun finishSuppressionLocked(postId: String, authoritativeCount: Int?) {
        _displayState.update { current ->
            val revealed = if (authoritativeCount != null) {
                current.revealedCounts + (
                    postId to maxOf(
                        current.revealedCounts[postId] ?: 0,
                        authoritativeCount.coerceAtLeast(0)
                    )
                )
            } else {
                current.revealedCounts
            }

            current.copy(
                revealedCounts = revealed,
                suppressedBaselines = current.suppressedBaselines - postId,
                activeWindows = current.activeWindows - postId
            )
        }
    }
}
