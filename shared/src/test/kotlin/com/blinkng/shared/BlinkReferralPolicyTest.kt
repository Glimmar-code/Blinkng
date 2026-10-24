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
