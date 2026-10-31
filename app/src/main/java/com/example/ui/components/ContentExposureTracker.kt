package com.example.ui.components

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.data.views.ContentViewCoordinator
import kotlinx.coroutines.delay

private const val MIN_VISIBLE_FRACTION = 0.50f
private const val POST_QUALIFICATION_MS = 1_500L

/** Tracks a single genuine exposure per continuous qualified visibility session. */
internal class ContentVisibilityGate {
    private var wasQualified = false

    fun update(isQualified: Boolean): Boolean {
        val newlyQualified = isQualified && !wasQualified
        wasQualified = isQualified
        return newlyQualified
    }
}

@Composable
fun rememberDelayedContentViewCount(contentId: String, modelCount: Int): Int {
    val displayState by ContentViewCoordinator.displayState.collectAsState()
    return displayState.displayedCount(contentId, modelCount)
}

/**
 * A position callback alone is not a qualified view: require sustained
 * half-screen visibility while the app window has focus. Cancels dwell timing
 * when a row scrolls out, screen navigation disposes it, or the app loses focus.
 *
 * For short-form video callers, supply an appropriate observed playback duration
 * threshold and use real playback telemetry rather than fake timers.
 */
@Composable
fun Modifier.trackContentExposure(
    contentId: String,
    currentlyDisplayedCount: Int,
    minVisibleMillis: Long = POST_QUALIFICATION_MS,
    playbackActive: Boolean = true,
): Modifier {
    if (contentId.isBlank()) return this
    val appContext = LocalContext.current.applicationContext
    val rootView = LocalView.current
    // An exposure is counted once per continuous visibility session, not forever
    // for a saved LazyColumn item. Scrolling fully away permits requalification.
    var wasRecorded by remember(contentId) { mutableStateOf(false) }
    val visibilityGate = remember(contentId) { ContentVisibilityGate() }
    var currentlyVisible by remember(contentId) { mutableStateOf(false) }
    var windowFocused by remember(rootView) { mutableStateOf(rootView.hasWindowFocus()) }

    DisposableEffect(rootView) {
        val observer = rootView.viewTreeObserver
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            windowFocused = focused
        }
        observer.addOnWindowFocusChangeListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
        }
    }

    LaunchedEffect(contentId, currentlyVisible, windowFocused, playbackActive, minVisibleMillis, wasRecorded) {
        if (!currentlyVisible) {
            visibilityGate.update(false)
            // The item left the viewport: reset only then, not on a user pause,
            // buffer event, or lost app focus within the same viewing session.
            wasRecorded = false
            return@LaunchedEffect
        }
        if (!windowFocused || !playbackActive || wasRecorded) return@LaunchedEffect
        delay(minVisibleMillis.coerceAtLeast(1_000L))
        if (currentlyVisible && windowFocused && playbackActive && !wasRecorded && visibilityGate.update(true)) {
            wasRecorded = true
            ContentViewCoordinator.recordExposure(
                context = appContext,
                postId = contentId,
                currentlyDisplayedCount = currentlyDisplayedCount,
            )
        }
    }

    return onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) {
            currentlyVisible = false
            return@onGloballyPositioned
        }
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()
        if (rootWidth <= 0f || rootHeight <= 0f) {
            currentlyVisible = false
            return@onGloballyPositioned
        }

        val bounds: Rect = coordinates.boundsInWindow()
        if (bounds.width <= 0f || bounds.height <= 0f) {
            currentlyVisible = false
            return@onGloballyPositioned
        }

        val visibleWidth = (minOf(bounds.right, rootWidth) - maxOf(bounds.left, 0f))
            .coerceAtLeast(0f)
        val visibleHeight = (minOf(bounds.bottom, rootHeight) - maxOf(bounds.top, 0f))
            .coerceAtLeast(0f)
        val widthReference = minOf(bounds.width, rootWidth)
        val heightReference = minOf(bounds.height, rootHeight)
        currentlyVisible =
            visibleWidth >= widthReference * MIN_VISIBLE_FRACTION &&
            visibleHeight >= heightReference * MIN_VISIBLE_FRACTION
    }
}
