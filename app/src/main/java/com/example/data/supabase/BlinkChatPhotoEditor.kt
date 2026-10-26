package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(
                    it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize }
                )
            } ?: throw IllegalArgumentException("This photo cannot be edited.")
            val photo = try {
                BlinkChatImageOrientation.apply(context, uri, decoded)
            } catch (failure: Exception) {
                decoded.recycle()
                throw failure
            }
            if (photo !== decoded) decoded.recycle()
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

    /**
     * Composable editor adjustment pipeline. All values are constrained and all
     * output is a new app-private cache copy; original device photos are untouched.
     */
    suspend fun adjust(
        context: Context,
        uri: Uri,
        exposure: Float,
        contrast: Float,
        saturation: Float
    ): Uri = withContext(Dispatchers.IO) {
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
            "Animation is not supported by the still-photo editor."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IllegalArgumentException("Photo unavailable.")
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        var sample = 1
        while (maxOf(bounds.outWidth,bounds.outHeight) / sample > 2048) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        } ?: throw IllegalArgumentException("Photo cannot be edited.")
        val original = try {
            BlinkChatImageOrientation.apply(context, uri, decoded)
        } catch (err: Exception) {
            decoded.recycle()
            throw err
        }
        if (original !== decoded) decoded.recycle()
        val output = File.createTempFile("blink-edited-", ".jpg", context.cacheDir)
        try {
            val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(result)
                canvas.drawColor(Color.WHITE)
                val c = contrast.coerceIn(.5f, 1.5f)
                val offset = (1f - c) * 128f + exposure.coerceIn(-1f, 1f) * 75f
                val colorTransform = ColorMatrix().apply {
                    setSaturation(saturation.coerceIn(0f, 2f))
                    postConcat(ColorMatrix(floatArrayOf(
                        c, 0f, 0f, 0f, offset,
                        0f, c, 0f, 0f, offset,
                        0f, 0f, c, 0f, offset,
                        0f, 0f, 0f, 1f, 0f
                    )))
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = ColorMatrixColorFilter(colorTransform)
                }
                canvas.drawBitmap(original, 0f, 0f, paint)
                FileOutputStream(output).use { stream ->
                    require(result.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                        "Failed to export picture."
                    }
                }
                BlinkChatAttachmentKind.IMAGE.verify("image/jpeg", output.length())
                Uri.fromFile(output)
            } finally { result.recycle() }
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { original.recycle() }
    }

}
