package com.example.data.repository

import android.util.Log
import com.example.data.models.FeedPost
import com.example.data.models.Comment
import com.example.data.models.PostPoll
import com.example.data.models.Story
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
        isReel: Boolean = false,
        audience: String = "Everyone",
        category: String = "Campus Life",
        location: String? = null,
        linkUrl: String? = null,
        allowComments: Boolean = true,
        hideLikes: Boolean = false,
        isPinned: Boolean = false,
        isDisappearing: Boolean = false,
        audioTitle: String? = null,
        altText: String? = null
    ): FeedPost? = withContext(Dispatchers.IO) {
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
            isReel = isReel,
            audience = audience,
            category = category,
            location = location,
            linkUrl = linkUrl,
            allowComments = allowComments,
            hideLikes = hideLikes,
            isPinned = isPinned,
            isDisappearing = isDisappearing,
            audioTitle = audioTitle,
            altText = altText
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

    
    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {
        supabaseService.togglePostBookmark(postId, bookmarked)
    }

    suspend fun fetchComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        supabaseService.fetchComments(postId)
    }

    suspend fun addComment(postId: String, text: String, replyToUser: String?): Comment? = withContext(Dispatchers.IO) {
        supabaseService.addComment(postId, text, replyToUser)
    }

    suspend fun toggleCommentLike(commentId: Long, liked: Boolean, newLikeCount: Int): Boolean = withContext(Dispatchers.IO) {
        supabaseService.toggleCommentLike(commentId, liked, newLikeCount)
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

    // ============================================================
    // STORIES & POLLS
    // ============================================================

    suspend fun fetchStories(): List<Story> = withContext(Dispatchers.IO) {
        supabaseService.fetchStories()
    }

    suspend fun createStory(story: Story): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createStory(story)
    }

    suspend fun markStoryViewed(storyId: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.markStoryViewed(storyId)
    }

    suspend fun toggleStoryLike(storyId: String, nextLiked: Boolean, newCount: Int): Boolean = withContext(Dispatchers.IO) {
        supabaseService.toggleStoryLike(storyId, nextLiked, newCount)
    }

    suspend fun reactToStory(storyId: String, emoji: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.reactToStory(storyId, emoji)
    }

    suspend fun replyToStory(storyId: String, recipientUsername: String, replyText: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.replyToStory(storyId, recipientUsername, replyText)
    }

    suspend fun votePoll(postId: String, optionId: String, updatedPoll: PostPoll): Boolean = withContext(Dispatchers.IO) {
        supabaseService.votePoll(postId, optionId, updatedPoll)
    }
}
