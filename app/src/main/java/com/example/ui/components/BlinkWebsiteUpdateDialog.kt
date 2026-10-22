package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.update.BlinkReleaseInfo
import com.example.update.WebsiteApkDownloadCoordinator

/**
 * User-controlled website APK updates, distinct from Google Play updates.
 * Android's package installer remains the only installation authority.
 */
@Composable
fun BlinkWebsiteUpdateDialog(
    release: BlinkReleaseInfo,
    downloadState: WebsiteApkDownloadCoordinator.State,
    onUpdateNow: (Boolean) -> Unit,
    onInstall: () -> Unit,
    onPauseDownload: () -> Unit,
    onOpenWebsite: () -> Unit,
    onLater: () -> Unit,
) {
    val downloading = downloadState is WebsiteApkDownloadCoordinator.State.Downloading
    var unmeteredOnly by rememberSaveable { mutableStateOf(false) }
    // Outside taps/back gestures must not silently discard an update. Use the explicit
    // Later/Continue in background actions so users remain in control.
    Dialog(onDismissRequest = {}) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF101010),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("BLINK • UPDATE", color = Color(0xFFBBBBBB), fontWeight = FontWeight.Medium)
                Text(
                    "NEW UPDATE AVAILABLE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "Version ${release.versionName} is available from the official BLINK release.",
                    color = Color(0xFFE2E2E2),
                )

                if (!downloading && downloadState !is WebsiteApkDownloadCoordinator.State.Ready) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = unmeteredOnly, onCheckedChange = { unmeteredOnly = it })
                        Text("Unmetered network only (e.g. Wi-Fi)", color = Color.White,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                when (downloadState) {
                    WebsiteApkDownloadCoordinator.State.Idle -> {
                        Text(
                            "BLINK uses a smaller patch from your installed version when available, even if you missed several updates. Otherwise, it downloads the verified full APK.",
                            color = Color(0xFFBABABA),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = { onUpdateNow(unmeteredOnly) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) { Text("Download update") }
                    }
                    is WebsiteApkDownloadCoordinator.State.Downloading -> {
                        val done = downloadState.bytes / (1024.0 * 1024.0)
                        val total = downloadState.total
                        val progress = if (total > 0) {
                            ((downloadState.bytes * 100) / total).coerceIn(0, 100)
                        } else null
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        if (downloadState.waitingForNetwork) {
                            Text("Waiting for permitted network or retry…", color = Color(0xFFBBBBBB))
                        }
                        Text(
                            if (progress == null) "Downloading ${"%.1f".format(done)} MB…"
                            else "Downloading ${progress}% • ${"%.1f".format(done)} MB",
                            color = Color.White,
                        )
                        TextButton(onClick = onPauseDownload, modifier = Modifier.fillMaxWidth()) {
                            Text("Pause download", color = Color(0xFFE2E2E2))
                        }
                        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue in background", color = Color(0xFFE2E2E2))
                        }
                    }
                    is WebsiteApkDownloadCoordinator.State.Paused -> {
                        val downloadedMb = "%.1f".format(downloadState.bytes / (1024.0 * 1024.0))
                        Text("Paused • ${downloadedMb} MB saved. Resume without starting over when the server supports it.",
                            color = Color(0xFFE2E2E2))
                        Button(
                            onClick = { onUpdateNow(unmeteredOnly) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) { Text("Resume download") }
                    }
                    is WebsiteApkDownloadCoordinator.State.Ready -> {
                        Text(
                            "APK verified. Android will ask you to approve installation. If prompted, allow BLINK to install this one update in Settings.",
                            color = Color(0xFFBDE7C4),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onInstall,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) { Text("Install verified update") }
                    }
                    is WebsiteApkDownloadCoordinator.State.Failed -> {
                        Text(downloadState.message, color = Color(0xFFFFC1C1))
                        Button(
                            onClick = { onUpdateNow(unmeteredOnly) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        ) { Text("Retry download") }
                        TextButton(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth()) {
                            Text("Open official website", color = Color(0xFFE2E2E2))
                        }
                    }
                }
                if (!downloading) {
                    TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth()) {
                        Text("Remind me later", color = Color(0xFFDDDDDD))
                    }
                }
            }
        }
    }
}
