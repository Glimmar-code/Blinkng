package com.blinkng.shared

import com.blinkng.shared.discovery.BlinkReferralPolicy
import com.blinkng.shared.discovery.BlinkReferralSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BlinkReferralPolicyTest {
    @Test fun exactUsernameRanksAheadOfPrefixes() {
        val query = "ab"
        assertEquals(0, BlinkReferralPolicy.suggestionRank(query, BlinkReferralSuggestion("1","ab","First")))
        assertEquals(1, BlinkReferralPolicy.suggestionRank(query, BlinkReferralSuggestion("2","abacus","Second")))
        assertEquals(2, BlinkReferralPolicy.suggestionRank(query, BlinkReferralSuggestion("3","user","Abigail")))
        assertEquals(3, BlinkReferralPolicy.suggestionRank(query, BlinkReferralSuggestion("4","xxab","Fourth")))
        assertEquals(4, BlinkReferralPolicy.suggestionRank(query, BlinkReferralSuggestion("5","user","Fabian")))
    }
    @Test fun permanentAchievementsAreIndependentOfTemporaryDiscovery() {
        val counts = listOf(0L,1L,5L,10L,25L,50L,100L,150L)
        val tiers = listOf("New member","Starter","Builder","Advocate","Connector","Champion","Ambassador","Ambassador")
        val targets = listOf(1,5,10,25,50,100,null,null)
        counts.indices.forEach { i ->
            assertEquals(tiers[i], BlinkReferralPolicy.tierForLifetime(counts[i]))
            assertEquals(targets[i], BlinkReferralPolicy.nextMilestone(counts[i]))
        }
        val stats = com.blinkng.shared.discovery.BlinkReferralStats(
            total=5, activeLast30Days=0, visibilityPoints=0,
            achievementTier="Builder", nextAchievementTarget=10,
        )
        assertEquals("5 more confirmed referrals to reach 10",
            BlinkReferralPolicy.milestoneLabel(stats))
        assertEquals("Builder", stats.achievementTier)
    }

    @Test fun referralVisibilityDecaysWithoutDestroyingAchievements() {
        val expected = mapOf(-1L to 0, 0L to 3, 9L to 3, 10L to 2,
            19L to 2, 20L to 1, 29L to 1, 30L to 0, 365L to 0)
        expected.forEach { (days,points) ->
            assertEquals(points, BlinkReferralPolicy.pointsForReferralAgeDays(days))
        }
    }
    @Test fun inviteUrlIsCanonicalAndRejectsUnsafeUsernames() {
        assertEquals("https://blink.com.ng/invite/glimmar", BlinkReferralPolicy.inviteUrl("@GLIMMAR"))
        assertFailsWith<IllegalArgumentException> { BlinkReferralPolicy.inviteUrl("../admin") }
        assertFailsWith<IllegalArgumentException> { BlinkReferralPolicy.inviteUrl("a") }
    }
    @Test fun lookupRemovesAtSignAndBoundsInput() {
        assertEquals("alpha", BlinkReferralPolicy.normalizedQuery("  @alpha  "))
        assertEquals(40, BlinkReferralPolicy.normalizedQuery("x".repeat(100)).length)
    }
}
