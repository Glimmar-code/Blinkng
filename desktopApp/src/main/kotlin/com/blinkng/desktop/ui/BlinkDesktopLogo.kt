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
            val p = Path().apply {
                moveTo(w * 0.267f, h * 0.133f)
                lineTo(w * 0.600f, h * 0.133f)
                cubicTo(w * 0.700f, h * 0.133f, w * 0.783f, h * 0.208f, w * 0.783f, h * 0.317f)
                cubicTo(w * 0.783f, h * 0.425f, w * 0.700f, h * 0.500f, w * 0.600f, h * 0.500f)
                lineTo(w * 0.383f, h * 0.500f)
                lineTo(w * 0.217f, h * 0.633f)
                lineTo(w * 0.217f, h * 0.200f)
                cubicTo(w * 0.217f, h * 0.158f, w * 0.233f, h * 0.133f, w * 0.267f, h * 0.133f)
                close()

                moveTo(w * 0.383f, h * 0.533f)
                lineTo(w * 0.608f, h * 0.533f)
                cubicTo(w * 0.717f, h * 0.533f, w * 0.800f, h * 0.608f, w * 0.800f, h * 0.717f)
                cubicTo(w * 0.800f, h * 0.825f, w * 0.717f, h * 0.900f, w * 0.608f, h * 0.900f)
                lineTo(w * 0.383f, h * 0.900f)
                lineTo(w * 0.217f, h * 0.967f)
                lineTo(w * 0.217f, h * 0.617f)
                close()
            }
            drawPath(path = p, color = Color.White)
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
