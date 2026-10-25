package com.example.data.supabase

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.blinkng.shared.BlinkChatAttachmentKind
import com.blinkng.shared.BlinkChatImageQuality
import com.blinkng.shared.BlinkChatPhotoAlbum
import java.io.File
import java.util.UUID

/**
 * Durable, network-constrained, per-account image outbox.
 * Never sends a stored photo if the authenticated account has changed.
 * The message UUID is stable across retries, so the server row is idempotent.
 */
class BlinkChatImageUploadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getString("messageId") ?: return Result.failure()
        val userId = inputData.getString("ownerId") ?: return Result.failure()
        val partner = inputData.getString("partner") ?: return Result.failure()
        val mimeQuality = inputData.getString("imageQuality") ?: "STANDARD"
        val caption = inputData.getString("caption").orEmpty()
        val fileName = inputData.getString("filename") ?: return Result.failure()
        val outbox = File(applicationContext.filesDir, "blink-chat-image-outbox")
        val file = File(outbox, fileName)
        if (!outbox.canonicalPath.equals(file.parentFile?.canonicalPath) ||
            !fileName.startsWith("$messageId.") || !file.isFile) return Result.failure()

        // Restore/refresh an expired stored session before retrying a queued upload.
        if (!SupabaseService().restoreSession()) {
            return if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("error" to "Sign in again to send picture."))
        }
        val token = SupabaseService.accessToken() ?: return Result.failure()
        val actualUser = runCatching {
            val json = org.json.JSONObject(String(
                android.util.Base64.decode(
                    token.split('.')[1], android.util.Base64.URL_SAFE or
                        android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
                ), Charsets.UTF_8
            ))
            json.optString("sub")
        }.getOrNull()
        // A different signed-in account must never upload the previous user's picture.
        if (actualUser != userId) return Result.failure(workDataOf("error" to "Account changed."))

        val quality = runCatching { BlinkChatImageQuality.valueOf(mimeQuality) }
            .getOrDefault(BlinkChatImageQuality.STANDARD)
        val albumId = inputData.getString("albumId").orEmpty()
        val album = if (albumId.isNotBlank()) runCatching {
            BlinkChatPhotoAlbum.of(
                albumId, inputData.getInt("albumPosition", 0),
                inputData.getInt("albumSize", 1)
            )
        }.getOrDefault(BlinkChatPhotoAlbum.single())
        else BlinkChatPhotoAlbum.single()
        val outcome = MessageMediaService.sendAttachment(
            applicationContext, partner, Uri.fromFile(file),
            BlinkChatAttachmentKind.IMAGE,
            clientMessageId = UUID.fromString(messageId).toString(),
            onUploadProgress = { percent ->
                setProgressAsync(workDataOf("progress" to percent))
            },
            caption = caption,
            imageQuality = quality,
            album = album
        )
        return outcome.fold(
            onSuccess = {
                file.delete()
                File(outbox, "$messageId.pending").delete()
                Result.success(workDataOf("serverMessageId" to it.id))
            },
            onFailure = {
                // A transient loss of network should retry after constraints return;
                // repeated server denials eventually fail, not spin forever.
                if (runAttemptCount < 3) Result.retry()
                else Result.failure(workDataOf("error" to "Picture could not be sent."))
            }
        )
    }
}
