package com.blinkng.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
        Image(
            painter = painterResource("blink-logo.png"),
            contentDescription = "BLINK logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size),
        )

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
