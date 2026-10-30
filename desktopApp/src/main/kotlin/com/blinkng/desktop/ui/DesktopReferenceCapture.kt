package com.blinkng.desktop.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import com.blinkng.desktop.media.DesktopVideoEditor

/** Opt-in visual diagnostic. Never constructs app state, loads accounts or invokes uploads. */
internal object DesktopReferenceCapture {
    fun run(destination: String?): Int {
        val folder = destination?.takeIf(String::isNotBlank)?.let(::File) ?: return 2
        if (folder.exists() && (!folder.isDirectory || folder.listFiles()?.isNotEmpty() != false)) return 2
        if (!folder.exists() && !folder.mkdirs()) return 2
        val report = File(folder, "reference-ui.json")
        return try {
            check(!GraphicsEnvironment.isHeadless()) { "An interactive display is required for real GUI captures" }
            val photo = File(folder, "synthetic-photo.png")
            val fixture = BufferedImage(640, 420, BufferedImage.TYPE_INT_RGB)
            fixture.createGraphics().let { g ->
                try {
                    g.color = Color(65, 100, 160); g.fillRect(0, 0, 640, 420)
                    g.color = Color(60, 120, 70); g.fillRect(0, 260, 640, 160)
                    g.color = Color(225, 210, 175); g.fillRect(140, 140, 360, 180)
                    g.color = Color.BLACK; g.drawString("BLINK synthetic visual fixture", 210, 230)
                } finally { g.dispose() }
            }
            ImageIO.write(fixture, "png", photo); fixture.flush()
            val video = File(folder, "synthetic-video.mp4")
            val process = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=blue:s=160x120:r=15:d=1", "-c:v", "mpeg4", video.absolutePath)
                .redirectErrorStream(true).redirectOutput(File(folder, "fixture.log")).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Synthetic video generation timed out") }
            check(process.exitValue() == 0) { "Synthetic video generation failed" }
            var failure: Throwable? = null
            application(exitProcessOnExit = false) {
                var stage by remember { mutableStateOf(0) }
                Window(onCloseRequest = ::exitApplication, title = "BLINK isolated visual diagnostic",
                    state = rememberWindowState(width = 1050.dp, height = 900.dp)) {
                    BlinkDesktopTheme(appearance = "dark") {
                        Text("Synthetic media only · no account or backend")
                        if (stage == 0) DesktopPhotoEditorDialog(photo, {}, {})
                        else DesktopVideoEditorDialog(video, {}, {})
                    }
                }
                LaunchedEffect(Unit) {
                    try {
                        // Allow native window creation, asynchronous preview and first rendered frames.
                        delay(4000)
                        capture(folder, "photo-editor")
                        stage = 1
                        delay(2500)
                        capture(folder, "video-editor-top")
                        withContext(Dispatchers.IO) {
                            val bounds = dialogBounds()
                            Robot().apply { mouseMove(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2); mouseWheel(12) }
                        }
                        delay(1500)
                        capture(folder, "video-editor-bottom")
                    } catch (error: Throwable) { failure = error }
                    finally { exitApplication() }
                }
            }
            failure?.let { throw it }
            report.writeText(JSONObject().put("status", "captured-awaiting-review")
                .put("platform", System.getProperty("os.name"))
                .put("captures", listOf("photo-editor.png", "video-editor-top.png", "video-editor-bottom.png"))
                .put("scope", "Actual photo and video editors with synthetic files; no client/backend integration").toString())
            0
        } catch (error: Throwable) {
            report.writeText(JSONObject().put("status", "failed").put("message", error.message ?: error.javaClass.simpleName).toString())
            1
        }
    }

    private fun dialogBounds(): java.awt.Rectangle {
        // Compose can host a dialog in the owning window. Capture only this process's
        // visible editor/application window, never the whole desktop or another app.
        val windows = java.awt.Window.getWindows().filter { it.isShowing && it.width >= 240 && it.height >= 240 }
        val dialog = windows.lastOrNull { it.owner != null } ?: windows.lastOrNull()
            ?: error("The editor window is not visible")
        return dialog.bounds.also { check(it.width >= 240 && it.height >= 240) { "Editor dialog is too small" } }
    }

    private suspend fun capture(folder: File, name: String) = withContext(Dispatchers.IO) {
        val image = Robot().createScreenCapture(dialogBounds())
        try {
            val colors = mutableSetOf<Int>()
            for (y in 0 until image.height step 8) for (x in 0 until image.width step 8) colors += image.getRGB(x, y)
            check(colors.size > 30) { "Editor capture is blank or obscured" }
            check(ImageIO.write(image, "png", File(folder, "$name.png"))) { "PNG encoder is unavailable" }
        } finally { image.flush() }
    }
}
