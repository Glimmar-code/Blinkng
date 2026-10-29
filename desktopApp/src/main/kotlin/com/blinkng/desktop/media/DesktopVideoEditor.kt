package com.blinkng.desktop.media

import org.bytedeco.javacpp.Loader
import com.blinkng.shared.BlinkReelEditPolicy
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

data class VideoClip(val file: File, val start: Double = 0.0, val end: Double? = null)
data class VideoInfo(val duration: Double, val width: Int, val height: Int, val audio: Boolean)
data class VideoEdit(
    val clips: List<VideoClip>, val aspect: String = "Original", val speed: Double = 1.0,
    val filter: String = "Original", val brightness: Double = 0.0, val contrast: Double = 1.0,
    val originalVolume: Double = 1.0, val music: File? = null, val text: String = "", val sticker: String = "",
)
class VideoExportException(val diagnostic: String) : IllegalStateException("Video export failed. Your source clips are unchanged.")

/** Isolated native processor. No shell, remote URLs or edits to source media. */
object DesktopVideoEditor {
    fun decodePhoto(file: File): java.awt.image.BufferedImage {
        require(file.isFile && file.length() in 1..(25L * 1024 * 1024)) { "Choose a photo up to 25 MB." }
        val data = JSONObject(command(listOf(ffprobe(), "-v", "error", "-protocol_whitelist", "file,pipe", "-show_entries", "stream=width,height", "-of", "json", file.absolutePath), 20))
        val stream = data.getJSONArray("streams").getJSONObject(0)
        val width = stream.getInt("width"); val height = stream.getInt("height")
        require(width > 0 && height > 0 && width.toLong() * height <= DesktopPhotoEditor.MAX_PIXELS) { "This editor supports photos up to 24 megapixels." }
        val decoded = File.createTempFile("blink-photo-decode-", ".png")
        try {
            command(listOf(ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", "-protocol_whitelist", "file,pipe", "-i", file.absolutePath, "-frames:v", "1", decoded.absolutePath), 30)
            return requireNotNull(ImageIO.read(decoded)) { "Cannot decode this photo." }
        } finally { decoded.delete() }
    }
    fun ffmpeg(): String = loadTool(org.bytedeco.ffmpeg.ffmpeg::class.java)
    fun ffprobe(): String = loadTool(org.bytedeco.ffmpeg.ffprobe::class.java)
    private fun loadTool(type: Class<*>): String = try { Loader.load(type) } catch (failure: LinkageError) { throw IllegalStateException("The video processor could not load on this installation.", failure) }
    private fun number(value: Double) = String.format(Locale.ROOT, "%.5f", value)
    fun probe(file: File): VideoInfo {
        require(file.isFile && file.extension.lowercase() in listOf("mp4", "m4v", "mov", "webm")) { "Choose a local MP4, MOV or WebM video." }
        require(file.length() in 1..(200L * 1024 * 1024)) { "Choose a video up to 200 MB." }
        val text = command(listOf(ffprobe(), "-v", "error", "-protocol_whitelist", "file,pipe", "-show_entries", "format=duration:stream=codec_type,width,height", "-of", "json", file.absolutePath), 30)
        val result = JSONObject(text)
        val streams = result.getJSONArray("streams")
        val video = (0 until streams.length()).map { streams.getJSONObject(it) }.firstOrNull { it.optString("codec_type") == "video" }
            ?: throw IllegalArgumentException("Choose a video clip.")
        val duration = result.getJSONObject("format").getString("duration").toDouble()
        require(duration.isFinite() && duration > 0) { "Cannot read this video's duration." }
        return VideoInfo(duration, video.getInt("width"), video.getInt("height"),
            (0 until streams.length()).any { streams.getJSONObject(it).optString("codec_type") == "audio" })
    }

    fun export(edit: VideoEdit, folder: File, cancel: () -> Unit = {}, progress: (Double) -> Unit = {}): File {
        require(edit.clips.size in 1..BlinkReelEditPolicy.MAX_CLIPS) { "Choose 1–4 clips." }
        require(edit.speed in listOf(.5, 1.0, 1.5, 2.0)) { "Choose a supported playback speed." }
        require(edit.brightness in -.5.. .5 && edit.contrast in .5..1.5 && edit.originalVolume in 0.0..1.0)
        require(edit.filter in listOf("Original", "Mono", "Warm", "Cool"))
        val info = edit.clips.map { cancel(); probe(it.file) }
        val durations = edit.clips.mapIndexed { index, clip ->
            val end = clip.end ?: info[index].duration
            require(clip.start.isFinite() && end.isFinite() && clip.start >= 0 && end > clip.start && end <= info[index].duration + .05) { "Set a valid start/end for each clip." }
            (end - clip.start) / edit.speed
        }
        val duration = durations.sum()
        require(duration <= BlinkReelEditPolicy.MAX_TOTAL_DURATION_MS / 1000.0 + .05) { "The edited Reel must be at most 3 minutes." }
        val first = info.first()
        val target = when (edit.aspect) {
            "Portrait" -> 720 to 1280; "Square" -> 1080 to 1080; "Wide" -> 1280 to 720
            "Original" -> {
                val scale = minOf(1.0, 1920.0 / maxOf(first.width, first.height))
                maxOf(2, (first.width * scale).toInt() / 2 * 2) to maxOf(2, (first.height * scale).toInt() / 2 * 2)
            }
            else -> error("Unknown video aspect ratio.")
        }
        require(folder.isDirectory || folder.mkdirs()) { "Cannot create video export folder." }
        val output = File.createTempFile("blink-reel-", ".mp4", folder)
        val overlay = File.createTempFile("blink-overlay-", ".png", folder)
        val log = File.createTempFile("blink-export-", ".log", folder)
        val status = File.createTempFile("blink-progress-", ".txt", folder)
        var process: Process? = null
        var success = false
        try {
            val args = mutableListOf(ffmpeg(), "-hide_banner", "-loglevel", "error", "-y")
            edit.clips.forEach { clip ->
                args += listOf("-ss", number(clip.start))
                clip.end?.let { args += listOf("-t", number(it - clip.start)) }
                args += listOf("-protocol_whitelist", "file,pipe", "-i", clip.file.absolutePath)
            }
            val filters = mutableListOf<String>()
            edit.clips.forEachIndexed { i, _ ->
                val color = when (edit.filter) { "Mono" -> ",colorchannelmixer=rr=.2126:rg=.7152:rb=.0722:gr=.2126:gg=.7152:gb=.0722:br=.2126:bg=.7152:bb=.0722"; "Warm" -> ",colorchannelmixer=rr=1.08:bb=.92"; "Cool" -> ",colorchannelmixer=rr=.92:bb=1.08"; else -> "" }
                val tone = "(val-128)*${number(edit.contrast)}+128+${number(edit.brightness)}*255"
                filters += "[$i:v]setpts=(PTS-STARTPTS)/${number(edit.speed)},scale=${target.first}:${target.second}:force_original_aspect_ratio=decrease,pad=${target.first}:${target.second}:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=30,format=rgb24$color,lutrgb=r='$tone':g='$tone':b='$tone',format=yuv420p[v$i]"
                filters += if (info[i].audio) "[$i:a]aresample=48000,aformat=sample_fmts=fltp:channel_layouts=stereo,asetpts=PTS-STARTPTS,atempo=${number(edit.speed)}[a$i]"
                    else "anullsrc=r=48000:cl=stereo,atrim=duration=${number(durations[i])}[a$i]"
            }
            filters += edit.clips.indices.joinToString("") { "[v$it][a$it]" } + "concat=n=${edit.clips.size}:v=1:a=1[basev][basea]"
            if (edit.text.isNotBlank() || edit.sticker.isNotBlank()) {
                val blank = java.awt.image.BufferedImage(target.first, target.second, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                val painted = DesktopPhotoEditor.render(blank, PhotoEdit(text = edit.text, sticker = edit.sticker))
                try { check(ImageIO.write(painted, "png", overlay)) } finally { blank.flush(); painted.flush() }
                args += listOf("-loop", "1", "-i", overlay.absolutePath)
                filters += "[basev][${edit.clips.size}:v]overlay=shortest=1[outv]"
            } else filters += "[basev]null[outv]"
            if (edit.music != null) {
                require(edit.music.isFile && edit.music.extension.lowercase() in listOf("mp3", "wav", "m4a")) { "Choose a supported audio file." }
                val musicIndex = edit.clips.size + if (edit.text.isNotBlank() || edit.sticker.isNotBlank()) 1 else 0
                args += listOf("-stream_loop", "-1", "-protocol_whitelist", "file,pipe", "-i", edit.music.absolutePath)
                filters += "[basea]volume=${number(edit.originalVolume)}[original];[$musicIndex:a]aresample=48000,atrim=duration=${number(duration)},asetpts=PTS-STARTPTS,volume=.5[music];[original][music]amix=inputs=2:duration=first[outa]"
            } else filters += "[basea]volume=${number(edit.originalVolume)}[outa]"
            val encoders = command(listOf(ffmpeg(), "-hide_banner", "-encoders"), 20)
            val encoder = when {
                "libopenh264" in encoders -> "libopenh264"
                "h264_mf" in encoders && System.getProperty("os.name").startsWith("Windows") -> "h264_mf"
                else -> error("This installation has no supported H.264 encoder. Keep the original video and report this error.")
            }
            args += listOf("-filter_complex", filters.joinToString(";"), "-map", "[outv]", "-map", "[outa]",
                "-c:v", encoder, "-b:v", "5M", "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", "-t", number(duration), "-progress", status.absolutePath, output.absolutePath)
            process = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start()
            val started = System.nanoTime()
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                cancel()
                check(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(15)) { "Video export timed out. Retry with fewer or shorter clips." }
                val time = status.takeIf { it.length() < 1_000_000 }?.readLines()?.lastOrNull { it.startsWith("out_time_us=") }
                    ?.substringAfter('=')?.toDoubleOrNull()
                if (time != null) progress((time / 1_000_000 / duration).coerceIn(0.0, .99))
            }
            cancel()
            if (process.exitValue() != 0 || output.length() == 0L) throw VideoExportException(log.readText().takeLast(4000))
            val exported = probe(output)
            check(kotlin.math.abs(exported.duration - duration) < 1.0) { "The exported video duration was incorrect. Retry the export." }
            progress(1.0)
            success = true
            return output
        } finally {
            // Windows cannot unlink media/logs while the native process holds
            // them. Wait for termination before removing cancelled exports.
            process?.takeIf { it.isAlive }?.let { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) }
            removeTemporaryFiles(listOf(overlay, log, status) + if (!success) listOf(output) else emptyList())
        }
    }

    private fun command(args: List<String>, timeout: Long): String {
        val log = File.createTempFile("blink-media-probe-", ".txt")
        var process: Process? = null
        try {
            val running = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start().also { process = it }
            check(running.waitFor(timeout, TimeUnit.SECONDS)) { "The video processor did not respond." }
            check(running.exitValue() == 0 && log.length() < 2_000_000) { "This media file could not be processed." }
            return log.readText()
        } finally {
            process?.takeIf { it.isAlive }?.let { it.destroyForcibly(); it.waitFor(5, TimeUnit.SECONDS) }
            removeTemporaryFiles(listOf(log))
        }
    }

    private fun removeTemporaryFiles(files: List<File>) {
        // Windows can briefly retain an inherited handle after process exit.
        // Retry deletion itself, never the export or a failed verification.
        repeat(30) { attempt ->
            val remaining = files.filter { file ->
                try { java.nio.file.Files.deleteIfExists(file.toPath()); false }
                catch (_: java.nio.file.FileSystemException) { true }
            }
            if (remaining.isEmpty()) return
            if (attempt < 29) Thread.sleep(100)
        }
        error("Could not remove temporary media files. Close any program using the export folder and retry.")
    }
}
