package com.example.game

import org.godotengine.godot.GodotActivity

/**
 * Native Godot host in :blink_life, isolated from BLINK's other screens.
 *
 * The only credentials passed into this separate process are one-time, 90s
 * game-join tickets. The full Supabase session JWT is never sent to Godot.
 */
class BlinkLifeGodotActivity : GodotActivity() {
    override fun getCommandLine(): List<String> {
        val ticket = intent.getStringExtra("blink_life_ticket").orEmpty()
        val host = intent.getStringExtra("blink_life_host").orEmpty()
        val port = intent.getIntExtra("blink_life_port", 0)\n        val transport = intent.getStringExtra("blink_life_transport").orEmpty()
        if (transport != "wss" || ticket.length !in 110..240 || ticket.split('.').size != 4 ||
            host.isBlank() || host.length > 253 ||\n            host.any { it.isWhitespace() || it == '/' || it == ':' } ||\n            port !in 1024..65535
        ) return emptyList()

        // Godot 4.3 GodotHost.getCommandLine passes these to the native engine.
        // The separator marks application-specific arguments for GDScript.
        return listOf(
            "--",
            "--blink-host=$host",
            "--blink-port=$port",
            "--blink-ticket=$ticket",\n            "--blink-transport=wss",
        )
    }
}
