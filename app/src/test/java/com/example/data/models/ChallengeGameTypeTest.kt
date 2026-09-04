package com.example.data.models

import org.junit.Assert.assertEquals
import org.junit.Test

class ChallengeGameTypeTest {
    @Test
    fun `canonical Supabase game types round trip`() {
        ChallengeGameType.entries.forEach { type ->
            assertEquals(type, ChallengeGameType.fromApiName(type.apiName))
        }
    }

    @Test
    fun `legacy game types map to canonical challenge types`() {
        assertEquals(ChallengeGameType.GENERAL_KNOWLEDGE, ChallengeGameType.fromApiName("trivia"))
        assertEquals(ChallengeGameType.MATH_SPRINT, ChallengeGameType.fromApiName("math"))
        assertEquals(ChallengeGameType.BRAIN_MIX, ChallengeGameType.fromApiName("speed"))
    }
}
