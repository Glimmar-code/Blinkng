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
        val mime = if (uri.scheme == "file") "image/jpeg" else
            context.contentResolver.getType(uri)?.lowercase().orEmpty()
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
        val image = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Could not decode picture.")
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
}
