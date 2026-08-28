package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.VerificationBadge
import com.example.ui.theme.*

@Composable
fun BlinkMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    showText: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BlinkAnimation")
    val scaleY by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2800
                1f at 0
                1f at 2400
                0.15f at 2500
                1f at 2600
                1f at 2800
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "EyeScaleY"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.32f))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            BlinkPink,
                            BlinkPinkDeep,
                            BlinkPurple
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size * 0.52f)
                    .scale(scaleX = 1f, scaleY = scaleY),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "Blink",
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (showText) {
            val glowIntensity by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "GlowIntensity"
            )

            Text(
                text = "Bl!nk",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                color = BlinkPink,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = BlinkPink.copy(alpha = 0.4f + 0.4f * glowIntensity),
                        blurRadius = 12f + 12f * glowIntensity
                    )
                )
            )
        }
    }
}

@Composable
fun FacultyBadge(
    tag: String,
    modifier: Modifier = Modifier
) {
    val color = getFacultyColor(tag)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun VerifiedMark(
    badge: VerificationBadge,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    if (badge == VerificationBadge.NONE) return

    val isGold = badge == VerificationBadge.GOLD
    val bgColor = if (isGold) BlinkGold else BlinkBlue
    val iconTint = if (isGold) Color.Black else Color.White

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (isGold) "Gold VIP Verified" else "Blue Verified",
            tint = iconTint,
            modifier = Modifier.size(size * 0.68f)
        )
    }
}
