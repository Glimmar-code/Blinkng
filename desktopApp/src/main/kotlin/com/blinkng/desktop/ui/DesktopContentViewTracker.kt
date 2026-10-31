package com.blinkng.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import com.blinkng.desktop.data.DesktopSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** BLINK Android and desktop share a two-minute presentation-only delay. */
internal const val DESKTOP_VIEW_REFLECTION_DELAY_MS = 120_000L

/** Minimum foreground dwell before a visible item becomes a view. */
internal const val DESKTOP_CONTENT_EXPOSURE_DWELL_MS = 1_500L

/** Do not count repeated layouts or preloaded/off-screen content. */
internal class DesktopVisibilityGate {
    private var wasQualified = false
    fun update(qualified: Boolean): Boolean {
        val started = qualified && !wasQualified
        wasQualified = qualified
        return started
    }
}

internal data class DesktopContentViewState(
    val baselines: Map<String, Int> = emptyMap(),
    val active: Set<String> = emptySet(),
    val revealed: Map<String, Int> = emptyMap(),
) {
    fun displayed(postId: String, modelCount: Int): Int {
        val current = modelCount.coerceAtLeast(0)
        if (postId in active) return baselines[postId] ?: current
        return maxOf(current, revealed[postId] ?: 0)
    }
}

private class DesktopViewWindow(
    val id: String,
    val baseline: Int,
    var newestCount: Int? = null,
    var pending: Int = 0,
    var client: DesktopSupabaseClient,
)

/**
 * View writes happen immediately using the authenticated, idempotent
 * record_content_view RPC. Only the public counter is held back 120 seconds.
 */
internal object DesktopContentViewCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val windows = mutableMapOf<String, DesktopViewWindow>()
    private val _state = MutableStateFlow(DesktopContentViewState())
    val state = _state.asStateFlow()

    fun record(client: DesktopSupabaseClient, postId: String, currentCount: Int) {
        if (postId.isBlank() || client.session == null) return
        val created: DesktopViewWindow?
        val window: DesktopViewWindow
        synchronized(lock) {
            val existing = windows[postId]
            if (existing != null) {
                window = existing
                created = null
            } else {
                window = DesktopViewWindow(
                    postId,
                    _state.value.displayed(postId, currentCount),
                    client = client,
                )
                windows[postId] = window
                _state.update { old ->
                    old.copy(
                        baselines = old.baselines + (postId to window.baseline),
                        active = old.active + postId,
                    )
                }
                created = window
            }
            window.pending += 1
        }
        if (created != null) scope.launch {
            delay(DESKTOP_VIEW_REFLECTION_DELAY_MS)
            // One authoritative refetch for the window; the RPC's writes were immediate.
            val fetched = runCatching { created.client.fetchContentViewCount(postId) }.getOrNull()
            synchronized(lock) {
                if (windows[postId] !== created) return@synchronized
                windows.remove(postId)
                val updated = maxOf(
                    created.baseline,
                    created.newestCount ?: 0,
                    fetched ?: 0,
                )
                _state.update { old ->
                    old.copy(
                        baselines = old.baselines - postId,
                        active = old.active - postId,
                        revealed = old.revealed + (postId to maxOf(old.revealed[postId] ?: 0, updated)),
                    )
                }
            }
        }
        scope.launch {
            val reply = runCatching { client.recordQualifiedContentView(postId) }.getOrNull()
            synchronized(lock) {
                if (windows[postId] === window) {
                    val count = reply?.optInt("view_count", -1) ?: -1
                    if (count >= 0) window.newestCount = maxOf(window.newestCount ?: 0, count)
                    window.pending = (window.pending - 1).coerceAtLeast(0)
                } else if (reply != null) {
                    val count = reply.optInt("view_count", -1)
                    if (count >= 0) _state.update { old ->
                        old.copy(revealed = old.revealed + (postId to maxOf(old.revealed[postId] ?: 0, count)))
                    }
                }
            }
        }
    }
}

@Composable
internal fun desktopDisplayedViewCount(postId: String, modelCount: Int): Int {
    val display by DesktopContentViewCoordinator.state.collectAsState()
    return display.displayed(postId, modelCount)
}

/** Tracks a new view only when >=50% of the card is inside the window. */
@Composable
internal fun Modifier.trackDesktopContentExposure(
    client: DesktopSupabaseClient,
    postId: String,
    currentCount: Int,
): Modifier {
    val window = LocalWindowInfo.current
    val gate = remember(postId) { DesktopVisibilityGate() }
    val scope = rememberCoroutineScope()
    var currentlyVisible by remember(postId) { mutableStateOf(false) }
    var pendingExposure by remember(postId) { mutableStateOf<Job?>(null) }

    DisposableEffect(postId) {
        onDispose { pendingExposure?.cancel() }
    }

    return this.onGloballyPositioned { coordinates ->
        val size = window.containerSize
        val bounds = coordinates.boundsInWindow()
        val refWidth = minOf(bounds.width, size.width.toFloat())
        val refHeight = minOf(bounds.height, size.height.toFloat())
        val visibleWidth = (minOf(bounds.right, size.width.toFloat()) -
            maxOf(bounds.left, 0f)).coerceAtLeast(0f)
        val visibleHeight = (minOf(bounds.bottom, size.height.toFloat()) -
            maxOf(bounds.top, 0f)).coerceAtLeast(0f)
        val qualified = coordinates.isAttached && size.width > 0 && size.height > 0 &&
            refWidth > 0f && refHeight > 0f &&
            visibleWidth >= refWidth * 0.5f && visibleHeight >= refHeight * 0.5f

        if (!qualified) {
            currentlyVisible = false
            pendingExposure?.cancel()
            pendingExposure = null
            gate.update(false)
        } else if (!currentlyVisible) {
            currentlyVisible = true
            pendingExposure = scope.launch {
                delay(DESKTOP_CONTENT_EXPOSURE_DWELL_MS)
                if (currentlyVisible && gate.update(true)) {
                    DesktopContentViewCoordinator.record(client, postId, currentCount)
                }
                pendingExposure = null
            }
        }
    }
}
