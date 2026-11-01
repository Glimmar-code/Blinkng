package com.blinkng.desktop.media

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

data class PhotoPoint(val x: Float, val y: Float)
data class PhotoStroke(val points: List<PhotoPoint>, val color: Int = 0xFFFF3B57.toInt())
data class PhotoEdit(
    val turns: Int = 0,
    val crop: String = "Original",
    val filter: String = "Original",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val text: String = "",
    val sticker: String = "",
    val strokes: List<PhotoStroke> = emptyList(),
    val cropPosition: PhotoPoint = PhotoPoint(.5f, .5f),
    val textPosition: PhotoPoint = PhotoPoint(.05f, .9f),
    val stickerPosition: PhotoPoint = PhotoPoint(.05f, .2f),
)

/** Pure Java2D export; original files are never modified. Coordinates follow the edited preview. */
object DesktopPhotoEditor {
    const val MAX_PIXELS = 24_000_000L
    fun load(file: File): BufferedImage {
        ImageIO.createImageInputStream(file).use { input ->
            requireNotNull(input) { "Cannot open this photo." }
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext() && file.extension.equals("webp", true)) return DesktopVideoEditor.decodePhoto(file)
            require(readers.hasNext()) { "This photo format cannot be edited. Choose JPEG, PNG or WebP." }
            val reader = readers.next()
            try {
                reader.input = input
                require(reader.getWidth(0).toLong() * reader.getHeight(0) <= MAX_PIXELS) {
                    "This editor supports photos up to 24 megapixels. Your original is unchanged."
                }
                val decoded = reader.read(0)
                val orientation = jpegOrientation(file)
                if (orientation == 1) return decoded
                val oriented = BufferedImage(if (orientation >= 5) decoded.height else decoded.width,
                    if (orientation >= 5) decoded.width else decoded.height, BufferedImage.TYPE_INT_ARGB)
                val w = decoded.width.toDouble(); val h = decoded.height.toDouble()
                val transform = when (orientation) {
                    2 -> java.awt.geom.AffineTransform(-1.0, 0.0, 0.0, 1.0, w, 0.0)
                    3 -> java.awt.geom.AffineTransform(-1.0, 0.0, 0.0, -1.0, w, h)
                    4 -> java.awt.geom.AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, h)
                    5 -> java.awt.geom.AffineTransform(0.0, 1.0, 1.0, 0.0, 0.0, 0.0)
                    6 -> java.awt.geom.AffineTransform(0.0, 1.0, -1.0, 0.0, h, 0.0)
                    7 -> java.awt.geom.AffineTransform(0.0, -1.0, -1.0, 0.0, h, w)
                    else -> java.awt.geom.AffineTransform(0.0, -1.0, 1.0, 0.0, 0.0, w)
                }
                oriented.createGraphics().useGraphics { it.drawImage(decoded, transform, null) }
                decoded.flush()
                return oriented
            } finally { reader.dispose() }
        }
    }

    fun render(source: BufferedImage, edit: PhotoEdit): BufferedImage {
        require(edit.brightness in -1f..1f && edit.contrast in .5f..1.5f)
        require(listOf(edit.cropPosition, edit.textPosition, edit.stickerPosition).all {
            it.x in 0f..1f && it.y in 0f..1f
        }) { "Keep crop and overlays inside the image." }
        val turns = Math.floorMod(edit.turns, 4)
        val rotated = BufferedImage(if (turns % 2 == 0) source.width else source.height,
            if (turns % 2 == 0) source.height else source.width, BufferedImage.TYPE_INT_ARGB)
        rotated.createGraphics().useGraphics { g ->
            when (turns) {
                1 -> { g.translate(rotated.width.toDouble(), 0.0); g.rotate(Math.PI / 2) }
                2 -> { g.translate(rotated.width.toDouble(), rotated.height.toDouble()); g.rotate(Math.PI) }
                3 -> { g.translate(0.0, rotated.height.toDouble()); g.rotate(-Math.PI / 2) }
            }
            g.drawImage(source, 0, 0, null)
        }
        val ratio = when (edit.crop) {
            "Original" -> rotated.width.toDouble() / rotated.height
            "Square" -> 1.0
            "Portrait" -> 4.0 / 5
            "Wide" -> 16.0 / 9
            else -> error("Unknown crop format.")
        }
        val width = minOf(rotated.width, (rotated.height * ratio).roundToInt()).coerceAtLeast(1)
        val height = minOf(rotated.height, (rotated.width / ratio).roundToInt()).coerceAtLeast(1)
        val output = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        output.createGraphics().useGraphics { it.drawImage(rotated,
            -((rotated.width - width) * edit.cropPosition.x).roundToInt(),
            -((rotated.height - height) * edit.cropPosition.y).roundToInt(), null) }
        rotated.flush()
        require(edit.filter in listOf("Original", "Mono", "Warm", "Cool"))
        fun channel(value: Double) = (((value / 255.0 - .5) * edit.contrast + .5 + edit.brightness) * 255)
            .roundToInt().coerceIn(0, 255)
        for (y in 0 until height) for (x in 0 until width) {
            val pixel = output.getRGB(x, y)
            var r = ((pixel ushr 16) and 255).toDouble()
            var g = ((pixel ushr 8) and 255).toDouble()
            var b = (pixel and 255).toDouble()
            when (edit.filter) {
                "Mono" -> { val v = .2126 * r + .7152 * g + .0722 * b; r = v; g = v; b = v }
                "Warm" -> { r *= 1.1; b *= .9 }
                "Cool" -> { r *= .9; b *= 1.1 }
            }
            output.setRGB(x, y, (pixel and 0xFF000000.toInt()) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b))
        }
        output.createGraphics().useGraphics { g ->
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.stroke = BasicStroke((width / 160f).coerceAtLeast(2f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            edit.strokes.forEach { stroke ->
                g.color = Color(stroke.color, true)
                stroke.points.zipWithNext().forEach { (a, b) ->
                    g.drawLine((a.x.coerceIn(0f, 1f) * width).roundToInt(), (a.y.coerceIn(0f, 1f) * height).roundToInt(),
                        (b.x.coerceIn(0f, 1f) * width).roundToInt(), (b.y.coerceIn(0f, 1f) * height).roundToInt())
                }
            }
            val fontSize = (width / 18).coerceAtLeast(12)
            g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
            g.color = Color.WHITE
            val label = edit.text.take(160)
            if (label.isNotBlank()) {
                val lines = label.chunked(maxOf(1, width / maxOf(1, fontSize * 3 / 5) - 2))
                val left = (edit.textPosition.x * width).roundToInt()
                    .coerceIn(0, maxOf(0, width - lines.maxOf { g.fontMetrics.stringWidth(it) } - 2))
                val baseline = (edit.textPosition.y * height).roundToInt()
                    .coerceIn(minOf(height - 1, fontSize * lines.size), height - 1)
                lines.forEachIndexed { i, line ->
                    val y = baseline - fontSize * (lines.size - 1 - i)
                    g.color = Color.BLACK; g.drawString(line, left + 2, y + 2)
                    g.color = Color.WHITE; g.drawString(line, left, y)
                }
            }
            if (edit.sticker.isNotBlank()) {
                g.font = Font(Font.SANS_SERIF, Font.BOLD, maxOf(14, width / 8))
                g.color = Color(0xFFCC33)
                val sticker = edit.sticker.take(8)
                val x = (edit.stickerPosition.x * width).roundToInt()
                    .coerceIn(0, maxOf(0, width - g.fontMetrics.stringWidth(sticker)))
                val y = (edit.stickerPosition.y * height).roundToInt()
                    .coerceIn(minOf(g.fontMetrics.ascent, height - 1), height - 1)
                g.drawString(sticker, x, y)
            }
        }
        return output
    }

    fun export(source: BufferedImage, edit: PhotoEdit, directory: File, format: String = "png"): File {
        require(format in listOf("png", "jpg"))
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create edited-photo folder." }
        val output = File.createTempFile("blink-edited-", ".$format", directory)
        val image = render(source, edit)
        try {
            if (format == "png") check(ImageIO.write(image, "png", output)) { "PNG export is unavailable." }
            else {
                val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                rgb.createGraphics().useGraphics { g -> g.color = Color.WHITE; g.fillRect(0, 0, rgb.width, rgb.height); g.drawImage(image, 0, 0, null) }
                val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
                try {
                    ImageIO.createImageOutputStream(output).use { stream ->
                        writer.output = stream
                        val parameters = writer.defaultWriteParam
                        parameters.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                        parameters.compressionQuality = .95f
                        writer.write(null, javax.imageio.IIOImage(rgb, null, null), parameters)
                    }
                } finally { writer.dispose(); rgb.flush() }
            }
            return output
        } catch (failure: Throwable) { output.delete(); throw failure }
        finally { image.flush() }
    }
}

