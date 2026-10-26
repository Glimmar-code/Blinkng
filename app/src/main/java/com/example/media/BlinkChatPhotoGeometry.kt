package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import com.example.data.supabase.BlinkChatImageOrientation
import com.blinkng.shared.BlinkChatAttachmentKind
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Non-destructive precision crop/straighten for still images. */
internal object BlinkChatPhotoGeometry {
    suspend fun edit(
        context: Context,
        source: Uri,
        aspectRatio: Float?,
        rotationDegrees: Float,
        cropScale: Float
    ): Uri = withContext(Dispatchers.IO) {
        require(aspectRatio == null || aspectRatio in .3f..3f)
        require(rotationDegrees in -45f..45f)
        require(cropScale in .45f..1f)
        val mime = if (source.scheme == "file") {
            when(File(source.path.orEmpty()).extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "heic" -> "image/heic"
                else -> ""
            }
        } else context.contentResolver.getType(source).orEmpty().lowercase()
        require(mime in setOf("image/jpeg","image/png","image/heic","image/heif")) {
            "This image cannot be edited without losing animation."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it,null,bounds)
        } ?: error("Unable to read picture.")
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000)
        var sample = 1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048) sample *= 2
        val decoded = context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it,null,
                BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unable to decode picture.")
        val oriented = try {
            BlinkChatImageOrientation.apply(context,source,decoded)
        } catch (err: Exception) { decoded.recycle(); throw err }
        if(oriented !== decoded) decoded.recycle()
        val output = File.createTempFile("blink-edited-", ".jpg",context.cacheDir)
        var transformed: Bitmap? = null
        var cropped: Bitmap? = null
        try {
            val rotated = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(oriented,0,0,oriented.width,oriented.height,matrix,true)
                    .also { transformed = it }
            } else oriented
            val sourceRatio = rotated.width.toFloat()/rotated.height
            val targetRatio = aspectRatio ?: sourceRatio
            var cropWidth = rotated.width
            var cropHeight = rotated.height
            if (sourceRatio > targetRatio) cropWidth =
                (rotated.height*targetRatio).toInt().coerceIn(1,rotated.width)
            else cropHeight = (rotated.width/targetRatio).toInt().coerceIn(1,rotated.height)
            cropWidth = (cropWidth*cropScale).toInt().coerceAtLeast(1)
            cropHeight = (cropHeight*cropScale).toInt().coerceAtLeast(1)
            cropped = Bitmap.createBitmap(rotated,
                (rotated.width-cropWidth)/2, (rotated.height-cropHeight)/2,
                cropWidth,cropHeight)
            val opaque=Bitmap.createBitmap(cropWidth,cropHeight,Bitmap.Config.RGB_565)
            try {
                Canvas(opaque).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(cropped,0f,0f,null)
                }
                output.outputStream().use {
                    check(opaque.compress(Bitmap.CompressFormat.JPEG,90,it))
                }
                BlinkChatAttachmentKind.IMAGE.verify("image/jpeg",output.length())
                Uri.fromFile(output)
            } finally { opaque.recycle() }
        } catch(err:Exception) {
            output.delete()
            throw err
        } finally {
            cropped?.takeUnless { it===oriented || it===transformed }?.recycle()
            transformed?.takeUnless { it===oriented }?.recycle()
            oriented.recycle()
        }
    }
}
