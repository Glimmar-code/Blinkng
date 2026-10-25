package com.blinkng.desktop.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import com.blinkng.desktop.DesktopAppState
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * A countable discovery impression requires a profile card to occupy >=60%
 * of its rendered area inside the current window for at least 750ms.
 * These approximate client-reported events never grant Coins.
 */
@Composable
internal fun Modifier.trackDesktopReferralImpression(
    state: DesktopAppState,
    profileId: String,
    source: String,
): Modifier {
    val viewport = LocalWindowInfo.current.containerSize
    val viewportW = viewport.width.toFloat()
    val viewportH = viewport.height.toFloat()
    var visible by remember(profileId, source, state.session?.userId) {
        mutableStateOf(false)
    }

    LaunchedEffect(profileId, source, visible, state.session?.userId) {
        if (visible && profileId.isNotBlank() && state.session != null) {
            delay(750)
            runCatching { state.client.recordReferralImpression(profileId, source) }
        }
    }

    return this.onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInWindow()
        val area = rect.width * rect.height
        if (area <= 0f || viewportW <= 0f || viewportH <= 0f) {
            visible = false
        } else {
            val visibleWidth = max(0f, min(rect.right, viewportW) - max(rect.left, 0f))
            val visibleHeight = max(0f, min(rect.bottom, viewportH) - max(rect.top, 0f))
            visible = (visibleWidth * visibleHeight / area) >= 0.60f
        }
    }
}
