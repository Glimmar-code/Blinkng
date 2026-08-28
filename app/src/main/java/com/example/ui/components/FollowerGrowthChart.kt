package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

data class FollowerDataPoint(
    val dayIndex: Int,
    val dateLabel: String,
    val formattedDate: String,
    val followers: Int,
    val dailyGain: Int
)

/**
 * Generates realistic 30-day follower progression data ending at [currentFollowers].
 */
fun generate30DayFollowerData(currentFollowers: Int): List<FollowerDataPoint> {
    val totalDays = 30
    val startFollowers = (currentFollowers * 0.72).roundToInt().coerceAtLeast(10)
    val totalGain = currentFollowers - startFollowers
    
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    val shortSdf = SimpleDateFormat("d", Locale.getDefault())

    val points = mutableListOf<FollowerDataPoint>()
    var runningCount = startFollowers

    // Realistic growth distribution with small daily variations
    val weights = listOf(
        1.1, 0.8, 1.4, 0.6, 1.2, 1.5, 0.9, 1.3, 1.0, 1.6,
        0.7, 1.1, 1.8, 1.2, 0.9, 1.4, 1.3, 1.0, 1.5, 0.8,
        1.7, 1.2, 1.1, 1.6, 1.3, 1.5, 1.2, 1.8, 1.4, 1.0
    )
    val weightSum = weights.sum()

    for (i in 0 until totalDays) {
        val daysAgo = totalDays - 1 - i
        val dayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        
        val stepRatio = (weights[i] / weightSum) * totalGain
        val gain = stepRatio.roundToInt().coerceAtLeast(0)
        
        if (i == totalDays - 1) {
            runningCount = currentFollowers
        } else {
            runningCount += gain
        }

        points.add(
            FollowerDataPoint(
                dayIndex = i,
                dateLabel = shortSdf.format(dayCal.time),
                formattedDate = sdf.format(dayCal.time),
                followers = runningCount,
                dailyGain = gain
            )
        )
    }

    return points
}

/**
 * Follower Growth Chart:
 * Smooth area trend graph tracking the user's last 30-day follower trajectory
 * with a Gold Verification threshold milestone (1,000 followers target).
 */
