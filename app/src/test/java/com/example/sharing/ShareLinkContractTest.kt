package com.example.sharing

import android.app.Application
import android.net.Uri
import com.example.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ShareLinkContractTest {

    private val postId = "11111111-2222-3333-4444-555555555555"
    private val reelId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"

    @Test
    fun `every post uses the canonical public Blink URL and routes back to that post`() {
        val link = ShareLinkManager.generateShareLink(ShareContentType.POST, postId)

        assertEquals("https://www.blink.com.ng/post/$postId", link)
        assertEquals(
            AppDeepLink(ShareContentType.POST, postId),
            DeepLinkRouter.parse(Uri.parse(link))
        )
    }

    @Test
    fun `every reel uses the canonical public Blink URL and routes back to that reel`() {
        val link = ShareLinkManager.generateShareLink(ShareContentType.REEL, reelId)

        assertEquals("https://www.blink.com.ng/reel/$reelId", link)
        assertEquals(
            AppDeepLink(ShareContentType.REEL, reelId),
            DeepLinkRouter.parse(Uri.parse(link))
        )
    }

    @Test
    fun `canonical share base remains the production Blink domain`() {
        assertEquals("https://www.blink.com.ng", BuildConfig.SHARE_BASE_URL)
        assertNotNull(DeepLinkRouter.parse(Uri.parse("blink://post/$postId")))
        assertNotNull(DeepLinkRouter.parse(Uri.parse("blink://reel/$reelId")))
    }
}
