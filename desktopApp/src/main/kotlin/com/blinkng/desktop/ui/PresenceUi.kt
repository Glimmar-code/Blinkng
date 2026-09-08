package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val desktopPresenceDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private val onlineGreen = Color(0xFF22C55E)
private val offlineBrown = Color(0xFF8B5A2B)

internal fun desktopPresenceStatus(
    isOnline: Boolean,
    rawTimestamp: String?,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (isOnline) return "Active now"
    val raw = rawTimestamp?.trim().orEmpty()
    val instant = if (raw.isBlank() || raw.equals("null", true)) null else {
        runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
    } ?: return "Last seen recently"
    val seconds = max(0L, now.epochSecond - instant.epochSecond)
    val minutes = seconds / 60L
    val hours = seconds / 3_600L
    val days = seconds / 86_400L
    return when {
        seconds < 60L -> "Last seen just now"
        minutes == 1L -> "Last seen 1 min ago"
        minutes < 60L -> "Last seen $minutes mins ago"
        hours == 1L -> "Last seen 1 hr ago"
        hours < 24L -> "Last seen $hours hrs ago"
        days == 1L -> "Last seen 1 day ago"
        else -> "Last seen ${desktopPresenceDateFormatter.format(instant.atZone(zoneId))}"
    }
}

@Composable
internal fun PresenceAvatar(
    name: String,
    isOnline: Boolean?,
    size: Dp = 38.dp,
) {
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier.fillMaxSize().clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "B",
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        isOnline?.let { active ->
            Box(
                modifier = Modifier
                    .size((size.value * .28f).dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(if (active) onlineGreen else offlineBrown)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }
    }
}
