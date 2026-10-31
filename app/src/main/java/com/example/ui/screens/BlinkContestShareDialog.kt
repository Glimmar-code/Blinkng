package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.blinkng.shared.BlinkContestShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.example.data.supabase.BlinkContestService
import kotlinx.coroutines.launch
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private val flyerHttp = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS).build()
private const val FLYER_W = 1080
private const val FLYER_H = 1350
private val WHITE = Color.WHITE
private val BLUE = Color.rgb(59, 154, 255)
private val MUTED = Color.rgb(178, 195, 219)

private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                   else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

private fun Canvas.center(text: String, y: Float, initialSize: Float, color: Int,
                          maxWidth: Float = 940f, bold: Boolean = false) {
    val paint = textPaint(initialSize, color, bold)
    while (paint.measureText(text) > maxWidth && paint.textSize > 26f) paint.textSize -= 2f
    val label = if (paint.measureText(text) <= maxWidth) text else {
        var trim = text
        while (trim.length > 1 && paint.measureText(trim + "…") > maxWidth) trim = trim.dropLast(1)
        trim + "…"
    }
    drawText(label, (FLYER_W - paint.measureText(label)) / 2, y, paint)
}

private fun loadAvatar(url: String): Bitmap? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
    return runCatching {
        flyerHttp.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body ?: return@use null
            if (body.contentLength() > 3_000_000) return@use null
            val bytes = body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (output.size() <= 3_000_000) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > 3_000_000) return@use null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth !in 1..8192 || bounds.outHeight !in 1..8192) return@use null
            val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / 600)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}

private fun createContestFlyer(candidate: JSONObject, link: String, avatar: Bitmap?): Bitmap {
    val bitmap = Bitmap.createBitmap(FLYER_W, FLYER_H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(0f, 0f, 1080f, 1350f,
        intArrayOf(Color.rgb(7, 13, 25), Color.rgb(16, 25, 44), Color.rgb(3, 8, 17)),
        null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1080f, 1350f, paint)
    paint.shader = null

    // Restrained cobalt framing and premium topographic geometry.
    paint.color = BLUE
    canvas.drawRoundRect(RectF(56f, 56f, 1024f, 1294f), 38f, 38f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BLUE; style = Paint.Style.STROKE; strokeWidth = 2f; alpha = 120
        })
    paint.color = Color.rgb(25, 48, 80)
    canvas.drawCircle(1000f, 160f, 150f, paint)
    canvas.drawCircle(60f, 1170f, 110f, paint)
    canvas.center("B L I N K", 142f, 62f, WHITE, 900f, true)
    canvas.center("OFFICIAL CONTEST ENTRY", 202f, 25f, MUTED)
    paint.color = BLUE
    canvas.drawRoundRect(RectF(410f, 230f, 670f, 237f), 4f, 4f, paint)

    val cx = 540f
    val cy = 434f
    paint.color = Color.rgb(19, 35, 57)
    canvas.drawCircle(cx, cy, 170f, paint)
    paint.color = BLUE
    canvas.drawCircle(cx, cy, 157f, paint)
    if (avatar != null) {
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(avatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
                val matrix = Matrix()
                val crop = 292f / minOf(avatar.width, avatar.height).toFloat()
                matrix.setScale(crop, crop)
                matrix.postTranslate(cx - avatar.width * crop / 2f, cy - avatar.height * crop / 2f)
                shader.setLocalMatrix(matrix)
            }
        }
        canvas.drawCircle(cx, cy, 146f, imagePaint)
    } else {
        paint.color = Color.rgb(34, 57, 86)
        canvas.drawCircle(cx, cy, 146f, paint)
        canvas.center(candidate.optString("name").take(1).uppercase().ifBlank { "B" },
            cy + 43f, 130f, WHITE, 220f, true)
    }

    // A real scan-to-vote QR also works when a social app drops EXTRA_TEXT for image shares.
    val qr = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 164, 164,
        mapOf(EncodeHintType.MARGIN to 1))
    paint.color = WHITE
    canvas.drawRoundRect(RectF(793f, 318f, 973f, 498f), 14f, 14f, paint)
    paint.color = Color.BLACK
    for (y in 0 until qr.height) {
        for (x in 0 until qr.width) {
            if (qr[x, y]) canvas.drawRect(801f + x, 326f + y,
                802f + x, 327f + y, paint)
        }
    }
    canvas.drawText("SCAN TO VOTE", 794f, 535f,
        textPaint(22f, MUTED, true))

    val name = candidate.optString("name").ifBlank { candidate.optString("username") }
    canvas.center(name, 662f, 65f, WHITE, 880f, true)
    canvas.center("@" + candidate.optString("username"), 710f, 29f, MUTED)
    canvas.center("I'M CONTESTING IN", 781f, 27f, BLUE, 900f, true)
    canvas.center(candidate.optString("contest_title"), 829f, 37f, WHITE, 880f, true)

    paint.color = Color.rgb(18, 32, 52)
    canvas.drawRoundRect(RectF(129f, 870f, 951f, 1072f), 28f, 28f, paint)
    val rank = candidate.optInt("rank")
    canvas.center(if (rank > 0) "RANK #$rank" else "CONTESTANT", 949f, 64f, WHITE, 730f, true)
    canvas.center(candidate.optInt("votes").toString() + " VOTES SO FAR", 1008f, 29f, MUTED)

    canvas.center("HELP ME REACH THE TOP!", 1147f, 38f, WHITE, 850f, true)
    canvas.center("Your support matters. Vote for me for FREE.", 1202f, 27f, MUTED)
    canvas.center("OPEN BLINK & VOTE  •  ONE VOTE PER ACCOUNT", 1253f, 22f, BLUE, 850f, true)

    // The full clickable URL is attached to the shared image as caption.
    // On the image itself the official domain is unmistakable.
    canvas.center("blink.com.ng  /  contest", 1322f, 22f, MUTED)
    return bitmap
}

