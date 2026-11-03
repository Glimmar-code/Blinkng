package com.blinkng.shared

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BlinkMessageDatePolicyTest {
    private val now = Instant.parse("2026-10-12T18:00:00Z")
    private fun dateLabels(vararg entries: Pair<String, String>) =
        BlinkMessageDatePolicy.labels(
            entries.map { BlinkMessageDatePolicy.Entry(it.first, it.second) },
            now,
            ZoneOffset.UTC
        )

    @Test fun groupsAdjacentMessagesWithoutChangingRowIndices() {
        val labels = dateLabels(
            "one" to "2026-10-12T08:00:00Z",
            "two" to "2026-10-12T09:00:00Z",
            "three" to "2026-10-11T10:00:00Z",
            "four" to "2026-10-11T10:01:00Z",
            "five" to "2026-09-01T07:00:00Z"
        )
        assertEquals(mapOf(
            "one" to "Today",
            "three" to "Yesterday",
            "five" to "Sep 1, 2026"
        ), labels)
        assertFalse(labels.containsKey("two"))
        assertFalse(labels.containsKey("four"))
    }

    @Test fun offsetsAreConvertedToTheReadersTimezone() {
        val labels = dateLabels("one" to "2026-10-13T00:30:00+03:00")
        assertEquals("Today", labels["one"])
    }

    @Test fun invalidTimestampsCannotCrashTheConversation() {
        val labels = dateLabels(
            "bad" to "invalid",
            "valid" to "2026-10-12T10:00:00Z",
            "bad2" to ""
        )
        assertEquals(mapOf("valid" to "Today"), labels)
    }
}
