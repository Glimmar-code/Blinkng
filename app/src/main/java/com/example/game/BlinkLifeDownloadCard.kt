package com.example.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private fun findActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> findActivity(context.baseContext)
    else -> null
}

/** BLINK LIFE is visible to everyone, but the large game downloads only on demand. */
@Composable
fun BlinkLifeDownloadCard() {
    val context = LocalContext.current
    val activity = remember(context) { findActivity(context) }
    val delivery = remember(context.applicationContext) {
        BlinkLifeFeatureDelivery(context.applicationContext)
    }
    val state = delivery.snapshot
    var confirmRemoval by remember { mutableStateOf(false) }

    DisposableEffect(delivery, activity) {
        delivery.onReady = { BlinkLifeLaunchGate.play(context) }
        delivery.start(activity)
        onDispose {
            delivery.onReady = null
            delivery.stop()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("BLINK LIFE 3D", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(
                "Optional 3D neighbourhood preview. Download it only when you want to play. " +
                    "It stays on your phone until removed. Multiplayer requires an online server.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            when (state.phase) {
                BlinkLifeFeatureDelivery.Phase.INSTALLED -> {
                    Text("Game Storage · Installed", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { BlinkLifeLaunchGate.play(context) }) {
                            Text("Play BLINK LIFE 3D")
                        }
                        OutlinedButton(onClick = { confirmRemoval = true }) {
                            Text("Remove files")
                        }
                    }
                }
                BlinkLifeFeatureDelivery.Phase.REQUESTING,
                BlinkLifeFeatureDelivery.Phase.DOWNLOADING,
                BlinkLifeFeatureDelivery.Phase.INSTALLING -> {
                    val fraction = if (state.total > 0L) {
                        (state.downloaded.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                    } else null
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Downloading " + (fraction * 100).toInt() + "% · " +
                                Formatter.formatShortFileSize(context, state.downloaded) + " / " +
                                Formatter.formatShortFileSize(context, state.total),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            if (state.phase == BlinkLifeFeatureDelivery.Phase.INSTALLING)
                                "Installing game files…" else "Preparing game download…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (state.phase != BlinkLifeFeatureDelivery.Phase.INSTALLING) {
                        TextButton(onClick = delivery::cancel) { Text("Cancel download") }
                    }
                }
                BlinkLifeFeatureDelivery.Phase.REMOVING -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    Text(
                        "Game Storage · Not downloaded",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = delivery::downloadAndPlay) {
                        Text(if (state.phase == BlinkLifeFeatureDelivery.Phase.FAILED)
                            "Retry download" else "Download & Play")
                    }
                }
            }
            if (state.message.isNotBlank()) {
                Text(
                    state.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                "Free game download. Google Play handles the game package; " +
                    "the normal BLINK app remains installed. Website APK users need " +
                    "the Play version for this download.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }

    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove BLINK LIFE files?") },
            text = {
                Text("Google Play will remove the optional game files. " +
                    "Your normal BLINK posts and messages stay untouched. " +
                    "You can download the game again later.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoval = false
                    delivery.removeGameFiles()
                }) { Text("Remove game") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text("Keep game") }
            },
        )
    }
}
