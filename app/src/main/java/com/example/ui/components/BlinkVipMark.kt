package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.models.BlinkStoreCatalog
import com.example.data.supabase.BlinkEconomyService
import com.example.premiumExperience
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class BlinkPublicPremiumIdentity(
    val isVip: Boolean = false,
    val catalogId: String? = null,
    val publicLabel: String? = null,
    val motion: String = "premium_reveal",
    val collectionCount: Int = 0,
    val collectionLevel: Int = 0
)

private object BlinkPublicPremiumCache {
    private data class Entry(val identity: BlinkPublicPremiumIdentity, val checkedAt: Long)
    private const val TTL_MILLIS = 5 * 60 * 1000L
    private val values = ConcurrentHashMap<String, Entry>()

    suspend fun resolve(username: String, knownVip: Boolean? = null): BlinkPublicPremiumIdentity {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isBlank()) return BlinkPublicPremiumIdentity(isVip = knownVip ?: false)
        val now = System.currentTimeMillis()
        values[key]?.takeIf { now - it.checkedAt < TTL_MILLIS }?.let { cached ->
            return if (knownVip == null) cached.identity
            else cached.identity.copy(isVip = cached.identity.isVip || knownVip)
        }

        val service = BlinkEconomyService()
        val response = service.publicPremiumStyleByUsername(key).getOrNull()
        if (response != null && response.optBoolean("found", false)) {
            val ids = response.optJSONArray("items")
            val candidates = buildList {
                if (ids != null) for (index in 0 until ids.length()) {
                    val catalogId = ids.optJSONObject(index)?.optString("catalog_id").orEmpty()
                    val item = BlinkStoreCatalog.items.firstOrNull { it.id == catalogId } ?: continue
                    add(item to item.premiumExperience())
                }
            }
            val strongest = candidates.maxByOrNull { it.second.priority }
            val identity = BlinkPublicPremiumIdentity(
                isVip = response.optBoolean("is_vip", false) || knownVip == true,
                catalogId = strongest?.first?.id,
                publicLabel = strongest?.second?.publicLabel,
                motion = strongest?.second?.motion ?: "premium_reveal",
                collectionCount = response.optInt("collection_count", 0).coerceAtLeast(0),
                collectionLevel = response.optInt("collection_level", 0).coerceAtLeast(0)
            )
            values[key] = Entry(identity, now)
            return identity
        }

        val vip = knownVip ?: service.vipStatusByUsername(key).getOrNull()?.optBoolean("is_vip", false) ?: false
        return BlinkPublicPremiumIdentity(isVip = vip).also { values[key] = Entry(it, now) }
    }

    fun putVip(username: String, active: Boolean) {
        val key = username.trim().removePrefix("@").lowercase(Locale.US)
        if (key.isBlank()) return
        val current = values[key]?.identity ?: BlinkPublicPremiumIdentity()
        values[key] = Entry(current.copy(isVip = current.isVip || active), System.currentTimeMillis())
    }

    fun clear() = values.clear()
}

/** Call after Store purchase/equip/activate actions so public identity and Collection refresh immediately. */
fun invalidateBlinkPublicPremiumIdentityCache() = BlinkPublicPremiumCache.clear()

@Composable
fun BlinkVipMark(isVip: Boolean, modifier: Modifier = Modifier, horizontalPadding: Dp = 5.dp) {
    if (!isVip) return
    Box(
        modifier = modifier
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF6D28D9), Color(0xFFDB2777), Color(0xFFF59E0B))),
                RoundedCornerShape(100.dp)
            )
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .semantics { contentDescription = "Blink VIP" },
        contentAlignment = Alignment.Center
    ) {
        Text("VIP", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.35.sp)
    }
}

