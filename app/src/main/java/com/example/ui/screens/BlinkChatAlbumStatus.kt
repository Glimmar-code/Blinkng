package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Upload state of an album (photos/videos sent together).
 * Map this from your BlinkChatImageUploadWorker / outbox state.
 */
data class AlbumUploadState(
    val total: Int,
    val completed: Int,
    val progress: Float,          // 0f..1f overall
    val failed: Boolean = false,
    val retryAttempt: Int = 0,    // > 0 while auto-retrying
) {
    val isDone: Boolean get() = !failed && completed >= total && total > 0
}

@Composable
private fun ProgressRing(
    progress: Float,
    color: Color,
    size: Dp,
    stroke: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "ringProgress",
    )
    Canvas(modifier.size(size)) {
        val s = stroke.toPx()
        val inset = s / 2
        val arcSize = Size(this.size.width - s, this.size.height - s)
        drawArc(
            color = color.copy(alpha = 0.25f), startAngle = 0f, sweepAngle = 360f,
            useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(s),
        )
        drawArc(
            color = color, startAngle = -90f, sweepAngle = 360f * animated,
            useCenter = false, topLeft = Offset(inset, inset), size = arcSize,
            style = Stroke(s, cap = StrokeCap.Round),
        )
    }
}

/** Full-cover overlay for an album grid while uploading / retrying / failed. */
@Composable
fun BlinkChatAlbumStatusOverlay(
    state: AlbumUploadState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isDone) return
    val scrim by animateColorAsState(
        if (state.failed) Color(0x99000000) else Color(0x66000000),
        tween(250), label = "scrim",
    )
    Box(
        modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(scrim)
            .clickable(enabled = state.failed, onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = state.failed,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "albumStatusSwap",
        ) { failed ->
            if (failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color(0xCC1B1B1E)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Refresh, "Retry", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("Tap to retry", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(contentAlignment = Alignment.Center) {
                        ProgressRing(state.progress, Color.White, 48.dp)
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onCancel),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Close, "Cancel upload", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (state.retryAttempt > 0) "Retrying…" else "${state.completed}/${state.total}",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Compact pill variant (e.g. for the inbox row or a status strip). */
@Composable
fun BlinkChatAlbumStatusPill(
    state: AlbumUploadState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isDone) return
    Row(
        modifier
            .clip(CircleShape)
            .background(Color(0xCC111114))
            .clickable(enabled = state.failed, onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.failed) {
            Icon(Icons.Filled.Refresh, null, tint = BlinkChatColors.Danger, modifier = Modifier.size(18.dp))
            Text("Failed · tap to retry", color = Color.White, fontSize = 12.sp)
        } else {
            ProgressRing(state.progress, BlinkChatColors.Accent, 18.dp, 2.dp)
            Text(
                if (state.retryAttempt > 0) "Retrying…" else "Sending ${state.completed}/${state.total}",
                color = Color.White, fontSize = 12.sp,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                Icons.Filled.Close, "Cancel", tint = BlinkChatColors.Muted,
                modifier = Modifier.size(16.dp).clickable(onClick = onCancel),
            )
        }
    }
}
