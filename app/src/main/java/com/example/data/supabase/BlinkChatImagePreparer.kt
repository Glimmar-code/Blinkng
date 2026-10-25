package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatImageQuality
import java.io.File
import java.io.FileOutputStream

/**
 * Image quality adapter: original streams the selected source unchanged;
 * standard limits decoded dimensions before compressing a bounded JPEG.
 * Animated GIF/WebP remain original so they are not silently flattened.
 */
internal object BlinkChatImagePreparer {
    fun open(context: Context, uri: Uri, quality: BlinkChatImageQuality): BoundedMessageAttachment {
        val mime = if (uri.scheme == "file") {
            when (File(uri.path.orEmpty()).extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                else -> throw IllegalArgumentException("Unsupported queued picture format.")
            }
        } else context.contentResolver.getType(uri)?.lowercase().orEmpty()
        if (quality == BlinkChatImageQuality.ORIGINAL ||
            mime == "image/gif" || mime == "image/webp") {
            return BoundedMessageAttachment.open(context, uri, BlinkChatAttachmentKind.IMAGE)
        }
        BlinkChatAttachmentKind.IMAGE.verify(mime, 1L)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Cannot read picture.")
        require(options.outWidth > 0 && options.outHeight > 0) { "Unsupported picture." }
        var sample = 1
        while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) {
            sample *= 2
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not decode picture.")
        val image = try {
            BlinkChatImageOrientation.apply(context, uri, decoded)
        } catch (failure: Exception) {
            decoded.recycle()
            throw failure
        }
        if (image !== decoded) decoded.recycle()
        val temporary = File.createTempFile("blink-standard-", ".jpg", context.cacheDir)
        try {
            // JPEG has no alpha; white background avoids unintended black regions.
            val opaque = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.RGB_565)
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(image, 0f, 0f, null)
            }
            if (opaque !== image) image.recycle()
            try {
                FileOutputStream(temporary).use {
                    check(opaque.compress(Bitmap.CompressFormat.JPEG, 82, it)) {
                        "Could not prepare picture."
                    }
                }
            } finally {
                opaque.recycle()
            }
            BlinkChatAttachmentKind.IMAGE.verify("image/jpeg", temporary.length())
            return BoundedMessageAttachment(
                temporary, "image/jpeg", "Picture.jpg", temporary.length(), "jpg"
            )
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }
    /** Create a tiny independent JPEG preview; the original remains unmodified. */
    fun thumbnail(context: Context, staged: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(staged.path, bounds)
        if (bounds.outWidth !in 1..20000 || bounds.outHeight !in 1..20000) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 384) sample *= 2
        val bitmap = BitmapFactory.decodeFile(
            staged.path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val target = File.createTempFile("blink-preview-", ".jpg", context.cacheDir)
        return try {
            val scale = minOf(1f, 320f / maxOf(bitmap.width, bitmap.height))
            val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val downsized = Bitmap.createScaledBitmap(bitmap, width, height, true)
            val opaque = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            try {
                Canvas(opaque).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(downsized, 0f, 0f, null)
                }
                FileOutputStream(target).use {
                    check(opaque.compress(Bitmap.CompressFormat.JPEG, 65, it))
                }
                if (target.length() !in 1..180000) {
                    target.delete()
                    null
                } else target
            } finally {
                opaque.recycle()
                if (downsized !== bitmap) downsized.recycle()
                bitmap.recycle()
            }
        } catch (_: Exception) {
            bitmap.recycle()
            target.delete()
            null
        }
    }
}