@Composable
private fun BlinkPremiumIdentityMark(catalogId: String, label: String, motion: String) {
    var revealed by remember(catalogId) { mutableStateOf(false) }
    LaunchedEffect(catalogId) { revealed = true }
    val scale by animateFloatAsState(
        if (revealed) 1f else 0.78f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "blinkPremiumIdentityScale"
    )
    val alpha by animateFloatAsState(
        if (revealed) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "blinkPremiumIdentityAlpha"
    )
    val colors = when {
        catalogId == "creator_badge" || catalogId == "limited_edition_badge" -> listOf(Color(0xFFF59E0B), Color(0xFFEF4444))
        catalogId.contains("spotlight") -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
        catalogId.contains("ring") || catalogId.contains("frame") || catalogId == "avatar_decoration" -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED))
        catalogId.contains("glow") || catalogId.contains("name") -> listOf(Color(0xFF7C3AED), Color(0xFFEC4899))
        catalogId.contains("theme") || catalogId.contains("background") || catalogId == "profile_banner" -> listOf(Color(0xFF4F46E5), Color(0xFF06B6D4))
        else -> listOf(Color(0xFF7C3AED), Color(0xFFDB2777))
    }
    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .background(Brush.horizontalGradient(colors), RoundedCornerShape(100.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
            .semantics { contentDescription = "Blink premium $label effect, $motion" },
        contentAlignment = Alignment.Center
    ) {
        Text("✦ ${label.take(12)}", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun BlinkCollectionMark(count: Int, level: Int, onClick: () -> Unit) {
    if (count <= 0) return
    Surface(
        modifier = Modifier.clickable(onClick = onClick).semantics {
            contentDescription = "Blink Collection, $count items, level $level"
        },
        shape = RoundedCornerShape(100.dp),
        color = Color(0xFF111827),
        shadowElevation = 1.dp
    ) {
        Text("◆ $count", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), Color(0xFFFBBF24), 7.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun BlinkCollectionDialog(username: String, onDismiss: () -> Unit) {
    var snapshot by remember(username) { mutableStateOf<JSONObject?>(null) }
    var loading by remember(username) { mutableStateOf(true) }
    var error by remember(username) { mutableStateOf<String?>(null) }

    LaunchedEffect(username) {
        loading = true
        BlinkEconomyService().publicCollectionByUsername(username)
            .onSuccess { snapshot = it }
            .onFailure { error = it.message ?: "Unable to load this Blink Collection." }
        loading = false
    }

    val rows = snapshot?.optJSONArray("items")
    val count = snapshot?.optInt("collection_count", rows?.length() ?: 0) ?: 0
    val level = snapshot?.optInt("collection_level", 0) ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Blink Collection", fontWeight = FontWeight.Black)
                Text("@${username.trim().removePrefix("@")} • Level $level • $count items", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            when {
                loading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                rows == null || rows.length() == 0 -> Text("No public Blink Store items yet.")
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(
                        count = rows.length(),
                        key = { index -> rows.optJSONObject(index)?.optString("catalog_id").orEmpty() + index }
                    ) { index ->
                        val row = rows.optJSONObject(index) ?: return@items
                        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (row.optBoolean("limited_edition", false)) "◆" else "✦",
                                color = if (row.optBoolean("limited_edition", false)) Color(0xFFF59E0B) else Color(0xFF7C3AED),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(24.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(row.optString("name"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${row.optString("category")} • ${row.optString("state").lowercase().replaceFirstChar(Char::uppercase)}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (row.optBoolean("equipped", false)) Text("APPLIED", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                        }
                        if (index < rows.length() - 1) HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

/** Shows VIP, the strongest active/equipped premium effect, and a tappable public Blink Collection count. */
@Composable
fun BlinkVipMarkForUsername(username: String, knownVip: Boolean? = null, modifier: Modifier = Modifier) {
    var identity by remember(username, knownVip) { mutableStateOf(BlinkPublicPremiumIdentity(isVip = knownVip ?: false)) }
    var showCollection by remember(username) { mutableStateOf(false) }

    LaunchedEffect(username, knownVip) {
        if (knownVip == true) BlinkPublicPremiumCache.putVip(username, true)
        if (username.isNotBlank()) identity = BlinkPublicPremiumCache.resolve(username, knownVip)
    }

    if (!identity.isVip && identity.catalogId == null && identity.collectionCount <= 0) return

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BlinkVipMark(identity.isVip)
        val catalogId = identity.catalogId
        val label = identity.publicLabel
        if (!catalogId.isNullOrBlank() && !label.isNullOrBlank()) {
            if (identity.isVip) Spacer(Modifier.width(3.dp))
            BlinkPremiumIdentityMark(catalogId, label, identity.motion)
        }
        if (identity.collectionCount > 0) {
            if (identity.isVip || !catalogId.isNullOrBlank()) Spacer(Modifier.width(3.dp))
            BlinkCollectionMark(identity.collectionCount, identity.collectionLevel) { showCollection = true }
        }
    }

    if (showCollection) BlinkCollectionDialog(username) { showCollection = false }
}
