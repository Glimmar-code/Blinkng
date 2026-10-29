package com.blinkng.desktop.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.desktop.media.*
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

private data class TrimClip(val file: File, val start: String = "0", val end: String = "")

@Composable
internal fun DesktopVideoEditorDialog(file: File, onDismiss: () -> Unit, onApply: (File) -> Unit) {
    val scope = rememberCoroutineScope()
    var clips by remember(file) { mutableStateOf(listOf(TrimClip(file))) }
    var aspect by remember { mutableStateOf("Original") }
    var speed by remember { mutableStateOf(1.0) }
    var filter by remember { mutableStateOf("Original") }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1f) }
    var volume by remember { mutableStateOf(1f) }
    var music by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    var sticker by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }
    var completed by remember { mutableStateOf<File?>(null) }
    var handedOff by remember { mutableStateOf(false) }
    LaunchedEffect(clips, aspect, speed, filter, brightness, contrast, volume, music, text, sticker) {
        completed?.delete(); completed = null
    }
    DisposableEffect(Unit) { onDispose { if (!handedOff) completed?.delete() } }
    fun preview(path: File) {
        runCatching { Desktop.getDesktop().open(path) }.onFailure { error = "No video player is available for preview on this device." }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 850.dp).fillMaxWidth().heightIn(max = 900.dp)) {
            Column(Modifier.padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Back") }
                    Text("Edit Reel", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { completed?.let { handedOff = true; onApply(it) } }, enabled = completed != null && !busy) { Text("Use video") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Clips · in playback order", style = MaterialTheme.typography.titleMedium)
                clips.forEachIndexed { index, clip ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${index + 1}. ${clip.file.name}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(clip.start, { value -> clips = clips.mapIndexed { i, old -> if (i == index) old.copy(start = value) else old } }, enabled = !busy,
                                label = { Text("Start (seconds)") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(clip.end, { value -> clips = clips.mapIndexed { i, old -> if (i == index) old.copy(end = value) else old } }, enabled = !busy,
                                label = { Text("End (blank = full)") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Row {
                            TextButton(onClick = { preview(clip.file) }, enabled = !busy) { Text("Play original") }
                            TextButton(onClick = {
                                val next = clips.toMutableList(); next.removeAt(index); next.add(index - 1, clip); clips = next
                            }, enabled = !busy && index > 0) { Text("Move earlier") }
                            TextButton(onClick = { clips = clips.filterIndexed { i, _ -> i != index } }, enabled = !busy && clips.size > 1) { Text("Remove") }
                        }
                    }
                }
                OutlinedButton(onClick = { scope.launch {
                    val picked = withContext(Dispatchers.IO) { chooseEditorFiles(false) }
                    clips = (clips + picked.map { TrimClip(it) }).distinctBy { it.file.absolutePath }.take(4)
                } }, enabled = !busy && clips.size < 4) { Text("Add clips (${clips.size}/4)") }
                Text("Frame · original aspect is preserved with padding")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Original", "Portrait", "Square", "Wide").forEach { value -> FilterChip(aspect == value, { aspect = value }, enabled = !busy, label = { Text(value) }) }
                }
                Text("Speed")
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(.5, 1.0, 1.5, 2.0).forEach { value -> FilterChip(speed == value, { speed = value }, enabled = !busy, label = { Text("${value}x") }) }
                }
                Text("Filter")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Original", "Mono", "Warm", "Cool").forEach { value -> FilterChip(filter == value, { filter = value }, enabled = !busy, label = { Text(value) }) }
                }
                Text("Brightness"); Slider(brightness, { brightness = it }, enabled = !busy, valueRange = -.5f.. .5f)
                Text("Contrast"); Slider(contrast, { contrast = it }, enabled = !busy, valueRange = .5f..1.5f)
                Text("Original audio volume"); Slider(volume, { volume = it }, enabled = !busy)
                Row {
                    TextButton(onClick = { scope.launch { music = withContext(Dispatchers.IO) { chooseEditorFiles(true).firstOrNull() } } }, enabled = !busy) { Text("Add your audio") }
                    if (music != null) TextButton(onClick = { music = null }, enabled = !busy) { Text("Remove audio") }
                }
                music?.let { Text(it.name) }
                Text("Use audio you have permission to share. No music library is bundled.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it.take(160) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), label = { Text("Text overlay") })
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("", "★", "♥", "✓", "!").forEach { value -> FilterChip(sticker == value, { sticker = value }, enabled = !busy, label = { Text(value.ifBlank { "No sticker" }) }) }
                }
                if (busy) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("Exporting ${(progress * 100).toInt()}%")
                    OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel export") }
                } else Button(onClick = {
                    completed?.delete(); completed = null
                    error = null; progress = 0f; busy = true
                    job = scope.launch {
                        var produced: File? = null
                        try {
                            val selected = clips.map { clip ->
                                VideoClip(clip.file, clip.start.toDoubleOrNull() ?: error("Enter a valid start time."),
                                    if (clip.end.isBlank()) null else clip.end.toDoubleOrNull() ?: error("Enter a valid end time."))
                            }
                            val edit = VideoEdit(selected, aspect, speed, filter, brightness.toDouble(), contrast.toDouble(), volume.toDouble(), music, text, sticker)
                            val context = currentCoroutineContext()
                            val ready = withContext(Dispatchers.IO) {
                                val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "BLINK/edited-videos")
                                DesktopVideoEditor.export(edit, directory, cancel = { context.ensureActive() }, progress = { progress = it.toFloat() }).also { produced = it }
                            }
                            completed = ready
                        } catch (cancelled: CancellationException) { produced?.delete(); error = "Export cancelled. Your clips are unchanged."; throw cancelled }
                        catch (failure: Exception) { produced?.delete(); error = failure.message ?: "Export failed. Retry with your source clips." }
                        finally { busy = false }
                    }
                }) { Text(if (completed == null) "Export video" else "Export again") }
                completed?.let { video -> OutlinedButton(onClick = { preview(video) }) { Text("Play edited preview") } }
                Text("MP4 export · H.264/AAC · at most 3 minutes. Original framing keeps up to 1920 pixels on the long edge; Portrait uses 720×1280, Square 1080×1080 and Wide 1280×720. Sources are never overwritten.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun chooseEditorFiles(audio: Boolean): List<File> {
    var selected = emptyList<File>()
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser().apply {
            isMultiSelectionEnabled = !audio
            fileFilter = if (audio) FileNameExtensionFilter("Your audio", "mp3", "wav", "m4a") else FileNameExtensionFilter("Video clips", "mp4", "m4v", "mov", "webm")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) selected = (if (audio) listOf(chooser.selectedFile) else chooser.selectedFiles.toList()).filter { it.isFile }
    }
    return selected
}
