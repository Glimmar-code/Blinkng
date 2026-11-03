package com.example.data.local

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.supabase.SupabaseService
import com.example.media.BlinkReelPoster
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Fail-closed, account-bound post recovery after process death.
 * The payload is encrypted at rest by PersistentTextDraftStore, never in WorkManager Data.
 * A stable request UUID makes server-side createFeedPost idempotent across retries.
 */
object BlinkPostOutbox {
    private const val TAG = "blink_post_outbox"
    private val active = ConcurrentHashMap.newKeySet<String>()

    fun begin(requestId: String): Boolean = active.add(requestId)
    fun end(requestId: String) { active.remove(requestId) }
    private fun key(id: String) = "post_outbox_${id}"
    private fun workName(id: String) = "blink_post_${id}"

    fun enqueue(context: Context, ownerId: String, requestId: String, payload: JSONObject): Boolean {
        if (runCatching { UUID.fromString(ownerId) }.isFailure ||
            runCatching { UUID.fromString(requestId) }.isFailure) return false
        val data = payload.toString()
        if (data.length > 40_000) return false
        return try {
            PersistentTextDraftStore.writeValue(context, key(requestId), ownerId, data)
            if (PersistentTextDraftStore.readValue(context, key(requestId), ownerId) != data) {
                false
            } else {
                val request = OneTimeWorkRequestBuilder<BlinkPostOutboxWorker>()
                    .setInitialDelay(90L, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .setInputData(workDataOf("owner" to ownerId, "request" to requestId))
                    .addTag(TAG)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(requestId), ExistingWorkPolicy.KEEP, request
                )
                true
            }
        } catch (_: Exception) { false }
    }

    fun payload(context: Context, owner: String, id: String): JSONObject? =
        PersistentTextDraftStore.readValue(context, key(id), owner)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun complete(context: Context, owner: String, id: String) {
        PersistentTextDraftStore.clearValue(context, key(id), owner)
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }
}

class BlinkPostOutboxWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val owner = inputData.getString("owner") ?: return Result.failure()
        val requestId = inputData.getString("request") ?: return Result.failure()
        if (!BlinkPostOutbox.begin(requestId)) return Result.retry()
        try {
            val service = SupabaseService()
            if (!service.restoreSession()) return Result.retry()
            if (service.getCurrentUserId() != owner) {
                // A different signed-in account must never send the previous user's draft.
                return Result.failure()
            }
            val payload = BlinkPostOutbox.payload(applicationContext, owner, requestId)
                ?: return Result.success()

            fun JSONObject.optional(key: String): String? =
                optString(key).takeIf { it.isNotBlank() && it != "null" }
            fun JSONArray?.strings(): List<String> = buildList {
                if (this@strings != null) for (index in 0 until this@strings.length()) {
                    this@strings.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
            suspend fun upload(value: String, video: Boolean): String? {
                if (!value.startsWith("content://") && !value.startsWith("file://")) return value
                val uri = Uri.parse(value)
                val mime = applicationContext.contentResolver.getType(uri)
                    ?: if (video) "video/mp4" else "image/jpeg"
                return service.uploadPostMediaUri(owner, value, mime, video)
            }

            val imageSources = payload.optJSONArray("images").strings()
            val images = mutableListOf<String>()
            for (source in imageSources) {
                if (isStopped || service.getCurrentUserId() != owner) return Result.retry()
                images += upload(source, false) ?: return Result.retry()
            }
            val videoSource = payload.optional("video")
            val videoUrl = videoSource?.let { upload(it, true) }
            if (videoSource != null && videoUrl == null) return Result.retry()
            if (service.getCurrentUserId() != owner) return Result.failure()

            if (images.isEmpty() && videoSource != null &&
                (videoSource.startsWith("content://") || videoSource.startsWith("file://"))) {
                runCatching {
                    BlinkReelPoster.extract(applicationContext, Uri.parse(videoSource))
                }.getOrNull()?.let { bytes ->
                    runCatching {
                        service.uploadPostMedia(owner, bytes, "image/jpeg", false)
                    }.getOrNull()?.let { images.add(it) }
                }
            }
            val imagePayload = when (images.size) {
                0 -> null
                1 -> images.first()
                else -> JSONArray(images).toString()
            }
            val pollPayload = payload.optJSONObject("poll")
            val poll = pollPayload?.let {
                PostPoll(
                    question = it.optString("question"),
                    options = it.optJSONArray("options").strings().map { option ->
                        PollOption(UUID.randomUUID().toString(), option)
                    }
                )
            }
            val created = service.createFeedPost(
                author = payload.optString("author"),
                authorAvatar = payload.optString("avatar"),
                facultyTag = payload.optString("faculty"),
                text = payload.optString("text"),
                imageUrl = imagePayload,
                videoUrl = videoUrl,
                tags = payload.optJSONArray("tags").strings(),
                mentions = payload.optJSONArray("mentions").strings(),
                poll = poll,
                isReel = videoUrl != null,
                audience = payload.optString("audience", "Everyone"),
                category = payload.optString("category", "Campus Life"),
                location = payload.optional("location"),
                linkUrl = payload.optional("link"),
                allowComments = payload.optBoolean("comments", true),
                hideLikes = payload.optBoolean("hide_likes", false),
                isPinned = payload.optBoolean("pinned", false),
                isDisappearing = payload.optBoolean("disappearing", false),
                audioTitle = payload.optional("audio"),
                altText = payload.optional("alt"),
                textStyle = payload.optional("style"),
                clientRequestId = requestId
            ) ?: return if (runAttemptCount >= 9) Result.failure() else Result.retry()

            // A post is complete only after Supabase confirms the server row.
            if (created.id.isBlank()) return Result.retry()
            PersistentTextDraftStore.clearValue(applicationContext, "post_outbox_${requestId}", owner)
            return Result.success()
        } catch (_: Exception) {
            return if (runAttemptCount >= 9) Result.failure() else Result.retry()
        } finally {
            BlinkPostOutbox.end(requestId)
        }
    }
}
