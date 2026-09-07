package com.example.call

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.app.Person
import androidx.core.content.ContextCompat

object IncomingCallNotification {
    const val CHANNEL_INCOMING_CALLS = "blink_incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "blink_ongoing_calls"
    const val CHANNEL_MISSED_CALLS = "blink_missed_calls"
    const val FOREGROUND_NOTIFICATION_ID = 8701

    private val incomingVibrationPattern = longArrayOf(0, 500, 350, 500, 350, 500)

    private fun notificationId(callId: String): Int =
        70_000 + (callId.hashCode() and 0x7fffffff) % 20_000

    private fun missedNotificationId(callId: String): Int =
        90_000 + (callId.hashCode() and 0x7fffffff) % 20_000

    @SuppressLint("MissingPermission")
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
        // Android 13+ requires POST_NOTIFICATIONS before the operating system may show
        // the incoming-call heads-up/full-screen UI. The app requests it after sign-in.
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

        val caller = Person.Builder()
            .setName(peerName.ifBlank { "Blink user" })
            .setKey(peerId.ifBlank { peerUsername.ifBlank { callId } })
            .setImportant(true)
            .build()
        val label = if (callType == CallType.VIDEO) "Incoming video call" else "Incoming voice call"
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

        // CallStyle gives Android a real incoming-call surface with system Answer/Decline
        // affordances. The full-screen intent is used when Android permits it (for example
        // on a locked device); otherwise the same notification degrades to a heads-up banner.
        // setSound()/setVibrate() are also applied directly so Android 7.x devices, which do
        // not support notification channels, still audibly ring and vibrate.
        val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(peerName.ifBlank { "Blink user" })
            .setContentText(label)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSound(ringtone)
            .setVibrate(incomingVibrationPattern)
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(50_000L)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)
            .build()
            .apply {
                // Keep the ringtone repeating until Answer/Decline/cancel/timeout, rather than
                // behaving like a one-shot message notification.
                flags = flags or Notification.FLAG_INSISTENT
            }

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(callId), notification)
        }
    }

    @SuppressLint("MissingPermission")
    fun showMissed(
        context: Context,
        callId: String,
        callType: CallType,
        peerName: String
    ) {
        if (callId.isBlank()) return
        createChannels(context)
        if (!hasNotificationPermission(context)) return
        cancel(context, callId)

        val openPendingIntent = PendingIntent.getActivity(
            context,
            missedNotificationId(callId) + 1,
            CallActivity.restoreIntent(context, callId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val kind = if (callType == CallType.VIDEO) "video" else "voice"
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED_CALLS)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Missed $kind call")
            .setContentText("From ${peerName.ifBlank { "Blink user" }}")
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(missedNotificationId(callId), notification)
        }
    }

    fun cancel(context: Context, callId: String) {
        if (callId.isBlank()) return
        NotificationManagerCompat.from(context).cancel(notificationId(callId))
    }

    fun handleCallUpdate(context: Context, callId: String, event: String) {
        val normalized = event.lowercase()
        if (normalized == "answered") {
            // Another device on the same receiver account answered. Remove only the stale
            // incoming notification; never stop an already-running call foreground service.
            cancel(context, callId)
            return
        }
        if (normalized in setOf("cancelled", "declined", "ended", "missed", "failed")) {
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
            .setSmallIcon(android.R.drawable.ic_menu_call)
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
            vibrationPattern = incomingVibrationPattern
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
        val missed = NotificationChannel(
            CHANNEL_MISSED_CALLS,
            "Missed calls",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Missed Blink voice and video calls"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannels(listOf(incoming, ongoing, missed))
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}