package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
    onUpdateNow: () -> Unit,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenWebsite: () -> Unit,
    onLater: () -> Unit,
) {
    val downloading = downloadState is WebsiteApkDownloadCoordinator.State.Downloading
    Dialog(onDismissRequest = { if (!downloading) onLater() }) {
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

                when (downloadState) {
                    WebsiteApkDownloadCoordinator.State.Idle -> {
                        Text(
                            "Download within BLINK. The APK will be checked before Android asks for installation approval.",
                            color = Color(0xFFBABABA),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(
                            onClick = onUpdateNow,
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
                        Text(
                            if (progress == null) "Downloading ${"%.1f".format(done)} MB…"
                            else "Downloading ${progress}% • ${"%.1f".format(done)} MB",
                            color = Color.White,
                        )
                        TextButton(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancel download", color = Color(0xFFE2E2E2))
                        }
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
                            onClick = onUpdateNow,
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
