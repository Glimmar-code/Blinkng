package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
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
import com.example.data.supabase.BlinkDropDiscovery
import java.text.NumberFormat
import java.util.Locale

/**
 * Feed discovery module for creators the viewer did not follow when a Drop started.
 * It is a separate feed row and never reorders the ranked organic posts around it.
 */
@Composable
fun BlinkDropDiscoveryCard(
    item: BlinkDropDiscovery,
    onFollow: () -> Unit,
    onOpenDrops: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = NumberFormat.getIntegerInstance(Locale.US)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f)
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row {
                Icon(
                    Icons.Default.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "You missed a BLINK Drop",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "@${item.creatorUsername} gave followers a chance to earn BLINK Coins.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                "${formatter.format(item.totalCoins)} coins · ${formatter.format(item.rewardPerUser)} each · ${item.winnerCount} rewards",
                fontWeight = FontWeight.Bold,
            )
            Text(
                "You weren't following this creator when the Drop started. Follow now to be eligible for future Drops.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFollow) {
                    Text("Follow")
                }
                OutlinedButton(onClick = onOpenDrops) {
                    Text("View Drops")
                }
            }
        }
    }
}
