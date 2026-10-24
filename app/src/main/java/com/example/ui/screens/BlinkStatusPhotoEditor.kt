package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import com.blinkng.shared.notification.BlinkStatusPhotoDecorPolicy
import android.media.ExifInterface
import android.net.Uri
import com.blinkng.shared.notification.BlinkStatusPhotoEditPolicy
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Produces a bounded, privately cached JPEG used for both preview and upload.
 * Rotation and 9:16 cropping use the SAME file, preventing preview/upload drift.
 * Original media is not edited or replaced.
 */
internal object BlinkStatusPhotoEditor {
    suspend fun edit(
        context: Context,
        source: Uri,
        quarterTurns: Int,
        portraitCrop: Boolean,
        filter: String = BlinkStatusPhotoDecorPolicy.ORIGINAL
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Bounds-only decoding intentionally returns null; check stream availability,
        // not the nullable Bitmap result.
        val boundsStream = resolver.openInputStream(source)
            ?: throw IllegalArgumentException("Couldn't open that photo.")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "This photo couldn't be decoded."
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = BlinkStatusPhotoEditPolicy.decodeSampleSize(
                bounds.outWidth, bounds.outHeight
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("This photo isn't supported.")

        val exif = runCatching {
            resolver.openInputStream(source)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        // Camera EXIF must be applied BEFORE the user's manual rotation.
        val exifTurns = when (exif) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 1
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 2
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 3
            else -> 0
        }
        val mirror = exif in listOf(
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )
        val degrees = BlinkStatusPhotoEditPolicy.quarterTurns(
            exifTurns + quarterTurns
        ) * 90f
        val matrix = Matrix().apply {
            if (mirror) postScale(-1f, 1f)
            if (degrees != 0f) postRotate(degrees)
        }
        val rotated = if (mirror || degrees != 0f) {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                matrix, true
            ).also { if (it !== decoded) decoded.recycle() }
        } else decoded

        val cropped = if (portraitCrop) {
            val rect = BlinkStatusPhotoEditPolicy.portraitCrop(
                rotated.width, rotated.height
            )
            Bitmap.createBitmap(
                rotated, rect.x, rect.y, rect.width, rect.height
            ).also { if (it !== rotated) rotated.recycle() }
        } else rotated

        // Flatten transparency onto white rather than converting it to black.
        val flattened = Bitmap.createBitmap(
            cropped.width, cropped.height, Bitmap.Config.RGB_565
        )
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        when (BlinkStatusPhotoDecorPolicy.filter(filter)) {
            BlinkStatusPhotoDecorPolicy.MONO -> {
                paint.colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply { setSaturation(0f) }
                )
            }
            BlinkStatusPhotoDecorPolicy.WARM -> {
                paint.colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        1.06f, 0f, 0f, 0f, 6f,
                        0f, 1.00f, 0f, 0f, 2f,
                        0f, 0f, .87f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            }
            BlinkStatusPhotoDecorPolicy.COOL -> {
                paint.colorFilter = ColorMatrixColorFilter(
                    ColorMatrix(floatArrayOf(
                        .91f, 0f, 0f, 0f, 0f,
                        0f, 1.00f, 0f, 0f, 2f,
                        0f, 0f, 1.10f, 0f, 4f,
                        0f, 0f, 0f, 1f, 0f
                    ))
                )
            }
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)
        cropped.recycle()

        val dir = File(context.cacheDir, "blink-status-edits").apply { mkdirs() }
        // Keep cache bounded; never touch source photo or files outside this folder.
        dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?.drop(12)?.forEach { it.delete() }
        val output = File(dir, "${UUID.randomUUID()}.jpg")
        try {
            FileOutputStream(output).use { stream ->
                check(flattened.compress(Bitmap.CompressFormat.JPEG, 87, stream)) {
                    "Photo compression failed."
                }
            }
            require(output.length() in 1..(15L * 1024 * 1024)) {
                "Edited photo is too large."
            }
            Uri.fromFile(output)
        } catch (failure: Exception) {
            output.delete()
            throw failure
        } finally {
            flattened.recycle()
        }
    }

    /**
     * Stamp one allowlisted emoji onto an already edited, app-private JPEG.
     * The preview shows the un-stamped image plus the same positioned Compose
     * overlay; Share only enables after this composited file exists.
     */
    suspend fun stamp(context: Context, baseUri: Uri, sticker: String, x: Float, y: Float): Uri =
        withContext(Dispatchers.IO) {
            val emoji = BlinkStatusPhotoDecorPolicy.sticker(sticker)
            require(emoji.isNotBlank()) { "Choose a supported sticker." }
            val folder = File(context.cacheDir, "blink-status-edits").canonicalFile
            val source = File(requireNotNull(baseUri.path)).canonicalFile
            require(source.parentFile == folder && source.extension == "jpg") {
                "Only locally edited photo files may be stamped."
            }
            val photo = BitmapFactory.decodeFile(source.absolutePath)
                ?: throw IllegalArgumentException("Edited photo cannot be opened.")
            val output = File(folder, "${UUID.randomUUID()}.jpg")
            val pos = BlinkStatusPhotoDecorPolicy.position(x, y)
            try {
                val image = photo.copy(Bitmap.Config.ARGB_8888, true)
                    ?: throw IllegalArgumentException("Can't prepare sticker canvas.")
                try {
                    val canvas = Canvas(image)
                    val brush = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        color = Color.WHITE
                        textAlign = Paint.Align.CENTER
                        textSize = image.width * 0.115f
                    }
                    val centerX = image.width.toFloat() * pos.x
                    val centerY = image.height.toFloat() * pos.y
                    // Baseline offset approximates visual centring across emoji fonts.
                    canvas.drawText(emoji, centerX, centerY + brush.textSize * .33f, brush)
                    FileOutputStream(output).use { stream ->
                        check(image.compress(Bitmap.CompressFormat.JPEG, 87, stream)) {
                            "Could not export sticker photo."
                        }
                    }
                } finally {
                    image.recycle()
                }
                require(output.length() in 1..(15L * 1024L * 1024L)) {
                    "Sticker image is too large."
                }
                Uri.fromFile(output)
            } catch (failure: Exception) {
                output.delete()
                throw failure
            } finally {
                photo.recycle()
            }
        }
}