@Composable
fun FollowerGrowthChart(
    profile: UserProfile,
    isDark: Boolean,
    onOpenGetVerified: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTimeframe by remember { mutableIntStateOf(30) } // 7, 14, 30
    val raw30DayData = remember(profile.followerCount) {
        generate30DayFollowerData(profile.followerCount)
    }

    val activeData = remember(selectedTimeframe, raw30DayData) {
        when (selectedTimeframe) {
            7 -> raw30DayData.takeLast(7)
            14 -> raw30DayData.takeLast(14)
            else -> raw30DayData
        }
    }

    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(selectedTimeframe) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val cardBg = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) Color.White else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val borderColor = if (isDark) DarkBorder else LightBorder

    val currentFollowers = profile.followerCount
    val initialFollowers = activeData.firstOrNull()?.followers ?: currentFollowers
    val totalGain = (currentFollowers - initialFollowers).coerceAtLeast(0)
    val percentageGain = if (initialFollowers > 0) ((totalGain.toDouble() / initialFollowers) * 100) else 0.0
    val dailyAvg = if (activeData.isNotEmpty()) totalGain.toDouble() / activeData.size else 0.0
    
    val goldTarget = 1000
    val remainingForGold = (goldTarget - currentFollowers).coerceAtLeast(0)
    val progressToGold = (currentFollowers.toFloat() / goldTarget).coerceIn(0f, 1f)
    val daysToReachGold = if (dailyAvg > 0 && remainingForGold > 0) (remainingForGold / dailyAvg).roundToInt() else 0

    val activePoint = selectedPointIndex?.let { idx -> activeData.getOrNull(idx) } ?: activeData.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .padding(18.dp)
            .testTag("follower_growth_chart_card")
    ) {
        // Header with Title and Range Picker
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = BlinkGold,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Follower Growth Trends",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary
                    )
                }
                Text(
                    text = "Track velocity to 1,000 Gold VIP milestone",
                    fontSize = 11.5.sp,
                    color = textSecondary
                )
            }

            // Timeframe Segmented Switcher (7D, 14D, 30D)
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = if (isDark) DarkBackground else Color(0xFFF1F3F5),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Row(modifier = Modifier.padding(3.dp)) {
                    listOf(7 to "7D", 14 to "14D", 30 to "30D").forEach { (days, label) ->
                        val isSelected = selectedTimeframe == days
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSelected) BlinkGold else Color.Transparent)
                                .clickable {
                                    selectedTimeframe = days
                                    selectedPointIndex = null
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Key Metric Summary Counters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Active Scrubber / Current Stat Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) DarkBackground else Color(0xFFF8F9FA),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (selectedPointIndex != null) "On ${activePoint?.formattedDate}" else "Current Total",
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${activePoint?.followers ?: currentFollowers}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary
                    )
                    Text(
                        text = if (selectedPointIndex != null) "+${activePoint?.dailyGain ?: 0} gained" else "+$totalGain (${String.format(Locale.US, "%.1f", percentageGain)}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlinkOnlineGreen
                    )
                }
            }

            // Target Progress / Remaining Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) DarkBackground else Color(0xFFF8F9FA),
                border = BorderStroke(1.dp, if (profile.verificationBadge == VerificationBadge.GOLD || currentFollowers >= 1000) BlinkGold.copy(alpha = 0.5f) else borderColor),
                modifier = Modifier.weight(1.1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Gold VIP Goal",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = BlinkGold
                        )
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = BlinkGold, modifier = Modifier.size(13.dp))
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    if (currentFollowers >= 1000) {
                        Text(
                            text = "Target Met 🎉",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = BlinkGold
                        )
                        Text(
                            text = "1,000+ Unlocked!",
                            fontSize = 11.sp,
                            color = BlinkOnlineGreen,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "$remainingForGold to go",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = textPrimary
                        )
                        Text(
                            text = if (daysToReachGold > 0) "Est. in ~$daysToReachGold days (+${String.format(Locale.US, "%.1f", dailyAvg)}/d)" else "${(progressToGold * 100).roundToInt()}% completed",
                            fontSize = 11.sp,
                            color = textSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Progress Bar toward 1,000 Followers
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Progress: ${currentFollowers}/1,000",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textSecondary
                )
                Text(
                    text = "${(progressToGold * 100).roundToInt()}%",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (progressToGold >= 1f) BlinkGold else BlinkPink
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progressToGold },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = BlinkGold,
                trackColor = if (isDark) DarkBorder else Color(0xFFE2E6EA)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Custom Native Canvas Curve Graph (Area Chart + Target Line + Scrubber)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeData) {
                        detectTapGestures { offset ->
                            val width = size.width
                            val stepX = width / (activeData.size - 1).coerceAtLeast(1)
                            val tappedIdx = (offset.x / stepX).roundToInt().coerceIn(0, activeData.size - 1)
                            selectedPointIndex = tappedIdx
                        }
                    }
                    .pointerInput(activeData) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val width = size.width
                            val stepX = width / (activeData.size - 1).coerceAtLeast(1)
                            val draggedIdx = (change.position.x / stepX).roundToInt().coerceIn(0, activeData.size - 1)
                            selectedPointIndex = draggedIdx
                        }
                    }
            ) {
                if (activeData.isEmpty()) return@Canvas

                val width = size.width
                val height = size.height
                val bottomPadding = 24.dp.toPx()
                val topPadding = 20.dp.toPx()
                val usableHeight = height - bottomPadding - topPadding

                val minFollowers = (activeData.minOf { it.followers } * 0.95).toInt().coerceAtLeast(0)
                val maxFollowers = maxOf(activeData.maxOf { it.followers }, 1050) // Scale to include 1,000 threshold
                val range = (maxFollowers - minFollowers).coerceAtLeast(1)

                val stepX = width / (activeData.size - 1).coerceAtLeast(1)

                // 1. Draw Gold Milestone Threshold Line at 1,000
                val targetRatio = (1000 - minFollowers).toFloat() / range
                val targetY = (topPadding + usableHeight * (1f - targetRatio.coerceIn(0f, 1f)))

                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                drawLine(
                    color = BlinkGold.copy(alpha = 0.65f),
                    start = Offset(0f, targetY),
                    end = Offset(width, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashPathEffect
                )

                // 2. Compute Points on Curve with Animation Progress
                val points = activeData.mapIndexed { i, pt ->
                    val x = i * stepX
                    val ratio = (pt.followers - minFollowers).toFloat() / range
                    val y = topPadding + usableHeight * (1f - (ratio * animationProgress.value).coerceIn(0f, 1f))
                    Offset(x, y)
                }

                // 3. Build Smooth Cubic Spline Path
                val strokePath = Path()
                val fillPath = Path()

                if (points.isNotEmpty()) {
                    strokePath.moveTo(points[0].x, points[0].y)
                    fillPath.moveTo(points[0].x, height - bottomPadding)
                    fillPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points[i]
                        val p1 = points[i + 1]
                        val controlX1 = p0.x + (p1.x - p0.x) / 2
                        val controlY1 = p0.y
                        val controlX2 = p0.x + (p1.x - p0.x) / 2
                        val controlY2 = p1.y

                        strokePath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                        fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, p1.x, p1.y)
                    }

                    fillPath.lineTo(points.last().x, height - bottomPadding)
                    fillPath.close()

                    // Gradient Fill under curve
                    val areaGradient = Brush.verticalGradient(
                        colors = listOf(
                            BlinkGold.copy(alpha = 0.35f),
                            BlinkPink.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        startY = topPadding,
                        endY = height - bottomPadding
                    )
                    drawPath(path = fillPath, brush = areaGradient)

                    // Curve Line
                    val strokeGradient = Brush.horizontalGradient(
                        colors = listOf(BlinkPink, BlinkGold)
                    )
                    drawPath(
                        path = strokePath,
                        brush = strokeGradient,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 4. Highlight Selected or Active Point
                    val activeIdx = selectedPointIndex ?: (points.size - 1)
                    val activePt = points[activeIdx.coerceIn(0, points.size - 1)]

                    // Vertical Scrubber Guideline
                    if (selectedPointIndex != null) {
                        drawLine(
                            color = textSecondary.copy(alpha = 0.3f),
                            start = Offset(activePt.x, topPadding),
                            end = Offset(activePt.x, height - bottomPadding),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // Scrubber Dot (Outer glow + Inner solid)
                    drawCircle(
                        color = BlinkGold.copy(alpha = 0.25f),
                        radius = 12.dp.toPx(),
                        center = activePt
                    )
                    drawCircle(
                        color = BlinkGold,
                        radius = 6.dp.toPx(),
                        center = activePt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = activePt
                    )
                }
            }

            // Milestone Badge tag positioned directly at target
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = BlinkGold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "1,000 Target (Gold VIP)",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }
            }
        }

        // X-Axis Date Labels Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelCount = if (selectedTimeframe == 7) 7 else 5
            val step = (activeData.size - 1).coerceAtLeast(1) / (labelCount - 1).coerceAtLeast(1)
            
            for (i in 0 until labelCount) {
                val idx = (i * step).coerceAtMost(activeData.size - 1)
                val item = activeData.getOrNull(idx)
                if (item != null) {
                    Text(
                        text = item.dateLabel,
                        fontSize = 10.sp,
                        color = if (selectedPointIndex == idx) BlinkGold else textSecondary,
                        fontWeight = if (selectedPointIndex == idx) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CTA Banner: Get Verified / Unlock Gold VIP
        if (profile.verificationBadge != VerificationBadge.GOLD) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BlinkGold.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, BlinkGold.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BlinkGold,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                            }
                        }
                        Column {
                            Text(
                                text = if (currentFollowers >= 1000) "Eligible for Gold VIP Badge!" else "Unlock Gold VIP at 1,000 Followers",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                            Text(
                                text = if (currentFollowers >= 1000) "Tap to activate your Gold badge (₦2,000)" else "Blue Badge available instantly for ₦800",
                                fontSize = 11.sp,
                                color = textSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onOpenGetVerified,
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentFollowers >= 1000) BlinkGold else BlinkPink),
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (currentFollowers >= 1000) "Activate Gold" else "Get Verified",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}
