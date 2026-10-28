package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.data.views.ContentViewCoordinator

private const val MIN_VISIBLE_FRACTION = 0.50f

/** An exposure is counted once per continuous qualified visit, not per recomposition. */
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

@Composable
fun Modifier.trackContentExposure(
    contentId: String,
    currentlyDisplayedCount: Int
): Modifier {
    if (contentId.isBlank()) return this
    val appContext = LocalContext.current.applicationContext
    val rootView = LocalView.current
    // Do not save this transient state in LazyList's saveable-state holder:
    // after an item is disposed and scrolled back into view it must count again.
    val visibilityGate = remember(contentId) { ContentVisibilityGate() }

    return onGloballyPositioned { coordinates ->
        if (!coordinates.isAttached) {
            visibilityGate.update(false)
            return@onGloballyPositioned
        }
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()
        if (rootWidth <= 0f || rootHeight <= 0f) {
            visibilityGate.update(false)
            return@onGloballyPositioned
        }

        val bounds: Rect = coordinates.boundsInWindow()
        if (bounds.width <= 0f || bounds.height <= 0f) {
            visibilityGate.update(false)
            return@onGloballyPositioned
        }

        val visibleWidth = (minOf(bounds.right, rootWidth) - maxOf(bounds.left, 0f))
            .coerceAtLeast(0f)
        val visibleHeight = (minOf(bounds.bottom, rootHeight) - maxOf(bounds.top, 0f))
            .coerceAtLeast(0f)
        val widthReference = minOf(bounds.width, rootWidth)
        val heightReference = minOf(bounds.height, rootHeight)
        val isQualified = visibleWidth >= widthReference * MIN_VISIBLE_FRACTION &&
            visibleHeight >= heightReference * MIN_VISIBLE_FRACTION

        if (visibilityGate.update(isQualified)) {
            ContentViewCoordinator.recordExposure(
                context = appContext,
                postId = contentId,
                currentlyDisplayedCount = currentlyDisplayedCount
            )
        }
    }
}
