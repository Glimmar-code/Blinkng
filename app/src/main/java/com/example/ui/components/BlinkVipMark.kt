package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.BlinkStoreCatalog
import com.example.data.supabase.BlinkEconomyService
import com.example.premiumExperience
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class BlinkPublicPremiumIdentity(
    val isVip: Boolean = false,
    val catalogId: String? = null,
    val publicLabel: String? = null,
    val motion: String = "premium_reveal"
)

private object BlinkPublicPremiumCache {
    private data class Entry(val identity: BlinkPublicPremiumIdentity, val checkedAt: Long)
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private val values = ConcurrentHashMap<String, Entry>()

    suspend fun resolve(username: String, knownVip: Boolean? = null): BlinkPublicPremiumIdentity {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isBlank()) return BlinkPublicPremiumIdentity(isVip = knownVip ?: false)

        val now = System.currentTimeMillis()
        values[key]
            ?.takeIf { now - it.checkedAt < TTL_MILLIS }
            ?.let { cached ->
                return if (knownVip == null) cached.identity
                else cached.identity.copy(isVip = cached.identity.isVip || knownVip)
            }

        val service = BlinkEconomyService()
        val response = service.publicPremiumStyleByUsername(key).getOrNull()
        if (response != null && response.optBoolean("found", false)) {
            val ids = response.optJSONArray("items")
            val candidates = buildList {
                if (ids != null) {
                    for (index in 0 until ids.length()) {
                        val catalogId = ids.optJSONObject(index)?.optString("catalog_id").orEmpty()
                        val item = BlinkStoreCatalog.items.firstOrNull { it.id == catalogId } ?: continue
                        add(item to item.premiumExperience())
                    }
                }
            }
            val strongest = candidates.maxByOrNull { it.second.priority }
            val identity = BlinkPublicPremiumIdentity(
                isVip = response.optBoolean("is_vip", false) || knownVip == true,
                catalogId = strongest?.first?.id,
                publicLabel = strongest?.second?.publicLabel,
                motion = strongest?.second?.motion ?: "premium_reveal"
            )
            values[key] = Entry(identity, now)
            return identity
        }

        // Rollout-safe fallback: the original VIP endpoint still works if the new
        // public-style migration has not reached an environment yet.
        val vip = knownVip ?: service.vipStatusByUsername(key)
            .getOrNull()
            ?.optBoolean("is_vip", false)
            ?: false
        return BlinkPublicPremiumIdentity(isVip = vip).also {
            values[key] = Entry(it, now)
        }
    }

    fun putVip(username: String, active: Boolean) {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isBlank()) return
        val current = values[key]?.identity ?: BlinkPublicPremiumIdentity()
        values[key] = Entry(current.copy(isVip = current.isVip || active), System.currentTimeMillis())
    }

    fun clear() = values.clear()
}

/** Call after any Store equip/activate action so returning to profile/feed shows it immediately. */
fun invalidateBlinkPublicPremiumIdentityCache() {
    BlinkPublicPremiumCache.clear()
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
                    listOf(Color(0xFF6D28D9), Color(0xFFDB2777), Color(0xFFF59E0B))
                ),
                shape = RoundedCornerShape(100.dp)
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .semantics { contentDescription = "Blink VIP" },
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

@Composable
private fun BlinkPremiumIdentityMark(
    catalogId: String,
    label: String,
    motion: String,
    modifier: Modifier = Modifier
) {
    var revealed by remember(catalogId) { mutableStateOf(false) }
    LaunchedEffect(catalogId) { revealed = true }

    val scale by animateFloatAsState(
        targetValue = if (revealed) 1f else 0.78f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "blinkPremiumIdentityScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "blinkPremiumIdentityAlpha"
    )

    val colors = when {
        catalogId == "creator_badge" -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
        catalogId.contains("spotlight") -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
        catalogId.contains("ring") || catalogId.contains("frame") -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
        catalogId.contains("glow") || catalogId.contains("name") -> listOf(Color(0xFF7C3AED), Color(0xFFEC4899))
        catalogId.contains("theme") || catalogId.contains("background") -> listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))
        else -> listOf(Color(0xFF7C3AED), Color(0xFFDB2777))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(Brush.horizontalGradient(colors), RoundedCornerShape(100.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .semantics {
                contentDescription = "Blink premium $label effect, $motion"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✦ ${label.take(11)}",
            color = Color.White,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.15.sp,
            maxLines = 1
        )
    }
}

/**
 * Drop-in premium identity renderer for surfaces that only have a username.
 *
 * Every existing caller keeps its old API, but now it can show both VIP and the user's
 * strongest equipped/active public Blink Store cosmetic. The short cache prevents feed,
 * comments and profile scrolling from turning into repeated RPC traffic.
 */
@Composable
fun BlinkVipMarkForUsername(
    username: String,
    knownVip: Boolean? = null,
    modifier: Modifier = Modifier
) {
    var identity by remember(username, knownVip) {
        mutableStateOf(BlinkPublicPremiumIdentity(isVip = knownVip ?: false))
    }

    LaunchedEffect(username, knownVip) {
        if (knownVip == true) BlinkPublicPremiumCache.putVip(username, true)
        if (username.isNotBlank()) {
            identity = BlinkPublicPremiumCache.resolve(username, knownVip)
        }
    }

    if (!identity.isVip && identity.catalogId == null) return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BlinkVipMark(isVip = identity.isVip)
        val catalogId = identity.catalogId
        val label = identity.publicLabel
        if (!catalogId.isNullOrBlank() && !label.isNullOrBlank()) {
            if (identity.isVip) Spacer(Modifier.width(3.dp))
            BlinkPremiumIdentityMark(
                catalogId = catalogId,
                label = label,
                motion = identity.motion
            )
        }
    }
}
