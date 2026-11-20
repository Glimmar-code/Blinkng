package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Explicit, user-initiated system download of an HTTPS media link. Never begins
 * network transfer on composition or on entering the chat. All progress comes
 * from DownloadManager, not a fabricated animation.
 */
@Composable
internal fun BlinkChatDownloadAction(
    url: String?,
    kind: String,
    extension: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    menuLabel: String? = null
) {
    val context = LocalContext.current
    val downloader = remember(context) {
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }
    val valid = remember(url) {
        runCatching {
            val parsed = Uri.parse(url)
            parsed.scheme == "https" && !parsed.host.isNullOrBlank() &&
                parsed.userInfo == null
        }.getOrDefault(false)
    }
    var transferId by remember(url) { mutableStateOf<Long?>(null) }
    var state by remember(url) { mutableStateOf("") }
    var progress by remember(url) { mutableIntStateOf(-1) }

    LaunchedEffect(transferId) {
        val id = transferId ?: return@LaunchedEffect
        while (transferId == id) {
            val query = DownloadManager.Query().setFilterById(id)
            val result = runCatching {
                downloader.query(query).use { cursor ->
                    if (!cursor.moveToFirst()) return@use "Download unavailable" to -1
                    val current = cursor.getInt(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val done = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val ratio = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1
                    when (current) {
                        DownloadManager.STATUS_SUCCESSFUL -> "Saved to Downloads" to 100
                        DownloadManager.STATUS_FAILED -> "Download failed" to -1
                        DownloadManager.STATUS_PAUSED -> "Download paused" to ratio
                        else -> "Downloading" to ratio
                    }
                }
            }.getOrDefault("Download unavailable" to -1)
            state = result.first
            progress = result.second
            if (state == "Saved to Downloads" || state == "Download failed" ||
                state == "Download unavailable") break
            delay(650)
        }
    }
    val performDownload: () -> Unit = {
                val id = transferId
                if (id != null && state in setOf("Downloading", "Download paused")) {
                    runCatching { downloader.remove(id) }
                    transferId = null
                    state = "Cancelled"
                    progress = -1
                } else {
                    runCatching {
                        val uri = Uri.parse(requireNotNull(url))
                        val safeExtension = extension.lowercase()
                            .filter { it in 'a'..'z' || it in '0'..'9' }
                            .take(8).ifBlank { "bin" }
                        val title = "BLINK-${kind.filter { it.isLetterOrDigit() }.take(16)}-" +
                            System.currentTimeMillis() + "." + safeExtension
                        val request = DownloadManager.Request(uri)
                            .setTitle("BLINK Chat ${kind.take(30)}")
                            .setDescription("Download requested from your private chat")
                            .setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title)
                        downloader.enqueue(request)
                    }.onSuccess { next ->
                        transferId = next
                        state = "Downloading"
                        progress = -1
                    }.onFailure {
                        state = "Download failed"
                    }
                }
    }
    Row(
        modifier
            .then(if (menuLabel != null) Modifier.fillMaxWidth().clickable(
                enabled = valid && state != "Saved to Downloads",
                onClick = performDownload
            ) else Modifier)
            .testTag(if (menuLabel != null) "chat_voice_download_menu" else "chat_media_download"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = (if (compact && kind == "document")
                Modifier.size(32.dp)
                    .border(1.dp, Color(0xFF87909D), CircleShape)
            else if (compact) Modifier.size(30.dp)
            else Modifier)
                .testTag(if (compact && kind == "document")
                    "chat_reference_document_download_circle"
                    else "chat_reference_media_download_button"),
            enabled = valid && state != "Saved to Downloads",
            onClick = performDownload
        ) {
            val isDownloading = transferId != null &&
                state in setOf("Downloading", "Download paused")
            val animatedProgress by animateFloatAsState(
                targetValue = (progress.coerceIn(0, 100) / 100f),
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                label = "chatDownloadProgress"
            )
            Box(
                modifier = Modifier.size(if (compact) 22.dp else 26.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading && progress >= 0) {
                    Canvas(Modifier.size(if (compact) 22.dp else 26.dp)) {
                        val stroke = 2.dp.toPx()
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        val origin = Offset(stroke / 2f, stroke / 2f)
                        drawArc(
                            color = Color(0xFF69B9FF).copy(alpha = 0.22f),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = origin, size = arcSize, style = Stroke(stroke)
                        )
                        drawArc(
                            color = Color(0xFF69B9FF),
                            startAngle = -90f, sweepAngle = 360f * animatedProgress,
                            useCenter = false, topLeft = origin, size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Icon(
                    when {
                        isDownloading -> Icons.Default.Close
                        state == "Saved to Downloads" -> Icons.Default.CheckCircle
                        state in setOf("Download failed", "Download unavailable") -> Icons.Default.Refresh
                        else -> Icons.Default.Download
                    },
                    contentDescription = when (state) {
                        "Downloading", "Download paused" -> "Cancel ${kind} download"
                        "Saved to Downloads" -> "${kind} saved in Downloads"
                        "Download failed", "Download unavailable" -> "Retry ${kind} download"
                        else -> "Download ${kind}"
                    },
                    tint = if (state in setOf("Download failed", "Download unavailable"))
                        Color(0xFFFF8A80) else Color(0xFF69B9FF),
                    modifier = Modifier.size(if (compact) 18.dp else 20.dp)
                )
            }
        }
        if (menuLabel != null) {
            Text(
                menuLabel,
                color = Color(0xFFE6E9EF),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                    .weight(1f),
                maxLines = 1
            )
        }
        if (state.isNotBlank()) {
            Text(
                if (state == "Downloading" && progress >= 0) "${progress}%" else state,
                fontSize = 10.sp, color = Color(0xFFB7C7DA),
                maxLines = 1
            )
        }
    }
}
