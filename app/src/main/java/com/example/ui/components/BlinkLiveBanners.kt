package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.live.BlinkLiveSnapshot

/** Non-blocking banners with per-content dismissal. No remote links or code. */
@Composable
fun BlinkLiveBanners(snapshot: BlinkLiveSnapshot, modifier: Modifier = Modifier) {
    val messages = listOfNotNull(
        snapshot.visibleAnnouncement?.let { "Announcement" to it },
        snapshot.visiblePromotion?.let { "BLINK" to it }
    )
    if (messages.isEmpty()) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((kind, message) in messages) {
            key(kind, message.id) {
                var dismissed by remember { mutableStateOf(false) }
                if (!dismissed) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 4.dp,
                        shadowElevation = 3.dp
                    ) {
                        Row(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kind + " • " + message.title, style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold)
                                Text(message.message, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { dismissed = true }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Dismiss " + kind)
                            }
                        }
                    }
                }
            }
        }
    }
}
