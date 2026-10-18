package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.blinkng.shared.notification.BlinkNotificationPresentation
import com.blinkng.shared.notification.BlinkNotificationQuickActionPolicy
import com.blinkng.shared.notification.BlinkNotificationQuickAction
import com.blinkng.shared.notification.BlinkNotificationKind
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue

/**
 * Central notification manager for Blink.
 *
 * Features:
 * - Android 8+ notification channels
 * - Android 13+ POST_NOTIFICATIONS permission awareness
 * - Channel groups
 * - MessagingStyle chat notifications
 * - Inbox-style social notifications
 * - Market notifications
 * - Per-conversation channel support
 * - Notification grouping
 * - Deep links into MainActivity
 * - Auto-cancel
 * - Large expandable text
 * - Conversation shortcuts groundwork
 * - Badge support
 * - Notification cancellation helpers
 * - Settings shortcuts
 * - Stable positive notification IDs
 * - Safer notification permission handling
 * - Quiet / normal / urgent channel separation
 */
object BlinkNotificationHelper {

    // ================================================================
    // CHANNEL GROUPS
    // ================================================================

    const val GROUP_COMMUNICATION = "blink_communication_group"
    const val GROUP_SOCIAL = "blink_social_group"
    const val GROUP_MARKET = "blink_market_group"

    // ================================================================
    // MAIN CHANNELS
    // ================================================================

    const val CHANNEL_MESSAGES = "blink_messages_channel"
    const val CHANNEL_SOCIAL = "blink_social_channel"
    const val CHANNEL_MARKET = "blink_market_channel"

    // ================================================================
    // OPTIONAL SUBCHANNELS
    // ================================================================

    const val CHANNEL_MENTIONS = "blink_mentions_channel"
    const val CHANNEL_COMMENTS = "blink_comments_channel"
    const val CHANNEL_FOLLOWS = "blink_follows_channel"
    const val CHANNEL_MARKET_ORDERS = "blink_market_orders_channel"

    // ================================================================
    // NOTIFICATION ID RANGES
    // ================================================================

    private const val MSG_ID_BASE = 1000
    private const val SOCIAL_ID_BASE = 2000
    private const val MARKET_ID_BASE = 3000
    private const val MENTION_ID_BASE = 4000
    private const val COMMENT_ID_BASE = 5000

    // ================================================================
    // GROUP KEYS
    // ================================================================

    private const val GROUP_KEY_MESSAGES = "blink_group_messages"
    private const val GROUP_KEY_SOCIAL = "blink_group_social"
    private const val GROUP_KEY_MARKET = "blink_group_market"

    // ================================================================
    // ACTION KEYS
    // ================================================================

    const val EXTRA_ACTION = "EXTRA_ACTION"
    const val EXTRA_PARTNER_USERNAME = "EXTRA_PARTNER_USERNAME"
    const val EXTRA_PARTNER_NAME = "EXTRA_PARTNER_NAME"
    const val EXTRA_PARTNER_AVATAR = "EXTRA_PARTNER_AVATAR"
    const val EXTRA_POST_ID = "EXTRA_POST_ID"
    const val EXTRA_MARKET_ID = "EXTRA_MARKET_ID"
    const val EXTRA_PROFILE_USERNAME = "EXTRA_PROFILE_USERNAME"

    const val ACTION_OPEN_CHAT = "OPEN_CHAT"
    const val ACTION_OPEN_POST = "OPEN_POST"
    const val ACTION_OPEN_MARKET = "OPEN_MARKET"
    const val ACTION_OPEN_SOCIAL = "OPEN_SOCIAL"
    const val ACTION_OPEN_PROFILE = "OPEN_PROFILE"

    // ================================================================
    // COLORS
    // ================================================================

    private const val BRAND_COLOR = 0xFF171717.toInt()

    // ================================================================
    // PUBLIC CHANNEL INITIALIZATION
    // ================================================================

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as? NotificationManager
                ?: return

        // ------------------------------------------------------------
        // GROUPS
        // ------------------------------------------------------------

