package com.blinkng.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blinkng.desktop.data.DesktopLiveRemoteConfig
import com.blinkng.shared.live.BlinkLiveSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Desktop parity: identical announcements, promotions and visibility rules. */
@Composable
fun DesktopLiveStrip() {
    var snapshot by remember { mutableStateOf(BlinkLiveSnapshot()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            DesktopLiveRemoteConfig.load()?.let { snapshot = it }
            delay(120_000L)
        }
    }
    val notices = listOfNotNull(
        snapshot.visibleAnnouncement?.let { "Announcement" to it },
        snapshot.visiblePromotion?.let { "BLINK" to it }
    )
    if (notices.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        notices.forEach { (kind, notice) ->
            key(kind, notice.id) {
                var dismissed by remember { mutableStateOf(false) }
                if (!dismissed) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kind + ": " + notice.title, fontWeight = FontWeight.SemiBold)
                                Text(notice.message, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { dismissed = true }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
