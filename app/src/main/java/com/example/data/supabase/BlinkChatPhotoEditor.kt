package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import com.blinkng.shared.BlinkChatAttachmentKind
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Non-destructive photo editing of selected JPEG/PNG/HEIC files.
 * Writes only to Blink's private cache; the original file remains intact.
 * Animation formats are intentionally refused so no frames are silently lost.
 */
internal object BlinkChatPhotoEditor {
    enum class Edit { ROTATE_RIGHT, CENTER_SQUARE }

    suspend fun edit(context: Context, uri: Uri, operation: Edit): Uri =
        withContext(Dispatchers.IO) {
            val mime = if (uri.scheme == "file") {
                when (File(uri.path.orEmpty()).extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "heic" -> "image/heic"
                    "heif" -> "image/heif"
                    else -> ""
                }
            } else context.contentResolver.getType(uri)?.lowercase().orEmpty()
            require(mime in setOf("image/jpeg", "image/png", "image/heic", "image/heif")) {
                "Editing this format could remove animation. Use the original."
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: throw IllegalArgumentException("Photo cannot be read.")
            require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000) {
                "Unsupported photo dimensions."
            }
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 2048) {
                sampleSize *= 2
            }
            val photo = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            } ?: throw IllegalArgumentException("This photo cannot be edited.")
            val result = try {
                when (operation) {
                    Edit.ROTATE_RIGHT -> {
                        val matrix = Matrix().apply { postRotate(90f) }
                        Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, matrix, true)
                    }
                    Edit.CENTER_SQUARE -> {
                        val side = minOf(photo.width, photo.height)
                        Bitmap.createBitmap(
                            photo, (photo.width - side) / 2, (photo.height - side) / 2, side, side
                        )
                    }
                }
            } catch (error: Exception) {
                photo.recycle()
                throw error
            }
            val output = File.createTempFile("blink-edited-", ".jpg", context.cacheDir)
            try {
                val opaque = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.RGB_565)
                try {
                    Canvas(opaque).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(result, 0f, 0f, null)
                    }
                    FileOutputStream(output).use {
                        require(opaque.compress(Bitmap.CompressFormat.JPEG, 90, it)) {
                            "Could not export edited picture."
                        }
                    }
                    BlinkChatAttachmentKind.IMAGE.verify("image/jpeg", output.length())
                    Uri.fromFile(output)
                } finally { opaque.recycle() }
            } catch (error: Exception) {
                output.delete()
                throw error
            } finally {
                if (result !== photo) result.recycle()
                photo.recycle()
            }
        }
}
