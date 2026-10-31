package com.example.ui.screens

import com.example.data.models.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkChatVideoAlbumMediaTest {
    @Test fun pendingLocalVideoHasPreviewAndPlaybackWithoutRemoteUrl() {
        val pending = ChatMessage(
            id = "pending-video", localAttachmentUri = "content://media/video/42"
        )
        assertTrue(BlinkChatVideoAlbumMedia.available(pending))
        assertEquals("content://media/video/42", BlinkChatVideoAlbumMedia.playback(pending))
        assertEquals("content://media/video/42", BlinkChatVideoAlbumMedia.thumbnail(pending))
    }

    @Test fun localFileVideoUriInAttachedUrlIsPlayableAndSafeToPreview() {
        val local = "file:///data/user/0/com.example/cache/pending.mp4"
        val pending = ChatMessage(id = "pending-file-video", attachedVideoUrl = local)
        assertTrue(BlinkChatVideoAlbumMedia.available(pending))
        assertEquals(local, BlinkChatVideoAlbumMedia.playback(pending))
        assertEquals(local, BlinkChatVideoAlbumMedia.thumbnail(pending))
    }

    @Test fun remoteVideoRequiresRealPreviewToAvoidFullMediaFetch() {
        val uploaded = ChatMessage(
            id = "published-video",
            attachedVideoUrl = "https://private.example/video.mp4"
        )
        assertTrue(BlinkChatVideoAlbumMedia.available(uploaded))
        assertNull(BlinkChatVideoAlbumMedia.thumbnail(uploaded))
        val withPreview = uploaded.copy(imagePreviewUrl = "https://private.example/preview.webp")
        assertEquals("https://private.example/preview.webp",
            BlinkChatVideoAlbumMedia.thumbnail(withPreview))
        assertEquals("https://private.example/video.mp4",
            BlinkChatVideoAlbumMedia.playback(withPreview))
    }

    @Test fun unavailableAndDeletedAttachmentsStayOutOfAlbum() {
        assertFalse(BlinkChatVideoAlbumMedia.available(ChatMessage()))
        assertFalse(BlinkChatVideoAlbumMedia.available(
            ChatMessage(localAttachmentUri = "content://video", deletedForEveryone = true)))
        assertFalse(BlinkChatVideoAlbumMedia.available(
            ChatMessage(attachedVideoUrl = "http://not-secure/video.mp4")))
    }
}
