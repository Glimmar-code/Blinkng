package com.blinkng.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkContestDraftValidatorTest {
    private val dates = listOf(
        "2026-11-01T09:00:00+01:00",
        "2026-11-04T09:00:00+01:00",
        "2026-11-05T09:00:00+01:00",
        "2026-11-07T09:00:00+01:00",
        "2026-11-08T09:00:00+01:00"
    )
    private fun check(
        title: String = "BLINK Campus Showcase",
        times: List<String> = dates,
        prizes: List<String> = listOf("30000", "15000", "5000"),
        max: String = "",
        cover: String = "",
        selected: Boolean = false
    ) = BlinkContestDraftValidator.error(title, times, prizes, max, cover, selected)

    @Test fun acceptsValidDraftWithOrWithoutLimit() {
        assertNull(check())
        assertNull(check(max = "250", cover = "https://example.com/cover.jpg"))
        assertNull(check(selected = true))
    }

    @Test fun rejectsEmptyOrOutOfOrderDatesAndInvalidTimeZones() {
        assertEquals("Select all five application, voting and results dates.",
            check(times = listOf("", "", "", "", "")))
        assertEquals("Use valid dates with a time zone.",
            check(times = dates.toMutableList().apply { set(1, "2026-11-04") }))
        assertEquals("Dates must follow: applications, voting, then results.",
            check(times = dates.toMutableList().apply { set(3, "2026-11-02T09:00:00+01:00") }))
    }

    @Test fun rejectsInvalidPrizeAndParticipantSettings() {
        assertEquals("Enter a contest title (4–100 characters).", check(title = "A"))
        assertEquals("Enter three valid, non-negative prize amounts.",
            check(prizes = listOf("oops", "150", "50")))
        assertEquals("Enter three valid, non-negative prize amounts.",
            check(prizes = listOf("-1", "150", "50")))
        assertEquals("Participant limit must be 2–100,000, or blank for unlimited.",
            check(max = "1"))
        assertEquals("Contest cover URL must use HTTPS.", check(cover = "http://example.com/img"))
    }
}
