package com.example.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.blinkng.shared.notification.BlinkNotificationQuickAction
import com.blinkng.shared.notification.BlinkNotificationQuickActionPolicy
import com.blinkng.shared.notification.BlinkNotificationKind

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
    const val EXTRA_EXPECTED_ACCOUNT = "blink_direct_reply_expected_account"

    private const val ACTION_DIRECT_REPLY = "com.example.notification.ACTION_DIRECT_REPLY"
    private const val MESSAGE_ID_BASE = 1000
    private const val SUMMARY_ID_BASE = 1800
    private const val GROUP_KEY_MESSAGES = "blink_group_messages"

    // Only keep a few previews in RAM while an alert is active, never in disk preferences.
    // Account-switching, dismissal and process death all invalidate this transient history.
    private data class Preview(val text: String, val atMillis: Long)
    private val historyLock = Any()
    private val activeHistory = LinkedHashMap<String, MutableList<Preview>>()
    private var historyAccount: String? = null

    private fun recentPreviews(
        account: String, conversation: String, notificationWasActive: Boolean,
        text: String, now: Long
    ): List<Preview> {
        if (!BlinkNotificationQuickActionPolicy.isId(account)) return listOf(Preview(text, now))
        synchronized(historyLock) {
            if (historyAccount != account) {
                activeHistory.clear()
                historyAccount = account
            }
            val key = conversation
            if (!notificationWasActive) activeHistory.remove(key)
            val previews = activeHistory.getOrPut(key) { mutableListOf() }
            previews.add(Preview(text, now))
            while (previews.size > 4) previews.removeAt(0)
            while (activeHistory.size > 16) {
                activeHistory.remove(activeHistory.keys.first())
            }
            return previews.toList()
        }
    }

    private fun messageGroup(account: String): String =
        if (BlinkNotificationQuickActionPolicy.isId(account)) "$GROUP_KEY_MESSAGES:$account"
        else GROUP_KEY_MESSAGES

    private fun activeMessageNotifications(context: Context, group: String): List<android.service.notification.StatusBarNotification> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return emptyList()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return emptyList()
        return runCatching {
            manager.activeNotifications.filter {
                it.id in MESSAGE_ID_BASE until MESSAGE_ID_BASE + 700 &&
                    it.notification.group == group &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
        }.getOrDefault(emptyList())
    }

    private fun updateGroupSummary(context: Context, account: String, group: String) {
        if (!BlinkNotificationQuickActionPolicy.isId(account)) return
        val summaryId = SUMMARY_ID_BASE + positiveHash(account) % 100
        val children = activeMessageNotifications(context, group)
        if (children.size < 2) {
            NotificationManagerCompat.from(context).cancel(summaryId)
            return
        }
        val senderNames = children.mapNotNull {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.trim()?.takeIf(String::isNotBlank)
        }.distinct().take(5)
        val inboxStyle = NotificationCompat.InboxStyle().apply {
            senderNames.forEach(::addLine)
            setSummaryText("${children.size} conversations")
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("blink://notification/messages/${Uri.encode(account)}")
            putExtra(BlinkNotificationHelper.EXTRA_ACTION, BlinkNotificationHelper.ACTION_OPEN_MESSAGES)
        }
        val openInbox = PendingIntent.getActivity(
            context, summaryId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
            .setContentTitle("BLINK Messages (${children.size})")
            .setContentText("${children.size} conversations")
            .setStyle(inboxStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(group)
            .setGroupSummary(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openInbox)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        postNotification(context, summaryId, summary)
    }

    fun show(
        context: Context,
        senderUsername: String,
        senderName: String,
        messageText: String,
        senderAvatar: String = "",
        conversationId: String = "",
        messageId: String = "",
        expectedAccount: String = "",
    ) {
        if (!BlinkNotificationHelper.hasNotificationPermission(context)) return
        if (!MessageNotificationLedger.claim(context, messageId)) return
        BlinkNotificationHelper.createNotificationChannels(context)

        val stableConversationKey = conversationId.ifBlank { senderUsername }
        val notificationId = MESSAGE_ID_BASE + positiveHash(stableConversationKey) % 700
        val group = messageGroup(expectedAccount)
        val previousAlertActive = activeMessageNotifications(context, group).any { it.id == notificationId }
        val now = System.currentTimeMillis()
        val previews = recentPreviews(expectedAccount, stableConversationKey, previousAlertActive, messageText, now)
        val sender = Person.Builder()
            .setName(senderName.ifBlank { senderUsername.ifBlank { "Blink user" } })
            .setKey(senderUsername.ifBlank { stableConversationKey })
            .build()
        val me = Person.Builder().setName("You").setKey("blink_self").build()

        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(sender.name)
            .setGroupConversation(false)
        previews.forEach { preview ->
            messagingStyle.addMessage(preview.text, preview.atMillis, sender)
        }

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
            originalMessageId = messageId,
            expectedAccount = expectedAccount,
        )

        val builder = NotificationCompat.Builder(context, BlinkNotificationHelper.CHANNEL_MESSAGES)
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
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
            .setGroup(group)
            .setGroupSummary(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val allowedActions = BlinkNotificationQuickActionPolicy.available(
            BlinkNotificationKind.MESSAGE,
            recipientBound = BlinkNotificationQuickActionPolicy.isId(expectedAccount),
        )
        if (BlinkNotificationQuickAction.REPLY in allowedActions) builder.addAction(replyAction)
        if (BlinkNotificationQuickAction.MARK_READ in allowedActions) {
            BlinkNotificationHelper.buildQuickAction(
                context, NotificationQuickActionWorker.ACTION_MARK_READ,
                expectedAccount, "", notificationId, username = senderUsername,
            )?.let(builder::addAction)
        }
        postNotification(context, notificationId, builder.build())
        // Android manages the collapsed view; a silent summary makes multiple
        // independent conversations easier to expand without duplicate alerts.
        updateGroupSummary(context, expectedAccount, group)
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
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
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
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
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
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
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
        originalMessageId: String,
        expectedAccount: String,
    ): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, NotificationReplyReceiver::class.java).apply {
            action = ACTION_DIRECT_REPLY
            // Intent extras do not participate in PendingIntent identity, so give each
            // conversation a distinct data URI as well as a distinct request code.
            data = Uri.parse(
                "blink://notification/reply/${Uri.encode(expectedAccount)}/${Uri.encode(conversationId.ifBlank { senderUsername })}"
            )
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_ORIGINAL_MESSAGE_ID, originalMessageId)
            putExtra(EXTRA_EXPECTED_ACCOUNT, expectedAccount)
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
