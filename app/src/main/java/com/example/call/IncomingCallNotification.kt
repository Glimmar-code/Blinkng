package com.example.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat

object IncomingCallNotification {
    /** Legacy channel id retained for compatibility with old installs/settings links. */
    const val CHANNEL_INCOMING_CALLS = "blink_incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "blink_ongoing_calls"
    const val CHANNEL_MISSED_CALLS = "blink_missed_calls"
    const val FOREGROUND_NOTIFICATION_ID = 8701

    private val incomingVibrationPattern = longArrayOf(0, 500, 350, 500, 350, 500)

    fun incomingChannelId(context: Context, callType: CallType): String =
        CallSoundPreferences.channelId(context, callType)

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

        // When Blink already has a visible, unlocked window, use Blink's own call banner.
        // This guarantees an Answer/Decline surface even when notification permission or
        // heads-up presentation is disabled. Background and lock-screen calls continue to
        // use Android CallStyle/full-screen notifications, which is the platform-safe path.
        if (shouldUseInAppBanner(context)) {
            IncomingCallBannerActivity.show(
                context = context.applicationContext,
                callId = callId,
                callType = callType,
                peerId = peerId,
                peerUsername = peerUsername,
                peerName = peerName,
                peerAvatar = peerAvatar,
                conversationId = conversationId
            )
            return
        }

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

        val caller = Person.Builder()
            .setName(peerName.ifBlank { "Blink user" })
            .setKey(peerId.ifBlank { peerUsername.ifBlank { callId } })
            .setImportant(true)
            .build()
        val label = if (callType == CallType.VIDEO) "Incoming video call" else "Incoming voice call"
        val ringtone = CallSoundPreferences.ringtoneUri(context, callType)
        val vibrate = CallSoundPreferences.vibrateEnabled(context)

        val builder = NotificationCompat.Builder(context, incomingChannelId(context, callType))
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
            .setOngoing(true)
            .setAutoCancel(false)
            .setTimeoutAfter(50_000L)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(openPendingIntent, true)

        // Android 7.x has no notification channels, so attach the selected sound/vibration
        // directly. Android 8+ gets the same behavior from the versioned channel below.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (ringtone != null) builder.setSound(ringtone) else builder.setSilent(true)
            if (vibrate) builder.setVibrate(incomingVibrationPattern) else builder.setVibrate(longArrayOf(0L))
        }

        val notification = builder.build().apply {
            if (ringtone != null) {
                // Repeat the selected ringtone until Answer/Decline/cancel/timeout.
                flags = flags or Notification.FLAG_INSISTENT
            }
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
            Intent(context, CallHistoryActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
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
            cancel(context, callId)
            IncomingCallBannerActivity.dismiss(callId)
            return
        }
        if (normalized in setOf("cancelled", "declined", "ended", "missed", "failed")) {
            cancel(context, callId)
            IncomingCallBannerActivity.dismiss(callId)
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
        val callAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun incomingChannel(type: CallType, name: String): NotificationChannel {
            val ringtone = CallSoundPreferences.ringtoneUri(context, type)
            val vibrate = CallSoundPreferences.vibrateEnabled(context)
            return NotificationChannel(
                incomingChannelId(context, type),
                name,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming Blink ${if (type == CallType.VIDEO) "video" else "voice"} calls"
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = incomingVibrationPattern
                if (ringtone != null) setSound(ringtone, callAudioAttributes) else setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        }

        val voice = incomingChannel(CallType.AUDIO, "Incoming voice calls")
        val video = incomingChannel(CallType.VIDEO, "Incoming video calls")
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
        manager.createNotificationChannels(listOf(voice, video, ongoing, missed))
    }

    private fun shouldUseInAppBanner(context: Context): Boolean {
        val process = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(process)
        if (process.importance != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) return false

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive == false) return false

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) return false

        return true
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
