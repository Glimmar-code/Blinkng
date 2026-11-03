package com.example.ui.screens

import com.example.data.models.ChatMessage

/**
 * Chat albums show local pending uploads as well as server-confirmed videos.
 * A thumbnail must not eagerly fetch a signed full-size video just to render
 * the conversation: prefer a real server thumbnail or local source.
 */
internal object BlinkChatVideoAlbumMedia {
    private fun local(uri: String?): String? = uri?.takeIf {
        it.startsWith("content://") || it.startsWith("file:")
    }
    private fun remote(uri: String?): String? = uri?.takeIf { it.startsWith("https://") }

    fun playback(message: ChatMessage): String? =
        remote(message.attachedVideoUrl) ?: local(message.attachedVideoUrl)
            ?: remote(message.localAttachmentUri) ?: local(message.localAttachmentUri)

    /** Prefer an uploaded poster; otherwise decode a frame from an actual local video. */
    fun thumbnail(message: ChatMessage): String? =
        remote(message.imagePreviewUrl) ?: local(message.imagePreviewUrl)
            ?: local(message.attachedVideoUrl) ?: local(message.localAttachmentUri)

    fun available(message: ChatMessage): Boolean =
        !message.deletedForEveryone && playback(message) != null
}
