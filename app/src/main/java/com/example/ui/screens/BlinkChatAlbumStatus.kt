package com.example.ui.screens

import com.example.data.models.MessageStatus

/**
 * A grouped attachment cannot be considered delivered/read while another
 * item is still uploading or has failed. This is shared by photo and video
 * albums so both surfaces show the same trustworthy receipt status.
 */
internal object BlinkChatAlbumStatus {
    fun aggregate(statuses: List<MessageStatus>): MessageStatus {
        require(statuses.isNotEmpty()) { "Cannot derive an album receipt from no messages" }
        return when {
            MessageStatus.FAILED in statuses -> MessageStatus.FAILED
            MessageStatus.SENDING in statuses -> MessageStatus.SENDING
            statuses.all { it == MessageStatus.READ } -> MessageStatus.READ
            statuses.all { it == MessageStatus.READ || it == MessageStatus.DELIVERED } ->
                MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
    }
}
