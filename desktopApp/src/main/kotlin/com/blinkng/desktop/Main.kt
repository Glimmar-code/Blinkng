package com.blinkng.desktop

import com.blinkng.desktop.data.DesktopChatPhotoWorker

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--media-export-smoke") {
        kotlin.system.exitProcess(com.blinkng.desktop.media.DesktopMediaSmoke.run(args.getOrNull(1)))
    } else if (args.firstOrNull() == DesktopChatPhotoWorker.FLAG) {
        DesktopChatPhotoWorker.run(args.getOrNull(1).orEmpty())
    } else {
        runBlinkDesktopApplication()
    }
}
