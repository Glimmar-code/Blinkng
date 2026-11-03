package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.example.media.BlinkChatAudioTrim
import com.example.media.BlinkChatPdfNote

private data class PreviewDocument(val title: String, val size: Long, val mime: String)

/**
 * PDF/audio review BEFORE upload. Uses a single scaled PDF page, not full-document
 * bitmap allocation. Other supported document formats remain ordinary file cards.
 */
@Composable
internal fun BlinkChatAttachmentReviewDialog(
    uri: Uri,
    isAudio: Boolean,
    onSend: (Uri) -> Unit,
    onOpenBlinkAi: (Uri) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var info by remember(uri) { mutableStateOf(PreviewDocument("Attachment", 0L, "")) }
    var pdfPage by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var workingUri by remember(uri) { mutableStateOf(uri) }
    var pdfNote by remember(uri) { mutableStateOf("") }
    var pdfNoteApplied by remember(uri) { mutableStateOf(false) }
    var exportingPdf by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                var name = "Attachment"
                var bytes = 0L
                context.contentResolver.query(uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),null,null,null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val s = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (n >= 0) name = cursor.getString(n) ?: name
                        if (s >= 0 && !cursor.isNull(s)) bytes = cursor.getLong(s)
                    }
                }
                val preview = if (mime == "application/pdf") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                        PdfRenderer(fd).use { renderer ->
                            if (renderer.pageCount == 0) null else renderer.openPage(0).use { page ->
                                val scale = minOf(1f, 900f / page.width.coerceAtLeast(1))
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(android.graphics.Color.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                } else null
                PreviewDocument(name, bytes, mime) to preview
            }.onSuccess { (metadata, bitmap) ->
                info = metadata
                pdfPage = bitmap
            }.onFailure { error = it.message ?: "Preview unavailable" }
        }
    }
    val audioPlayer = remember(uri, isAudio) {
        if (isAudio) ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        } else null
    }
    DisposableEffect(audioPlayer) {
        onDispose { audioPlayer?.release() }
    }
    var duration by remember(uri) { mutableLongStateOf(0L) }
    var position by remember(uri) { mutableLongStateOf(0L) }
    var playing by remember(uri) { mutableStateOf(false) }
    var trimStart by remember(uri) { mutableLongStateOf(0L) }
    var trimEnd by remember(uri) { mutableLongStateOf(0L) }
    var exportingAudio by remember(uri) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(duration) {
        if (trimEnd <= 0L && duration > 0L) trimEnd = duration
    }
    LaunchedEffect(audioPlayer) {
        if (audioPlayer != null) while (true) {
            duration = audioPlayer.duration.coerceAtLeast(0L)
            position = audioPlayer.currentPosition.coerceAtLeast(0L)
            playing = audioPlayer.isPlaying
            delay(250)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(Modifier.fillMaxSize().background(Color(0xFF090D12))
            .statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                Text(if (isAudio) "AUDIO PREVIEW" else "DOCUMENT PREVIEW",
                    color = Color.White, modifier = Modifier.weight(1f))
                Button(
                    enabled = !exportingAudio && !exportingPdf &&
                        (isAudio || info.mime != "application/pdf" || pdfNote.isBlank() ||
                            pdfNoteApplied),
                    onClick = {
                        if (isAudio && duration > 500L &&
                            (trimStart > 0L || trimEnd < duration - 100L)) {
                            exportingAudio = true
                            audioPlayer?.pause()
                            scope.launch {
                                runCatching {
                                    BlinkChatAudioTrim.export(
                                        context, uri, trimStart, trimEnd
                                    )
                                }.onSuccess(onSend).onFailure {
                                    error = it.message ?: "Could not trim this audio."
                                }
                                exportingAudio = false
                            }
                        } else onSend(workingUri)
                    }
                ) { Text(if (exportingAudio || exportingPdf) "Exporting" else "Send") }
            }
            Column(Modifier.fillMaxWidth().weight(1f)
                .verticalScroll(rememberScrollState()).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (isAudio) Icons.Default.MusicNote else Icons.Default.Description,
                    null, tint = Color(0xFF70BAFF),
                    modifier = Modifier.size(50.dp))
                Spacer(Modifier.height(10.dp))
                Text(info.title, color = Color.White, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        info.mime.takeIf { it.isNotBlank() },
                        info.size.takeIf { it > 0 }?.let { "${it / 1024} KB" }
                    ).joinToString(" • "),
                    color = Color.LightGray
                )
                error?.let { Text(it, color = Color(0xFFFF8A80)) }
                if (isAudio && !exportingAudio) {
                    OutlinedButton(onClick = { onOpenBlinkAi(uri) }) {
                        Text("Ask BLINK AI about audio")
                    }
                }
                if (isAudio && audioPlayer != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (audioPlayer.isPlaying) audioPlayer.pause() else audioPlayer.play()
                        }) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play or pause audio", tint = Color.White)
                        }
                        Slider(
                            value = if (duration > 0) (position.toFloat() / duration)
                                .coerceIn(0f, 1f) else 0f,
                            onValueChange = {
                                if (duration > 0) audioPlayer.seekTo((duration * it).toLong())
                            }, modifier = Modifier.weight(1f)
                        )
                    }
                    if (duration > 500L) {
                        Text("Trim recording", color = Color.White)
                        Text("Start ${trimStart / 1000}s", color = Color.LightGray)
                        Slider(
                            value = trimStart.toFloat() / duration,
                            onValueChange = {
                                trimStart = (it * duration).toLong()
                                    .coerceAtMost((trimEnd - 500L).coerceAtLeast(0))
                                audioPlayer.seekTo(trimStart)
                            },
                            enabled = !exportingAudio
                        )
                        Text("End ${trimEnd / 1000}s", color = Color.LightGray)
                        Slider(
                            value = trimEnd.toFloat() / duration,
                            onValueChange = {
                                trimEnd = (it * duration).toLong()
                                    .coerceIn(trimStart + 500L, duration)
                                audioPlayer.seekTo((trimEnd - 200L).coerceAtLeast(0L))
                            },
                            enabled = !exportingAudio
                        )
                        Text("AAC/M4A clips can be trimmed without recompression. Other audio formats may be sent unedited.",
                            color = Color.LightGray)
                    }
                }
                pdfPage?.let { bitmap ->
                    Spacer(Modifier.height(18.dp))
                    Image(bitmap.asImageBitmap(), "First PDF page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp))
                    Text("Preview of page 1", color = Color.LightGray)
                }
                if (!isAudio && info.mime == "application/pdf") {
                    Spacer(Modifier.height(14.dp))
                    Text("Add a note to each PDF page", color = Color.White)
                    OutlinedTextField(
                        value = pdfNote,
                        onValueChange = {
                            pdfNote = it.take(180)
                            pdfNoteApplied = false
                            workingUri = uri
                        },
                        label = { Text("Footer note (optional)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Notes are added to a separate flattened PDF copy. Original text, links and form fields are not preserved in the annotated copy.",
                        color = Color.LightGray
                    )
                    if (pdfNoteApplied) Text(
                        "Annotated PDF copy prepared. Tap Send to share it.",
                        color = Color(0xFF76D9B2)
                    )
                    OutlinedButton(
                        enabled = !exportingPdf && pdfNote.isNotBlank() && !pdfNoteApplied,
                        onClick = {
                            exportingPdf = true
                            error = null
                            scope.launch {
                                runCatching {
                                    BlinkChatPdfNote.createCopy(context, uri, pdfNote)
                                }.onSuccess {
                                    workingUri = it
                                    pdfNoteApplied = true
                                }.onFailure {
                                    error = it.message ?: "Could not prepare annotated PDF."
                                }
                                exportingPdf = false
                            }
                        }
                    ) { Text(if (exportingPdf) "Preparing PDF…" else "Apply note to copy") }
                }
                if (!isAudio && pdfPage == null) {
                    Spacer(Modifier.height(18.dp))
                    Text("Review the filename and format before sending.",
                        color = Color.LightGray)
                }
            }
        }
    }
}
