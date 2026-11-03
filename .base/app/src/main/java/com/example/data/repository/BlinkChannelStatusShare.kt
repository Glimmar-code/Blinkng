package com.example.data.repository

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.blinkng.shared.BlinkChannelUpdate
import com.example.data.models.Story
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Explicit user-initiated reshare of an existing broadcast to the user's
 * own BLINK Status. Creates an attributed share CARD, not a copied private
 * channel attachment or leaked follower list.
 */
object BlinkChannelStatusShare {
    fun publicUrl(channelId: String, updateId: String): String =
        "https://blink.com.ng/channel/$channelId/update/$updateId"

    suspend fun publish(channelId: String, channelName: String, update: BlinkChannelUpdate) =
        withContext(Dispatchers.IO) {
            val service = SupabaseService()
            val uid = service.getCurrentUserId() ?: error("Sign in to share a Status.")
            val bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val stream = ByteArrayOutputStream()
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.rgb(16, 13, 32))
                val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 48f; isFakeBoldText = true
                }
                val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 37f
                }
                val footPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(194, 176, 255); textSize = 27f
                }
                canvas.drawText("BLINK · CHANNEL", 52f, 130f, footPaint)
                val title = channelName.take(35)
                canvas.drawText(title, 52f, 220f, titlePaint)
                val message = update.body.trim().take(540).ifBlank {
                    if (update.mediaKind == "poll") update.pollQuestion.orEmpty()
                    else "Shared a ${update.mediaKind} update"
                }
                val layout = StaticLayout.Builder.obtain(
                    message, 0, message.length, bodyPaint, 616
                ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(8f, 1.03f)
                    .setMaxLines(17)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                    .build()
                canvas.save()
                canvas.translate(52f, 340f)
                layout.draw(canvas)
                canvas.restore()
                canvas.drawText("Read the original update on BLINK", 52f, 1190f, footPaint)
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)) {
                    "Couldn't prepare the Channel Status preview."
                }
                val url = service.uploadStoryMedia(uid, stream.toByteArray(), "image/png", false)
                    ?: error("Status media upload failed.")
                val shareCaption = "Forwarded from $channelName · ${publicUrl(channelId, update.id)}"
                val story = Story(
                    id = UUID.randomUUID().toString(),
                    username = "", avatar = "",
                    storyImage = url,
                    caption = shareCaption
                )
                check(service.createStory(story, isVideo = false)) {
                    "BLINK could not publish this Status."
                }
            } finally {
                bitmap.recycle()
                stream.close()
            }
        }
}