private fun jpegOrientation(file: File): Int = try {
    java.io.DataInputStream(file.inputStream().buffered()).use { input ->
        if (input.readUnsignedShort() != 0xFFD8) return 1
        repeat(256) {
            if (input.readUnsignedByte() != 0xFF) return 1
            var marker = input.readUnsignedByte()
            while (marker == 0xFF) marker = input.readUnsignedByte()
            if (marker == 0xDA || marker == 0xD9) return 1
            val length = input.readUnsignedShort() - 2
            if (length < 0) return 1
            val bytes = ByteArray(length); input.readFully(bytes)
            if (marker == 0xE1 && length >= 14 && bytes.copyOfRange(0, 6).contentEquals(byteArrayOf(69, 120, 105, 102, 0, 0))) {
                val little = bytes[6] == 73.toByte() && bytes[7] == 73.toByte()
                if (!little && !(bytes[6] == 77.toByte() && bytes[7] == 77.toByte())) return 1
                val buffer = java.nio.ByteBuffer.wrap(bytes).order(if (little) java.nio.ByteOrder.LITTLE_ENDIAN else java.nio.ByteOrder.BIG_ENDIAN)
                if (buffer.getShort(8).toInt() != 42) return 1
                val offset = buffer.getInt(10).toLong() + 6
                if (offset < 14 || offset > length - 2) return 1
                val count = buffer.getShort(offset.toInt()).toInt() and 65535
                repeat(minOf(count, 512)) { index ->
                    val entry = offset + 2 + index * 12L
                    if (entry > length - 12) return 1
                    val at = entry.toInt()
                    if ((buffer.getShort(at).toInt() and 65535) == 0x112 && buffer.getShort(at + 2).toInt() == 3 && buffer.getInt(at + 4) == 1) {
                        return (buffer.getShort(at + 8).toInt() and 65535).takeIf { it in 1..8 } ?: 1
                    }
                }
            }
        }
        1
    }
} catch (_: java.io.IOException) { 1 }

private inline fun java.awt.Graphics2D.useGraphics(block: (java.awt.Graphics2D) -> Unit) {
    try { block(this) } finally { dispose() }
}
