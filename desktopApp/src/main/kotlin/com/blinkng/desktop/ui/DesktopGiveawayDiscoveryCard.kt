package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.util.Locale

private fun giveawayEndedLabel(endsAt: String): String {
    val endedAt = runCatching { Instant.parse(endsAt) }.getOrNull() ?: return "Giveaway ended"
    val seconds = Duration.between(endedAt, Instant.now()).seconds.coerceAtLeast(0)
    val age = when {
        seconds < 60 -> "less than a minute ago"
        seconds < 3_600 -> "${seconds / 60} minutes ago"
        seconds < 86_400 -> "${seconds / 3_600} hours ago"
        else -> "${seconds / 86_400} days ago"
    }
    return "Ended $age"
}

@Composable
internal fun DesktopGiveawayDiscoveryCard(
    item: JSONObject,
    followBusy: Boolean,
    onFollow: () -> Unit,
    onOpen: () -> Unit,
) {
    val active = item.optString("status").equals("ACTIVE", ignoreCase = true)
    val following = item.optBoolean("is_following")
    val username = item.optString("creator_username").ifBlank { "creator" }
    val amount = NumberFormat.getIntegerInstance(Locale.US).format(item.optLong("total_coins"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Rounded.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (active) "BLINK GIVEAWAY · LIVE" else "You missed this BLINK Giveaway",
                        fontWeight = FontWeight.Bold,
                    )
                    Text("@$username", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!active) Text(giveawayEndedLabel(item.optString("ends_at")), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("$amount BLINK Coins · ${item.optInt("reward_per_user")} each · ${item.optInt("winner_count")} winners",
                fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    active && following -> "You follow @$username. Join before this Giveaway ends."
                    active -> "Anyone in this Giveaway's audience can join. Follow to hear about the next one."
                    following -> "You follow @$username. Watch for the next Giveaway."
                    else -> "Follow @$username to be among the first notified next time."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!following) {
                    OutlinedButton(onClick = onFollow, enabled = !followBusy) {
                        Text(if (followBusy) "Following…" else "Follow")
                    }
                }
                Button(onClick = onOpen) {
                    Text(if (active) "View Giveaway" else "View Giveaway details")
                }
            }
        }
    }
}
