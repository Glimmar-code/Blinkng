package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private data class BlinkPdfPage(val bitmap: Bitmap, val count: Int)
private val chatPdfClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS).build()

/** Explicit private PDF preview: bounded HTTPS download, one page at a time. */
@Composable
internal fun BlinkChatPdfViewer(url: String, filename: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var localFile by remember(url) { mutableStateOf<File?>(null) }
    var page by remember(url) { mutableIntStateOf(0) }
    var rendered by remember(url) { mutableStateOf<BlinkPdfPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var busy by remember(url) { mutableStateOf(true) }
    DisposableEffect(url) { onDispose { localFile?.delete() } }
    LaunchedEffect(url) {
        busy = true
        try {
            localFile = withContext(Dispatchers.IO) {
                val uri = Uri.parse(url)
                require(uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                    uri.userInfo == null) { "Unsafe document link." }
                val tmp = File.createTempFile("chat-private-", ".pdf",
                    File(context.cacheDir, "blink-pdf").apply { mkdirs() })
                try {
                    chatPdfClient.newCall(Request.Builder().url(url).get().build())
                        .execute().use { response ->
                        require(response.isSuccessful) { "Document unavailable." }
                        val body = response.body ?: kotlin.error("Document unavailable.")
                        require(body.contentLength() <= 16_777_216L) { "PDF exceeds preview limit." }
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                val buffer = ByteArray(16384)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= 16_777_216L) { "PDF exceeds preview limit." }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    require(tmp.length() > 4) { "PDF is empty." }
                    tmp
                } catch (e: Exception) { tmp.delete(); throw e }
            }
        } catch (e: Exception) { error = e.message ?: "Unable to load PDF." }
        busy = false
    }
    LaunchedEffect(localFile, page) {
        val target = localFile ?: return@LaunchedEffect
        busy = true
        try {
            rendered = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)
                    .use { descriptor ->
                        PdfRenderer(descriptor).use { pdf ->
                            require(pdf.pageCount > 0) { "PDF has no pages." }
                            pdf.openPage(page.coerceIn(0, pdf.pageCount - 1)).use { p ->
                                val scale = minOf(1.7f, 1400f / maxOf(p.width, p.height))
                                val bitmap = Bitmap.createBitmap(
                                    (p.width * scale).toInt().coerceIn(1, 1400),
                                    (p.height * scale).toInt().coerceIn(1, 1400),
                                    Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(AndroidColor.WHITE)
                                p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                BlinkPdfPage(bitmap, pdf.pageCount)
                            }
                        }
                    }
            }
            error = null
        } catch (e: Exception) { error = e.message ?: "Cannot display this PDF." }
        busy = false
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF060D16))
            .testTag("chat_reference_document_viewer")) {
            Row(Modifier.fillMaxWidth().background(Color(0xFF09131E)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Close document", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(filename.take(90).ifBlank { "PDF" }, color = Color.White, fontSize = 15.sp)
                    Text("Page " + (page + 1) + " / " + (rendered?.count ?: "…"),
                        color = Color(0xFFB7C7DA), fontSize = 11.sp)
                }
                BlinkChatDownloadAction(url, "document", "pdf")
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(color = Color(0xFF1685FF))
                else if (error != null) Text(error.orEmpty(), color = Color.White)
                else rendered?.let { image ->
                    Image(image.bitmap.asImageBitmap(), "PDF page " + (page + 1),
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentScale = ContentScale.Fit)
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(enabled = !busy && page > 0,
                    onClick = { page-- }) { Text("Previous") }
                OutlinedButton(enabled = !busy && page + 1 < (rendered?.count ?: 0),
                    onClick = { page++ }) { Text("Next") }
            }
        }
    }
}
