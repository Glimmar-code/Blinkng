package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class DocKind(val label: String, val color: Color) {
    Pdf("PDF", Color(0xFFE5484D)),
    Word("DOC", Color(0xFF2B6CD9)),
    Excel("XLS", Color(0xFF1F9D55)),
    Powerpoint("PPT", Color(0xFFE8742A)),
    Text("TXT", Color(0xFF7A7A82)),
    Archive("ZIP", Color(0xFFB58B2A)),
    Audio("AUD", Color(0xFF8B5CF6)),
    Other("FILE", Color(0xFF5B6270)),
}

sealed interface DocTransfer {
    data object Idle : DocTransfer
    data class Downloading(val progress: Float) : DocTransfer
    data object Done : DocTransfer
    data object Failed : DocTransfer
}

/** Pure helpers (unit-testable): file kind, size text and subtitle. */
object BlinkDocumentPresentation {
    fun kindFor(name: String, mime: String? = null): DocKind {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            ext == "pdf" || mime == "application/pdf" -> DocKind.Pdf
            ext in setOf("doc", "docx", "odt", "rtf") -> DocKind.Word
            ext in setOf("xls", "xlsx", "csv", "ods") -> DocKind.Excel
            ext in setOf("ppt", "pptx", "odp") -> DocKind.Powerpoint
            ext in setOf("txt", "md", "log") || mime?.startsWith("text/") == true -> DocKind.Text
            ext in setOf("zip", "rar", "7z", "tar", "gz") -> DocKind.Archive
            ext in setOf("mp3", "m4a", "wav", "ogg", "aac", "flac") || mime?.startsWith("audio/") == true -> DocKind.Audio
            else -> DocKind.Other
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }

    fun subtitle(name: String, sizeBytes: Long, mime: String? = null): String {
        val kind = kindFor(name, mime)
        val size = formatSize(sizeBytes)
        return if (size.isEmpty()) kind.label else "$size · ${kind.label}"
    }
}

@Composable
private fun TransferRing(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350, easing = FastOutSlowInEasing), label = "docRing")
    Canvas(modifier) {
        val s = 2.5.dp.toPx()
        val a = Size(size.width - s, size.height - s)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, Offset(s / 2, s / 2), a, style = Stroke(s))
        drawArc(color, -90f, 360f * p, false, Offset(s / 2, s / 2), a, style = Stroke(s, cap = StrokeCap.Round))
    }
}

/** Document card: file-type tile, name, "2.4 MB · PDF", and an animated download / progress / open button. */
@Composable
fun BlinkDocumentCard(
    name: String,
    sizeBytes: Long,
    transfer: DocTransfer,
    fg: Color,
    meta: Color,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    mime: String? = null,
) {
    val kind = BlinkDocumentPresentation.kindFor(name, mime)
    Row(modifier.width(250.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(width = 40.dp, height = 46.dp).clip(RoundedCornerShape(7.dp)).background(kind.color),
            contentAlignment = Alignment.Center,
        ) { Text(kind.label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(BlinkDocumentPresentation.subtitle(name, sizeBytes, mime), color = meta, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = transfer::class, label = "docAction",
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .border(1.5.dp, fg.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        when (transfer) {
                            DocTransfer.Idle, DocTransfer.Failed -> onDownload()
                            is DocTransfer.Downloading -> onCancel()
                            DocTransfer.Done -> onOpen()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when (transfer) {
                    DocTransfer.Idle -> Icon(Icons.Filled.Download, "Download", tint = fg, modifier = Modifier.size(20.dp))
                    is DocTransfer.Downloading -> {
                        TransferRing(transfer.progress, BlinkChatColors.Accent, Modifier.size(36.dp))
                        Icon(Icons.Filled.Close, "Cancel", tint = fg, modifier = Modifier.size(16.dp))
                    }
                    DocTransfer.Done -> Icon(Icons.Filled.FileOpen, "Open", tint = fg, modifier = Modifier.size(20.dp))
                    DocTransfer.Failed -> Icon(Icons.Filled.Refresh, "Retry", tint = BlinkChatColors.Danger, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
