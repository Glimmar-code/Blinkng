package com.blinkng.desktop.shared

import com.blinkng.shared.BlinkContestDraftValidator
import com.blinkng.shared.BlinkContestPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlinkContestTimingPolicyTest {
    private val dates = listOf(
        "2026-11-01T09:00:00+01:00",
        "2026-11-07T09:00:00+01:00",
        "2026-11-08T09:00:00+01:00"
    )

    private fun validate(times: List<String> = dates) = BlinkContestDraftValidator.error(
        "BLINK Campus Showcase", times, listOf("30000", "15000", "5000"), "", "", false
    )

    @Test
    fun contestRequiresOnlyStartEndAndResultsInOrder() {
        assertNull(validate())
        assertEquals(
            "Select the contest start, end and results dates.",
            validate(listOf("", "", ""))
        )
        assertEquals(
            "Use valid dates with a time zone.",
            validate(dates.toMutableList().apply { set(1, "2026-11-07") })
        )
        assertEquals(
            "Dates must follow: contest start, end, then results.",
            validate(dates.toMutableList().apply { set(1, "2026-10-31T09:00:00+01:00") })
        )
        assertNull(validate(listOf(dates[0], dates[1], dates[1])))
    }

    @Test
    fun entriesRemainOpenThroughVotingAndCloseOutsideThatWindow() {
        assertTrue(BlinkContestPolicy.canApplyDuringWindow("voting", "My contest entry", true, ""))
        assertTrue(BlinkContestPolicy.canApplyDuringWindow("registration", "My contest entry", true, ""))
        assertFalse(BlinkContestPolicy.canApplyDuringWindow("upcoming", "My contest entry", true, ""))
        assertFalse(BlinkContestPolicy.canApplyDuringWindow("review", "My contest entry", true, ""))
    }
}
