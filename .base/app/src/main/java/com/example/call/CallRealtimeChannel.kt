package com.example.call

import android.util.Log
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class CallRealtimeChannel(
    private val callId: String,
    private val onSignal: (CallSignal) -> Unit,
    private val onCallUpdate: (BlinkCall) -> Unit,
    private val onConnection: (Boolean) -> Unit = {}
) {
    companion object {
        private const val TAG = "CallRealtimeChannel"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val refCounter = AtomicInteger(1)
    private var socket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastTokenSent = ""

    fun connect() {
        if (closed.get() || connected.get()) return
        val base = SupabaseConfig.url.trimEnd('/')
        val wsUrl = if (base.startsWith("https://")) {
            "wss://${base.removePrefix("https://")}/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
        } else {
            "ws://${base.removePrefix("http://")}/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
        }
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        heartbeatJob?.cancel()
        reconnectJob?.cancel()
        connected.set(false)
        onConnection(false)
        runCatching { socket?.close(1000, "Call finished") }
        socket = null
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (closed.get()) {
                webSocket.close(1000, "Already closed")
                return
            }
            connected.set(true)
            lastTokenSent = ""
            onConnection(true)
            sendAccessToken(force = true)
            joinTable("call_signals", "call_id=eq.$callId")
            joinTable("calls", "id=eq.$callId")
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            handleDisconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Call realtime socket failed: ${t.message}")
            handleDisconnect()
        }
    }

    private fun joinTable(table: String, filter: String) {
        val config = JSONObject().put(
            "postgres_changes",
            JSONArray().put(
                JSONObject()
                    .put("event", "*")
                    .put("schema", "public")
                    .put("table", table)
                    .put("filter", filter)
            )
        )
        socket?.send(
            JSONObject()
                .put("topic", "realtime:public:$table:$callId")
                .put("event", "phx_join")
                .put("payload", JSONObject().put("config", config))
                .put("ref", refCounter.getAndIncrement().toString())
                .toString()
        )
    }

    private fun sendAccessToken(force: Boolean = false) {
        val token = SupabaseService.accessToken()?.takeIf { it.isNotBlank() } ?: return
        if (!force && token == lastTokenSent) return
        val sent = socket?.send(
            JSONObject()
                .put("topic", "realtime")
                .put("event", "access_token")
                .put("payload", JSONObject().put("access_token", token))
                .put("ref", refCounter.getAndIncrement().toString())
                .toString()
        ) == true
        if (sent) lastTokenSent = token
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && connected.get() && !closed.get()) {
                delay(25_000L)
                sendAccessToken()
                socket?.send(
                    JSONObject()
                        .put("topic", "phoenix")
                        .put("event", "heartbeat")
                        .put("payload", JSONObject())
                        .put("ref", "hb_${refCounter.getAndIncrement()}")
                        .toString()
                )
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val root = JSONObject(text)
            if (root.optString("event") != "postgres_changes") return
            val data = root.optJSONObject("payload")?.optJSONObject("data") ?: return
            val table = data.optString("table")
            val record = data.optJSONObject("record") ?: return
            when (table) {
                "call_signals" -> onSignal(CallSignal.fromJson(record))
                "calls" -> onCallUpdate(BlinkCall.fromJson(record))
            }
        } catch (error: Exception) {
            Log.w(TAG, "Unable to parse call realtime event", error)
        }
    }

    private fun handleDisconnect() {
        if (!connected.compareAndSet(true, false) && closed.get()) return
        heartbeatJob?.cancel()
        onConnection(false)
        if (closed.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(1_500L)
            if (!closed.get() && !connected.get()) connect()
        }
    }
}
