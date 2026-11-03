package com.blinkng.desktop.data

import com.blinkng.desktop.media.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DesktopVideoEditorTest {
    @TempDir lateinit var folder: File
    private fun render(edit: VideoEdit, progress: (Double) -> Unit = {}): File = try {
        DesktopVideoEditor.export(edit, folder, progress = progress)
    } catch (error: VideoExportException) { throw AssertionError(error.diagnostic, error) }
    private fun fixture(name: String, color: String, size: String, duration: Int = 1): File {
        val file = File(folder, name)
        val process = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "color=c=$color:s=$size:r=15:d=$duration", "-c:v", "mpeg4", file.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(folder, "$name.log")).start()
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(0, process.exitValue())
        return file
    }
    private fun frame(video: File, seconds: String): java.awt.image.BufferedImage {
        val target = File(folder, "frame-$seconds.png")
        val p = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", "-ss", seconds,
            "-i", video.absolutePath, "-frames:v", "1", target.absolutePath).redirectErrorStream(true)
            .redirectOutput(File(folder, "frame-$seconds.log")).start()
        assertTrue(p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, p.exitValue())
        return javax.imageio.ImageIO.read(target)
    }
    @Test fun packagedProcessorExportsRealOrderedMontageWithoutDistortingItsCanvas() {
        val first = fixture("one.mp4", "red", "160x120")
        val second = fixture("two.mp4", "blue", "120x160")
        val bytes = first.readBytes()
        val progress = mutableListOf<Double>()
        val output = render(VideoEdit(listOf(VideoClip(first), VideoClip(second)), aspect = "Square", text = "BLINK fixture"), progress = { progress += it })
        val info = DesktopVideoEditor.probe(output)
        assertEquals(1080, info.width); assertEquals(1080, info.height)
        assertTrue(info.duration in 1.8..2.2)
        assertTrue(info.audio)
        assertArrayEquals(bytes, first.readBytes())
        assertEquals(1.0, progress.last())
        val red = frame(output, "0.3"); val blue = frame(output, "1.3")
        assertTrue(java.awt.Color(red.getRGB(540, 540)).red > 180)
        assertTrue(java.awt.Color(blue.getRGB(540, 540)).blue > 180)
        assertTrue(java.awt.Color(red.getRGB(540, 10)).red < 30) // letterbox, not stretch
        assertTrue(java.awt.Color(blue.getRGB(10, 540)).blue < 30)
    }
    @Test fun trimmingSpeedAndCancellationAreRealAndDoNotDeleteSources() {
        val source = fixture("source.mp4", "green", "160x120")
        val edited = render(VideoEdit(listOf(VideoClip(source, .2, .8)), speed = 2.0))
        assertTrue(DesktopVideoEditor.probe(edited).duration in .2.. .6)
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            DesktopVideoEditor.export(VideoEdit(listOf(VideoClip(source))), folder, cancel = { throw java.util.concurrent.CancellationException() })
        }
        assertTrue(source.isFile)
        assertTrue(folder.listFiles()!!.none { it.extension == "txt" || it.name.startsWith("blink-overlay") })
    }
    @Test fun customAudioAndNonDefaultFiltersExportWithoutLicensedMusicAssets() {
        val source = fixture("audio-source.mp4", "red", "160x120")
        val audio = File(folder, "tone.wav")
        val generator = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=1", audio.absolutePath).start()
        assertTrue(generator.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, generator.exitValue())
        for (filter in listOf("Mono", "Warm", "Cool")) {
            val output = render(VideoEdit(listOf(VideoClip(source)), filter = filter, brightness = .05, contrast = 1.1, music = audio))
            assertTrue(DesktopVideoEditor.probe(output).audio)
            val raw = File(folder, "$filter.raw")
            val decode = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", "-i", output.absolutePath,
                "-vn", "-ac", "1", "-ar", "8000", "-f", "f32le", raw.absolutePath).start()
            assertTrue(decode.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, decode.exitValue())
            val samples = java.nio.ByteBuffer.wrap(raw.readBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            var peak = 0f
            while (samples.hasRemaining()) peak = maxOf(peak, kotlin.math.abs(samples.get()))
            assertTrue(peak > .001f, "The added tone must be audible")
        }
    }
    @Test fun cancellingAnActiveExportRemovesPartialOutput() {
        val source = fixture("long-source.mp4", "blue", "320x240", 30)
        val before = folder.listFiles()!!.map { it.name }.toSet()
        var checkpoints = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            DesktopVideoEditor.export(VideoEdit(listOf(VideoClip(source)), aspect = "Square"), folder, cancel = {
                checkpoints++
                if (checkpoints >= 2) throw java.util.concurrent.CancellationException()
            })
        }
        assertTrue(checkpoints >= 2)
        assertEquals(before, folder.listFiles()!!.map { it.name }.toSet())
        assertTrue(source.length() > 0)
    }
}
