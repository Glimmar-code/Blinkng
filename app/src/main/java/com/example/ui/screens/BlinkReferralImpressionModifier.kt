package com.example.ui.screens

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.data.supabase.BlinkReferralClient
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Count source-attributed profile cards after >=60% is actually on-screen
 * for at least 750ms. These are estimates, never monetizable referral credits.
 */
@Composable
internal fun Modifier.trackBlinkReferralImpression(
    profileId: String,
    source: String,
): Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val viewportW = with(density) { configuration.screenWidthDp.dp.toPx() }
    val viewportH = with(density) { configuration.screenHeightDp.dp.toPx() }
    var visible by remember(profileId, source) { mutableStateOf(false) }

    LaunchedEffect(profileId, source, visible) {
        if (visible && profileId.isNotBlank()) {
            delay(750)
            runCatching { BlinkReferralClient.recordImpression(profileId, source) }
        }
    }

    return this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val fullArea = bounds.width * bounds.height
        if (fullArea <= 0f || viewportW <= 0f || viewportH <= 0f) {
            visible = false
        } else {
            val inWidth = max(0f, min(bounds.right, viewportW) - max(bounds.left, 0f))
            val inHeight = max(0f, min(bounds.bottom, viewportH) - max(bounds.top, 0f))
            visible = (inWidth * inHeight / fullArea) >= 0.60f
        }
    }
}
