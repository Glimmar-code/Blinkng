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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONObject

sealed class RealtimeEvent {
    data class MessageEvent(val eventType: String, val message: ChatMessage) : RealtimeEvent()
    data class ConversationEvent(val eventType: String, val conversationId: String, val lastMessage: String, val updatedAt: String) : RealtimeEvent()
    data class NotificationEvent(val eventType: String, val id: String, val userId: String, val username: String, val type: String, val title: String, val content: String) : RealtimeEvent()
    data class FeedPostEvent(val eventType: String, val postId: String) : RealtimeEvent()
}

class SupabaseRealtimeManager private constructor() {
    companion object {
        private const val TAG = "SupabaseRealtimeManager"
        @Volatile private var instance: SupabaseRealtimeManager? = null
        fun getInstance(): SupabaseRealtimeManager = instance ?: synchronized(this) { instance ?: SupabaseRealtimeManager().also { instance = it } }
    }
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).writeTimeout(10, TimeUnit.SECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val refCounter = AtomicInteger(1)
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var activeUsername = ""
    var activeUserId: String = ""
        private set
    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()

    fun connect(username: String, userId: String) {
        if (username.isBlank() && userId.isBlank()) return
        activeUsername = username; activeUserId = userId
        if (isConnected.get() || isConnecting.get()) return
        isConnecting.set(true); scope.launch { doConnect() }
    }
    private fun doConnect() {
        try {
            val baseUrl = SupabaseConfig.url.trimEnd('/')
            val wsUrl = if (baseUrl.startsWith("https://")) "wss://${baseUrl.substring(8)}/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0" else "ws://$baseUrl/realtime/v1/websocket?apikey=${SupabaseConfig.anonKey}&v=1.0.0"
            webSocket = client.newWebSocket(Request.Builder().url(wsUrl).build(), createListener())
        } catch (e: Exception) { Log.e(TAG, "Failed to initiate WebSocket connection", e); isConnecting.set(false); scheduleReconnect() }
    }
    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) { isConnected.set(true); isConnecting.set(false); startHeartbeat(); sendAccessToken(); subscribeToTables(); setPresence(true) }
        override fun onMessage(webSocket: WebSocket, text: String) = handleIncomingMessage(text)
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { handleDisconnected() }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { Log.e(TAG, "WebSocket Failure: ${t.message}", t); handleDisconnected(); scheduleReconnect() }
    }
    private fun setPresence(online: Boolean) {
        val uid = activeUserId
        if (uid.isBlank()) return
        scope.launch {
            try {
                val body = JSONObject().apply { put("is_online", online); put("online_now", online); put("last_seen", nowIso()); put("last_seen_at", nowIso()) }
                val request = Request.Builder().url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/profiles?id=$uid").addHeader("apikey", SupabaseConfig.anonKey).addHeader("Authorization", "Bearer ${SupabaseService.accessToken() ?: SupabaseConfig.anonKey}").addHeader("Content-Type", "application/json").patch(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString())).build()
                client.newCall(request).execute().use { response -> if (!response.isSuccessful) Log.w(TAG, "Presence update failed: ${response.code}") }
            } catch (e: Exception) { Log.w(TAG, "Presence update exception", e) }
        }
    }
    private fun startHeartbeat() {
        heartbeatJob?.cancel(); heartbeatJob = scope.launch {
            while (isActive && isConnected.get()) { delay(25_000L); webSocket?.send(JSONObject().apply { put("topic", "phoenix"); put("event", "heartbeat"); put("payload", JSONObject()); put("ref", "hb_${refCounter.getAndIncrement()}") }.toString()); setPresence(true) }
        }
    }
    private fun sendAccessToken() { val token = SupabaseService.accessToken() ?: return; webSocket?.send(JSONObject().apply { put("topic", "realtime"); put("event", "access_token"); put("payload", JSONObject().put("access_token", token)); put("ref", refCounter.getAndIncrement().toString()) }.toString()) }
    private fun subscribeToTables() {
        val tables = listOf("messages","conversations","notifications","activities","feed_posts","post_likes","post_bookmarks","comments","comment_likes","comment_replies","stories","story_likes","story_reactions","story_replies","story_views","market_items","connection_requests","study_circles","study_circle_members","roommate_profiles","roommate_applications","skill_endorsements","poll_votes")
        tables.forEach { table -> val join = JSONObject().apply { put("topic", "realtime:public:$table"); put("event", "phx_join"); put("payload", JSONObject().apply { put("config", JSONObject().apply { put("postgres_changes", org.json.JSONArray().apply { put(JSONObject().apply { put("event", "*"); put("schema", "public"); put("table", table) }) }) }) }); put("ref", refCounter.getAndIncrement().toString()) }; webSocket?.send(join.toString()) }
    }
    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text); val event = json.optString("event"); val payload = json.optJSONObject("payload") ?: return; if (event != "postgres_changes") return
            val data = payload.optJSONObject("data") ?: return; val type = data.optString("type"); val table = data.optString("table"); val record = data.optJSONObject("record") ?: data.optJSONObject("old_record") ?: JSONObject()
            when (table) {
                "messages" -> { val senderId = record.optString("sender_id"); val senderUsername = record.optString("sender_username"); val createdAt = record.optString("created_at"); _events.tryEmit(RealtimeEvent.MessageEvent(type, ChatMessage(id = record.optString("id"), senderId = senderId.ifBlank { senderUsername }, senderUsername = senderUsername, receiverId = record.optString("receiver_id").ifBlank { record.optString("receiver_username") }, receiverUsername = record.optString("receiver_username"), text = record.optString("content", record.optString("text")), rawTimestamp = createdAt, timestamp = formatTimestamp(createdAt), isFromMe = senderId == activeUserId || senderUsername.equals(activeUsername, true), isRead = record.optBoolean("is_read", false), status = MessageStatus.SENT))) }
                "conversations" -> _events.tryEmit(RealtimeEvent.ConversationEvent(type, record.optString("id"), record.optString("last_message"), record.optString("updated_at", record.optString("last_message_at"))))
                "notifications" -> _events.tryEmit(RealtimeEvent.NotificationEvent(type, record.optString("id"), record.optString("user_id"), record.optString("username"), record.optString("type"), record.optString("title"), record.optString("content")))
                "feed_posts" -> _events.tryEmit(RealtimeEvent.FeedPostEvent(type, record.optString("id")))
            }
        } catch (e: Exception) { Log.e(TAG, "Error parsing realtime message", e) }
    }
    private fun handleDisconnected() { isConnected.set(false); isConnecting.set(false); heartbeatJob?.cancel(); setPresence(false) }
    private fun scheduleReconnect() { reconnectJob?.cancel(); reconnectJob = scope.launch { delay(5_000L); if (!isConnected.get() && activeUsername.isNotBlank()) connect(activeUsername, activeUserId) } }
    fun disconnect() { setPresence(false); activeUsername = ""; activeUserId = ""; heartbeatJob?.cancel(); reconnectJob?.cancel(); try { webSocket?.close(1000, "User logged out") } catch (_: Exception) {}; webSocket = null; isConnected.set(false); isConnecting.set(false) }
    private fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun formatTimestamp(value: String): String {
        if (value.isBlank()) return "Just now"
        return try {
            val normalized = value.trim().replace("Z", "+0000")
            val sdf = if (value.contains(".")) SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US) else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            SimpleDateFormat("h:mm a", Locale.US).format(sdf.parse(normalized) ?: Date())
        } catch (_: Exception) {
            "Just now"
        }
    }
}
