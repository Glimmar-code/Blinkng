package com.example.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object IncomingCallNotification {
    const val CHANNEL_INCOMING_CALLS = "blink_incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "blink_ongoing_calls"
    const val FOREGROUND_NOTIFICATION_ID = 8701

    private fun notificationId(callId: String): Int =
        70_000 + (callId.hashCode() and 0x7fffffff) % 20_000

    fun showIncoming(
        context: Context,
        callId: String,
        callType: CallType,
        peerId: String,
        peerUsername: String,
        peerName: String,
        peerAvatar: String,
        conversationId: String
    ) {
        if (callId.isBlank()) return
        createChannels(context)
        if (!hasNotificationPermission(context)) return

        val answerIntent = CallActivity.incomingIntent(
            context = context,
            callId = callId,
            peerId = peerId,
            peerUsername = peerUsername,
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = callType,
            conversationId = conversationId,
            answerImmediately = true
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val answerPendingIntent = PendingIntent.getActivity(
            context,
            notificationId(callId) + 1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = CallActivity.incomingIntent(
            context = context,
            callId = callId,
            peerId = peerId,
            peerUsername = peerUsername,
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = callType,
            conversationId = conversationId,
            answerImmediately = false
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId(callId) + 2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(callId) + 3,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val label = if (callType == CallType.VIDEO) "Incoming video call" else "Incoming voice call"
        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(peerName.ifBlank { "Blink user" })
            .setContentText(label)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(50_000L)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Answer", answerPendingIntent)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(notificationId(callId), notification) }
    }

    fun cancel(context: Context, callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    fun handleCallUpdate(context: Context, callId: String, event: String) {
        if (event.lowercase() in setOf("cancelled", "declined", "ended", "missed", "failed")) {
            cancel(context, callId)
            if (BlinkCallForegroundService.activeCallId == callId) {
                context.stopService(Intent(context, BlinkCallForegroundService::class.java))
            }
        }
    }

    fun ongoingNotification(
        context: Context,
        callId: String,
        peerName: String,
        callType: CallType,
        status: String
    ): Notification {
        createChannels(context)
        val openIntent = CallActivity.restoreIntent(context, callId)
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId(callId) + 10,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_END
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
        }
        val endPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId(callId) + 11,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val kind = if (callType == CallType.VIDEO) "Video call" else "Voice call"
        return NotificationCompat.Builder(context, CHANNEL_ONGOING_CALLS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(peerName.ifBlank { "Blink call" })
            .setContentText("$kind • $status")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End", endPendingIntent)
            .build()
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val callAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val incoming = NotificationChannel(
            CHANNEL_INCOMING_CALLS,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming Blink voice and video calls"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 350, 500, 350, 500)
            setSound(ringtone, callAudioAttributes)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING_CALLS,
            "Active calls",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active Blink voice and video calls"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannels(listOf(incoming, ongoing))
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
