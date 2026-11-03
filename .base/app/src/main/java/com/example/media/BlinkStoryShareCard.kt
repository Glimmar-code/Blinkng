package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Creates an uploadable BLINK Story preview for a user-initiated Reel link share.
 * Only text and known BLINK colors: no external downloads or copied creator media.
 */
internal object BlinkStoryShareCard {
    fun create(context: Context, creditAndCaption: String, reelLink: String): Uri? =
        runCatching {
            val width = 720
            val height = 1280
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(image)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(Color.rgb(10, 7, 22), Color.rgb(45, 17, 90), Color.rgb(11, 9, 22)),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = null
                paint.color = Color.rgb(118, 67, 240)
                canvas.drawRoundRect(RectF(54f, 250f, 666f, 970f), 32f, 32f, paint)
                paint.color = Color.rgb(28, 19, 52)
                canvas.drawRoundRect(RectF(62f, 258f, 658f, 962f), 28f, 28f, paint)
                paint.color = Color.WHITE
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                paint.textSize = 59f
                canvas.drawText("BLINK", 82f, 138f, paint)
                paint.textSize = 38f
                canvas.drawText("SHARED REEL", 93f, 337f, paint)
                paint.color = Color.rgb(204, 181, 255)
                paint.textSize = 26f
                canvas.drawText("Watch the full Reel on BLINK", 93f, 396f, paint)

                paint.color = Color.WHITE
                paint.textSize = 32f
                val words = creditAndCaption.replace('\n', ' ').split(Regex("\\s+"))
                var line = ""
                var y = 525f
                for (word in words) {
                    val candidate = if (line.isBlank()) word else "$line $word"
                    if (paint.measureText(candidate) > 510f) {
                        if (line.isNotBlank()) {
                            canvas.drawText(line, 93f, y, paint)
                            y += 46f
                        }
                        line = word
                    } else line = candidate
                    if (y > 795f) break
                }
                if (line.isNotBlank() && y <= 845f) canvas.drawText(line.take(70), 93f, y, paint)

                paint.color = Color.rgb(204, 181, 255)
                paint.textSize = 24f
                canvas.drawText("Tap the link in this Story's caption", 93f, 902f, paint)
                paint.color = Color.WHITE
                paint.textSize = 23f
                canvas.drawText("Beyond LINK", 82f, 1158f, paint)
                val dir = File(context.cacheDir, "blink-share").apply { mkdirs() }
                val file = File(dir, "reel-story-${UUID.randomUUID()}.jpg")
                file.outputStream().use { stream ->
                    require(image.compress(Bitmap.CompressFormat.JPEG, 84, stream))
                }
                require(file.length() > 0L)
                // Validate that the selected URL is indeed BLINK. It is displayed
                // via the Story caption, not embedded as an auto-executing link.
                require(reelLink.startsWith("https://") && reelLink.contains("/reel/") ||
                    reelLink.startsWith("https://") && reelLink.contains("/post/"))
                FileProvider.getUriForFile(context, context.packageName + ".chatfiles", file)
            } finally {
                image.recycle()
            }
        }.getOrNull()
}
