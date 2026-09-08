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
         * Fetches the current FCM token, stores it locally, registers it with Supabase,
         * then refreshes server-backed notification preferences for the active account.
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
                    NotificationPreferenceStore.refreshFromServerAsync(appContext)
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
                        NotificationPreferenceStore.refreshFromServerAsync(context.applicationContext)
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
        val type = BlinkNotificationType.fromWire(data["type"])
        Log.d(
            TAG,
            "FCM message received from=${message.from} type=$type dataKeys=${data.keys.joinToString()} hasNotification=${message.notification != null}"
        )

        // A token can have a pending FCM packet while the phone signs out of account A and
        // signs into account B. New server payloads include recipient_id so such a packet can
        // never surface in the wrong account. Old payloads remain backward compatible.
        if (!NotificationPreferenceStore.isIntendedForCurrentAccount(this, data["recipient_id"])) {
            Log.w(TAG, "Ignored push intended for a different Blink account.")
            return
        }

        val title = data["title"] ?: message.notification?.title ?: "Blink"
        val body = data["body"] ?: message.notification?.body ?: "You have a new notification."
        val sender = data["sender_username"].orEmpty()
        val senderName = data["sender_name"] ?: sender.ifBlank { "Blink" }
        val senderAvatar = data["sender_avatar"].orEmpty()
        val conversationId = data["conversation_id"].orEmpty()
        val messageId = data["message_id"].orEmpty()

        // Delivery acknowledgement describes transport delivery, not whether the user elected
        // to show the alert. A muted DM should still become "delivered" to the sender.
        if (type == BlinkNotificationType.MESSAGE && messageId.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    SupabaseService.initialize(applicationContext)
                    ChatRepository().markMessageDelivered(messageId)
                }.onFailure { error ->
                    Log.w(TAG, "Unable to acknowledge delivered message $messageId", error)
                }
            }
        }

        if (!NotificationPreferenceStore.isAllowed(this, type)) {
            Log.d(TAG, "Notification suppressed by Blink preference/quiet-hour policy: $type")
            return
        }

        val notificationId = data["notification_id"].orEmpty()
        val currentUid = runCatching {
            SupabaseService.initialize(applicationContext)
            SupabaseService().getCurrentUserId().orEmpty()
        }.getOrDefault("")

        // Social/admin pushes can arrive from both realtime recovery and FCM. Claim their
        // server id before rendering so only one path can alert on this installation.
        if (type != BlinkNotificationType.MESSAGE && notificationId.isNotBlank()) {
            if (SocialNotificationRecovery.wasShown(applicationContext, currentUid, notificationId)) {
                Log.d(TAG, "Duplicate notification suppressed: $notificationId")
                return
            }
            SocialNotificationRecovery.markShown(applicationContext, currentUid, notificationId)
        }

        // Visible notification work must stay synchronous. FCM gives this callback a short
        // execution window; never block incoming chat/call UI on avatar or database fetches.
        when (type) {
            BlinkNotificationType.INCOMING_CALL -> {
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

            BlinkNotificationType.CALL_UPDATE -> {
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

            BlinkNotificationType.MESSAGE -> {
                if (sender.isNotBlank()) {
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
            }

            BlinkNotificationType.MARKET -> BlinkNotificationHelper.showMarketNotification(
                context = this,
                title = title,
                body = body,
                targetMarketId = data["market_id"]
            )

            BlinkNotificationType.MARKET_ORDER -> BlinkNotificationHelper.showMarketOrderNotification(
                context = this,
                title = title,
                body = body,
                marketId = data["market_id"].orEmpty()
            )

            BlinkNotificationType.LIKE -> {
                val postId = data["post_id"].orEmpty()
                if (sender.isNotBlank() && postId.isNotBlank()) {
                    BlinkNotificationHelper.showLikeNotification(this, sender, postId)
                } else {
                    BlinkNotificationHelper.showSocialNotification(this, title, body, postId.ifBlank { null })
                }
            }

            BlinkNotificationType.COMMENT,
            BlinkNotificationType.REPLY -> {
                val postId = data["post_id"].orEmpty()
                if (sender.isNotBlank() && postId.isNotBlank()) {
                    BlinkNotificationHelper.showCommentNotification(this, sender, body, postId)
                } else {
                    BlinkNotificationHelper.showSocialNotification(this, title, body, postId.ifBlank { null })
                }
            }

            BlinkNotificationType.MENTION -> {
                val postId = data["post_id"].orEmpty()
                if (sender.isNotBlank() && postId.isNotBlank()) {
                    BlinkNotificationHelper.showMentionNotification(this, sender, postId, body)
                } else {
                    BlinkNotificationHelper.showSocialNotification(this, title, body, postId.ifBlank { null })
                }
            }

            BlinkNotificationType.FOLLOW -> {
                if (sender.isNotBlank()) {
                    BlinkNotificationHelper.showFollowNotification(this, sender)
                } else {
                    BlinkNotificationHelper.showSocialNotification(this, title, body, null)
                }
            }

            else -> BlinkNotificationHelper.showSocialNotification(
                context = this,
                title = title,
                body = body,
                targetPostId = data["post_id"]
            )
        }
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        Log.w(TAG, "FCM reported deleted pending messages; scheduling immediate Supabase reconciliation.")
        enqueueGapSync(applicationContext)
    }
}
