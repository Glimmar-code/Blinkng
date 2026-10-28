package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkCreatorStudioAnalyticsTest {
    private fun post(
        id: String,
        views: Int = 0,
        likes: Int = 0,
        comments: Int = 0,
        shares: Int = 0,
        saves: Int? = null,
        reel: Boolean = false,
        tags: List<String> = emptyList(),
        sponsored: Boolean = false,
        repostedById: String? = null
    ) = FeedPost(
        id = id, author = "owner", authorAvatar = "",
        timeAgo = "now", text = "Content $id", likes = likes,
        commentsCount = comments, sharesCount = shares,
        viewsCount = views, savesCount = saves,
        isReel = reel, tags = tags, isSponsored = sponsored,
        repostedById = repostedById
    )

    @Test fun `summarizes only unique original published content`() {
        val result = BlinkCreatorStudioAnalytics.summarize(listOf(
            post("a", views = 100, likes = 10, comments = 2, shares = 1, saves = 3,
                tags = listOf("#Campus", "CAMPUS")),
            post("a", views = 100, likes = 10),
            post("b", views = 50, likes = 5, reel = true, tags = listOf("campus")),
            post("ad", views = 900, sponsored = true),
            post("repost", views = 400, repostedById = "other"),
            post("", views = 300)
        ))
        assertEquals(2, result.contentCount)
        assertEquals(1, result.postCount)
        assertEquals(1, result.reelCount)
        assertEquals(150L, result.qualifiedViews)
        assertEquals(15L, result.likes)
        assertEquals(2L, result.comments)
        assertEquals(1L, result.shares)
        assertEquals(3L, result.knownSaves)
        assertEquals(1, result.contentsWithSaveCount)
        assertEquals(14.0, result.interactionsPer100Views, 0.0001)
        assertEquals("a", result.topContent.first().id)
        assertEquals("campus", result.topTags.first().tag)
        assertEquals(2, result.topTags.first().contentCount)
    }

    @Test fun `never fabricates negative metrics missing saves or unique audience`() {
        val result = BlinkCreatorStudioAnalytics.summarize(listOf(
            post("a", views = -30, likes = -2, comments = -1, shares = -9),
            post("b", views = 0, saves = -5)
        ))
        assertEquals(0L, result.qualifiedViews)
        assertEquals(0L, result.likes)
        assertEquals(0L, result.knownSaves)
        assertEquals(0.0, result.interactionsPer100Views, 0.0)
        assertEquals(null, result.topContent.first { it.id == "a" }.saves)
        assertEquals(1, result.contentsWithSaveCount)
    }

    @Test fun `empty loading snapshot is not represented as growth`() {
        val result = BlinkCreatorStudioAnalytics.summarize(emptyList())
        assertEquals(0, result.contentCount)
        assertTrue(result.topContent.isEmpty())
        assertTrue(result.topTags.isEmpty())
    }
}
