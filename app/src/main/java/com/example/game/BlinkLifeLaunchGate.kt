package com.example.game

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

/**
 * BLINK LIFE is opt-in and cold-started only on a deliberate Play action.
 *
 * This launcher deliberately has NO reference to a Godot Java/Kotlin class:
 * browsing BLINK's feed, messages or Game cards cannot instantiate the engine.
 * Android starts the declared :blink_life process only when the player taps Play.
 * Its assets stay packaged on disk until Godot opens them.
 */
object BlinkLifeLaunchGate {
    private const val TAG = "BlinkLifeLaunchGate"
    private const val NATIVE_ACTIVITY = "com.example.game.BlinkLifeGodotActivity"
    private const val DOUBLE_TAP_WINDOW_MS = 1_000L

    private var lastLaunchTimeMs: Long = -DOUBLE_TAP_WINDOW_MS

    /** Keep the BLINK host app responsive even if game launch is unavailable. */
    fun play(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunchTimeMs < DOUBLE_TAP_WINDOW_MS) return
        lastLaunchTimeMs = now

        try {
            val intent = Intent()
                .setClassName(context.packageName, NATIVE_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(intent)
        } catch (failure: RuntimeException) {
            lastLaunchTimeMs = -DOUBLE_TAP_WINDOW_MS
            Log.e(TAG, "The optional native 3D game could not be opened", failure)
            Toast.makeText(
                context.applicationContext,
                "BLINK LIFE couldn't open. You can continue using BLINK normally.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
