package com.blinkng.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.desktop.media.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File

/** Desktop adapter for the six reference editing tools; edits/export run away from the UI thread. */
@Composable
internal fun DesktopPhotoEditorDialog(file: File, onDismiss: () -> Unit, onApply: (File) -> Unit) {
    val scope = rememberCoroutineScope()
    var source by remember(file) { mutableStateOf<BufferedImage?>(null) }
    var history by remember(file) { mutableStateOf(listOf(PhotoEdit())) }
    var position by remember(file) { mutableStateOf(0) }
    val edit = history[position]
    var tool by remember { mutableStateOf("Crop") }
    var preview by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf("png") }
    var stroke by remember { mutableStateOf(emptyList<PhotoPoint>()) }
    fun change(next: PhotoEdit) {
        if (!exporting && next != edit) { history = (history.take(position + 1) + next).takeLast(50); position = history.lastIndex }
    }
    val currentEdit by rememberUpdatedState(edit)
    val applyEdit by rememberUpdatedState<(PhotoEdit) -> Unit>({ change(it) })
    LaunchedEffect(file) {
        try { source = withContext(Dispatchers.IO) { DesktopPhotoEditor.load(file) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Cannot open this photo." }
    }
    LaunchedEffect(source, edit) {
        val original = source ?: return@LaunchedEffect
        rendering = true
        try {
            delay(120)
            preview = withContext(Dispatchers.Default) {
                val scale = minOf(1.0, 900.0 / maxOf(original.width, original.height))
                val small = BufferedImage(maxOf(1, (original.width * scale).toInt()), maxOf(1, (original.height * scale).toInt()), BufferedImage.TYPE_INT_ARGB)
                small.createGraphics().let { g -> try { g.drawImage(original, 0, 0, small.width, small.height, null) } finally { g.dispose() } }
                val result = DesktopPhotoEditor.render(small, edit)
                try { result.toComposeImageBitmap() } finally { small.flush(); result.flush() }
            }
            failure = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Preview failed." }
        finally { rendering = false }
    }
    Dialog(onDismissRequest = { if (!exporting) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.widthIn(max = 800.dp).fillMaxWidth().heightIn(max = 850.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, enabled = !exporting) { Text("Back") }
                    Text("Edit Photo", style = MaterialTheme.typography.titleLarge)
                    Button(enabled = source != null && !exporting && !rendering, onClick = {
                        val original = source ?: return@Button
                        exporting = true
                        scope.launch {
                            var exported: File? = null
                            try {
                                val directory = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "BLINK/edited-photos")
                                val ready = withContext(Dispatchers.IO) {
                                    DesktopPhotoEditor.export(original, edit, directory, format).also { exported = it }
                                }
                                require(ready.length() <= 25L * 1024 * 1024) { "This export exceeds the 25 MB upload limit. Choose JPEG or a smaller crop." }
                                onApply(ready)
                            } catch (cancelled: CancellationException) { exported?.delete(); throw cancelled }
                            catch (error: Exception) { exported?.delete(); failure = error.message ?: "Export failed. Your original is unchanged." }
                            finally { exporting = false }
                        }
                    }) { Text(if (exporting) "Exporting…" else "Next") }
                }
                if (rendering || exporting || source == null && failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                preview?.let { image ->
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val imageHeight = maxWidth * image.height / image.width
                        Box(Modifier.fillMaxWidth().height(imageHeight)) {
                            Image(image, "Photo edit preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            if ((tool == "Text" || tool == "Stickers") && !exporting) {
                                Canvas(Modifier.fillMaxSize().pointerInput(tool) {
                                    var location = PhotoPoint(0f, 0f)
                                    detectDragGestures(onDragStart = {
                                        location = if (tool == "Text") currentEdit.textPosition else currentEdit.stickerPosition
                                    }) { gesture, delta ->
                                        gesture.consume()
                                        location = PhotoPoint((location.x + delta.x / size.width).coerceIn(0f, 1f),
                                            (location.y + delta.y / size.height).coerceIn(0f, 1f))
                                        applyEdit(if (tool == "Text") currentEdit.copy(textPosition = location) else currentEdit.copy(stickerPosition = location))
                                    }
                                }) {}
                            }
                            if (tool == "Doodle" && !exporting) Canvas(Modifier.fillMaxSize().pointerInput(edit, tool) {
                                detectDragGestures(onDragStart = { p -> stroke = listOf(PhotoPoint(p.x / size.width, p.y / size.height)) },
                                    onDrag = { change, _ -> change.consume(); stroke = stroke + PhotoPoint(change.position.x / size.width, change.position.y / size.height) },
                                    onDragEnd = { if (stroke.size > 1) change(edit.copy(strokes = edit.strokes + PhotoStroke(stroke))); stroke = emptyList() },
                                    onDragCancel = { stroke = emptyList() })
                            }) {
                                stroke.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFFFF3B57), androidx.compose.ui.geometry.Offset(a.x * size.width, a.y * size.height), androidx.compose.ui.geometry.Offset(b.x * size.width, b.y * size.height), strokeWidth = 4f) }
                            }
                        }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Crop", "Filter", "Adjust", "Text", "Stickers", "Doodle").forEach { name ->
                        FilterChip(tool == name, { tool = name }, enabled = !exporting, label = { Text(name) })
                    }
                }
                when (tool) {
                    "Crop" -> {
                        Text("Crop framing · rotate in 90° steps")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Original", "Square", "Portrait", "Wide").forEach { value -> FilterChip(edit.crop == value, { change(edit.copy(crop = value)) }, enabled = !exporting, label = { Text(value) }) }
                        }
                        if (edit.crop != "Original") {
                            Text("Horizontal framing")
                            Slider(edit.cropPosition.x, { change(edit.copy(cropPosition = edit.cropPosition.copy(x = it))) }, enabled = !exporting)
                            Text("Vertical framing")
                            Slider(edit.cropPosition.y, { change(edit.copy(cropPosition = edit.cropPosition.copy(y = it))) }, enabled = !exporting)
                        }
                        TextButton(onClick = { change(edit.copy(turns = (edit.turns + 1) % 4)) }, enabled = !exporting) { Text("Rotate clockwise") }
                    }
                    "Filter" -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Original", "Mono", "Warm", "Cool").forEach { value -> FilterChip(edit.filter == value, { change(edit.copy(filter = value)) }, enabled = !exporting, label = { Text(value) }) }
                    }
                    "Adjust" -> {
                        Text("Brightness")
                        Slider(edit.brightness, { change(edit.copy(brightness = it)) }, enabled = !exporting, valueRange = -.5f.. .5f)
                        Text("Contrast")
                        Slider(edit.contrast, { change(edit.copy(contrast = it)) }, enabled = !exporting, valueRange = .5f..1.5f)
                    }
                    "Text" -> {
                        OutlinedTextField(edit.text, { change(edit.copy(text = it.take(160))) }, enabled = !exporting, label = { Text("Caption overlay") }, modifier = Modifier.fillMaxWidth())
                        Text("Drag on the preview to position the text.")
                    }
                    "Stickers" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("", "★", "♥", "✓", "!").forEach { value -> FilterChip(edit.sticker == value, { change(edit.copy(sticker = value)) }, enabled = !exporting, label = { Text(value.ifBlank { "None" }) }) }
                    }
                    "Doodle" -> Text("Drag on the photo to draw. Undo removes the last stroke.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { position-- }, enabled = position > 0 && !exporting) { Text("Undo") }
                    OutlinedButton(onClick = { position++ }, enabled = position < history.lastIndex && !exporting) { Text("Redo") }
                    TextButton(onClick = { change(PhotoEdit()) }, enabled = !exporting) { Text("Reset") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(format == "png", { format = "png" }, enabled = !exporting, label = { Text("Lossless PNG") })
                    FilterChip(format == "jpg", { format = "jpg" }, enabled = !exporting, label = { Text("JPEG · 95%") })
                }
                Text("Export keeps the edited photo's full resolution in a separate file. JPEG uses 95% quality and a white background for transparency. Your original stays unchanged.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
