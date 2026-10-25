package com.blinkng.desktop.ui

import java.io.File

/**
 * Launches the same Godot 4.3 project shipped inside the Android APK.
 *
 * Windows installers stage a pinned Godot 4.3 runtime at:
 * resources/blink-life/BlinkLife.exe
 * and copy game3d/ into resources/blink-life/game/.
 *
 * The fallback preview remains available when running a desktop development
 * build without a packaged native executable.
 */
internal object BlinkLifeNativeLauncher {
    fun launch(): Boolean {
        val resources = System.getProperty("compose.application.resources.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: return false
        val executable = File(resources, "blink-life/BlinkLife.exe")
        val gameProject = File(resources, "blink-life/game")
        if (!executable.isFile || !File(gameProject, "project.godot").isFile) return false

        return runCatching {
            ProcessBuilder(executable.absolutePath, "--path", gameProject.absolutePath)
                .directory(gameProject)
                .start()
            true
        }.getOrDefault(false)
    }
}
