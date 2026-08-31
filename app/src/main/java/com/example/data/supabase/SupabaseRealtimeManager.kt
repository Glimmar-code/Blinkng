package com.example.data.supabase

import android.util.Log
import com.example.data.models.ChatMessage
import com.example.data.models.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed class RealtimeEvent {
    data class MessageEvent(val eventType: String, val message: ChatMessage) : RealtimeEvent()
    data class ConversationEvent(val eventType: String, val conversationId: String, val lastMessage: String, val updatedAt: String) : RealtimeEvent()
    data class NotificationEvent(val eventType: String, val id: String, val userId: String, val username: String, val type: String, val title: String, val content: String) : RealtimeEvent()
    data class FeedPostEvent(val eventType: String, val postId: String) : RealtimeEvent()
}

class SupabaseRealtimeManager private constructor() {

    companion object {
        private const val TAG = "SupabaseRealtimeManager"
        @Volatile
        private var instance: SupabaseRealtimeManager? = null

        fun getInstance(): SupabaseRealtimeManager {
            return instance ?: synchronized(this) {
                instance ?: SupabaseRealtimeManager().also { instance = it }
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null

    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val refCounter = AtomicInteger(1)

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    private var activeUsername: String = ""
    var activeUserId: String = ""
        private set

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    fun connect(username: String, userId: String) {
        if (username.isBlank() && userId.isBlank()) return
        
        activeUsername = username
        activeUserId = userId

        if (isConnected.get() || isConnecting.get()) return

        isConnecting.set(true)
        scope.launch {
            doConnect()
        }
    }

    private fun doConnect() {
        try {
            val baseUrl = SupabaseConfig.url.trimEnd('/')
            val wsUrl = if (baseUrl.startsWith("https://")) {
                "wss://" + baseUrl.substring(8) + "/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
            } else if (baseUrl.startsWith("http://")) {
                "ws://" + baseUrl.substring(7) + "/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
            } else {
                "wss://$baseUrl/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
            }

            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, createListener())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate WebSocket connection", e)
            isConnecting.set(false)
            scheduleReconnect()
        }
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "Supabase Realtime WebSocket Connected")
            isConnected.set(true)
            isConnecting.set(false)

            startHeartbeat()
            subscribeToTables()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.w(TAG, "WebSocket Closing: $code / $reason")
            handleDisconnected()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket Failure: ${t.message}", t)
            handleDisconnected()
            scheduleReconnect()
        }
    }

    private fun subscribeToTables() {
        val tables = listOf("messages", "conversations", "notifications", "feed_posts")
        tables.forEach { table ->
            val ref = refCounter.getAndIncrement().toString()
            val joinMsg = JSONObject().apply {
                put("topic", "realtime:public:$table")
                put("event", "phx_join")
                put("payload", JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("postgres_changes", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("event", "*")
                                put("schema", "public")
                                put("table", table)
                            })
                        })
                    })
                })
                put("ref", ref)
            }
            webSocket?.send(joinMsg.toString())
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected.get()) {
                delay(25_000L)
                val hbMsg = JSONObject().apply {
                    put("topic", "phoenix")
                    put("event", "heartbeat")
                    put("payload", JSONObject())
                    put("ref", "hb_${refCounter.getAndIncrement()}")
                }
                webSocket?.send(hbMsg.toString())
            }
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val event = json.optString("event", "")
            val topic = json.optString("topic", "")
            val payload = json.optJSONObject("payload") ?: return

            if (event == "postgres_changes") {
                val data = payload.optJSONObject("data") ?: return
                val type = data.optString("type", "")
                val table = data.optString("table", "")
                val record = data.optJSONObject("record") ?: data.optJSONObject("old_record") ?: JSONObject()

                when (table) {
                    "messages" -> {
                        val id = record.optString("id", "")
                        val senderId = record.optString("sender_id", "")
                        val senderUsername = record.optString("sender_username", "")
                        val receiverId = record.optString("receiver_id", "")
                        val receiverUsername = record.optString("receiver_username", "")
                        val content = record.optString("content", record.optString("text", ""))
                        val createdAt = record.optString("created_at", "")
                        val isRead = record.optBoolean("is_read", false)

                        val isFromMe = (activeUserId.isNotBlank() && senderId == activeUserId) ||
                                (activeUsername.isNotBlank() && senderUsername.equals(activeUsername, ignoreCase = true))

                        val displayTimestamp = formatTimestamp(createdAt)

                        val msg = ChatMessage(
                            id = id,
                            senderId = senderId.ifBlank { senderUsername },
                            senderUsername = senderUsername,
                            receiverId = receiverId.ifBlank { receiverUsername },
                            receiverUsername = receiverUsername,
                            text = content,
                            rawTimestamp = createdAt,
                            timestamp = displayTimestamp,
                            isFromMe = isFromMe,
                            isRead = isRead,
                            status = MessageStatus.SENT
                        )
                        _events.tryEmit(RealtimeEvent.MessageEvent(type, msg))
                    }
                    "conversations" -> {
                        val id = record.optString("id", "")
                        val lastMessage = record.optString("last_message", "")
                        val updatedAt = record.optString("updated_at", record.optString("last_message_at", ""))
                        _events.tryEmit(RealtimeEvent.ConversationEvent(type, id, lastMessage, updatedAt))
                    }
                    "notifications" -> {
                        val id = record.optString("id", "")
                        val userId = record.optString("user_id", "")
                        val username = record.optString("username", "")
                        val nType = record.optString("type", "")
                        val title = record.optString("title", "")
                        val content = record.optString("content", "")
                        _events.tryEmit(RealtimeEvent.NotificationEvent(type, id, userId, username, nType, title, content))
                    }
                    "feed_posts" -> {
                        val id = record.optString("id", "")
                        _events.tryEmit(RealtimeEvent.FeedPostEvent(type, id))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing realtime message", e)
        }
    }

    private fun handleDisconnected() {
        isConnected.set(false)
        isConnecting.set(false)
        heartbeatJob?.cancel()
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(5_000L)
            if (!isConnected.get() && activeUsername.isNotBlank()) {
                Log.i(TAG, "Attempting WebSocket Reconnect...")
                connect(activeUsername, activeUserId)
            }
        }
    }

    fun disconnect() {
        activeUsername = ""
        activeUserId = ""
        heartbeatJob?.cancel()
        reconnectJob?.cancel()

        try {
            webSocket?.close(1000, "User logged out")
        } catch (_: Exception) {}

        webSocket = null
        isConnected.set(false)
        isConnecting.set(false)
    }

    private fun formatTimestamp(isoString: String): String {
        if (isoString.isBlank()) return "Just now"
        return try {
            val normalized = isoString.trim().replace("Z", "+0000")
            val sdf = if (isoString.contains(".")) {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            } else {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            }
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(normalized) ?: Date()

            val outFormat = SimpleDateFormat("h:mm a", Locale.US)
            outFormat.format(date)
        } catch (_: Exception) {
            "Just now"
        }
    }
}
