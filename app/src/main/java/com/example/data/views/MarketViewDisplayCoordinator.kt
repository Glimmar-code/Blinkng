package com.example.data.views

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val MARKET_VIEW_REFLECTION_DELAY_MS = 120_000L

internal data class MarketViewDisplayState(
    val revealedCounts: Map<String, Int> = emptyMap(),
    val suppressedBaselines: Map<String, Int> = emptyMap(),
    val activeWindows: Set<String> = emptySet(),
) {
    fun displayedCount(itemId: String, modelCount: Int): Int {
        val cleanModel = modelCount.coerceAtLeast(0)
        if (itemId in activeWindows) {
            return (suppressedBaselines[itemId] ?: cleanModel).coerceAtLeast(0)
        }
        return maxOf(cleanModel, revealedCounts[itemId]?.coerceAtLeast(0) ?: 0)
    }
}

private class MarketViewWindow(
    val itemId: String,
    val baseline: Int,
    var latestAuthoritativeCount: Int,
)

/**
 * Listing view writes are immediate on the server. Only the displayed Market
 * counter is delayed, matching Feed/Profile/Reels without deduplicating visits.
 */
internal object MarketViewDisplayCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val windows = mutableMapOf<String, MarketViewWindow>()
    private val _state = MutableStateFlow(MarketViewDisplayState())
    val state: StateFlow<MarketViewDisplayState> = _state.asStateFlow()

    fun recordExposure(itemId: String, modelCount: Int, authoritativeCount: Int) {
        if (itemId.isBlank()) return
        var created: MarketViewWindow? = null
        synchronized(lock) {
            val existing = windows[itemId]
            if (existing != null) {
                existing.latestAuthoritativeCount = maxOf(
                    existing.latestAuthoritativeCount,
                    authoritativeCount.coerceAtLeast(0),
                )
            } else {
                val baseline = _state.value.displayedCount(itemId, modelCount)
                MarketViewWindow(
                    itemId = itemId,
                    baseline = baseline,
                    latestAuthoritativeCount = maxOf(baseline, authoritativeCount.coerceAtLeast(0)),
                ).also { window ->
                    windows[itemId] = window
                    created = window
                    _state.update { current ->
                        current.copy(
                            suppressedBaselines = current.suppressedBaselines + (itemId to baseline),
                            activeWindows = current.activeWindows + itemId,
                        )
                    }
                }
            }
        }
        created?.let { window ->
            scope.launch {
                delay(MARKET_VIEW_REFLECTION_DELAY_MS)
                synchronized(lock) {
                    if (windows[itemId] !== window) return@synchronized
                    windows.remove(itemId)
                    _state.update { current ->
                        current.copy(
                            revealedCounts = current.revealedCounts + (
                                itemId to maxOf(
                                    current.revealedCounts[itemId] ?: 0,
                                    window.latestAuthoritativeCount,
                                )
                            ),
                            suppressedBaselines = current.suppressedBaselines - itemId,
                            activeWindows = current.activeWindows - itemId,
                        )
                    }
                }
            }
        }
    }
}

fun rememberDelayedMarketViewCount(itemId: String, modelCount: Int): Int {
    val displayState by androidx.compose.runtime.collectAsState(MarketViewDisplayCoordinator.state)
    return displayState.displayedCount(itemId, modelCount)
}
