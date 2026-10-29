package com.blinkng.desktop.media

import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Explicit packaged-app diagnostic. No sessions, accounts, services or real user media. */
object DesktopMediaSmoke {
    fun run(destination: String?): Int {
        System.setProperty("java.awt.headless", "true")
        val folder = destination?.takeIf { it.isNotBlank() }?.let(::File) ?: return 2
        if (!folder.isDirectory && !folder.mkdirs()) return 2
        val result = File(folder, "media-smoke.json")
        return try {
            val source = File(folder, "synthetic-source.mp4")
            val process = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "color=c=blue:s=160x120:r=15:d=1", "-c:v", "mpeg4", source.absolutePath)
                .redirectErrorStream(true).redirectOutput(File(folder, "fixture.log")).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Fixture generation timed out") }
            check(process.exitValue() == 0) { "Fixture generation failed" }
            val output = DesktopVideoEditor.export(VideoEdit(listOf(VideoClip(source)), aspect = "Square", text = "BLINK media diagnostic"), folder)
            val info = DesktopVideoEditor.probe(output)
            check(info.width == 1080 && info.height == 1080 && info.duration in .8..1.2)
            result.writeText(JSONObject().put("status", "passed").put("width", info.width).put("height", info.height)
                .put("duration", info.duration).put("output", output.name).toString())
            0
        } catch (failure: Throwable) {
            result.writeText(JSONObject().put("status", "failed").put("message", failure.message ?: failure.javaClass.simpleName)
                .put("diagnostic", (failure as? VideoExportException)?.diagnostic ?: "").toString())
            1
        }
    }
}
