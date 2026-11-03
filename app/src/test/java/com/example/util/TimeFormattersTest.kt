package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimeFormattersTest {
    private val now = Instant.parse("2026-09-08T20:00:00Z")

    @Test
    fun presenceStatus_followsMinuteHourDayThenDateRules() {
        assertEquals("Active now", TimeFormatters.presenceStatus(true, "2026-09-08T19:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen just now", TimeFormatters.presenceStatus(false, "2026-09-08T19:59:30Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 min ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:59:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 59 mins ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:01:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 hr ago", TimeFormatters.presenceStatus(false, "2026-09-08T19:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 2 hrs ago", TimeFormatters.presenceStatus(false, "2026-09-08T18:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen 1 day ago", TimeFormatters.presenceStatus(false, "2026-09-07T20:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen Sep 6, 2026", TimeFormatters.presenceStatus(false, "2026-09-06T20:00:00Z", now, ZoneOffset.UTC))
        assertEquals("Last seen recently", TimeFormatters.presenceStatus(false, null, now, ZoneOffset.UTC))
    }
}
