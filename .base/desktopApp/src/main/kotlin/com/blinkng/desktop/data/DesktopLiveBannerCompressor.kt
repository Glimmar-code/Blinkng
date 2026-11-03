package com.blinkng.desktop.data

import com.blinkng.shared.live.BlinkLiveBannerImageLimits as Limits
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Offline image resizing before owner-authorized Supabase Storage upload. */
internal object DesktopLiveBannerCompressor {
    data class Prepared(val bytes: ByteArray, val mime: String) {
        val extension: String get() = if (mime == "image/png") "png" else "jpg"
    }

    fun prepare(file: File): Prepared {
        require(file.isFile && file.length() in 1..Limits.MAX_SOURCE_BYTES.toLong()) {
            "Choose a JPG or PNG smaller than 20 MB."
        }
        val original = file.readBytes()
        val mime = when {
            original.size >= 3 && original[0] == 0xff.toByte() &&
                original[1] == 0xd8.toByte() && original[2] == 0xff.toByte() -> "image/jpeg"
            original.size >= 8 && original.copyOfRange(0, 8).contentEquals(
                byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
            ) -> "image/png"
            else -> error("Only JPEG and PNG images are supported.")
        }
        val image = ImageIO.createImageInputStream(file).use { input ->
            requireNotNull(input) { "Cannot read this photo." }
            val readers = ImageIO.getImageReaders(input)
            require(readers.hasNext()) { "This image is damaged or unsupported." }
            val reader = readers.next()
            try {
                reader.setInput(input, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                require(Limits.validateSource(width, height, original.size.toLong())) {
                    "Choose a valid photo under 20 MB and 100 megapixels."
                }
                if (original.size <= Limits.MAX_UPLOAD_BYTES &&
                    maxOf(width, height) <= Limits.MAX_OUTPUT_DIMENSION
                ) return Prepared(original, mime)

                // Subsample before decoding to keep memory use bounded.
                var sample = 1
                while (maxOf(width / sample, height / sample) > Limits.MAX_OUTPUT_DIMENSION * 2)
                    sample *= 2
                val options = reader.defaultReadParam
                options.setSourceSubsampling(sample, sample, 0, 0)
                reader.read(0, options)
            } finally {
                reader.dispose()
            }
        }
        requireNotNull(image) { "Unable to decode this photo." }
        var bitmap = scale(image, Limits.MAX_OUTPUT_DIMENSION)
        if (mime == "image/png") {
            val pngBytes = ByteArrayOutputStream()
            if (ImageIO.write(bitmap, "png", pngBytes) &&
                pngBytes.size() <= Limits.MAX_UPLOAD_BYTES
            ) return Prepared(pngBytes.toByteArray(), "image/png")
        }

        var quality = 0.86f
        repeat(8) {
            val opaque = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
            opaque.createGraphics().useAndDispose {
                color = Color.WHITE
                fillRect(0, 0, opaque.width, opaque.height)
                drawImage(bitmap, 0, 0, null)
            }
            val bytes = jpegBytes(opaque, quality)
            if (bytes.size <= Limits.MAX_UPLOAD_BYTES) return Prepared(bytes, "image/jpeg")
            if (quality > 0.55f) quality -= 0.09f
            else bitmap = scale(bitmap, (maxOf(bitmap.width, bitmap.height) * 0.8).toInt()
                .coerceAtLeast(320))
        }
        error("This image cannot be compressed under 2 MB. Try a smaller photo.")
    }

    private fun scale(input: BufferedImage, maximum: Int): BufferedImage {
        val (width, height) = Limits.targetSize(input.width, input.height, maximum)
        if (input.width == width && input.height == height) return input
        val resized = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        resized.createGraphics().useAndDispose {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            drawImage(input, 0, 0, width, height, null)
        }
        return resized
    }

    private fun jpegBytes(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg")
            .asSequence().firstOrNull() ?: error("No JPEG encoder is available.")
        try {
            val output = ByteArrayOutputStream()
            ImageIO.createImageOutputStream(output).use { imageOutput ->
                writer.output = imageOutput
                val parameters = writer.defaultWriteParam
                if (parameters.canWriteCompressed()) {
                    parameters.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    parameters.compressionQuality = quality.coerceIn(0.3f, 1f)
                }
                writer.write(null, javax.imageio.IIOImage(image, null, null), parameters)
            }
            return output.toByteArray()
        } finally {
            writer.dispose()
        }
    }

    private inline fun Graphics2D.useAndDispose(block: Graphics2D.() -> Unit) {
        try { block() } finally { dispose() }
    }
}
