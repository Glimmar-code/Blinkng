package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
                            else "Downloading ${progress}% • ${"%.1f".format(done)} / ${"%.1f".format(total / (1024.0 * 1024.0))} MB",
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


/**
 * Draggable, compact continuation control for a user-approved background APK download.
 * Visible only while the user has explicitly closed the dialog; tapping restores it.
 * This is an in-app overlay, not a system overlay permission.
 */
@Composable
fun BlinkUpdateMiniProgress(
    downloadState: WebsiteApkDownloadCoordinator.State,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val size = 60.dp
        val sizePx = with(density) { size.toPx() }
        val maxX = (with(density) { maxWidth.toPx() } - sizePx).coerceAtLeast(0f)
        // This overlay is hosted in a measured bottom-aligned box.
        val maxY = (with(density) { maxHeight.toPx() } - sizePx).coerceAtLeast(0f)
        val initialX = with(density) { 14.dp.toPx() }.coerceAtMost(maxX)
        val initialY = (maxY - with(density) { 120.dp.toPx() }).coerceAtLeast(0f)
        var dragX by rememberSaveable { mutableStateOf(0f) }
        var dragY by rememberSaveable { mutableStateOf(0f) }
        val x = (initialX + dragX).coerceIn(0f, maxX)
        val y = (initialY + dragY).coerceIn(0f, maxY)
        val amount = (downloadState as? WebsiteApkDownloadCoordinator.State.Downloading)
        val percent = if (amount != null && amount.total > 0L) {
            (amount.bytes.toFloat() / amount.total).coerceIn(0f, 1f)
        } else null
        Surface(
            shape = CircleShape,
            color = Color(0xFF161616),
            shadowElevation = 8.dp,
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(size)
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        // Read the mutable offset each gesture event; pointerInput
                        // otherwise captures an outdated x/y during continuous dragging.
                        val currentX = (initialX + dragX).coerceIn(0f, maxX)
                        val currentY = (initialY + dragY).coerceIn(0f, maxY)
                        dragX = (currentX + delta.x).coerceIn(0f, maxX) - initialX
                        dragY = (currentY + delta.y).coerceIn(0f, maxY) - initialY
                    }
                }
                .clickable(onClick = onOpen),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (percent != null) {
                    CircularProgressIndicator(
                        progress = { percent },
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = Color.White,
                    )
                } else if (downloadState is WebsiteApkDownloadCoordinator.State.Downloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 4.dp,
                        color = Color.White,
                    )
                }
                Text(
                    when {
                        downloadState is WebsiteApkDownloadCoordinator.State.Ready -> "✓"
                        percent != null -> "${(percent * 100).roundToInt()}%"
                        downloadState is WebsiteApkDownloadCoordinator.State.Paused -> "Ⅱ"
                        else -> "↓"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}