        manager.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(
                    GROUP_COMMUNICATION,
                    "Blink Communication"
                ),
                NotificationChannelGroup(
                    GROUP_SOCIAL,
                    "Blink Social"
                ),
                NotificationChannelGroup(
                    GROUP_MARKET,
                    "Blink Market"
                )
            )
        )

        // ------------------------------------------------------------
        // MESSAGES
        // ------------------------------------------------------------

        val messagesChannel =
            NotificationChannel(
                CHANNEL_MESSAGES,
                "Direct Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "Messages from Blink students, creators and marketplace sellers"

                enableLights(true)
                lightColor =
                    Color.parseColor("#777777")

                enableVibration(true)
                vibrationPattern =
                    longArrayOf(
                        0,
                        180,
                        100,
                        180
                    )

                setShowBadge(true)
                setGroup(
                    GROUP_COMMUNICATION
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // SOCIAL
        // ------------------------------------------------------------

        val socialChannel =
            NotificationChannel(
                CHANNEL_SOCIAL,
                "Campus Activity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description =
                    "Likes, comments, mentions, follows and campus activity"

                enableLights(true)
                lightColor =
                    Color.parseColor("#777777")

                enableVibration(true)
                vibrationPattern =
                    longArrayOf(
                        0,
                        100,
                        100,
                        100
                    )

                setShowBadge(true)

                setGroup(
                    GROUP_SOCIAL
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // MARKET
        // ------------------------------------------------------------

        val marketChannel =
            NotificationChannel(
                CHANNEL_MARKET,
                "Aluta Market",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "Buyer inquiries, orders, seller activity and important marketplace updates"

                enableLights(true)
                lightColor =
                    Color.parseColor("#777777")

                enableVibration(true)
                vibrationPattern =
                    longArrayOf(
                        0,
                        200,
                        100,
                        200
                    )

                setShowBadge(true)

                setGroup(
                    GROUP_MARKET
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // MENTIONS
        // ------------------------------------------------------------

        val mentionsChannel =
            NotificationChannel(
                CHANNEL_MENTIONS,
                "Mentions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description =
                    "When someone mentions you on Blink"

                setShowBadge(true)
                setGroup(
                    GROUP_SOCIAL
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // COMMENTS
        // ------------------------------------------------------------

        val commentsChannel =
            NotificationChannel(
                CHANNEL_COMMENTS,
                "Comments",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {

                description =
                    "Comments and replies on your posts"

                setShowBadge(true)
                setGroup(
                    GROUP_SOCIAL
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // FOLLOWS
        // ------------------------------------------------------------

        val followsChannel =
            NotificationChannel(
                CHANNEL_FOLLOWS,
                "New Followers",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "New people following your Blink profile"

                setShowBadge(false)

                setGroup(
                    GROUP_SOCIAL
                )

                lockscreenVisibility =
                    NotificationCompat
                        .VISIBILITY_PRIVATE
            }

        // ------------------------------------------------------------
        // MARKET ORDERS
        // ------------------------------------------------------------

        val marketOrdersChannel =
            NotificationChannel(
                CHANNEL_MARKET_ORDERS,
                "Market Orders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {

                description =
                    "Important order and buyer/seller events"

                enableLights(true)
                lightColor =
                    Color.parseColor("#777777")

                enableVibration(true)

                setShowBadge(true)

                setGroup(
                    GROUP_MARKET
                )
            }

        manager.createNotificationChannels(
            listOf(
                messagesChannel,
                socialChannel,
                marketChannel,
                mentionsChannel,
                commentsChannel,
                followsChannel,
                marketOrdersChannel
            )
        )
    }

    // ================================================================
    // PERMISSION
    // ================================================================

    fun hasNotificationPermission(
        context: Context
    ): Boolean {

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) ==
                    PackageManager.PERMISSION_GRANTED

        } else {

            NotificationManagerCompat
                .from(context)
                .areNotificationsEnabled()
        }
    }

    fun areNotificationsEnabled(
        context: Context
    ): Boolean {

        return NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
    }

    // ================================================================
    // SETTINGS INTENT
    // ================================================================

    fun openNotificationSettings(
        context: Context
    ) {

        val intent =
            Intent(
                android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
            ).apply {

                putExtra(
                    android.provider.Settings.EXTRA_APP_PACKAGE,
                    context.packageName
                )
            }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)
    }

    fun openChannelSettings(
        context: Context,
        channelId: String
    ) {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            openNotificationSettings(
                context
            )
            return
        }

        val intent =
            Intent(
                android.provider.Settings
                    .ACTION_CHANNEL_NOTIFICATION_SETTINGS
            ).apply {

                putExtra(
                    android.provider.Settings.EXTRA_APP_PACKAGE,
                    context.packageName
                )

                putExtra(
                    android.provider.Settings.EXTRA_CHANNEL_ID,
                    channelId
                )
            }

        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(intent)
    }


    /** Scoped action intent. Explicit receiver plus unique data URI prevents PendingIntent aliasing. */
    fun buildQuickAction(
        context: Context,
        action: String,
        expectedAccount: String,
        targetId: String,
        notificationId: Int,
        username: String = "",
    ): NotificationCompat.Action? {
        if (!BlinkNotificationQuickActionPolicy.isId(expectedAccount)) return null
        if (action != NotificationQuickActionWorker.ACTION_MARK_READ &&
            !BlinkNotificationQuickActionPolicy.isId(targetId)) return null
        if (action == NotificationQuickActionWorker.ACTION_MARK_READ && username.isBlank()) return null
        val intent = Intent(context, NotificationQuickActionReceiver::class.java).apply {
            data = Uri.parse(
                "blink://notification/quick/${Uri.encode(expectedAccount)}/${Uri.encode(action)}/" +
                    "${Uri.encode(targetId.ifBlank { username })}/$notificationId"
            )
            putExtra(NotificationQuickActionWorker.KEY_ACTION, action)
            putExtra(NotificationQuickActionWorker.KEY_USER_ID, expectedAccount)
            putExtra(NotificationQuickActionWorker.KEY_TARGET_ID, targetId)
            putExtra(NotificationQuickActionWorker.KEY_USERNAME, username)
            putExtra(NotificationQuickActionWorker.KEY_NOTIFICATION_ID, notificationId)
        }
        val pending = PendingIntent.getBroadcast(
            context, positiveHash("${expectedAccount}_${action}_$targetId$username$notificationId"),
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val label = when (action) {
            NotificationQuickActionWorker.ACTION_FOLLOW -> "Follow Back"
            NotificationQuickActionWorker.ACTION_LIKE_POST,
            NotificationQuickActionWorker.ACTION_LIKE_COMMENT -> "Like"
            NotificationQuickActionWorker.ACTION_MARK_READ -> "Mark Read"
            else -> return null
        }
        return NotificationCompat.Action.Builder(0, label, pending).build()
    }

    private fun buildProfilePendingIntent(context: Context, username: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ACTION, ACTION_OPEN_PROFILE)
            putExtra(EXTRA_PROFILE_USERNAME, username)
            data = Uri.parse("blink://notification/profile/${Uri.encode(username)}")
        }
        return PendingIntent.getActivity(
            context, positiveHash("profile_$username"), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ================================================================
    // CHAT DEEP LINK
    // ================================================================

    private fun buildChatPendingIntent(
        context: Context,
        senderUsername: String,
        senderName: String,
        senderAvatar: String = ""
    ): PendingIntent {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    EXTRA_ACTION,
                    ACTION_OPEN_CHAT
                )

                putExtra(
                    EXTRA_PARTNER_USERNAME,
                    senderUsername
                )

                putExtra(
                    EXTRA_PARTNER_NAME,
                    senderName
                )

                putExtra(
                    EXTRA_PARTNER_AVATAR,
                    senderAvatar
                )
            }

        return PendingIntent.getActivity(
            context,
            positiveHash(
                senderUsername
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================================================================
    // POST DEEP LINK
    // ================================================================

    private fun buildPostPendingIntent(
        context: Context,
        postId: String?
    ): PendingIntent {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    EXTRA_ACTION,
                    ACTION_OPEN_POST
                )

                putExtra(
                    EXTRA_POST_ID,
                    postId
                )
            }

        return PendingIntent.getActivity(
            context,
            positiveHash(
                "post_${postId ?: "unknown"}"
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================================================================
    // MARKET DEEP LINK
    // ================================================================

    private fun buildMarketPendingIntent(
        context: Context,
        marketId: String?
    ): PendingIntent {

        val intent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    EXTRA_ACTION,
                    ACTION_OPEN_MARKET
                )

                putExtra(
                    EXTRA_MARKET_ID,
                    marketId
                )
            }

        return PendingIntent.getActivity(
            context,
            positiveHash(
                "market_${marketId ?: "unknown"}"
            ),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================================================================
    // CHAT NOTIFICATION
    // ================================================================

    private fun loadAvatarBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    fun showChatMessageNotification(
        context: Context,
        senderUsername: String,
        senderName: String,
        messageText: String,
        senderAvatar: String = "",
        messageId: String = ""
    ) {
        if (!hasNotificationPermission(context)) return
        if (!MessageNotificationLedger.claim(context, messageId)) return
        createNotificationChannels(context)

        val avatarBitmap = loadAvatarBitmap(senderAvatar)
        val personBuilder = Person.Builder()
            .setName(senderName)
            .setKey(senderUsername)
        avatarBitmap?.let { personBuilder.setIcon(IconCompat.createWithBitmap(it)) }
        val person = personBuilder.build()

        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("You").setKey("blink_self").build()
        )
            .addMessage(messageText, System.currentTimeMillis(), person)
            .setConversationTitle(senderName)
            .setGroupConversation(false)

        val chatIntent = buildChatPendingIntent(context, senderUsername, senderName, senderAvatar)
        val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(com.example.R.drawable.ic_stat_blink)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(BRAND_COLOR)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(chatIntent)
            .addAction(android.R.drawable.ic_menu_send, "Reply", chatIntent)
            .setGroup(GROUP_KEY_MESSAGES)
            .setGroupSummary(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        avatarBitmap?.let { builder.setLargeIcon(it) }

        notifySafely(
            context = context,
            id = MSG_ID_BASE + positiveHash(senderUsername) % 700,
            notification = builder.build()
        )
    }

    // ================================================================
    // MULTI-MESSAGE CHAT SUMMARY
    // ================================================================

    fun showChatSummaryNotification(
        context: Context,
        conversationTitle: String,
        messages: List<Pair<String, String>>,
        senderUsername: String
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        if (messages.isEmpty()) {
            return
        }

        createNotificationChannels(
            context
        )

        val inboxStyle =
            NotificationCompat.InboxStyle()

        messages
            .takeLast(5)
            .forEach { pair ->

                inboxStyle.addLine(
                    "${pair.first}: ${pair.second}"
                )
            }

        inboxStyle.setSummaryText(
            "$conversationTitle • ${messages.size} recent messages"
        )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_MESSAGES
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    conversationTitle
                )
                .setStyle(
                    inboxStyle
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_MESSAGE
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildChatPendingIntent(
                        context,
                        senderUsername,
                        conversationTitle
                    )
                )
                .setGroup(
                    GROUP_KEY_MESSAGES
                )
                .build()

        notifySafely(
            context,
            MSG_ID_BASE +
                    positiveHash(
                        "summary_$senderUsername"
                    ) % 700,
            notification
        )
    }

    // ================================================================
    // SOCIAL NOTIFICATION
    // ================================================================

    fun showSocialNotification(
        context: Context,
        title: String,
        body: String,
        targetPostId: String? = null
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )
        val safeTitle = BlinkNotificationPresentation.title(title)
        val safeBody = BlinkNotificationPresentation.body(body, "")

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_SOCIAL
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    safeTitle
                )
                .setContentText(
                    safeBody
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(safeBody)
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_SOCIAL
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_DEFAULT
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildPostPendingIntent(
                        context,
                        targetPostId
                    )
                )
                .setGroup(
                    GROUP_KEY_SOCIAL
                )
                .build()

        notifySafely(
            context,
            SOCIAL_ID_BASE +
                    positiveHash(
                        "$title$body"
                    ) % 700,
            notification
        )
    }

    // ================================================================
    // LIKE
    // ================================================================

    fun showLikeNotification(
        context: Context,
        username: String,
        postId: String
    ) {

        val actor = BlinkNotificationPresentation.username(username)
        showSocialNotification(
            context = context,
            title =
                if (actor.isBlank()) "Someone liked your post" else "❤️ @$actor liked your post",
            body =
                "Someone interacted with your campus post.",
            targetPostId =
                postId
        )
    }

    // ================================================================
    // COMMENT
    // ================================================================

    fun showCommentNotification(
        context: Context,
        username: String,
        comment: String,
        postId: String,
        commentId: String = "",
        expectedAccount: String = "",
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )
        val actor = BlinkNotificationPresentation.username(username)
        val safeComment = BlinkNotificationPresentation.body(comment, "New comment on your post")
        val notificationId = COMMENT_ID_BASE + positiveHash("${username}_${postId}_${comment}") % 700
        val actions = BlinkNotificationQuickActionPolicy.available(
            BlinkNotificationKind.COMMENT,
            recipientBound = BlinkNotificationQuickActionPolicy.isId(expectedAccount),
            commentId = commentId,
            postId = postId,
        )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_COMMENTS
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    if (actor.isBlank()) "Someone commented on your post" else "@$actor commented on your post"
                )
                .setContentText(
                    safeComment
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(safeComment)
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_SOCIAL
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_DEFAULT
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildPostPendingIntent(
                        context,
                        postId
                    )
                )
                .setGroup(
                    GROUP_KEY_SOCIAL
                )
        if (BlinkNotificationQuickAction.LIKE_COMMENT in actions) {
            buildQuickAction(
                context, NotificationQuickActionWorker.ACTION_LIKE_COMMENT,
                expectedAccount, commentId, notificationId,
            )?.let(builder::addAction)
        }
        builder.addAction(0, "Reply", buildPostPendingIntent(context, postId))
        val notification = builder.build()

        notifySafely(
            context,
            notificationId,
            notification
        )
    }

    // ================================================================
    // MENTION
    // ================================================================

    fun showMentionNotification(
        context: Context,
        username: String,
        postId: String,
        body: String,
        expectedAccount: String = "",
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )
        val actor = BlinkNotificationPresentation.username(username)
        val safeBody = BlinkNotificationPresentation.body(body, "You were mentioned in a post.")
        val notificationId = MENTION_ID_BASE + positiveHash("${username}_$postId") % 700
        val actions = BlinkNotificationQuickActionPolicy.available(
            BlinkNotificationKind.MENTION,
            recipientBound = BlinkNotificationQuickActionPolicy.isId(expectedAccount),
            postId = postId,
        )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_MENTIONS
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    if (actor.isBlank()) "Someone mentioned you" else "@$actor mentioned you"
                )
                .setContentText(
                    safeBody
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(safeBody)
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_SOCIAL
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildPostPendingIntent(
                        context,
                        postId
                    )
                )
                .setGroup(
                    GROUP_KEY_SOCIAL
                )
        if (BlinkNotificationQuickAction.LIKE_POST in actions) {
            buildQuickAction(
                context, NotificationQuickActionWorker.ACTION_LIKE_POST,
                expectedAccount, postId, notificationId,
            )?.let(builder::addAction)
        }
        builder.addAction(0, "View Post", buildPostPendingIntent(context, postId))
        val notification = builder.build()

        notifySafely(
            context,
            notificationId,
            notification
        )
    }

    // ================================================================
    // FOLLOW NOTIFICATION
    // ================================================================

    fun showFollowNotification(
        context: Context,
        username: String,
        actorId: String = "",
        expectedAccount: String = "",
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )
        val actor = BlinkNotificationPresentation.username(username)
        val notificationId = SOCIAL_ID_BASE + positiveHash("follow_$username") % 700
        val actions = BlinkNotificationQuickActionPolicy.available(
            BlinkNotificationKind.FOLLOW,
            recipientBound = BlinkNotificationQuickActionPolicy.isId(expectedAccount),
            senderId = actorId,
        )

        val builder =
            NotificationCompat.Builder(
                context,
                CHANNEL_FOLLOWS
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    "New follower"
                )
                .setContentText(
                    if (actor.isBlank()) "Someone started following you" else "@$actor started following you"
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_SOCIAL
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
        if (actor.isNotBlank()) {
            builder.setContentIntent(buildProfilePendingIntent(context, actor))
            builder.addAction(0, "View Profile", buildProfilePendingIntent(context, actor))
        }
        if (BlinkNotificationQuickAction.FOLLOW_BACK in actions) {
            buildQuickAction(
                context, NotificationQuickActionWorker.ACTION_FOLLOW,
                expectedAccount, actorId, notificationId,
            )?.let(builder::addAction)
        }
        val notification = builder.build()

        notifySafely(
            context,
            notificationId,
            notification
        )
    }

    // ================================================================
    // MARKET NOTIFICATION
    // ================================================================

    fun showMarketNotification(
        context: Context,
        title: String,
        body: String,
        targetMarketId: String? = null
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_MARKET
            )
                .setSmallIcon(
                    android.R.drawable
                        .ic_menu_agenda
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    body
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body)
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_EVENT
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildMarketPendingIntent(
                        context,
                        targetMarketId
                    )
                )
                .setGroup(
                    GROUP_KEY_MARKET
                )
                .build()

        notifySafely(
            context,
            MARKET_ID_BASE +
                    positiveHash(
                        "$title$body"
                    ) % 700,
            notification
        )
    }

    // ================================================================
    // BUYER INQUIRY
    // ================================================================

    fun showBuyerInquiryNotification(
        context: Context,
        buyerName: String,
        productName: String,
        marketId: String
    ) {

        showMarketNotification(
            context = context,
            title =
                "🛍 New buyer inquiry",
            body =
                "$buyerName is asking about $productName.",
            targetMarketId =
                marketId
        )
    }

    // ================================================================
    // MARKET ORDER
    // ================================================================

    fun showMarketOrderNotification(
        context: Context,
        title: String,
        body: String,
        marketId: String
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )

        val notification =
            NotificationCompat.Builder(
                context,
                CHANNEL_MARKET_ORDERS
            )
                .setSmallIcon(
                    android.R.drawable
                        .ic_menu_agenda
                )
                .setContentTitle(
                    title
                )
                .setContentText(
                    body
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body)
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_EVENT
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )
                .setColor(
                    BRAND_COLOR
                )
                .setAutoCancel(
                    true
                )
                .setContentIntent(
                    buildMarketPendingIntent(
                        context,
                        marketId
                    )
                )
                .setGroup(
                    GROUP_KEY_MARKET
                )
                .build()

        notifySafely(
            context,
            MARKET_ID_BASE +
                    positiveHash(
                        "order_$marketId"
                    ) % 700,
            notification
        )
    }

    // ================================================================
    // GENERIC NOTIFICATION
    // ================================================================

    fun showGenericNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
        color: Int = BRAND_COLOR
    ) {

        if (
            !hasNotificationPermission(
                context
            )
        ) {
            return
        }

        createNotificationChannels(
            context
        )

        val notification =
            NotificationCompat.Builder(
                context,
                channelId
            )
                .setSmallIcon(com.example.R.drawable.ic_stat_blink)
                .setContentTitle(
                    title
                )
                .setContentText(
                    body
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(body)
                )
                .setColor(
                    color
                )
                .setAutoCancel(
                    true
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_STATUS
                )
                .build()

        notifySafely(
            context,
            positiveHash(
                notificationId.toString()
            ),
            notification
        )
    }

    // ================================================================
    // CANCEL ONE NOTIFICATION
    // ================================================================

    fun cancelNotification(
        context: Context,
        notificationId: Int
    ) {

        NotificationManagerCompat
            .from(context)
            .cancel(
                notificationId
            )
    }

    // ================================================================
    // CANCEL CHAT
    // ================================================================

    fun cancelChatNotification(
        context: Context,
        senderUsername: String
    ) {

        cancelNotification(
            context,
            MSG_ID_BASE +
                    positiveHash(
                        senderUsername
                    ) % 700
        )
    }

    // ================================================================
    // CANCEL POST
    // ================================================================

    fun cancelPostNotification(
        context: Context,
        postId: String
    ) {

        cancelNotification(
            context,
            SOCIAL_ID_BASE +
                    positiveHash(
                        "post_$postId"
                    ) % 700
        )
    }

    // ================================================================
    // CANCEL MARKET
    // ================================================================

    fun cancelMarketNotification(
        context: Context,
        marketId: String
    ) {

        cancelNotification(
            context,
            MARKET_ID_BASE +
                    positiveHash(
                        "order_$marketId"
                    ) % 700
        )
    }

    // ================================================================
    // CANCEL ALL
    // ================================================================

    fun cancelAllNotifications(
        context: Context
    ) {

        NotificationManagerCompat
            .from(context)
            .cancelAll()
    }

    // ================================================================
    // ACTIVE NOTIFICATION COUNT
    // ================================================================

    fun getActiveNotificationCount(
        context: Context
    ): Int {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.M
        ) {
            return 0
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as? NotificationManager
                ?: return 0

        return manager.activeNotifications.size
    }

    // ================================================================
    // CHANNEL EXISTS
    // ================================================================

    fun doesChannelExist(
        context: Context,
        channelId: String
    ): Boolean {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return true
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as? NotificationManager
                ?: return false

        return manager.getNotificationChannel(
            channelId
        ) != null
    }

    // ================================================================
    // DELETE A TEST CHANNEL
    // ================================================================

    fun deleteNotificationChannel(
        context: Context,
        channelId: String
    ) {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as? NotificationManager
                ?: return

        manager.deleteNotificationChannel(
            channelId
        )
    }

    // ================================================================
    // SAFE NOTIFY
    // ================================================================

    private fun notifySafely(
        context: Context,
        id: Int,
        notification: android.app.Notification
    ) {

        try {

            NotificationManagerCompat
                .from(context)
                .notify(
                    id,
                    notification
                )

        } catch (
            _: SecurityException
        ) {
            // Permission was revoked while
            // posting the notification.
        }
    }

    // ================================================================
    // POSITIVE HASH
    // ================================================================

    private fun positiveHash(
        value: String
    ): Int {

        return value
            .hashCode()
            .absoluteValue
    }
}