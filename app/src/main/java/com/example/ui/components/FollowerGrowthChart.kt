package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.ProfileFollowerPoint
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.BlinkGold
import com.example.ui.theme.BlinkOnlineGreen
import com.example.ui.theme.BlinkPink
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkTextSecondary
import com.example.ui.theme.LightSurface
import com.example.ui.theme.LightTextPrimary
import com.example.ui.theme.LightTextSecondary
import kotlin.math.roundToInt

/**
 * Real follower analytics only. Historical values are never fabricated.
 */
@Composable
fun FollowerGrowthChart(
    profile: UserProfile,
    history: List<ProfileFollowerPoint> = emptyList(),
    isDark: Boolean,
    onOpenGetVerified: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTimeframe by remember { mutableIntStateOf(30) }
    val activeData = remember(history, selectedTimeframe) {
        history
            .filter { it.date.isNotBlank() }
            .distinctBy { it.date }
            .takeLast(selectedTimeframe)
    }
    var selectedPointIndex by remember(activeData) { mutableStateOf<Int?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(activeData) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )
    }

    val cardBg = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkBorder else MaterialTheme.colorScheme.outlineVariant

    val currentFollowers = profile.followerCount.coerceAtLeast(0)
    val firstSnapshot = activeData.firstOrNull()?.followerCount
    val followerDelta = firstSnapshot?.let { currentFollowers - it }
    val followerDeltaLabel = when {
        followerDelta == null -> "History is starting"
        followerDelta > 0 -> "+$followerDelta"
        else -> followerDelta.toString()
    }

    val goldTarget = 1_000
    val remainingForGold = (goldTarget - currentFollowers).coerceAtLeast(0)
    val progressToGold = (currentFollowers.toFloat() / goldTarget).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("follower_growth_chart_card"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = BlinkGold, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Follower analytics", fontSize = 16.sp, fontWeight = FontWeight.Black, color = textPrimary)
                Text("Real daily snapshots only", fontSize = 10.5.sp, color = textSecondary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                title = "Followers",
                value = currentFollowers.toString(),
                subtitle = followerDeltaLabel,
                modifier = Modifier.weight(1f),
                isDark = isDark
            )
            MetricCard(
                title = "Profile views",
                value = profile.profileViewsThisWeek.coerceAtLeast(0).toString(),
                subtitle = "Last 7 days",
                modifier = Modifier.weight(1f),
                isDark = isDark
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(100.dp),
            color = if (isDark) DarkBackground else MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf(7 to "7D", 14 to "14D", 30 to "30D").forEach { (days, label) ->
                    val selected = selectedTimeframe == days
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 30.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(if (selected) BlinkGold else Color.Transparent)
                            .clickable {
                                selectedTimeframe = days
                                selectedPointIndex = null
                            }
                    ) {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.Black else textSecondary
                        )
                    }
                }
            }
        }

        if (activeData.size >= 2) {
            val counts = activeData.map { it.followerCount.coerceAtLeast(0) }
            val minCount = counts.minOrNull() ?: 0
            val maxCount = counts.maxOrNull() ?: currentFollowers
            val range = (maxCount - minCount).coerceAtLeast(1)
            val activeIndex = (selectedPointIndex ?: activeData.lastIndex).coerceIn(0, activeData.lastIndex)
            val activePoint = activeData[activeIndex]

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${activePoint.date} • ${activePoint.followerCount} followers",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary
                )
                Box(modifier = Modifier.fillMaxWidth().height(176.dp)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(activeData) {
                                detectTapGestures { offset ->
                                    val stepX = size.width / (activeData.size - 1).coerceAtLeast(1)
                                    selectedPointIndex = (offset.x / stepX).roundToInt().coerceIn(0, activeData.lastIndex)
                                }
                            }
                            .pointerInput(activeData) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val stepX = size.width / (activeData.size - 1).coerceAtLeast(1)
                                    selectedPointIndex = (change.position.x / stepX).roundToInt().coerceIn(0, activeData.lastIndex)
                                }
                            }
                    ) {
                        val horizontalPadding = 8.dp.toPx()
                        val verticalPadding = 12.dp.toPx()
                        val usableWidth = size.width - horizontalPadding * 2
                        val usableHeight = size.height - verticalPadding * 2
                        val stepX = usableWidth / (activeData.size - 1).coerceAtLeast(1)
                        val points = activeData.mapIndexed { index, point ->
                            val ratio = (point.followerCount - minCount).toFloat() / range
                            Offset(
                                x = horizontalPadding + index * stepX,
                                y = verticalPadding + usableHeight * (1f - ratio * animationProgress.value)
                            )
                        }

                        for (index in 0 until points.lastIndex) {
                            drawLine(
                                color = BlinkGold,
                                start = points[index],
                                end = points[index + 1],
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        points.forEachIndexed { index, point ->
                            if (index == activeIndex || index == points.lastIndex) {
                                drawCircle(BlinkGold.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = point)
                                drawCircle(BlinkGold, radius = 4.5.dp.toPx(), center = point)
                            }
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) DarkBackground else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No invented trend line", fontWeight = FontWeight.Bold, color = textPrimary)
                    Text(
                        "BLINK will draw this graph after real daily follower snapshots have been collected.",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Gold goal", fontSize = 11.sp, color = textSecondary)
                Text(
                    if (remainingForGold == 0) "Reached" else "$remainingForGold to go",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingForGold == 0) BlinkOnlineGreen else BlinkGold
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(borderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressToGold)
                        .height(7.dp)
                        .background(BlinkGold)
                )
            }
        }

        if (profile.verificationBadge != VerificationBadge.GOLD) {
            Button(
                onClick = onOpenGetVerified,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BlinkPink),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Verification options", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = if (isDark) DarkBackground else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
