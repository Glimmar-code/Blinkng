package com.example.ui.screens

import com.example.data.models.FeedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoReelsBehaviorTest {

    private fun reel(id: String, author: String = "creator") = FeedPost(
        id = id,
        author = author,
        authorAvatar = "",
        timeAgo = "now",
        text = "",
        likes = 0,
        commentsCount = 0,
        sharesCount = 0,
        isReel = true,
        videoUrl = "https://example.com/$id.mp4"
    )

    @Test
    fun followingTab_usesOnlyFollowingReels() {
        val forYou = listOf(reel("a"), reel("b"))
        val following = listOf(reel("c"))

        assertEquals(
            listOf("c"),
            selectReelsForTab(forYou, following, "Following").map { it.id }
        )
    }

    @Test
    fun forYouTab_keepsRankedReelSource() {
        val forYou = listOf(reel("a"), reel("b"))
        val following = listOf(reel("c"))

        assertEquals(
            listOf("a", "b"),
            selectReelsForTab(forYou, following, "For You").map { it.id }
        )
    }

    @Test
    fun preloadWindow_keepsOnlyCurrentAndAdjacentPagesWarm() {
        assertTrue(shouldPreloadReelPage(index = 5, currentPage = 5))
        assertTrue(shouldPreloadReelPage(index = 4, currentPage = 5))
        assertTrue(shouldPreloadReelPage(index = 6, currentPage = 5))
        assertFalse(shouldPreloadReelPage(index = 3, currentPage = 5))
        assertFalse(shouldPreloadReelPage(index = 7, currentPage = 5))
    }
}
