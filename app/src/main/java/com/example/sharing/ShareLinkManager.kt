package com.example.sharing

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.BuildConfig

enum class ShareContentType(val pathSegment: String) {
    PROFILE("profile"),
    POST("post"),
    REEL("reel");

    companion object {
        fun fromPath(value: String?): ShareContentType? =
            entries.firstOrNull { it.pathSegment.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Single source of truth for public Blink profile/post/reel URLs. */
object ShareLinkManager {
    private val baseUrl: String
        get() = BuildConfig.SHARE_BASE_URL.trim().trimEnd('/')

    fun generateShareLink(type: String, id: String): String {
        val contentType = ShareContentType.fromPath(type)
            ?: throw IllegalArgumentException("Unsupported share type: $type")
        return generateShareLink(contentType, id)
    }

    fun generateShareLink(type: ShareContentType, id: String): String {
        val cleanId = id.trim().removePrefix("@")
        require(cleanId.isNotBlank()) { "Share id cannot be blank." }
        return "$baseUrl/${type.pathSegment}/${Uri.encode(cleanId)}"
    }

    fun share(
        context: Context,
        type: ShareContentType,
        id: String,
        title: String,
        message: String = "",
        previewImageUrl: String? = null
    ) {
        val link = generateShareLink(type, id)
        val cleanMessage = message.trim()
        val shareText = if (cleanMessage.isBlank()) link else "$cleanMessage\n$link"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            this.type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newPlainText(title.ifBlank { "Blink link" }, link)
        }

        // Remote preview images are intentionally not sent as EXTRA_STREAM. Social apps
        // obtain the avatar/post/reel preview from the Open Graph metadata of this URL.
        previewImageUrl?.takeIf { it.isNotBlank() }

        val chooser = Intent.createChooser(
            sendIntent,
            title.ifBlank { "Share on Blink" }
        )
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun copyLink(
        context: Context,
        type: ShareContentType,
        id: String,
        toastMessage: String = "Link copied"
    ): String {
        val link = generateShareLink(type, id)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Blink link", link))
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
        return link
    }
}
