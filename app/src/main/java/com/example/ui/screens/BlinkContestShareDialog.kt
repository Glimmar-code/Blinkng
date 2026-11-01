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
import android.graphics.RadialGradient
import android.graphics.SweepGradient
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
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay

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

private fun Canvas.centerAt(text: String, cx: Float, y: Float, initialSize: Float, color: Int,
                            maxWidth: Float, bold: Boolean = false, spacing: Float = 0f) {
    val paint = textPaint(initialSize, color, bold).apply { letterSpacing = spacing }
    while (paint.measureText(text) > maxWidth && paint.textSize > 22f) paint.textSize -= 2f
    val label = if (paint.measureText(text) <= maxWidth) text else {
        var trim = text
        while (trim.length > 1 && paint.measureText(trim + "…") > maxWidth) trim = trim.dropLast(1)
        trim + "…"
    }
    drawText(label, cx - paint.measureText(label) / 2f, y, paint)
}

private fun rankTone(rank: Int): Int = when (rank) {
    1 -> Color.rgb(255, 200, 87)
    2 -> Color.rgb(201, 212, 227)
    3 -> Color.rgb(217, 145, 91)
    else -> BLUE
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
    val rank = candidate.optInt("rank")
    val tone = rankTone(rank)

    // Deep navy base.
    paint.shader = LinearGradient(0f, 0f, 1080f, 1350f,
        intArrayOf(Color.rgb(7, 13, 25), Color.rgb(16, 25, 44), Color.rgb(3, 8, 17)),
        null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1080f, 1350f, paint)
    // Cobalt glow behind the portrait.
    paint.shader = RadialGradient(540f, 434f, 560f,
        intArrayOf(Color.argb(105, 59, 154, 255), Color.argb(0, 59, 154, 255)),
        null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, 1080f, 1000f, paint)
    paint.shader = null

    // Subtle dot-grid texture.
    paint.color = Color.argb(20, 255, 255, 255)
    var gy = 90f
    while (gy < 1300f) {
        var gx = 90f
        while (gx < 1000f) { canvas.drawCircle(gx, gy, 2f, paint); gx += 60f }
        gy += 60f
    }

    // Restrained cobalt framing.
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

    // Portrait with gradient ring.
    val cx = 540f
    val cy = 434f
    paint.color = Color.rgb(19, 35, 57)
    canvas.drawCircle(cx, cy, 174f, paint)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 12f
        shader = SweepGradient(cx, cy,
            intArrayOf(BLUE, Color.rgb(125, 224, 255), BLUE, Color.rgb(125, 224, 255), BLUE), null)
    }
    canvas.drawCircle(cx, cy, 160f, ring)
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
    // Rank badge on the portrait.
    if (rank > 0) {
        paint.color = Color.rgb(7, 13, 25)
        canvas.drawCircle(cx + 122f, cy + 122f, 54f, paint)
        paint.color = tone
        canvas.drawCircle(cx + 122f, cy + 122f, 46f, paint)
        canvas.centerAt("#$rank", cx + 122f, cy + 140f, 42f,
            if (rank in 1..3) Color.rgb(11, 22, 38) else WHITE, 72f, true)
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
    canvas.centerAt("SCAN TO VOTE", 883f, 535f, 22f, MUTED, 190f, true, 0.08f)

    val name = candidate.optString("name").ifBlank { candidate.optString("username") }
    canvas.center(name, 662f, 65f, WHITE, 880f, true)
    canvas.center("@" + candidate.optString("username"), 710f, 29f, MUTED)
    canvas.center("I'M CONTESTING IN", 781f, 27f, BLUE, 900f, true)
    canvas.center(candidate.optString("contest_title"), 829f, 37f, WHITE, 880f, true)

    // Stats card: rank | votes.
    paint.color = Color.rgb(18, 32, 52)
    canvas.drawRoundRect(RectF(129f, 870f, 951f, 1072f), 28f, 28f, paint)
    paint.color = Color.argb(70, 59, 154, 255)
    canvas.drawRect(539f, 904f, 541f, 1040f, paint)
    canvas.centerAt("RANK", 334f, 926f, 24f, MUTED, 330f, true, 0.12f)
    canvas.centerAt(if (rank > 0) "#$rank" else "—", 334f, 1018f, 84f, tone, 360f, true)
    canvas.centerAt("VOTES", 746f, 926f, 24f, MUTED, 330f, true, 0.12f)
    canvas.centerAt(NumberFormat.getIntegerInstance(Locale.US).format(candidate.optInt("votes")),
        746f, 1018f, 84f, WHITE, 360f, true)

    // Call to action pill.
    paint.shader = LinearGradient(220f, 0f, 860f, 0f, BLUE, Color.rgb(30, 111, 230),
        Shader.TileMode.CLAMP)
    canvas.drawRoundRect(RectF(220f, 1098f, 860f, 1174f), 38f, 38f, paint)
    paint.shader = null
    canvas.centerAt("HELP BY VOTING", 540f, 1150f, 40f, WHITE, 580f, true, 0.06f)
    canvas.center("Your support matters. Vote for me for FREE.", 1220f, 27f, MUTED, 850f)
    canvas.center("OPEN BLINK & VOTE  •  ONE VOTE PER ACCOUNT", 1262f, 22f, BLUE, 850f, true)

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

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1800); copied = false } }
    val haptic = LocalHapticFeedback.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Share contestant flyer", style = MaterialTheme.typography.titleLarge)
                Text("Personalized for " + name, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Crossfade(targetState = rendered, animationSpec = tween(450), label = "flyerPreview") { preview ->
                    if (preview == null) {
                        Box(Modifier.height(390.dp).aspectRatio(FLYER_W.toFloat() / FLYER_H)
                            .clip(RoundedCornerShape(16.dp)).contestShimmer(),
                            contentAlignment = Alignment.Center) {
                            Text("Designing your flyer…", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        var appeared by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { appeared = true }
                        val pop by animateFloatAsState(
                            if (appeared) 1f else 0.9f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow),
                            label = "flyerPop")
                        Image(bitmap = preview.asImageBitmap(),
                            contentDescription = "Personalized voting flyer for " + name,
                            modifier = Modifier.heightIn(max = 390.dp)
                                .graphicsLayer { scaleX = pop; scaleY = pop; alpha = pop.coerceIn(0f, 1f) }
                                .shadow(14.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Fit)
                    }
                }
                Button(
                    enabled = rendered != null && !sharing,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        val image = rendered ?: return@Button
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                ) {
                    if (sharing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = LocalContentColor.current)
                        Spacer(Modifier.width(8.dp))
                        Text("Preparing flyer…")
                    } else Text("Share flyer and voting link")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        val didCopy = runCatching {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                                ?: error("Clipboard is unavailable")
                            clipboard.setPrimaryClip(ClipData.newPlainText(
                                "BLINK contestant voting link", caption
                            ))
                        }.isSuccess
                        if (didCopy) {
                            copied = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else {
                            Toast.makeText(context, "Unable to copy voting link",
                                Toast.LENGTH_SHORT).show()
                        }
                        // Do not inflate share analytics when clipboard access failed.
                        if (didCopy) scope.launch {
                            eventApi.recordShare(contestId, id, "copy")
                        }
                    }
                ) {
                    Crossfade(targetState = copied, animationSpec = tween(200), label = "copyLabel") { done ->
                        Text(if (done) "Copied ✓" else "Copy voting message")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
