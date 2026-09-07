package com.example.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity

/**
 * Fast path for direct-message notifications.
 *
 * Chat pushes must be rendered from the FCM payload immediately. In particular, do not
 * download an avatar before calling notify(): Android only gives onMessageReceived a short
 * execution window and a network fetch here can turn an instant push into a delayed/missed one.
 */
object InstantChatNotification {
    const val KEY_TEXT_REPLY = "blink_direct_reply_text"
    const val EXTRA_NOTIFICATION_ID = "blink_direct_reply_notification_id"
    const val EXTRA_CONVERSATION_ID = "blink_direct_reply_conversation_id"
    const val EXTRA_ORIGINAL_MESSAGE_ID = "blink_direct_reply_original_message_id"
    const val EXTRA_SENDER_USERNAME = "blink_direct_reply_sender_username"
    const val EXTRA_SENDER_NAME = "blink_direct_reply_sender_name"
    const val EXTRA_SENDER_AVATAR = "blink_direct_reply_sender_avatar"

    private const val ACTION_DIRECT_REPLY = "com.example.notification.ACTION_DIRECT_REPLY"
    private const val MESSAGE_ID_BASE = 1000
    private const val GROUP_KEY_MESSAGES = "blink_group_messages"

    fun show(
        context: Context,
        senderUsername: String,
        senderName: String,
        messageText: String,
        senderAvatar: String = "",
        conversationId: String = "",
        messageId: String = ""
    ) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        if (!MessageNotificationLedger.claim(context, messageId)) return
        BlinkNotificationHelper.createNotificationChannels(context)

        val stableConversationKey = conversationId.ifBlank { senderUsername }
        val notificationId = MESSAGE_ID_BASE + positiveHash(stableConversationKey) % 700
        val sender = Person.Builder()
            .setName(senderName.ifBlank { senderUsername.ifBlank { "Blink user" } })
            .setKey(senderUsername.ifBlank { stableConversationKey })
            .build()
        val me = Person.Builder().setName("You").setKey("blink_self").build()

        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .addMessage(messageText, System.currentTimeMillis(), sender)
            .setConversationTitle(sender.name)
            .setGroupConversation(false)

        val contentIntent = buildChatPendingIntent(
            context = context,
            senderUsername = senderUsername,
            senderName = senderName,
            senderAvatar = senderAvatar
        )
        val replyAction = buildReplyAction(
            context = context,
            notificationId = notificationId,
            senderUsername = senderUsername,
            senderName = senderName,
            senderAvatar = senderAvatar,
            conversationId = conversationId,
            originalMessageId = messageId
        )

        val notification = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender.name)
            .setContentText(messageText)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(contentIntent)
            .addAction(replyAction)
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        postNotification(context, notificationId, notification)
    }

    fun showReplyQueued(
        context: Context,
        notificationId: Int,
        senderName: String,
        replyText: String
    ) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        BlinkNotificationHelper.createNotificationChannels(context)
        val notification = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Sending to ${senderName.ifBlank { "Blink user" }}")
            .setContentText(replyText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRemoteInputHistory(arrayOf(replyText))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        postNotification(context, notificationId, notification)
    }

    fun showReplySent(
        context: Context,
        notificationId: Int,
        senderName: String,
        replyText: String
    ) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Reply sent to ${senderName.ifBlank { "Blink user" }}")
            .setContentText(replyText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRemoteInputHistory(arrayOf(replyText))
            .setTimeoutAfter(3_000L)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        postNotification(context, notificationId, notification)
    }

    fun showReplyWaiting(
        context: Context,
        notificationId: Int,
        senderName: String,
        replyText: String
    ) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        val notification = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Reply waiting for connection")
            .setContentText("To ${senderName.ifBlank { "Blink user" }}: $replyText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRemoteInputHistory(arrayOf(replyText))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        postNotification(context, notificationId, notification)
    }

    private fun buildReplyAction(
        context: Context,
        notificationId: Int,
        senderUsername: String,
        senderName: String,
        senderAvatar: String,
        conversationId: String,
        originalMessageId: String
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_DIRECT_REPLY
            // Intent extras do not participate in PendingIntent identity, so give each
            // conversation a distinct data URI as well as a distinct request code.
            data = Uri.parse("blink://notification/reply/${Uri.encode(conversationId.ifBlank { senderUsername })}")
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_ORIGINAL_MESSAGE_ID, originalMessageId)
            putExtra(EXTRA_SENDER_USERNAME, senderUsername)
            putExtra(EXTRA_SENDER_NAME, senderName)
            putExtra(EXTRA_SENDER_AVATAR, senderAvatar)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setShowsUserInterface(false)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()
    }

    private fun buildChatPendingIntent(
        context: Context,
        senderUsername: String,
        senderName: String,
        senderAvatar: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlinkNotificationHelper.EXTRA_ACTION, BlinkNotificationHelper.ACTION_OPEN_CHAT)
            putExtra(BlinkNotificationHelper.EXTRA_PARTNER_USERNAME, senderUsername)
            putExtra(BlinkNotificationHelper.EXTRA_PARTNER_NAME, senderName)
            putExtra(BlinkNotificationHelper.EXTRA_PARTNER_AVATAR, senderAvatar)
        }
        return PendingIntent.getActivity(
            context,
            positiveHash(senderUsername),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Lint cannot infer the runtime permission contract hidden behind
     * BlinkNotificationHelper.hasNotificationPermission(), so the suppression is scoped to
     * this single guarded call. We still re-check immediately before notify and catch the
     * permission-revoked race instead of allowing a notification callback to crash.
     */
    @SuppressLint("MissingPermission")
    private fun postNotification(context: Context, notificationId: Int, notification: Notification) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify(). Skip safely.
        }
    }

    private fun positiveHash(value: String): Int = value.hashCode() and Int.MAX_VALUE
}
