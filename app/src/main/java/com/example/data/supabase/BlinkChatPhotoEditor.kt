package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
    enum class Edit { ROTATE_RIGHT, CENTER_SQUARE, CENTER_LANDSCAPE, CENTER_PORTRAIT, FLIP_HORIZONTAL }

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
                    Edit.CENTER_SQUARE -> cropCentered(photo, 1f)
                    Edit.CENTER_LANDSCAPE -> cropCentered(photo, 16f / 9f)
                    Edit.CENTER_PORTRAIT -> cropCentered(photo, 9f / 16f)
                    Edit.FLIP_HORIZONTAL -> {
                        val matrix = Matrix().apply { postScale(-1f, 1f) }
                        Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, matrix, true)
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


    data class Stroke(
        val points: List<Pair<Float, Float>>,
        val color: Int = Color.WHITE,
        val widthFraction: Float = .007f
    )
    data class Label(
        val text: String,
        val x: Float = .5f,
        val y: Float = .5f,
        val color: Int = Color.WHITE,
        val sizeFraction: Float = .07f
    )

    private fun cropCentered(photo: Bitmap, ratio: Float): Bitmap {
        require(ratio in .3f..3.0f)
        val imageRatio = photo.width.toFloat() / photo.height.toFloat()
        val w = if (imageRatio > ratio) (photo.height * ratio).toInt().coerceAtLeast(1)
                else photo.width
        val h = if (imageRatio > ratio) photo.height
                else (photo.width / ratio).toInt().coerceAtLeast(1)
        return Bitmap.createBitmap(
            photo, (photo.width - w) / 2, (photo.height - h) / 2, w, h
        )
    }

    /**
     * Flatten only user-created strokes, labels and stickers into a new cache
     * photo. These normalized coordinates are shared by all image entry points.
     */
    suspend fun overlay(
        context: Context,
        uri: Uri,
        strokes: List<Stroke>,
        labels: List<Label>
    ): Uri = withContext(Dispatchers.IO) {
        require(strokes.size <= 300 && labels.size <= 40)
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
            "This format cannot be edited without losing animation."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: throw IllegalArgumentException("Cannot read picture.")
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Cannot decode picture.")
        val photo = try { BlinkChatImageOrientation.apply(context,uri,decoded) } catch (e: Exception) {
            decoded.recycle(); throw e
        }
        if (photo !== decoded) decoded.recycle()
        val file = File.createTempFile("blink-edited-", ".jpg", context.cacheDir)
        try {
            val flattened = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(flattened)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(photo,0f,0f,null)
                strokes.forEach { stroke ->
                    val pts = stroke.points.take(1500)
                    if (pts.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(pts[0].first.coerceIn(0f,1f)*photo.width,
                                pts[0].second.coerceIn(0f,1f)*photo.height)
                            pts.drop(1).forEach { (x,y) ->
                                lineTo(x.coerceIn(0f,1f)*photo.width,y.coerceIn(0f,1f)*photo.height)
                            }
                        }
                        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = stroke.color
                            style = Paint.Style.STROKE
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            strokeWidth = photo.width * stroke.widthFraction.coerceIn(.002f,.04f)
                        })
                    }
                }
                labels.forEach { label ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                        color = label.color
                        textSize = photo.width * label.sizeFraction.coerceIn(.025f,.20f)
                        typeface = Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(3f,1f,2f,Color.BLACK)
                    }
                    canvas.drawText(label.text.take(80),
                        photo.width*label.x.coerceIn(.05f,.95f),
                        photo.height*label.y.coerceIn(.1f,.96f),paint)
                }
                FileOutputStream(file).use { stream ->
                    require(flattened.compress(Bitmap.CompressFormat.JPEG,90,stream))
                }
                BlinkChatAttachmentKind.IMAGE.verify("image/jpeg",file.length())
                Uri.fromFile(file)
            } finally { flattened.recycle() }
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally { photo.recycle() }
    }

}
