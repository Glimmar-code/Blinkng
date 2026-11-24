package com.example.ui.screens

import com.example.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.blinkng.shared.broadcast.Broadcast
import com.blinkng.shared.broadcast.BroadcastType
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

internal object BlinkBroadcastColors {
    val Blue = Color(0xFF1685FF)
    val Violet = Color(0xFF7457FF)
    val Mint = Color(0xFF35C99A)
    val Gold = Color(0xFFFFC857)
    val Coral = Color(0xFFFF7085)
    val Glass = Color(0x18FFFFFF)
    val HeroGradient = Brush.linearGradient(listOf(Color(0xFF132B4E), Color(0xFF5A39B8), Color(0xFFB8477A)))
    val PromoGradient = Brush.linearGradient(listOf(Color(0xFF17453C), Color(0xFF183458), Color(0xFF2C215A)))
}

@Composable
internal fun BlinkBroadcastSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 5.dp,
    ) {
        Column(
            Modifier.animateContentSize(tween(260, easing = FastOutSlowInEasing))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun BlinkBroadcastTypeBadge(type: BroadcastType, official: Boolean = false) {
    val label = when {
        official -> stringResource(R.string.broadcast_official)
        type == BroadcastType.ANNOUNCEMENT -> stringResource(R.string.broadcast_tab_announcements)
        type == BroadcastType.CAMPAIGN -> stringResource(R.string.broadcast_campaign)
        else -> stringResource(R.string.broadcast_offer)
    }
    val tint = when {
        official -> BlinkBroadcastColors.Blue
        type == BroadcastType.CAMPAIGN -> BlinkBroadcastColors.Violet
        type == BroadcastType.PROMO -> BlinkBroadcastColors.Mint
        else -> BlinkBroadcastColors.Gold
    }
    Row(
        Modifier.clip(CircleShape).background(tint.copy(alpha = .16f))
            .border(1.dp, tint.copy(alpha = .38f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (type == BroadcastType.PROMO) Icons.Default.LocalOffer
            else if (official) Icons.Default.Verified else Icons.Default.Campaign,
            contentDescription = null, tint = tint, modifier = Modifier.size(14.dp),
        )
        Text(label, color = tint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BlinkBroadcastCountdown(endsAt: Instant, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val seconds = (Duration.between(Instant.ofEpochMilli(now), endsAt).seconds).coerceAtLeast(0)
    val label = when {
        seconds >= 86_400 -> stringResource(R.string.broadcast_week_format, seconds / 3_600, (seconds % 3_600) / 60)
        seconds >= 3_600 -> stringResource(R.string.broadcast_hours_format, seconds / 3_600, (seconds % 3_600) / 60)
        seconds >= 60 -> stringResource(R.string.broadcast_minutes_format, seconds / 60, seconds % 60)
        else -> stringResource(R.string.broadcast_seconds_format, seconds)
    }
    Text(label, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun BlinkBroadcastProgress(progress: Int, target: Int, modifier: Modifier = Modifier) {
    val fraction = if (target > 0) (progress.toFloat() / target).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, tween(700), label = "broadcastProgress")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.broadcast_progress_format, progress.coerceAtLeast(0), target.coerceAtLeast(0)),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(7.dp)
            .clip(CircleShape), color = BlinkBroadcastColors.Mint, trackColor = MaterialTheme.colorScheme.surfaceVariant)
    }
}

@Composable
internal fun BlinkBroadcastPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = BlinkBroadcastColors.Blue)) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun BlinkBroadcastImage(url: String?, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = stringResource(R.string.broadcast_image_preview),
            contentScale = ContentScale.Crop, modifier = modifier.fillMaxWidth()
                .heightIn(min = 124.dp, max = 220.dp).clip(RoundedCornerShape(18.dp)))
    }
}

@Composable
internal fun BlinkBroadcastPreviewStamp() {
    Text(stringResource(R.string.broadcast_sample_disclaimer),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun BlinkBroadcastSkeleton(modifier: Modifier = Modifier) {
    val shimmer = rememberInfiniteTransition(label = "broadcastShimmer")
    val alpha by shimmer.animateFloat(0.25f, 0.68f,
        androidx.compose.animation.core.infiniteRepeatable(tween(850), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "shimmerAlpha")
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.fillMaxWidth(.36f).height(16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))
        Spacer(Modifier.fillMaxWidth(.78f).height(22.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))
        Spacer(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)))
    }
}

@Composable
internal fun BlinkBroadcastEmptyOrError(
    title: String,
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    BlinkBroadcastSurfaceCard {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        if (retryLabel != null && onRetry != null) {
            BlinkBroadcastPrimaryButton(retryLabel, onClick = onRetry)
        }
    }
}
