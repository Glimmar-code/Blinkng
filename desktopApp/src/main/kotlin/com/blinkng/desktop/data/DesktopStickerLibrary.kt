package com.blinkng.desktop.data

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO

/** Reusable, explicitly created Windows stickers; private to one signed-in account.
 * No media is silently uploaded or shared, and the original photo remains intact.
 */
internal object DesktopStickerLibrary {
    const val MAX_SAVED = 30

    private fun folder(ownerId:String):File {
        val id=UUID.fromString(ownerId).toString()
        return File(System.getProperty("user.home"),".blinkng/stickers/$id")
    }
    fun saved(ownerId:String):List<File> {
        val dir=runCatching{folder(ownerId)}.getOrNull()?:return emptyList()
        return dir.listFiles()?.asSequence()
            ?.filter {it.isFile && it.name.matches(Regex("[0-9a-f-]{36}\\.png")) &&
                it.length() in 1..2_000_000}
            ?.sortedByDescending(File::lastModified)?.take(MAX_SAVED)?.toList().orEmpty()
    }

    fun create(ownerId:String,source:File):File {
        require(source.isFile && source.length() in 1..(20L*1024*1024))
        require(source.extension.lowercase() in setOf("png","jpg","jpeg")) {
            "Choose a still PNG or JPEG image."
        }
        require(saved(ownerId).size<MAX_SAVED) {
            "Sticker collection is full (30). Remove one first."
        }
        val input=ImageIO.createImageInputStream(source)
            ?: error("Could not open source picture.")
        val bitmap=input.use {stream->
            val decoders=ImageIO.getImageReaders(stream)
            require(decoders.hasNext()){"Unsupported picture format."}
            val decoder=decoders.next()
            try {
                decoder.input=stream
                val width=decoder.getWidth(0);val height=decoder.getHeight(0)
                require(width in 1..20000 && height in 1..20000)
                var sample=1
                while(maxOf(width,height)/sample>640)sample*=2
                decoder.read(0,decoder.defaultReadParam.apply {
                    setSourceSubsampling(sample,sample,0,0)
                })
            }finally{decoder.dispose()}
        }
        val result=BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB)
        val g=result.createGraphics()
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val ratio=minOf(512f/bitmap.width,512f/bitmap.height)
            val width=(ratio*bitmap.width).toInt().coerceAtLeast(1)
            val height=(ratio*bitmap.height).toInt().coerceAtLeast(1)
            g.drawImage(bitmap,(512-width)/2,(512-height)/2,width,height,null)
        }finally{g.dispose()}
        val directory=folder(ownerId)
        directory.mkdirs()
        val dest=File(directory,"${UUID.randomUUID()}.png")
        try{
            check(ImageIO.write(result,"png",dest))
            require(dest.length() in 1..2_000_000)
            return dest
        }catch(e:Exception){dest.delete();throw e}
    }

    fun remove(ownerId:String,file:File):Boolean{
        val dir=runCatching{folder(ownerId).canonicalFile}.getOrNull()?:return false
        return file.canonicalFile.parentFile==dir &&
            file.name.matches(Regex("[0-9a-f-]{36}\\.png")) && file.delete()
    }
}
