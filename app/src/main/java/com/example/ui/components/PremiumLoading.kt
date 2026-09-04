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
fun PremiumPullRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    darkSurface: Boolean = false,
    refreshingLabel: String = "Updating your feed"
) {
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    val refreshThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    val shimmerOffset = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "refreshShimmer")
        transition.animateFloat(
            initialValue = -90f,
            targetValue = 190f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
            label = "refreshShimmerOffset"
        ).value
    } else {
        -70f + (progress * 240f)
    }

    AnimatedVisibility(
        visible = isRefreshing || progress > 0.04f,
        modifier = modifier,
        enter = fadeIn(tween(140)) + scaleIn(tween(200, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(140)) + scaleOut(tween(160))
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 10.dp)
                .graphicsLayer {
                    val visualProgress = if (isRefreshing) 1f else progress
                    translationY = (visualProgress * refreshThresholdPx) - size.height
                    alpha = visualProgress
                    scaleX = 0.86f + (visualProgress * 0.14f)
                    scaleY = 0.86f + (visualProgress * 0.14f)
                },
            shape = RoundedCornerShape(100.dp),
            color = if (darkSurface) Color(0xE61A1A1A) else MaterialTheme.colorScheme.surface.copy(alpha = .96f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        isRefreshing -> refreshingLabel
                        progress >= 1f -> "Release to refresh"
                        else -> "Pull to refresh"
                    },
                    color = if (darkSurface) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .width(74.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF7C3AED).copy(alpha = .24f),
                                    Color(0xFFD946EF),
                                    Color(0xFF7C3AED).copy(alpha = .24f)
                                ),
                                startX = shimmerOffset - 70f,
                                endX = shimmerOffset
                            )
                        )
                )
            }
        }
    }
}
