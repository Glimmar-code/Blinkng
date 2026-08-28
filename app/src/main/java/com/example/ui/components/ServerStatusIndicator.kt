package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ServerStatusIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val statusColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
    val statusText = if (isConnected) "LIVE" else "OFFLINE"

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(statusColor.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(statusColor.copy(alpha = if (isConnected) alpha else 1f))
        )
        Text(
            text = statusText,
            fontSize = 9.sp,
            letterSpacing = 0.5.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}
