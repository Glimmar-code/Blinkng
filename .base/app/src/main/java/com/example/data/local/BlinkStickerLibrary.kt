package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import com.example.data.supabase.BlinkChatImageOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Local, reusable, explicitly-created stickers; no network sync, no public bucket.
 * Every account owns a separate private folder, preventing cross-account leaks.
 * View-once media is never passed to this library.
 */
object BlinkStickerLibrary {
    const val MAX_SAVED = 30

    private fun stickerFolder(context: Context, ownerId: String): File {
        val safe = UUID.fromString(ownerId).toString()
        return File(context.filesDir, "blink-stickers/$safe")
    }

    fun saved(context: Context, ownerId: String): List<File> {
        val folder = runCatching { stickerFolder(context, ownerId) }.getOrNull()
            ?: return emptyList()
        return folder.listFiles()?.asSequence()
            ?.filter { it.isFile && it.name.matches(Regex("[a-f0-9-]{36}\\.png")) &&
                    it.length() in 1..2_000_000 }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_SAVED)?.toList().orEmpty()
    }

    suspend fun create(context: Context, ownerId: String, source: Uri): File =
        withContext(Dispatchers.IO) {
            val folder = stickerFolder(context, ownerId)
            val mime = if (source.scheme == "file") {
                when (File(source.path.orEmpty()).extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "heic", "heif" -> "image/heic"
                    else -> ""
                }
            } else context.contentResolver.getType(source)?.lowercase().orEmpty()
            require(mime in setOf("image/png", "image/jpeg", "image/heic", "image/heif")) {
                "Choose a still PNG, JPEG, or HEIC picture."
            }
            val existing = saved(context, ownerId)
            require(existing.size < MAX_SAVED) { "Sticker collection is full (30). Remove one first." }
            val resolver = context.contentResolver
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw IllegalArgumentException("Picture is unavailable.")
            require(opts.outWidth in 1..20000 && opts.outHeight in 1..20000) {
                "Unsupported image dimensions."
            }
            var sample = 1
            while (maxOf(opts.outWidth, opts.outHeight) / sample > 640) sample *= 2
            val bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inSampleSize = sample
                })
            } ?: throw IllegalArgumentException("Unable to decode picture.")
            val upright = try { BlinkChatImageOrientation.apply(context, source, bitmap) }
                catch (failure: Exception) { bitmap.recycle(); throw failure }
            if (upright !== bitmap) bitmap.recycle()
            folder.mkdirs()
            val file = File(folder, "${UUID.randomUUID()}.png")
            try {
                val output = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(output)
                    canvas.drawColor(android.graphics.Color.TRANSPARENT,
                        android.graphics.PorterDuff.Mode.CLEAR)
                    val ratio = minOf(512f / upright.width, 512f / upright.height)
                    val w = ratio * upright.width
                    val h = ratio * upright.height
                    canvas.drawBitmap(upright, null,
                        android.graphics.RectF((512f-w)/2f,(512f-h)/2f,(512f+w)/2f,(512f+h)/2f),
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or
                            android.graphics.Paint.FILTER_BITMAP_FLAG))
                    FileOutputStream(file).use {
                        require(output.compress(Bitmap.CompressFormat.PNG, 100, it))
                    }
                    require(file.length() in 1..2_000_000) { "Sticker image too large." }
                } finally { output.recycle() }
                file
            } catch (failure: Exception) {
                file.delete()
                throw failure
            } finally { upright.recycle() }
        }

    suspend fun remove(context: Context, ownerId: String, sticker: File): Boolean =
        withContext(Dispatchers.IO) {
            val root = stickerFolder(context, ownerId).canonicalFile
            val file = sticker.canonicalFile
            file.parentFile == root &&
                file.name.matches(Regex("[a-f0-9-]{36}\\.png")) && file.delete()
        }
}
