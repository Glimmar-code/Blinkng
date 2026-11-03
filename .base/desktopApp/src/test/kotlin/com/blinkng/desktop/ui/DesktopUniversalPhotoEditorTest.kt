package com.blinkng.desktop.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Synthetic local pictures, with no account/session, network or production data. */
class DesktopUniversalPhotoEditorTest {
    private fun original(width: Int = 240, height: Int = 160): File {
        val image=BufferedImage(width,height,BufferedImage.TYPE_INT_RGB)
        val g=image.createGraphics()
        try {g.color=Color(100,120,140);g.fillRect(0,0,width,height)}
        finally {g.dispose()}
        val file=File.createTempFile("blink-desktop-photo-test-", ".png")
        ImageIO.write(image,"png",file)
        return file
    }

    @Test fun cropRatiosAreCorrectAndOriginalIsUntouched() {
        val source=original()
        val bytes=source.readBytes()
        val square=DesktopPictureEditor.crop(source,DesktopPictureEditor.Crop.SQUARE)
        val wide=DesktopPictureEditor.crop(source,DesktopPictureEditor.Crop.WIDE)
        val portrait=DesktopPictureEditor.crop(source,DesktopPictureEditor.Crop.PORTRAIT)
        try {
            val a=ImageIO.read(square)
            val b=ImageIO.read(wide)
            val c=ImageIO.read(portrait)
            assertEquals(a.width,a.height)
            assertTrue(b.width>b.height)
            assertTrue(c.height>c.width)
            assertTrue(source.readBytes().contentEquals(bytes))
        } finally {source.delete();square.delete();wide.delete();portrait.delete()}
    }

    @Test fun rotateSwapsSizeAndAdjustmentsWriteNewCopy() {
        val source=original(120,240)
        val before=source.readBytes()
        val rotated=DesktopPictureEditor.crop(source,DesktopPictureEditor.Crop.ROTATE)
        val edited=DesktopPictureEditor.finish(rotated,.3f,1.3f,.7f,"BLINK","⭐")
        try {
            val result=ImageIO.read(rotated)
            assertEquals(240,result.width)
            assertEquals(120,result.height)
            assertTrue(edited.isFile && edited.length()>0)
            assertTrue(source.readBytes().contentEquals(before))
        } finally {source.delete();rotated.delete();edited.delete()}
    }
}
