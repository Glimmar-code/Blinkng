package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Modifier.shimmerBackground(
    shape: Shape,
    baseColor: Color,
    highlightColor: Color
): Modifier = clip(shape).background(
    // A static highlight keeps the premium skeleton treatment without starting a
    // separate infinite animation for every placeholder currently on screen.
    Brush.horizontalGradient(listOf(baseColor, highlightColor, baseColor))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun PremiumPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    darkSurface: Boolean = false,
    refreshingLabel: String = "Updating your feed"
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    val visualProgress = if (isRefreshing) 1f else progress
    val refreshThresholdPx = with(LocalDensity.current) { 54.dp.toPx() }

    // Keep refresh visually quiet and familiar: a single Chrome-style rolling circle.
    // No text pill, shimmer bar, or long entrance/exit animation.
    AnimatedVisibility(
        visible = isRefreshing || progress > 0.04f,
        modifier = modifier,
        enter = fadeIn(tween(80)),
        exit = fadeOut(tween(80))
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .graphicsLayer {
                    translationY = (visualProgress * refreshThresholdPx) - size.height
                    alpha = visualProgress.coerceIn(0.18f, 1f)
                    val scale = 0.9f + (visualProgress * 0.1f)
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier
                    .width(22.dp)
                    .height(22.dp),
                color = if (darkSurface) Color.White else MaterialTheme.colorScheme.primary,
                strokeWidth = 2.2.dp
            )
        }
    }
}
