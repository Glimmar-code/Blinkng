package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Decode-time EXIF correction for camera/gallery JPEG and HEIC images.
 * Apply before crop or JPEG resampling; do not rewrite the selected original.
 * All input bitmaps are already dimension-bounded by the callers.
 */
internal object BlinkChatImageOrientation {
    fun apply(context: Context, uri: Uri, image: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val transform = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                transform.postRotate(90f)
                transform.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                transform.postRotate(270f)
                transform.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
            else -> return image
        }
        return Bitmap.createBitmap(
            image, 0, 0, image.width, image.height, transform, true
        )
    }
}
