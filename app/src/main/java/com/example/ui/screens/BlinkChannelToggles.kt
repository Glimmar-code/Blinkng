package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastChannel
import com.blinkng.shared.broadcast.BroadcastRules

@Composable
fun BlinkChannelToggles(
    channels: Set<BroadcastChannel>,
    rules: BroadcastRules,
    onChannels: (Set<BroadcastChannel>) -> Unit,
    onRules: (BroadcastRules) -> Unit,
) {
    val values = listOf(
        BroadcastChannel.IN_APP to R.string.broadcast_channels_in_app,
        BroadcastChannel.PUSH to R.string.broadcast_channels_push,
        BroadcastChannel.EMAIL to R.string.broadcast_channels_email,
        BroadcastChannel.WEBSITE to R.string.broadcast_channels_website,
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { (channel, label) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label), Modifier.padding(vertical = 12.dp))
                Switch(channel in channels, { onChannels(if (it) channels + channel else channels - channel) })
            }
        }
        if (BroadcastChannel.PUSH in channels) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.broadcast_respect_quiet_hours), Modifier.weight(1f).padding(vertical = 12.dp))
                Switch(rules.respectQuietHours, { onRules(rules.copy(respectQuietHours = it)) })
            }
            OutlinedTextField(
                value = rules.maxPushPerWeek.toString(),
                onValueChange = { onRules(rules.copy(maxPushPerWeek = it.toIntOrNull()?.coerceIn(1, 50) ?: rules.maxPushPerWeek)) },
                label = { Text(stringResource(R.string.broadcast_frequency_cap)) },
                supportingText = { Text(stringResource(R.string.broadcast_push_limit_help)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}
