package com.example.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    compact: Boolean = false
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
    Row(modifier.testTag("chat_media_download"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            modifier = if (compact) Modifier.size(30.dp) else Modifier,
            enabled = valid && state != "Saved to Downloads",
            onClick = {
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
        ) {
            Icon(
                if (transferId != null && state in setOf("Downloading", "Download paused"))
                    Icons.Default.Close
                else if (state == "Saved to Downloads") Icons.Default.CheckCircle
                else Icons.Default.Download,
                contentDescription = when (state) {
                    "Downloading", "Download paused" -> "Cancel ${kind} download"
                    "Saved to Downloads" -> "${kind} saved in Downloads"
                    else -> "Download ${kind}"
                },
                tint = Color(0xFF69B9FF),
                modifier = Modifier.size(if (compact) 18.dp else 20.dp)
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
