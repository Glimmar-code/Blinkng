package com.example.notification

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.BuildConfig
import com.example.call.CallTimeoutWorker
import com.example.call.CallType
import com.example.call.IncomingCallNotification
import com.example.data.repository.ChatRepository
import com.example.data.supabase.SupabaseConfig
import com.example.data.supabase.SupabaseService
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BlinkFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "BlinkFCM"
        private const val PUSH_PREFS = "blink_push"
        private const val TOKEN_KEY = "fcm_token"

        /**
         * Fetches the current FCM token, prints it in debug Logcat for Firebase Console
         * testing, stores it locally, and registers it with Supabase when a real user
         * session is available.
         */
        fun syncCurrentToken(context: Context) {
            val appContext = context.applicationContext
            FirebaseApp.initializeApp(appContext)
                ?: run {
                    Log.i(TAG, "Firebase is not configured; add app/google-services.json to enable FCM.")
                    return
                }

            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isBlank()) {
                        Log.w(TAG, "Firebase returned an empty FCM token.")
                        return@addOnSuccessListener
                    }

                    saveToken(appContext, token)
                    logToken("Current FCM token", token)
                    CoroutineScope(Dispatchers.IO).launch {
                        syncTokenNow(appContext, token)
                    }
                    enqueueGapSync(appContext)
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to obtain FCM token", error)
                }
        }

        /**
         * Deactivates this device token for the currently authenticated account.
         * This must run before the Supabase access token is revoked during logout.
         */
        suspend fun unregisterCurrentToken(context: Context): Boolean = withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val token = appContext.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_KEY, "")
                .orEmpty()
            if (token.isBlank()) return@withContext true

            return@withContext try {
                SupabaseService.initialize(appContext)
                val accessToken = SupabaseService.accessToken() ?: return@withContext false
                val body = JSONObject()
                    .put("p_token", token)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/unregister_my_fcm_token")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "FCM token unregister failed: ${response.code}")
                    }
                    response.isSuccessful
                }
            } catch (error: Exception) {
                Log.w(TAG, "FCM token unregister error", error)
                false
            }
        }

        private fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PUSH_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(TOKEN_KEY, token)
                .apply()
        }

        private fun logToken(label: String, token: String) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "$label: $token")
            } else {
                Log.i(TAG, "$label refreshed (${token.take(8)}…)")
            }
        }

        private fun syncTokenNow(context: Context, token: String) {
            try {
                SupabaseService.initialize(context.applicationContext)
                SupabaseService().getCurrentUserId() ?: return
                val accessToken = SupabaseService.accessToken() ?: return
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
                val body = JSONObject()
                    .put("p_token", token)
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("${SupabaseConfig.url.trimEnd('/')}/rest/v1/rpc/register_my_fcm_token")
                    .addHeader("apikey", SupabaseConfig.anonKey)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        Log.w(TAG, "FCM token sync failed: ${response.code} ${responseBody.take(240)}")
                    } else {
                        Log.d(TAG, "FCM token registered with Supabase.")
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "FCM token sync error", error)
            }
        }

        private fun enqueueGapSync(context: Context) {
            val work = OneTimeWorkRequestBuilder<NotificationSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "blink_fcm_gap_sync",
                ExistingWorkPolicy.REPLACE,
                work
            )
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isBlank()) return

        saveToken(applicationContext, token)
        logToken("New FCM token", token)
        CoroutineScope(Dispatchers.IO).launch {
            syncTokenNow(applicationContext, token)
        }
        enqueueGapSync(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        Log.d(
            TAG,
            "FCM message received from=${message.from} dataKeys=${data.keys.joinToString()} hasNotification=${message.notification != null}"
        )

        val title = data["title"] ?: message.notification?.title ?: "Blink"
        val body = data["body"] ?: message.notification?.body ?: "You have a new notification."
        val type = data["type"] ?: "social"
        val sender = data["sender_username"].orEmpty()
        val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }
        val senderAvatar = data["sender_avatar"].orEmpty()
        val conversationId = data["conversation_id"].orEmpty()
        val messageId = data["message_id"].orEmpty()

        // Visible notification work must stay synchronous. FCM gives this callback a short
        // execution window; never block incoming chat/call UI on avatar or database fetches.
        when {
            type.equals("incoming_call", ignoreCase = true) -> {
                val callId = data["call_id"].orEmpty()
                val callerId = data["caller_id"].orEmpty()
                if (callId.isNotBlank() && callerId.isNotBlank()) {
                    val callType = CallType.fromWire(data["call_type"])
                    IncomingCallNotification.showIncoming(
                        context = this,
                        callId = callId,
                        callType = callType,
                        peerId = callerId,
                        peerUsername = sender,
                        peerName = senderName,
                        peerAvatar = senderAvatar,
                        conversationId = conversationId
                    )
                    CallTimeoutWorker.schedule(
                        context = this,
                        callId = callId,
                        callType = callType,
                        peerName = senderName
                    )
                }
            }

            type.equals("call_update", ignoreCase = true) -> {
                val callId = data["call_id"].orEmpty()
                val event = data["call_event"].orEmpty()
                CallTimeoutWorker.cancel(this, callId)
                IncomingCallNotification.handleCallUpdate(
                    context = this,
                    callId = callId,
                    event = event
                )
                if (event.equals("missed", ignoreCase = true)) {
                    IncomingCallNotification.showMissed(
                        context = this,
                        callId = callId,
                        callType = CallType.fromWire(data["call_type"]),
                        peerName = senderName
                    )
                }
            }

            type.equals("message", ignoreCase = true) && sender.isNotBlank() -> {
                InstantChatNotification.show(
                    context = this,
                    senderUsername = sender,
                    senderName = senderName,
                    messageText = body,
                    senderAvatar = senderAvatar,
                    conversationId = conversationId,
                    messageId = messageId
                )
            }

            type.equals("market", ignoreCase = true) -> {
                BlinkNotificationHelper.showMarketNotification(
                    context = this,
                    title = title,
                    body = body,
                    targetMarketId = data["market_id"]
                )
            }

            else -> {
                // When a social/admin push carries its server notification id, remember it
                // before rendering. The reconnect worker can then skip the same unread row.
                val notificationId = data["notification_id"].orEmpty()
                if (notificationId.isNotBlank()) {
                    val uid = runCatching {
                        SupabaseService.initialize(applicationContext)
                        SupabaseService().getCurrentUserId().orEmpty()
                    }.getOrDefault("")
                    SocialNotificationRecovery.markShown(applicationContext, uid, notificationId)
                }
                BlinkNotificationHelper.showSocialNotification(
                    context = this,
                    title = title,
                    body = body,
                    targetPostId = data["post_id"]
                )
            }
        }

        // Delivery acknowledgement is secondary work; never block the visible notification on it.
        if (type.equals("message", ignoreCase = true) && messageId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    SupabaseService.initialize(applicationContext)
                    ChatRepository().markMessageDelivered(messageId)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to acknowledge delivered message $messageId", error)
                }
            }
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "FCM reported deleted pending messages; scheduling immediate Supabase reconciliation.")
        enqueueGapSync(applicationContext)
    }
}
