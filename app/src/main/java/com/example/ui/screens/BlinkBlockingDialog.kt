package com.example.ui.screens

import com.example.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blinkng.shared.broadcast.Broadcast

@Composable
fun BlinkBlockingDialog(
    broadcast: Broadcast,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
) {
    BackHandler(enabled = true) {}
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(Modifier.fillMaxWidth(.92f), shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.broadcast_blocking_title), style = MaterialTheme.typography.headlineSmall)
                BlinkBroadcastTypeBadge(broadcast.type, official = true)
                Text(broadcast.title, style = MaterialTheme.typography.titleLarge)
                Text(broadcast.body, style = MaterialTheme.typography.bodyLarge)
                BlinkBroadcastPrimaryButton(
                    stringResource(if (broadcast.deepLink.startsWith("market://") ||
                        broadcast.deepLink.contains("play.google.com")) R.string.broadcast_update
                    else R.string.broadcast_retry_maintenance),
                    Modifier.fillMaxWidth(),
                ) {
                    if (broadcast.deepLink.startsWith("market://") || broadcast.deepLink.contains("play.google.com")) onUpdate()
                    else onRetry()
                }
            }
        }
    }
}
