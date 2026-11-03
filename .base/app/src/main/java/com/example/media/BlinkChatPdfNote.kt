package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Creates a non-destructive PDF copy with a visible user-entered footer note.
 * Pages are re-rendered and flattened: this is not editable PDF form annotation
 * or OCR, and does not preserve vector objects, links or searchable text.
 */
internal object BlinkChatPdfNote {
    suspend fun createCopy(
        context: Context, original: Uri, note: String
    ): Uri = withContext(Dispatchers.IO) {
        val clean = note.trim().take(180)
        require(clean.isNotBlank()) { "Enter a note for your PDF." }
        val folder = File(context.cacheDir, "blink-share").apply {
            check(isDirectory || mkdirs()) { "Cannot prepare a private PDF copy." }
        }
        val output = File.createTempFile("blink-annotated-", ".pdf", folder)
        val document = PdfDocument()
        var complete = false
        try {
            context.contentResolver.openFileDescriptor(original, "r")?.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    require(renderer.pageCount in 1..10) {
                        "PDF notes support documents of 1 to 10 pages."
                    }
                    for (i in 0 until renderer.pageCount) {
                        currentCoroutineContext().ensureActive()
                        renderer.openPage(i).use { source ->
                            require(source.width in 1..12000 && source.height in 1..12000) {
                                "Invalid PDF page dimensions."
                            }
                            val scale = minOf(1f, 1300f / source.width, 1800f / source.height)
                            val width = (source.width * scale).toInt().coerceAtLeast(1)
                            val height = (source.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(
                                width, height, Bitmap.Config.RGB_565
                            )
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                source.render(
                                    bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                                )
                                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, i + 1)
                                    .create()
                                val page = document.startPage(pageInfo)
                                val canvas: Canvas = page.canvas
                                canvas.drawColor(Color.WHITE)
                                // Reserve 36pt at the bottom for the note.
                                val fit = minOf(555f / width, 754f / height)
                                val drawWidth = width * fit
                                val drawHeight = height * fit
                                canvas.drawBitmap(
                                    bitmap, null,
                                    RectF(
                                        (595f - drawWidth) / 2f,
                                        (792f - drawHeight) / 2f,
                                        (595f + drawWidth) / 2f,
                                        (792f + drawHeight) / 2f
                                    ),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                )
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.DKGRAY
                                    textSize = 10f
                                }
                                // Fit the note to the footer without invisible overflow.
                                val allowed = 520f
                                var clipped = clean
                                while (clipped.length > 1 &&
                                    paint.measureText(clipped) > allowed
                                ) clipped = clipped.dropLast(1)
                                canvas.drawText(clipped, 20f, 817f, paint)
                                document.finishPage(page)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } ?: error("Cannot read this PDF.")
            output.outputStream().use { document.writeTo(it) }
            com.blinkng.shared.BlinkChatAttachmentKind.DOCUMENT.verify(
                "application/pdf", output.length()
            )
            complete = true
            FileProvider.getUriForFile(
                context, "${context.packageName}.chatfiles", output
            )
        } finally {
            document.close()
            if (!complete) output.delete()
        }
    }
}
