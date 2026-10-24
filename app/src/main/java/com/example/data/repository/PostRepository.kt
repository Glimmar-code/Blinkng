package com.example.data.repository

import android.util.Log
import com.example.data.models.FeedPost
import com.example.data.models.Comment
import com.example.data.models.PostPoll
import com.example.data.models.Story
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal fun isIgnorableLikeCountRefreshFailure(error: Throwable): Boolean =
    error is IllegalStateException &&
        error.message?.contains("Like count refresh failed", ignoreCase = true) == true

/** SQLSTATE 57014 can be an explicit cancellation as well as a timeout.
 * Only the server's actual statement-timeout text permits one safe fallback.
 */
internal fun isRecoverableRankedFeedTimeout(error: Throwable): Boolean =
    generateSequence(error) { it.cause }
        .any { cause -> cause.message.orEmpty().contains("statement timeout", ignoreCase = true) }

class PostRepository(
    private val supabaseService: SupabaseService = SupabaseService()
) {
    // Only retain per-post locks while operations are active or queued.
    private val likeMutationLocks = PerKeyMutationLocks()
    // Guard the expensive discovery RPC against simultaneous Home/Reels/realtime pulls.
    private val rankedFeedMutex = Mutex()
    private val chronologicalFallbackTypes = mutableSetOf<String>()

    /**
     * One chronological first-page fallback after a genuine statement timeout.
     * Keep the same pagination mode for the rest of that lane: no ranked-offset/
     * chronological-cursor mixing or unbounded rerunning of costly ranked queries.
     * Never swallow cancellation, authentication failures or secondary failures.
     */
    private suspend fun fetchRankedFeedWithTimeoutFallback(
        limit: Int,
        feedType: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null
    ): List<FeedPost> = rankedFeedMutex.withLock {
        val firstPage = beforeCreatedAt.isNullOrBlank() && beforeId.isNullOrBlank()
        if (!firstPage && feedType in chronologicalFallbackTypes) {
            return@withLock supabaseService.fetchFeedPage(
                limit = limit, beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId, feedType = feedType
            )
        }
        try {
            val page = supabaseService.fetchFeedPage(
                limit = limit, beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId, feedType = "ranked_$feedType"
            )
            if (firstPage) chronologicalFallbackTypes.remove(feedType)
            page
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (!firstPage || !isRecoverableRankedFeedTimeout(error)) throw error
            Log.w("PostRepository", "Ranked $feedType timed out; trying one authenticated chronological page", error)
            val page = supabaseService.fetchFeedPage(limit = limit, feedType = feedType)
            chronologicalFallbackTypes.add(feedType)
            page
        }
    }

    suspend fun fetchFeed(isReel: Boolean? = null): List<FeedPost> = withContext(Dispatchers.IO) {
        // Propagate failures if BOTH ranking and fallback fail: never fake an empty feed.
        when (isReel) {
            true -> fetchRankedFeedWithTimeoutFallback(30, "reels")
            false -> fetchRankedFeedWithTimeoutFallback(30, "posts")
            null -> fetchRankedFeedWithTimeoutFallback(60, "all")
        }
    }

    suspend fun fetchPostById(postId: String): FeedPost? = withContext(Dispatchers.IO) {
        supabaseService.fetchFeedPostById(postId)
    }

    suspend fun fetchFollowingFeed(limit: Int = 30): List<FeedPost> = withContext(Dispatchers.IO) {
        supabaseService.fetchFeedPage(limit = limit, feedType = "following")
    }

    suspend fun fetchFollowingFeedPage(
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 30
    ): List<FeedPost> = withContext(Dispatchers.IO) {
        supabaseService.fetchFeedPage(
            limit = limit,
            beforeCreatedAt = beforeCreatedAt,
            beforeId = beforeId,
            feedType = "following"
        )
    }

    suspend fun fetchFeedPage(
        isReel: Boolean,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 30
    ): List<FeedPost> = withContext(Dispatchers.IO) {
        fetchRankedFeedWithTimeoutFallback(
            limit = limit,
            beforeCreatedAt = beforeCreatedAt,
            beforeId = beforeId,
            feedType = if (isReel) "reels" else "posts"
        )
    }

    suspend fun searchPosts(
        query: String,
        beforeCreatedAt: String? = null,
        beforeId: String? = null,
        limit: Int = 30
    ): List<FeedPost> = withContext(Dispatchers.IO) {
        supabaseService.searchFeedPage(query, limit, beforeCreatedAt, beforeId)
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
        altText: String? = null,
        clientRequestId: String? = null
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
            altText = altText,
            clientRequestId = clientRequestId
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

    suspend fun uploadPostMediaUri(
        userId: String,
        uriString: String,
        isVideo: Boolean,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        supabaseService.uploadPostMediaUri(
            userId = userId,
            uriString = uriString,
            mimeType = mimeType,
            isVideo = isVideo
        )
    }

    suspend fun togglePostRepost(postId: String): Pair<Boolean, Int>? = withContext(Dispatchers.IO) {
        supabaseService.togglePostRepost(postId)
    }

    suspend fun togglePostBookmark(postId: String, bookmarked: Boolean): Boolean = withContext(Dispatchers.IO) {
        supabaseService.togglePostBookmark(postId, bookmarked)
    }

    suspend fun fetchComments(postId: String): List<Comment> = withContext(Dispatchers.IO) {
        supabaseService.fetchComments(postId)
    }

    suspend fun addComment(postId: String, text: String, parentCommentId: String?): Boolean = withContext(Dispatchers.IO) {
        supabaseService.addComment(postId, text, parentCommentId)
    }

    suspend fun toggleCommentLike(commentId: String, liked: Boolean): Boolean = withContext(Dispatchers.IO) {
        supabaseService.toggleCommentLike(commentId, liked)
    }

    suspend fun reportComment(commentId: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        supabaseService.reportComment(commentId, reason)
    }

    suspend fun togglePostLike(
        postId: String,
        liked: Boolean,
        newLikeCount: Int
    ): Boolean = withContext(Dispatchers.IO) {
        likeMutationLocks.withKey(postId) {
            try {
                supabaseService.togglePostLike(
                    postId = postId,
                    liked = liked,
                    newLikeCount = newLikeCount
                )
            } catch (error: IllegalStateException) {
                // SupabaseService currently performs a follow-up count GET after the
                // like row has already been committed. A failure in that optional
                // refresh must not roll back an otherwise successful optimistic like.
                if (isIgnorableLikeCountRefreshFailure(error)) {
                    Log.w(
                        "PostRepository",
                        "Like persisted but count refresh failed for post=$postId; keeping optimistic state.",
                        error
                    )
                    true
                } else {
                    throw error
                }
            }
        }
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

    suspend fun uploadStoryMedia(userId: String, bytes: ByteArray, mimeType: String, isVideo: Boolean): String? = withContext(Dispatchers.IO) {
        supabaseService.uploadStoryMedia(userId, bytes, mimeType, isVideo)
    }

    suspend fun createStory(story: Story, isVideo: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createStory(story, isVideo)
    }

    suspend fun createTextStory(story: Story): Boolean = withContext(Dispatchers.IO) {
        supabaseService.createTextStory(story)
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
