package com.example.game

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Opt-in game loader. The rest of BLINK never imports/initializes Godot.
 * Only after Play is selected do we request a 90-second join ticket; the game
 * itself still opens offline if a dedicated server is not deployed.
 */
object BlinkLifeLaunchGate {
    private const val TAG = "BlinkLifeLaunchGate"
    private const val NATIVE_ACTIVITY = "com.example.game.BlinkLifeGodotActivity"
    private const val DOUBLE_TAP_WINDOW_MS = 1_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    private var lastLaunchTimeMs: Long = -DOUBLE_TAP_WINDOW_MS
    @Volatile private var launching: Boolean = false

    /** No network calls, game classes or assets are loaded until this method. */
    fun play(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (launching || now - lastLaunchTimeMs < DOUBLE_TAP_WINDOW_MS) return
        lastLaunchTimeMs = now
        launching = true
        val applicationContext = context.applicationContext

        Thread({
            // Missing/offline sessions simply open the game in single-player.
            val joinDetails = fetchGameTicket()
            mainHandler.post {
                try {
                    val intent = Intent()
                        .setClassName(applicationContext.packageName, NATIVE_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    if (joinDetails != null) {
                        intent.putExtra("blink_life_host", joinDetails.host)
                        intent.putExtra("blink_life_port", joinDetails.port)
                        intent.putExtra("blink_life_ticket", joinDetails.ticket)\n                        intent.putExtra("blink_life_transport", "wss")
                    }
                    applicationContext.startActivity(intent)
                } catch (failure: RuntimeException) {
                    lastLaunchTimeMs = -DOUBLE_TAP_WINDOW_MS
                    Log.e(TAG, "Optional native 3D game failed to open", failure)
                    Toast.makeText(
                        applicationContext,
                        "BLINK LIFE couldn't open. BLINK is still available.",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    launching = false
                }
            }
        }, "BlinkLifeJoinTicket").apply { isDaemon = true }.start()
    }

    private data class JoinDetails(val host: String, val port: Int, val ticket: String)

    private fun fetchGameTicket(): JoinDetails? {
        val accessToken = SupabaseService.accessToken()?.takeIf { it.isNotBlank() } ?: return null
        val url = SupabaseConfig.url.trimEnd('/')
        val key = SupabaseConfig.anonKey
        if (!url.startsWith("https://") || key.isBlank()) return null

        return runCatching {
            val request = Request.Builder()
                .url("$url/functions/v1/blink-life-session")
                .header("Authorization", "Bearer $accessToken")
                .header("apikey", key)
                .header("Cache-Control", "no-store")
                .post(okhttp3.RequestBody.create(null, ByteArray(0)))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JSONObject(response.body?.string().orEmpty())
                val host = json.optString("host")
                val port = json.optInt("port")
                val ticket = json.optString("ticket")\n                val transport = json.optString("transport")
                if (transport != "wss" || host.isBlank() || host.length > 253 ||\n                    host.any { it.isWhitespace() || it == '/' || it == '\\\\' || it == ':' } ||\n                    port !in 1024..65535 ||
                    ticket.length !in 110..240 || ticket.split('.').size != 4
                ) null else JoinDetails(host, port, ticket)
            }
        }.getOrNull()
    }
}
