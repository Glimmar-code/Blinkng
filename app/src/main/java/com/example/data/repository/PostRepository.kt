package com.example.data.repository

import android.util.Log
import com.example.data.models.FeedPost
import com.example.data.models.PostPoll
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class PostRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val baseUrl = SupabaseConfig.url.trimEnd('/')
    private val anonKey = SupabaseConfig.anonKey

    private fun newRequestBuilder(endpoint: String): Request.Builder {
        val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
        return Request.Builder()
            .url(fullUrl)
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Accept", "application/json")
    }

    suspend fun fetchFeed(isReel: Boolean? = null): List<FeedPost> = withContext(Dispatchers.IO) {
        try {
            val allPosts = supabaseService.fetchFeedPosts()
            if (isReel != null) {
                allPosts.filter { it.isReel == isReel }
            } else {
                allPosts
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "fetchFeed error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchPostsByUser(username: String, isReel: Boolean? = null): List<FeedPost> = withContext(Dispatchers.IO) {
        try {
            val allPosts = supabaseService.fetchFeedPosts()
            allPosts.filter { post ->
                val matchesUser = post.author.equals(username, ignoreCase = true)
                if (isReel != null) matchesUser && post.isReel == isReel else matchesUser
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "fetchPostsByUser error: ${e.message}")
            emptyList()
        }
    }

    suspend fun createPost(
        author: String,
        authorAvatar: String,
        facultyTag: String,
        text: String,
        imageUrl: String?,
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        poll: PostPoll? = null,
        isReel: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createFeedPost(
            author = author,
            authorAvatar = authorAvatar,
            facultyTag = facultyTag,
            text = text,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            tags = tags,
            mentions = mentions,
            poll = poll,
            isReel = isReel
        )
    }

    suspend fun uploadPostMedia(
        userId: String,
        bytes: ByteArray,
        isVideo: Boolean,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val folder = if (isVideo) "videos" else "images"
            val ext = if (isVideo) "mp4" else "jpg"
            val fileName = "${System.currentTimeMillis()}_media.$ext"
            val path = "users/$userId/posts/$folder/$fileName"
            val mediaType = mimeType.toMediaType()
            val body = bytes.toRequestBody(mediaType)

            val req = newRequestBuilder("/storage/v1/object/post-media/$path")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext "$baseUrl/storage/v1/object/public/post-media/$path"
                }
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "uploadPostMedia error: ${e.message}")
        }
        null
    }

    suspend fun deletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = newRequestBuilder("/rest/v1/feed_posts?id=eq.$postId")
                .delete()
                .build()
            client.newCall(req).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "deletePost error: ${e.message}")
            false
        }
    }

    suspend fun recordView(postId: String, viewerUsername: String): Int = withContext(Dispatchers.IO) {
        supabaseService.recordPostView(postId, viewerUsername)
    }
}
