package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.shared.notification.BlinkAnnouncementPreview

/**
 * Local, read-only snapshot. Each entry remains available with airplane mode enabled.
 * Does not present old cached entries as freshly received notifications.
 */
@Composable
fun BlinkOfflineAnnouncementsPanel(
    announcements: List<BlinkAnnouncementPreview>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Saved BLINK Updates", fontWeight = FontWeight.Bold)
                Text(
                    "Recent announcements saved on this device. Connect to the internet to refresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (announcements.isEmpty()) {
            item {
                Text(
                    "No announcements saved yet. New BLINK updates will appear here after your phone receives them online.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(announcements, key = { it.id }) { entry ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(entry.title, fontWeight = FontWeight.SemiBold)
                        if (entry.message.isNotBlank()) {
                            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (entry.publishedAt.isNotBlank()) {
                            Text(
                                entry.publishedAt.replace('T', ' ').take(16),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
