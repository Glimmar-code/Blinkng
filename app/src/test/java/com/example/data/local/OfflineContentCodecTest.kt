package com.example.data.local

import com.example.data.models.ContactField
import com.example.data.models.FeedPost
import com.example.data.models.PollOption
import com.example.data.models.PostPoll
import com.example.data.models.UserProfile
import com.example.data.models.VerificationBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineContentCodecTest {
    private val codec = OfflineContentCodec()

    @Test
    fun postRoundTripPreservesReelAndPollState() {
        val post = FeedPost(
            id = "post-1",
            author = "glimmar",
            authorAvatar = "https://example.com/avatar.jpg",
            timeAgo = "Just now",
            text = "Offline first",
            images = listOf("https://example.com/photo.jpg"),
            likes = 12,
            commentsCount = 3,
            sharesCount = 2,
            viewsCount = 41,
            isReel = true,
            videoUrl = "https://example.com/reel.mp4",
            poll = PostPoll(
                question = "Works offline?",
                options = listOf(PollOption("yes", "Yes", 8, true)),
                totalVotes = 8,
                hasVoted = true
            )
        )

        val restored = codec.decodePost(requireNotNull(codec.encodePost(post)))

        assertNotNull(restored)
        assertEquals(post, restored)
    }

    @Test
    fun profileRoundTripPreservesNestedFields() {
        val profile = UserProfile(
            id = "user-1",
            fullName = "Gideon David",
            username = "glimmar",
            verificationBadge = VerificationBadge.BLUE,
            email = ContactField("student@example.com", true),
            coreSkills = mutableListOf("Kotlin", "Media"),
            hobbies = listOf("Writing")
        )

        val restored = codec.decodeProfile(requireNotNull(codec.encodeProfile(profile)))

        assertNotNull(restored)
        assertEquals(profile, restored)
    }
}
