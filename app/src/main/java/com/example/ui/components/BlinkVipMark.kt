package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.BlinkEconomyService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

private object BlinkVipStatusCache {
    private data class Entry(val active: Boolean, val checkedAt: Long)
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private val values = ConcurrentHashMap<String, Entry>()

    suspend fun resolve(username: String): Boolean {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isBlank()) return false
        val now = System.currentTimeMillis()
        values[key]?.takeIf { now - it.checkedAt < TTL_MILLIS }?.let { return it.active }
        val active = BlinkEconomyService().vipStatusByUsername(key)
            .getOrNull()
            ?.optBoolean("is_vip", false)
            ?: false
        values[key] = Entry(active, now)
        return active
    }

    fun put(username: String, active: Boolean) {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isNotBlank()) values[key] = Entry(active, System.currentTimeMillis())
    }
}

@Composable
fun BlinkVipMark(
    isVip: Boolean,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 5.dp
) {
    if (!isVip) return
    Box(
        modifier = modifier
            .background(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFF59E0B))
                ),
                shape = RoundedCornerShape(100.dp)
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "VIP",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.35.sp
        )
    }
}

/**
 * Drop-in badge for surfaces that only have a username. Results are cached briefly so
 * scrolling feeds/comments/chats does not repeatedly hit the VIP status RPC.
 */
@Composable
fun BlinkVipMarkForUsername(
    username: String,
    knownVip: Boolean? = null,
    modifier: Modifier = Modifier
) {
    var active by remember(username, knownVip) { mutableStateOf(knownVip ?: false) }
    LaunchedEffect(username, knownVip) {
        if (knownVip != null) {
            active = knownVip
            BlinkVipStatusCache.put(username, knownVip)
        } else if (username.isNotBlank()) {
            active = BlinkVipStatusCache.resolve(username)
        }
    }
    BlinkVipMark(isVip = active, modifier = modifier)
}
