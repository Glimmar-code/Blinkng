package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun OfflineConnectionBanner(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var showRestored by remember { mutableStateOf(false) }
    var previousOffline by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        val wasOffline = previousOffline
        previousOffline = visible

        if (visible) {
            showRestored = false
        } else if (wasOffline) {
            showRestored = true
            delay(2_500)
            showRestored = false
        }
    }

    val restored = !visible && showRestored
    val showBanner = visible || restored

    AnimatedVisibility(
        visible = showBanner,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 4.dp),
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(260, easing = FastOutSlowInEasing)
        ) + fadeIn(tween(160)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(210, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(140))
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (restored) Color(0xFF16A34A) else Color(0xFFD92D20),
            shadowElevation = 4.dp,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (restored) Icons.Rounded.CheckCircle else Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (restored) "Back online" else "No internet connection",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
