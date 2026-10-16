package com.example.game

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** For website APKs: no native second install, and system-managed background download. */
@Composable
fun BlinkLifeWebDownloadCard() {
    val context = LocalContext.current
    var progress by remember(context) { mutableStateOf(BlinkLifeWebPack.snapshot(context)) }
    var localError by remember { mutableStateOf("") }

    LaunchedEffect(context) {
        while (isActive) {
            val next = withContext(Dispatchers.IO) { BlinkLifeWebPack.snapshot(context) }
            if (next.phase == BlinkLifeWebPack.Phase.VERIFYING) {
                progress = next
                val installed = withContext(Dispatchers.IO) { BlinkLifeWebPack.verifyAndInstall(context) }
                progress = withContext(Dispatchers.IO) { BlinkLifeWebPack.snapshot(context) }
                localError = if (installed) "" else "Game files failed verification. Please retry."
            } else progress = next
            delay(1100)
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("BLINK LIFE 3D", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
            Text(
                "Download the optional game once. You can leave BLINK during the download. " +
                    "The game plays inside BLINK without installing another app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            when (progress.phase) {
                BlinkLifeWebPack.Phase.INSTALLED -> {
                    Text("Game Storage · Ready to play", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { context.startActivity(Intent(context, BlinkLifeWebActivity::class.java)) }) {
                            Text("Play BLINK LIFE")
                        }
                        OutlinedButton(onClick = {
                            BlinkLifeWebPack.remove(context)
                            progress = BlinkLifeWebPack.snapshot(context)
                        }) { Text("Remove files") }
                    }
                }
                BlinkLifeWebPack.Phase.DOWNLOADING, BlinkLifeWebPack.Phase.VERIFYING -> {
                    if (progress.total > 0 && progress.phase == BlinkLifeWebPack.Phase.DOWNLOADING) {
                        val ratio = (progress.downloaded.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Downloading ${(ratio * 100).toInt()}% · " +
                                Formatter.formatShortFileSize(context, progress.downloaded) + " / " +
                                Formatter.formatShortFileSize(context, progress.total),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            if (progress.phase == BlinkLifeWebPack.Phase.VERIFYING) "Checking game files…"
                            else "Waiting for transfer…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (progress.phase == BlinkLifeWebPack.Phase.DOWNLOADING) {
                        OutlinedButton(onClick = {
                            BlinkLifeWebPack.cancel(context)
                            progress = BlinkLifeWebPack.snapshot(context)
                        }) { Text("Cancel download") }
                    }
                }
                BlinkLifeWebPack.Phase.UNAVAILABLE -> Text(
                    progress.message,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {
                    Text("Game Storage · Not downloaded", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        localError = ""
                        runCatching { BlinkLifeWebPack.download(context) }
                            .onFailure { localError = "Download couldn't start. Check network and storage." }
                        progress = BlinkLifeWebPack.snapshot(context)
                    }) { Text(if (progress.phase == BlinkLifeWebPack.Phase.FAILED) "Retry download" else "Download game") }
                }
            }
            if (progress.message.isNotBlank() &&
                progress.phase !in setOf(BlinkLifeWebPack.Phase.UNAVAILABLE, BlinkLifeWebPack.Phase.INSTALLED)
            ) Text(progress.message, style = MaterialTheme.typography.bodySmall)
            if (localError.isNotBlank()) Text(
                localError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Android handles background transfer and notifications. " +
                    "Available offline after the verified files are prepared; WebGL 2 support is required.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
