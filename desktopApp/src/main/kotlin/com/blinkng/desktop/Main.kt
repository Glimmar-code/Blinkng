package com.blinkng.desktop

import com.blinkng.desktop.data.DesktopChatPhotoWorker

fun main(args: Array<String>) {
    if (args.firstOrNull() == DesktopChatPhotoWorker.FLAG) {
        DesktopChatPhotoWorker.run(args.getOrNull(1).orEmpty())
    } else {
        runBlinkDesktopApplication()
    }
}
