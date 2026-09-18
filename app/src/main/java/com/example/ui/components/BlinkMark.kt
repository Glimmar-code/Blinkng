package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.models.VerificationBadge
import com.example.ui.theme.*

@Composable
fun BlinkMark(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    showText: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_icon),
            contentDescription = "BLINK logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size)
        )

        if (showText) {
            Text(
                text = "BLINK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onBackground
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

fun formatNumber(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1_000.0).replace(".0k", "k")
        else -> count.toString()
    }
}

@Composable
fun HighlightedText(
    text: String,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.5.sp
) {
    val highlighted = remember(text, accentColor) {
        buildAnnotatedString {
            var cursor = 0
            val tokenPattern = Regex("""(?<![\p{L}\p{N}_])[@#][\p{L}\p{N}_.-]+""")
            tokenPattern.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                withStyle(
                    SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append(match.value)
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
    Text(
        text = highlighted,
        color = textColor,
        fontSize = fontSize,
        modifier = modifier
    )
}
