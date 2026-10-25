package com.example.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.blinkng.shared.live.BlinkLiveBannerImageLimits as Limits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Locally compresses eligible photos, never uploads originals bigger than 2 MB,
 * and relies on Supabase's owner-only RLS and MIME/size controls as the authority.
 */
object BlinkLiveBannerUploadService {
    private const val BUCKET = "blink-live-banners"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    private data class PreparedImage(val bytes: ByteArray, val mime: String) {
        val extension get() = if (mime == "image/png") "png" else "jpg"
    }

    private fun readBounded(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to read the selected photo.")
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16_384)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                require(total <= Limits.MAX_SOURCE_BYTES) {
                    "Choose a JPEG or PNG photo smaller than 20 MB."
                }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        }
    }

    private fun detectedMime(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xff.toByte() &&
            bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        ) -> "image/png"
        else -> error("Only JPEG and PNG photos are supported.")
    }

    private fun rotateExif(bitmap: Bitmap, original: ByteArray, mime: String): Bitmap {
        if (mime != "image/jpeg") return bitmap
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(original)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaled(bitmap: Bitmap, maximum: Int): Bitmap {
        val (width, height) = Limits.targetSize(bitmap.width, bitmap.height, maximum)
        return if (width == bitmap.width && height == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun prepare(context: Context, uri: Uri): PreparedImage {
        val original = readBounded(context, uri)
        val mime = detectedMime(original)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(original, 0, original.size, bounds)
        require(Limits.validateSource(bounds.outWidth, bounds.outHeight, original.size.toLong())) {
            "Choose a valid photo up to 20 MB and 100 megapixels."
        }
        // Keep already-small banners unchanged, including transparent PNGs.
        if (original.size <= Limits.MAX_UPLOAD_BYTES &&
            maxOf(bounds.outWidth, bounds.outHeight) <= Limits.MAX_OUTPUT_DIMENSION
        ) return PreparedImage(original, mime)

        val decoded = BitmapFactory.decodeByteArray(
            original, 0, original.size,
            BitmapFactory.Options().apply {
                inSampleSize = Limits.androidSampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = if (mime == "image/png") Bitmap.Config.ARGB_8888
                                    else Bitmap.Config.RGB_565
            }
        ) ?: error("This photo could not be decoded. Choose another JPEG or PNG.")

        val oriented = rotateExif(decoded, original, mime)
        if (oriented !== decoded) decoded.recycle()
        var bitmap = scaled(oriented, Limits.MAX_OUTPUT_DIMENSION)
        if (bitmap !== oriented) oriented.recycle()

        try {
            // Transparent PNG gets one lossless attempt; if it is too big,
            // flatten to white and export as JPEG to meet Storage's 2 MB limit.
            if (mime == "image/png") {
                val pngOutput = ByteArrayOutputStream()
                if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, pngOutput) &&
                    pngOutput.size() <= Limits.MAX_UPLOAD_BYTES
                ) return PreparedImage(pngOutput.toByteArray(), "image/png")
            }

            // JPEG cannot encode alpha; use a solid white background, not black.
            var quality = 86
            repeat(8) { attempt ->
                val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
                try {
                    val canvas = Canvas(opaque)
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    val out = ByteArrayOutputStream()
                    require(opaque.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                        "Photo compression failed."
                    }
                    if (out.size() <= Limits.MAX_UPLOAD_BYTES)
                        return PreparedImage(out.toByteArray(), "image/jpeg")
                } finally {
                    opaque.recycle()
                }
                if (quality > 55) quality -= 9
                else {
                    val smaller = scaled(bitmap, (maxOf(bitmap.width, bitmap.height) * 0.8).toInt()
                        .coerceAtLeast(320))
                    if (smaller !== bitmap) {
                        bitmap.recycle()
                        bitmap = smaller
                    }
                }
            }
            error("This image is too detailed to fit under 2 MB. Choose a smaller photo.")
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun upload(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SupabaseService.accessToken()?.takeIf(String::isNotBlank)
                ?: error("Please sign in again before uploading.")
            val image = prepare(context, uri)
            require(image.bytes.size <= Limits.MAX_UPLOAD_BYTES)
            val filePath = "banners/${UUID.randomUUID()}.${image.extension}"
            val base = SupabaseConfig.url.trimEnd('/')
            client.newCall(
                Request.Builder()
                    .url("$base/storage/v1/object/$BUCKET/$filePath")
                    .header("apikey", SupabaseConfig.anonKey)
                    .header("Authorization", "Bearer $token")
                    .header("x-upsert", "false")
                    .post(image.bytes.toRequestBody(image.mime.toMediaType()))
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    val raw = response.body?.string().orEmpty()
                    val reason = runCatching {
                        JSONObject(raw).optString("message").ifBlank {
                            JSONObject(raw).optString("error")
                        }
                    }.getOrDefault("")
                    error(reason.take(220).ifBlank {
                        "Banner upload failed (${response.code}); check your owner access."
                    })
                }
            }
            "$base/storage/v1/object/public/$BUCKET/$filePath"
        }
    }
}
