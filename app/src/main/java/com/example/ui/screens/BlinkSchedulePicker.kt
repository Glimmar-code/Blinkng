package com.example.ui.screens

import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.blinkng.shared.broadcast.BroadcastDraft
import java.time.Instant

@Composable
fun BlinkSchedulePicker(value: BroadcastDraft, onChange: (BroadcastDraft) -> Unit) {
    OutlinedTextField(
        value = value.startsAt.toString(),
        onValueChange = { input -> runCatching { Instant.parse(input) }.getOrNull()?.let { onChange(value.copy(startsAt = it)) } },
        label = { Text(stringResource(R.string.broadcast_start_time)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(
        value = value.endsAt.toString(),
        onValueChange = { input -> runCatching { Instant.parse(input) }.getOrNull()?.let { onChange(value.copy(endsAt = it)) } },
        label = { Text(stringResource(R.string.broadcast_end_time)) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
    )
    OutlinedTextField(value = value.timezone, onValueChange = { onChange(value.copy(timezone = it.take(64))) },
        label = { Text(stringResource(R.string.broadcast_timezone)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(value = value.priority.toString(),
        onValueChange = { onChange(value.copy(priority = it.toIntOrNull()?.coerceIn(0, 100) ?: value.priority)) },
        label = { Text(stringResource(R.string.broadcast_priority)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.broadcast_dismissible), Modifier.padding(vertical = 12.dp))
        Switch(value.dismissible, { onChange(value.copy(dismissible = it)) })
    }
}
