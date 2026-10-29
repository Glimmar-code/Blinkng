package com.blinkng.desktop.data

import com.blinkng.desktop.media.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DesktopPhotoEditorTest {
    @TempDir lateinit var folder: File
    private fun source() = BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB).apply {
        createGraphics().let { g -> try { g.color = Color.RED; g.fillRect(0, 0, 60, 80); g.color = Color.BLUE; g.fillRect(60, 0, 60, 80) } finally { g.dispose() } }
    }
    @Test fun noEditsAreLosslessAndExportNeverOverwritesOriginal() {
        val file = File(folder, "original.png")
        ImageIO.write(source(), "png", file)
        val bytes = file.readBytes()
        val output = DesktopPhotoEditor.export(DesktopPhotoEditor.load(file), PhotoEdit(), folder)
        assertNotEquals(file, output)
        assertArrayEquals(bytes, file.readBytes())
        val edited = ImageIO.read(output)
        assertEquals(120, edited.width); assertEquals(80, edited.height)
        assertEquals(Color.RED.rgb, edited.getRGB(20, 20))
        assertEquals(Color.BLUE.rgb, edited.getRGB(100, 20))
    }
    @Test fun rotationAndCenteredCropPreserveGeometry() {
        val rotated = DesktopPhotoEditor.render(source(), PhotoEdit(turns = 1))
        assertEquals(80, rotated.width); assertEquals(120, rotated.height)
        assertEquals(Color.RED.rgb, rotated.getRGB(40, 20)); assertEquals(Color.BLUE.rgb, rotated.getRGB(40, 100))
        val cropped = DesktopPhotoEditor.render(source(), PhotoEdit(crop = "Square"))
        assertEquals(80, cropped.width); assertEquals(80, cropped.height)
        assertEquals(Color.RED.rgb, cropped.getRGB(5, 5)); assertEquals(Color.BLUE.rgb, cropped.getRGB(75, 5))
    }
    @Test fun filtersAndDoodlesAreAppliedToPixels() {
        val mono = DesktopPhotoEditor.render(source(), PhotoEdit(filter = "Mono"))
        val color = Color(mono.getRGB(20, 20))
        assertEquals(color.red, color.green); assertEquals(color.green, color.blue)
        val drawn = DesktopPhotoEditor.render(source(), PhotoEdit(strokes = listOf(PhotoStroke(listOf(PhotoPoint(0f, .5f), PhotoPoint(1f, .5f)), Color.GREEN.rgb))))
        assertEquals(Color.GREEN.rgb, drawn.getRGB(60, 40))
    }
    @Test fun badInputsFailWithoutChangingSource() {
        val file = File(folder, "bad.jpg").apply { writeText("not an image") }
        assertThrows(IllegalArgumentException::class.java) { DesktopPhotoEditor.load(file) }
        assertThrows(IllegalArgumentException::class.java) { DesktopPhotoEditor.render(source(), PhotoEdit(filter = "unknown")) }
        assertEquals("not an image", file.readText())
    }
    @Test fun phoneExifOrientationIsAppliedBeforeEditing() {
        val rgb = BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().let { g -> try { g.drawImage(source(), 0, 0, null) } finally { g.dispose() } }
        val file = File(folder, "phone.jpg")
        ImageIO.write(rgb, "jpeg", file)
        val bytes = file.readBytes()
        val payload = java.nio.ByteBuffer.allocate(32).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        payload.put(byteArrayOf(69, 120, 105, 102, 0, 0, 73, 73)).putShort(42).putInt(8)
        payload.putShort(1).putShort(0x112).putShort(3).putInt(1).putShort(6).putShort(0).putInt(0)
        file.writeBytes(bytes.take(2).toByteArray() + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0, 34) + payload.array() + bytes.drop(2).toByteArray())
        val oriented = DesktopPhotoEditor.load(file)
        assertEquals(80, oriented.width); assertEquals(120, oriented.height)
        assertTrue(Color(oriented.getRGB(40, 20)).red > 200)
        assertTrue(Color(oriented.getRGB(40, 100)).blue > 200)
    }
    @Test fun explicitJpegExportKeepsDimensionsAndFlattensTransparencyToWhite() {
        val image = BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().let { g -> try { g.color = Color.RED; g.fillRect(12, 12, 16, 16) } finally { g.dispose() } }
        val output = DesktopPhotoEditor.export(image, PhotoEdit(), folder, "jpg")
        val jpeg = ImageIO.read(output)
        assertEquals(40, jpeg.width); assertEquals(40, jpeg.height)
        assertTrue(Color(jpeg.getRGB(2, 2)).green > 230)
        assertTrue(Color(jpeg.getRGB(20, 20)).red > 200)
        assertEquals(0, image.getRGB(2, 2))
    }
    @Test fun nativeWebpDecoderUsesTheFullResolutionPhoto() {
        val png = File(folder, "input.png"); ImageIO.write(source(), "png", png)
        val webp = File(folder, "photo.webp")
        val process = ProcessBuilder(DesktopVideoEditor.ffmpeg(), "-hide_banner", "-loglevel", "error", "-y", "-i", png.absolutePath, webp.absolutePath)
            .redirectErrorStream(true).redirectOutput(File(folder, "webp.log")).start()
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, process.exitValue())
        val decoded = DesktopPhotoEditor.load(webp)
        assertEquals(120, decoded.width); assertEquals(80, decoded.height)
        assertTrue(Color(decoded.getRGB(20, 20)).red > 180)
    }
}
