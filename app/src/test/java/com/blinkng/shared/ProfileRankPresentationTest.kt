package com.blinkng.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRankPresentationTest {
    @Test
    fun rankLabelUsesDashUntilRankExists() {
        assertEquals("—", profileRankLabel(0))
        assertEquals("—", profileRankLabel(-1))
        assertEquals("#1", profileRankLabel(1))
        assertEquals("#42", profileRankLabel(42))
    }

    @Test
    fun snapshotKeepsExistingRankWhenOneValueIsMissing() {
        val resolved = ProfileRankSnapshot(
            worldRank = 12,
            campusRank = 0,
        ).resolvedAgainst(
            existingWorldRank = 20,
            existingCampusRank = 5,
        )

        assertEquals(12, resolved.worldRank)
        assertEquals(5, resolved.campusRank)
    }
}
