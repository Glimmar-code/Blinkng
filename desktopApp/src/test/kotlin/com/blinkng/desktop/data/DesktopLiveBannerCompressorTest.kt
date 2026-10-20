package com.blinkng.desktop.data

import com.blinkng.shared.live.BlinkLiveBannerImageLimits as Limits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import javax.imageio.ImageIO
import java.util.Random

class DesktopLiveBannerCompressorTest {
    @Test fun smallPngKeepsOriginalBytesAndMime() {
        val file = Files.createTempFile("blink-banner-small", ".png").toFile()
        try {
            val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(30, 30, 0x880080ff.toInt())
            assertTrue(ImageIO.write(image, "png", file))
            val original = file.readBytes()
            val prepared = DesktopLiveBannerCompressor.prepare(file)
            assertEquals("image/png", prepared.mime)
            assertArrayEquals(original, prepared.bytes)
        } finally {
            file.delete()
        }
    }

    @Test fun largePhotoIsCompressedBelowStorageLimit() {
        val file = Files.createTempFile("blink-banner-large", ".png").toFile()
        try {
            val image = BufferedImage(1700, 1350, BufferedImage.TYPE_INT_RGB)
            val random = Random(41)
            for (y in 0 until image.height) for (x in 0 until image.width) {
                image.setRGB(x, y, random.nextInt(0x1000000))
            }
            assertTrue(ImageIO.write(image, "png", file))
            assertTrue(file.length() > Limits.MAX_UPLOAD_BYTES)
            val prepared = DesktopLiveBannerCompressor.prepare(file)
            assertEquals("image/jpeg", prepared.mime)
            assertTrue(prepared.bytes.size <= Limits.MAX_UPLOAD_BYTES)
            val output = ImageIO.read(ByteArrayInputStream(prepared.bytes))
            assertTrue(output.width <= Limits.MAX_OUTPUT_DIMENSION)
            assertTrue(output.height <= Limits.MAX_OUTPUT_DIMENSION)
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsNonImagesAndOversizedInputs() {
        val invalid = Files.createTempFile("blink-banner-invalid", ".jpg").toFile()
        val huge = Files.createTempFile("blink-banner-huge", ".png").toFile()
        try {
            invalid.writeText("not really a JPEG")
            val invalidError = runCatching { DesktopLiveBannerCompressor.prepare(invalid) }
            assertTrue(invalidError.isFailure)
            java.io.RandomAccessFile(huge, "rw").use {
                it.setLength(Limits.MAX_SOURCE_BYTES.toLong() + 1)
            }
            val hugeError = runCatching { DesktopLiveBannerCompressor.prepare(huge) }
            assertTrue(hugeError.isFailure)
        } finally {
            invalid.delete()
            huge.delete()
        }
    }
}
