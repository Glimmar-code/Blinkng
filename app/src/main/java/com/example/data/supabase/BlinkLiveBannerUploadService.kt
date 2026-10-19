package com.example.data.supabase

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Owner-only, image-only, immutable public artwork. The database RLS is authoritative. */
object BlinkLiveBannerUploadService {
    private const val BUCKET = "blink-live-banners"
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .build()

    suspend fun upload(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val token = SupabaseService.accessToken()
                ?.takeIf { it.isNotBlank() }
                ?: error("Please sign in again before uploading.")

            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Cannot access selected photo.")
            val bytes = stream.use { input ->
                val buffer = ByteArrayOutputStream()
                val part = ByteArray(16_384)
                var total = 0
                while (true) {
                    val n = input.read(part)
                    if (n < 0) break
                    total += n
                    require(total <= MAX_BYTES) { "Choose a JPG or PNG smaller than 2 MB." }
                    buffer.write(part, 0, n)
                }
                buffer.toByteArray()
            }
            require(bytes.isNotEmpty()) { "The selected image is empty." }
            val mime = when {
                bytes.size > 4 &&
                    bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
                    bytes[2] == 0xFF.toByte() -> "image/jpeg"
                bytes.size > 8 &&
                    bytes.copyOfRange(0, 8).contentEquals(
                        byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
                    ) -> "image/png"
                else -> error("Only JPEG and PNG images can be uploaded.")
            }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            require(opts.outWidth in 64..6000 && opts.outHeight in 64..6000 &&
                    opts.outWidth.toLong() * opts.outHeight.toLong() <= 20_000_000L) {
                "Select an image between 64 and 6000 pixels per side (maximum 20 megapixels)."
            }
            val extension = if (mime == "image/jpeg") "jpg" else "png"
            val filePath = "banners/${UUID.randomUUID()}.$extension"
            val base = SupabaseConfig.url.trimEnd('/')
            val request = Request.Builder()
                .url("$base/storage/v1/object/$BUCKET/$filePath")
                .header("apikey", SupabaseConfig.anonKey)
                .header("Authorization", "Bearer $token")
                .header("x-upsert", "false")
                .post(bytes.toRequestBody(mime.toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string().orEmpty()
                    val reason = runCatching {
                        JSONObject(errorBody).optString("message").ifBlank {
                            JSONObject(errorBody).optString("error")
                        }
                    }.getOrNull().orEmpty()
                    error(reason.take(220).ifBlank {
                        "Banner upload failed (${response.code}); verify owner access and Storage setup."
                    })
                }
            }
            "$base/storage/v1/object/public/$BUCKET/$filePath"
        }
    }
}
