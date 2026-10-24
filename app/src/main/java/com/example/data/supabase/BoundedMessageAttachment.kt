package com.example.data.supabase

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.safeChatFilename
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Private, bounded disk staging prevents 80 MB videos from exhausting the app heap. */
internal class BoundedMessageAttachment(
    val file: File,
    val mime: String,
    val name: String,
    val size: Long,
    val extension: String,
) : AutoCloseable {
    fun requestBody(): RequestBody = file.asRequestBody(mime.toMediaType())
    override fun close() { file.delete() }

    companion object {
        fun open(context: Context, uri: Uri, kind: BlinkChatAttachmentKind): BoundedMessageAttachment {
            val mime = if (kind == BlinkChatAttachmentKind.VOICE && uri.scheme == "file") {
                "audio/mp4"
            } else context.contentResolver.getType(uri)?.lowercase()
                ?: throw IllegalArgumentException("File type could not be verified.")
            require(kind.accepts(mime)) { "This ${kind.title.lowercase()} format is unsupported." }
            val title = if (uri.scheme == "file") {
                safeChatFilename(uri.lastPathSegment.orEmpty())
            } else runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) safeChatFilename(it.getString(0).orEmpty()) else null }
            }.getOrNull() ?: kind.title
            val staged = File.createTempFile("blink-attachment-", ".staged", context.cacheDir)
            try {
                val input = if (uri.scheme == "file") {
                    FileInputStream(File(uri.path ?: throw IllegalArgumentException("Recording file missing.")))
                } else context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot read the selected file.")
                var written = 0L
                input.use { src ->
                    FileOutputStream(staged).use { dst ->
                        val block = ByteArray(64 * 1024)
                        while (true) {
                            val n = src.read(block)
                            if (n < 0) break
                            written += n
                            if (written > kind.maxBytes) {
                                throw IllegalArgumentException(
                                    "File is too large. Limit: ${kind.maxBytes / (1024 * 1024)} MB."
                                )
                            }
                            dst.write(block, 0, n)
                        }
                    }
                }
                kind.verify(mime, written)
                return BoundedMessageAttachment(staged, mime, title, written, kind.extension(mime))
            } catch (t: Throwable) {
                staged.delete()
                throw t
            }
        }
    }
}
