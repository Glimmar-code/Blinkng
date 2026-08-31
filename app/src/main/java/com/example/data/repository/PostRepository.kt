package com.example.data.repository

import android.util.Log
import com.example.data.models.FeedPost
import com.example.data.models.PostPoll
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PostRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
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
        supabaseService.uploadPostMedia(
            userId = userId,
            bytes = bytes,
            mimeType = mimeType,
            isVideo = isVideo
        )
    }

    suspend fun togglePostLike(
        postId: String,
        liked: Boolean,
        newLikeCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        supabaseService.togglePostLike(
            postId = postId,
            liked = liked,
            newLikeCount = newLikeCount
        )
    }

    suspend fun deletePost(postId: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.deleteFeedPost(postId)
    }

    suspend fun recordView(postId: String, viewerUsername: String): Int = withContext(Dispatchers.IO) {
        supabaseService.recordPostView(postId, viewerUsername)
    }
}
