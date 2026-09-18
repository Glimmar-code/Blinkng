package com.blinkng.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BlinkDesktopLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    showText: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.24f))
                .background(Color.Black)
        ) {
            val w = this.size.width
            val h = this.size.height
            val barW = w * 0.54f
            val barH = h * 0.20f
            val left = w * 0.29f
            val radius = barH / 2f

            rotate(24f, Offset(w * 0.55f, h * 0.37f)) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, h * 0.27f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            rotate(-24f, Offset(w * 0.55f, h * 0.64f)) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(left, h * 0.54f),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }

            val play = Path().apply {
                moveTo(w * 0.29f, h * 0.42f)
                lineTo(w * 0.53f, h * 0.50f)
                lineTo(w * 0.29f, h * 0.58f)
                close()
            }
            drawPath(play, Color.White)
        }

        if (showText) {
            Text(
                text = "BLINK",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
