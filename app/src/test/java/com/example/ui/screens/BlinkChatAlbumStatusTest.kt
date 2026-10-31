package com.example.ui.screens

import com.example.data.models.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChatAlbumStatusTest {
    @Test fun groupedPhotoAndVideoReceiptsRequireEveryClipToProgress() {
        val policy = BlinkChatAlbumStatus
        assertEquals(MessageStatus.SENT,
            policy.aggregate(listOf(MessageStatus.SENT, MessageStatus.DELIVERED)))
        assertEquals(MessageStatus.DELIVERED,
            policy.aggregate(listOf(MessageStatus.READ, MessageStatus.DELIVERED)))
        assertEquals(MessageStatus.READ,
            policy.aggregate(listOf(MessageStatus.READ, MessageStatus.READ)))
    }

    @Test fun aPendingOrFailedClipCannotShowBlueReadTicks() {
        val policy = BlinkChatAlbumStatus
        assertEquals(MessageStatus.SENDING,
            policy.aggregate(listOf(MessageStatus.READ, MessageStatus.SENDING)))
        assertEquals(MessageStatus.FAILED,
            policy.aggregate(listOf(MessageStatus.READ, MessageStatus.FAILED)))
        assertEquals(MessageStatus.FAILED,
            policy.aggregate(listOf(MessageStatus.SENDING, MessageStatus.FAILED)))
    }

    @Test fun emptyAlbumNeverClaimsSuccessfulDelivery() {
        assertTrue(runCatching {
            BlinkChatAlbumStatus.aggregate(emptyList())
        }.exceptionOrNull() is IllegalArgumentException)
    }
}
