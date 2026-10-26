package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Converts 1..10 user-selected camera/scanned pages into an A4-shaped PDF.
 * Bounds decoded bitmaps and stores a private copy. Never changes gallery items.
 */
internal object BlinkChatDocumentScanner {
    suspend fun fromPhotos(context: Context, photos: List<Uri>): Uri =
        withContext(Dispatchers.IO) {
            require(photos.size in 1..10) { "Choose between 1 and 10 document pages." }
            val folder = File(context.cacheDir, "blink-share").apply { mkdirs() }
            val file = File.createTempFile("blink-scanned-", ".pdf", folder)
            val document = PdfDocument()
            try {
                photos.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, bounds)
                    } ?: error("Cannot read document page ${index+1}.")
                    require(bounds.outWidth in 1..14000 && bounds.outHeight in 1..14000) {
                        "Document page ${index+1} has unsupported dimensions."
                    }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1900) sample *= 2
                    val bitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null,
                            BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: error("Cannot decode document page ${index+1}.")
                    try {
                        // 595x842 points is the A4 page ratio; 20 point white margin.
                        val info = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                        val page = document.startPage(info)
                        val canvas: Canvas = page.canvas
                        canvas.drawColor(Color.WHITE)
                        val ratio = minOf(555f / bitmap.width, 802f / bitmap.height)
                        val width = bitmap.width * ratio
                        val height = bitmap.height * ratio
                        val boundsRect = RectF(
                            (595f - width) / 2,
                            (842f - height) / 2,
                            (595f + width) / 2,
                            (842f + height) / 2
                        )
                        canvas.drawBitmap(bitmap, null, boundsRect,
                            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                        document.finishPage(page)
                    } finally { bitmap.recycle() }
                }
                file.outputStream().use(document::writeTo)
                require(file.length() in 1..(40L * 1024 * 1024)) {
                    "Scanned PDF exceeds the upload limit."
                }
                FileProvider.getUriForFile(
                    context, "${context.packageName}.chatfiles", file
                )
            } catch (failure: Exception) {
                file.delete()
                throw failure
            } finally {
                document.close()
            }
        }
}
