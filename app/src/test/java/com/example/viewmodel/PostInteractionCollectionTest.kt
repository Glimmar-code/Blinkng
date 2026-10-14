package com.example.viewmodel

import com.example.data.models.FeedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostInteractionCollectionTest {
    private fun post(id: String) = FeedPost(
        id = id,
        author = "User",
        authorAvatar = "",
        timeAgo = "now",
        text = "",
        likes = 0,
        commentsCount = 0,
        sharesCount = 0
    )

    @Test fun unlikeRemovesTargetFromLikedTabWithoutTouchingOtherPosts() {
        val existing = listOf(post("a"), post("b"))
        val result = syncInteractionCollection(existing, post("a"), false) { it.copy(isLiked = false) }
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun unsaveRemovesTargetFromSavedTab() {
        val result = syncInteractionCollection(listOf(post("a"), post("b")), post("a"), false) {
            it.copy(isBookmarked = false)
        }
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test fun newLikeInsertsOnlyOnceAndUpdatesFlagAndCount() {
        val target = post("a")
        val result = syncInteractionCollection(listOf(post("b"), target), target, true) {
            it.copy(isLiked = true, likes = 3)
        }
        assertEquals(2, result.size)
        assertEquals(1, result.count { it.id == "a" })
        assertTrue(result.first { it.id == "a" }.isLiked)
        assertEquals(3, result.first { it.id == "a" }.likes)
    }

    @Test fun failedUnlikeCanRestoreOriginalLikedMembership() {
        val target = post("a").copy(isLiked = true, likes = 5)
        val removed = syncInteractionCollection(listOf(target), target, false) { it }
        val restored = syncInteractionCollection(removed, target, true) { it.copy(isLiked = true) }
        assertEquals(1, restored.size)
        assertTrue(restored.first().isLiked)
        assertFalse(restored.first().isBookmarked)
    }
}
