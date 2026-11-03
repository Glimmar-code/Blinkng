package com.blinkng.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.blinkng.shared.BlinkContestShare
import org.json.JSONObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.Color as AwtColor
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.JFileChooser

private fun drawCentered(g: java.awt.Graphics2D, text: String, y: Int,
                         px: Int, color: AwtColor, bold: Boolean = false) {
    var size = px
    g.color = color
    do {
        g.font = Font("SansSerif", if (bold) Font.BOLD else Font.PLAIN, size)
        if (g.fontMetrics.stringWidth(text) <= 930 || size <= 24) break
        size -= 2
    } while (true)
    var show = text
    while (g.fontMetrics.stringWidth(show) > 930 && show.isNotEmpty()) show = show.dropLast(1)
    g.drawString(show, (1080 - g.fontMetrics.stringWidth(show)) / 2, y)
}

private fun buildContestDesktopPng(candidate: JSONObject): BufferedImage {
    val image = BufferedImage(1080, 1350, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.paint = java.awt.GradientPaint(0f, 0f, AwtColor(7, 13, 25), 1080f, 1350f, AwtColor(19, 36, 59))
        g.fillRect(0, 0, 1080, 1350)
        g.color = AwtColor(66, 150, 249)
        g.stroke = java.awt.BasicStroke(2f)
        g.draw(RoundRectangle2D.Float(55f, 55f, 970f, 1240f, 32f, 32f))
        drawCentered(g, "B L I N K", 138, 65, AwtColor.WHITE, true)
        drawCentered(g, "OFFICIAL CONTEST ENTRY", 199, 26, AwtColor(186, 208, 235))
        g.color = AwtColor(63, 145, 249)
        g.fillRoundRect(410, 230, 260, 8, 8, 8)

        val avatarUrl = candidate.optString("avatar_url")
        var photo: BufferedImage? = null
        try {
            val uri = java.net.URI(avatarUrl)
            if (uri.scheme == "https") {
                val connection = URL(avatarUrl).openConnection()
                connection.connectTimeout = 3500
                connection.readTimeout = 3500
                if (connection.contentLengthLong <= 3_000_000) {
                    val bytes = connection.getInputStream().use { it.readNBytes(3_000_001) }
                    if (bytes.size <= 3_000_000) photo = ImageIO.read(bytes.inputStream())
                }
            }
        } catch (_: Exception) { /* Fallback to initials. */ }
        g.color = AwtColor(46, 96, 165)
        g.fillOval(379, 273, 322, 322)
        if (photo != null) {
            val prior = g.clip
            g.clip = Ellipse2D.Float(394f, 288f, 292f, 292f)
            val side = minOf(photo.width, photo.height)
            val x = (photo.width - side) / 2
            val y = (photo.height - side) / 2
            g.drawImage(photo, 394, 288, 686, 580, x, y, x + side, y + side, null)
            g.clip = prior
        } else {
            drawCentered(g, candidate.optString("name").take(1).uppercase().ifBlank { "B" },
                473, 133, AwtColor.WHITE, true)
        }
        val link = BlinkContestShare.link(candidate.optString("contest_id"), candidate.optString("id"))
        val qr = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 164, 164,
            mapOf(EncodeHintType.MARGIN to 1))
        g.color = AwtColor.WHITE
        g.fillRoundRect(793, 318, 180, 180, 14, 14)
        g.color = AwtColor.BLACK
        for (y in 0 until qr.height) for (x in 0 until qr.width) {
            if (qr[x, y]) g.fillRect(801 + x, 326 + y, 1, 1)
        }
        g.color = AwtColor(178, 200, 226)
        g.font = Font("SansSerif", Font.BOLD, 23)
        g.drawString("SCAN TO VOTE", 794, 536)
        drawCentered(g, candidate.optString("name").ifBlank { candidate.optString("username") },
            667, 64, AwtColor.WHITE, true)
        drawCentered(g, "@" + candidate.optString("username"), 719, 30, AwtColor(177, 197, 223))
        drawCentered(g, "I'M CONTESTING IN", 783, 26, AwtColor(80, 160, 255), true)
        drawCentered(g, candidate.optString("contest_title"), 831, 40, AwtColor.WHITE, true)
        g.color = AwtColor(19, 36, 59)
        g.fillRoundRect(128, 873, 824, 198, 28, 28)
        drawCentered(g, "RANK #" + candidate.optInt("rank"), 957, 66, AwtColor.WHITE, true)
        drawCentered(g, candidate.optInt("votes").toString() + " VOTES SO FAR", 1010, 30, AwtColor(177,197,223))
        drawCentered(g, "HELP ME REACH THE TOP!", 1149, 38, AwtColor.WHITE, true)
        drawCentered(g, "Support me with your FREE vote.", 1207, 30, AwtColor(180,204,234))
        drawCentered(g, "blink.com.ng / contest", 1290, 25, AwtColor(104,181,255))
        return image
    } finally {
        g.dispose()
    }
}

@Composable
fun BlinkDesktopContestShareDialog(
    candidate: JSONObject,
    onDismiss: () -> Unit,
    onShareRecorded: (String) -> Unit = {}
) {
    val contestId = candidate.optString("contest_id")
    val id = candidate.optString("id")
    if (!BlinkContestShare.isValid(contestId, id)) return
    val url = BlinkContestShare.link(contestId, id)
    val name = candidate.optString("name").ifBlank { candidate.optString("username") }
    val caption = BlinkContestShare.message(name, candidate.optString("contest_title"),
        candidate.optInt("rank"), candidate.optInt("votes"), url)
    var message by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF0C1424),
            modifier = Modifier.widthIn(min = 440.dp, max = 560.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Share my contest flyer", color = Color.White,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(Modifier.fillMaxWidth().background(Color(0xFF17263C), RoundedCornerShape(18.dp))
                    .padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("B L I N K", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                    Text("OFFICIAL CONTEST ENTRY", color = Color(0xFF8CC0FC))
                    Text(name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    Text("@" + candidate.optString("username"), color = Color(0xFFB4C3D6))
                    Text(candidate.optString("contest_title"), color = Color.White,
                        style = MaterialTheme.typography.titleLarge)
                    Text("RANK #" + candidate.optInt("rank") + " · " + candidate.optInt("votes") + " VOTES",
                        color = Color(0xFF93C9FF), fontWeight = FontWeight.Bold)
                    Text("HELP ME REACH THE TOP!", color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("blink.com.ng / contest", color = Color(0xFF9BCBFA))
                }
                if (message.isNotBlank()) Text(message, color = Color(0xFFACD6FF))
                Button(onClick = {
                    try {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(caption), null)
                        message = "Voting message copied. Paste it into WhatsApp or any other app."
                        onShareRecorded("copy")
                    } catch (_: Exception) { message = "Clipboard unavailable." }
                }, modifier = Modifier.fillMaxWidth()) { Text("Copy voting message and link") }
                OutlinedButton(onClick = {
                    try {
                        val chooser = JFileChooser().apply {
                            selectedFile = File("BLINK-Contest-" + id + ".png")
                            dialogTitle = "Save customized contest flyer"
                        }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            val chosen = chooser.selectedFile
                            val file = if (chosen.extension.lowercase() == "png") chosen
                                else File(chosen.parentFile, chosen.name + ".png")
                            check(ImageIO.write(buildContestDesktopPng(candidate), "png", file))
                            message = "Flyer saved. Share the PNG together with the copied voting link."
                            onShareRecorded("desktop")
                        }
                    } catch (e: Exception) {
                        message = "Couldn't save flyer: " + (e.message ?: "Unknown error")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Save personalized flyer (PNG)") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
