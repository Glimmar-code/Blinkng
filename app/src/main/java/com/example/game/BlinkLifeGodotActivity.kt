package com.example.game

import org.godotengine.godot.GodotActivity

/**
 * Runs BLINK LIFE in the actual Godot 4.3 renderer, separate from BLINK's Compose
 * navigation. The activity is isolated in :blink_life so quitting or restarting
 * Godot cannot terminate the social app process.
 *
 * The single source of gameplay is game3d/ and is copied into APK assets at build
 * time by stageBlinkLifeGodotAssets. No production wallet APIs are exposed.
 */
class BlinkLifeGodotActivity : GodotActivity()