/** Real PNG is attached to ACTION_SEND; tapping its caption link opens the exact candidate. */
@Composable
fun BlinkContestShareDialog(candidate: JSONObject, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val eventApi = remember { BlinkContestService() }
    val id = candidate.optString("id")
    val contestId = candidate.optString("contest_id")
    if (!BlinkContestShare.isValid(contestId, id)) return
    val link = remember(contestId, id) { BlinkContestShare.link(contestId, id) }
    val name = candidate.optString("name").ifBlank { candidate.optString("username") }
    val caption = remember(candidate.toString()) {
        BlinkContestShare.message(name, candidate.optString("contest_title"),
            candidate.optInt("rank"), candidate.optInt("votes"), link)
    }
    // Prefer the contestant's approved submitted PHOTO over an unrelated
    // profile avatar; videos use the profile avatar as the still-image fallback.
    val entryPhoto = candidate.optString("media_url")
    val portraitUrl = if (contestMediaExtension(entryPhoto) in
        setOf("jpg", "jpeg", "png", "webp")) entryPhoto
        else candidate.optString("avatar_url")
    val candidateSnapshot = candidate.toString()
    var rendered by remember(contestId, id, candidateSnapshot) {
        mutableStateOf<Bitmap?>(null)
    }
    var sharing by remember { mutableStateOf(false) }
    LaunchedEffect(contestId, id, candidateSnapshot, portraitUrl) {
        rendered = withContext(Dispatchers.IO) {
            val portrait = loadAvatar(portraitUrl)
            try { createContestFlyer(candidate, link, portrait) }
            finally { portrait?.recycle() }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Share contestant flyer", style = MaterialTheme.typography.titleLarge)
                Text("Personalized for " + name, style = MaterialTheme.typography.bodySmall)
                val preview = rendered
                if (preview == null) {
                    Box(Modifier.fillMaxWidth().height(300.dp),
                        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Image(bitmap = preview.asImageBitmap(), contentDescription = "Personalized voting flyer for " + name,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 390.dp),
                        contentScale = ContentScale.Fit)
                }
                Button(
                    enabled = rendered != null && !sharing, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val image = rendered ?: return@Button
                        sharing = true
                        scope.launch {
                            try {
                                // Encoding a 1080×1350 PNG on the Compose UI thread
                                // freezes low-memory devices. Write it on Dispatchers.IO.
                                val content = withContext(Dispatchers.IO) {
                                    val folder = File(context.cacheDir, "blink-share").apply {
                                        check(exists() || mkdirs()) { "Cannot create share cache" }
                                    }
                                    val file = File(folder, "blink-contest-$contestId-$id.png")
                                    val saved = FileOutputStream(file).use {
                                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                                    }
                                    check(saved) { "Contest flyer could not be saved" }
                                    FileProvider.getUriForFile(
                                        context, context.packageName + ".chatfiles", file
                                    )
                                }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, content)
                                    putExtra(Intent.EXTRA_TEXT, caption)
                                    putExtra(Intent.EXTRA_TITLE, "Vote for " + name)
                                    clipData = ClipData.newUri(context.contentResolver, "BLINK Contest", content)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share BLINK Contest flyer"))
                                scope.launch { eventApi.recordShare(contestId, id, "native") }
                            } catch (_: Exception) {
                                Toast.makeText(context,
                                    "Couldn't share flyer. Copy the voting link instead.",
                                    Toast.LENGTH_LONG).show()
                            } finally {
                                sharing = false
                            }
                        }
                    }
                ) { Text("Share flyer and voting link") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val copied = runCatching {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                                ?: error("Clipboard is unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText(
                                "BLINK contestant voting link", caption
                            ))
                        }.isSuccess
                        Toast.makeText(context,
                            if (copied) "Voting message copied" else "Unable to copy voting link",
                            Toast.LENGTH_SHORT).show()
                        // Do not inflate share analytics when clipboard access failed.
                        if (copied) scope.launch {
                            eventApi.recordShare(contestId, id, "copy")
                        }
                    }
                ) { Text("Copy voting message") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
