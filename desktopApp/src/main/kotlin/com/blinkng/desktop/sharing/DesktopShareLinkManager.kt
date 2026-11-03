package com.blinkng.desktop.sharing

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

object DesktopShareLinkManager {
    const val BASE_URL: String = "https://www.blink.com.ng"

    fun generate(postId: String, isReel: Boolean): String {
        val kind = if (isReel) "reel" else "post"
        return "$BASE_URL/$kind/$postId"
    }

    fun generateProfile(profileIdOrUsername: String): String =
        "$BASE_URL/profile/${profileIdOrUsername.trim().removePrefix("@")}"

    fun copyProfileToClipboard(profileIdOrUsername: String): Boolean {
        val link = generateProfile(profileIdOrUsername)
        return runCatching {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(link), null)
        }.isSuccess
    }

    fun copyToClipboard(postId: String, isReel: Boolean): Boolean {
        val link = generate(postId = postId, isReel = isReel)
        return runCatching {
            Toolkit.getDefaultToolkit()
                .systemClipboard
                .setContents(StringSelection(link), null)
        }.isSuccess
    }
}
