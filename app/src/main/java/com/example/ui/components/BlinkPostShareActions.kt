package com.example.ui.components

import android.app.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * One share contract for BLINK's Create Post confirmation screen. Post links
 * are not available until publication, so pre-publish sharing is caption/media.
 */
internal object BlinkPostShareActions {
    fun caption(text: String, pollQuestion: String): String =
        text.trim().ifBlank { pollQuestion.trim() }

    fun createIntent(
        caption: String,
        imageUris: List<String>,
        videoUri: String?,
        targetPackage: String? = null
    ): Intent {
        // Third-party apps must receive grantable content URIs, not private
        // file paths or URLs which ACTION_SEND recipients cannot open.
        val video = videoUri?.let(Uri::parse)?.takeIf { it.scheme == "content" }
        val photos = if (video == null && videoUri.isNullOrBlank()) {
            imageUris.map(Uri::parse).filter { it.scheme == "content" }.distinct().take(10)
        } else emptyList()
        val media = if (video != null) listOf(video) else photos
        val action = if (media.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
        return Intent(action).apply {
            type = when {
                video != null -> "video/*"
                media.isNotEmpty() -> "image/*"
                else -> "text/plain"
            }
            if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
            if (media.size == 1) putExtra(Intent.EXTRA_STREAM, media.first())
            if (media.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(media))
            if (media.isNotEmpty()) {
                clipData = ClipData.newRawUri("BLINK shared media", media.first()).also { clips ->
                    media.drop(1).forEach { clips.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (targetPackage != null) setPackage(targetPackage)
        }
    }

    fun hasShareableContent(caption: String, imageUris: List<String>, videoUri: String?): Boolean =
        caption.isNotBlank() || createIntent(caption, imageUris, videoUri)
            .hasExtra(Intent.EXTRA_STREAM)

    fun copyCaption(context: Context, caption: String): Boolean {
        if (caption.isBlank()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("BLINK post caption", caption))
        return true
    }

    fun share(
        context: Context,
        caption: String,
        images: List<String>,
        video: String?,
        whatsAppOnly: Boolean
    ): Boolean {
        if (!hasShareableContent(caption, images, video)) return false
        val general = createIntent(caption, images, video)
        // Do not silently omit a selected image or Reel when its URI is no
        // longer readable by a receiving app (for example, an old file:// draft).
        if ((images.isNotEmpty() || !video.isNullOrBlank()) &&
            !general.hasExtra(Intent.EXTRA_STREAM)) return false
        if (whatsAppOnly) {
            for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                try {
                    context.startActivity(Intent(general).setPackage(packageName))
                    return true
                } catch (_: ActivityNotFoundException) {
                    // WhatsApp / Business may not be installed.
                } catch (_: SecurityException) {
                    // Cannot open the target; use the system chooser instead.
                }
            }
        }
        return try {
            context.startActivity(Intent.createChooser(general, "Share BLINK post"))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
