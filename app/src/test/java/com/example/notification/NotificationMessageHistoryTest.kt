package com.example.notification

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationMessageHistoryTest {
    @After fun tearDown() = NotificationMessageHistory.clear()

    @Test fun boundedHistoryKeepsFiveMostRecent() {
        for (n in 1..6) {
            NotificationMessageHistory.append("account-1", "conversation", "message-$n", "Text $n", n * 1000L)
        }
        val result = NotificationMessageHistory.append("account-1", "conversation", "message-7", "Text 7", 7000L)
        assertEquals(5, result.size)
        assertEquals("Text 3", result.first().text)
        assertEquals("Text 7", result.last().text)
    }

    @Test fun historyNeverCrossesAccountBoundary() {
        NotificationMessageHistory.append("account-a", "conversation", "id-1", "secret A", 1000)
        val result = NotificationMessageHistory.append("account-b", "conversation", "id-2", "Message B", 2000)
        assertEquals(listOf("Message B"), result.map { it.text })
    }

    @Test fun missingUserIdentityDoesNotStorePreviews() {
        val first = NotificationMessageHistory.append("", "conversation", "a", "One", 1000)
        val second = NotificationMessageHistory.append("", "conversation", "b", "Two", 2000)
        assertEquals(1, first.size)
        assertEquals(listOf("Two"), second.map { it.text })
    }

    @Test fun expiredHistoryDoesNotAppearInNewNotification() {
        NotificationMessageHistory.append("account-a", "conversation", "old", "Old", 1000)
        val newOnly = NotificationMessageHistory.append("account-a", "conversation", "new", "New", 31 * 60 * 1000L)
        assertEquals(listOf("New"), newOnly.map { it.text })
        assertFalse(newOnly.any { it.messageId == "old" })
    }
}
