package com.example

import org.godotengine.godot.GodotActivity

/**
 * Full native Godot 4.3 3D runtime for BLINK LIFE.
 *
 * Launched explicitly from BLINK > Games and isolated in its own Android process
 * to avoid loading Godot's JNI libraries into the main social/WebRTC process.
 *
 * GodotActivity manages its own Android lifecycle and loads project.godot from
 * the app assets root (supplied from /game3d by Gradle).
 */
class BlinkLife3DActivity : GodotActivity()
