package com.blinkng.shared

import org.junit.Assert.*
import org.junit.Test

class BlinkChatDownloadPolicyTest {
    @Test fun onlyHttpsWithAHostAndWithoutCredentialsIsPermitted() {
        assertTrue(BlinkChatDownloadPolicy.acceptsSource(
            "https://storage.example.com/media/image.jpg?token=abc"
        ))
        assertFalse(BlinkChatDownloadPolicy.acceptsSource("http://storage.example.com/image.jpg"))
        assertFalse(BlinkChatDownloadPolicy.acceptsSource("https://"))
        assertFalse(BlinkChatDownloadPolicy.acceptsSource("https://name:pass@host.com/image.jpg"))
        assertFalse(BlinkChatDownloadPolicy.acceptsSource("file:///private/photo.jpg"))
        assertFalse(BlinkChatDownloadPolicy.acceptsSource("https://host.com/image.jpg#fragment"))
    }

    @Test fun boundedImageResponseAllowsUnknownLengthButBlocksOverflow() {
        assertTrue(BlinkChatDownloadPolicy.acceptsLength(-1L))
        assertTrue(BlinkChatDownloadPolicy.acceptsLength(
            BlinkChatDownloadPolicy.MAX_IMAGE_BYTES
        ))
        assertFalse(BlinkChatDownloadPolicy.acceptsLength(
            BlinkChatDownloadPolicy.MAX_IMAGE_BYTES + 1L
        ))
        assertTrue(BlinkChatDownloadPolicy.acceptsMime("image/jpeg; charset=binary"))
        assertTrue(BlinkChatDownloadPolicy.acceptsMime("application/octet-stream"))
        assertFalse(BlinkChatDownloadPolicy.acceptsMime("text/html"))
    }
}
