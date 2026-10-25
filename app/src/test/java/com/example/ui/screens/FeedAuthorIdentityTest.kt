package com.example.ui.screens

import com.example.data.models.FeedPost
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedAuthorIdentityTest {
    private fun post(
        creatorId: String = "",
        handle: String = "same",
        badge: VerificationBadge = VerificationBadge.NONE
    ) = FeedPost(
        id = "p",
        author = "Same Name",
        authorUsername = handle,
        authorProfileId = creatorId,
        authorAvatar = "old.png",
        verificationBadge = badge,
        timeAgo = "now",
        text = "",
        likes = 0,
        commentsCount = 0,
        sharesCount = 0
    )

    @Test fun serverOwnerIdWinsEvenWhenHandleChanged() {
        val owner = UserProfile(id = "real-owner", username = "new_handle")
        val pretender = UserProfile(id = "someone-else", username = "same")
        assertEquals(
            owner,
            findFeedAuthorProfile(post("real-owner"), mapOf(owner.id to owner, pretender.id to pretender),
                mapOf(pretender.username to pretender))
        )
    }

    @Test fun staleIdNeverAssociatesWithAnotherAccountUsingSameNameOrHandle() {
        val other = UserProfile(id = "other", fullName = "Same Name", username = "same")
        assertNull(
            findFeedAuthorProfile(post("missing"), mapOf(other.id to other), mapOf("same" to other))
        )
        assertEquals("missing", resolvedFeedAuthorId(post("missing"), null))
    }

    @Test fun legacyPostMayUseExactUsernameButNotDisplayName() {
        val owner = UserProfile(id = "owner", fullName = "Same Name", username = "same")
        assertEquals(owner, findFeedAuthorProfile(post(), mapOf("owner" to owner), mapOf("same" to owner)))
        assertNull(findFeedAuthorProfile(post(handle = ""), mapOf("owner" to owner), mapOf("same" to owner)))
    }

    @Test fun currentProfileBadgeRevocationOverridesStalePostBadge() {
        val revoked = UserProfile(id = "owner", verificationBadge = VerificationBadge.NONE)
        assertEquals(VerificationBadge.NONE, resolvedFeedAuthorBadge(post(badge = VerificationBadge.GOLD), revoked))
        assertEquals(VerificationBadge.GOLD, resolvedFeedAuthorBadge(post(badge = VerificationBadge.GOLD), null))
        assertEquals(VerificationBadge.BLUE,
            resolvedFeedAuthorBadge(post(), UserProfile(verificationBadge = VerificationBadge.BLUE)))
    }

    @Test fun freshAvatarReplacesOutdatedPostImage() {
        assertEquals("new.png", resolvedFeedAuthorAvatar(post(), UserProfile(avatarUrl = "new.png")))
        assertEquals("old.png", resolvedFeedAuthorAvatar(post(), UserProfile(avatarUrl = "")))
    }
}
