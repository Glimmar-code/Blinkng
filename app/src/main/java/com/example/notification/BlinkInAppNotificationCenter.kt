package com.example.notification

import android.os.SystemClock
import com.example.data.models.ActivityItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class BlinkInAppNotificationDestination {
    CHAT,
    POST,
    PROFILE,
    MARKET,
    NOTIFICATIONS
}

data class BlinkInAppNotification(
    val key: String,
    val title: String,
    val body: String,
    val destination: BlinkInAppNotificationDestination = BlinkInAppNotificationDestination.NOTIFICATIONS,
    val senderId: String = "",
    val senderUsername: String = "",
    val senderName: String = "",
    val senderAvatar: String = "",
    val postId: String? = null,
    val marketId: String? = null,
    val activity: ActivityItem? = null
)

/**
 * Process-local foreground notification bus.
 *
 * It intentionally has no replay: notifications received while Blink is in the background
 * belong to Android's notification tray and must not reappear as stale banners after resume.
 */
object BlinkInAppNotificationCenter {
    private const val DEDUPE_WINDOW_MS = 15_000L
    private const val MAX_RECENT_KEYS = 128

    private val mutableEvents = MutableSharedFlow<BlinkInAppNotification>(
        replay = 0,
        extraBufferCapacity = 32
    )
    val events = mutableEvents.asSharedFlow()

    private val recentKeys = LinkedHashMap<String, Long>()

    /**
     * Returns true when an active foreground host handled (or already handled) the event.
     * Callers can use that result to avoid showing a second Android heads-up alert.
     */
    fun publish(event: BlinkInAppNotification): Boolean {
        if (mutableEvents.subscriptionCount.value <= 0) return false

        if (event.key.isNotBlank()) {
            val now = SystemClock.elapsedRealtime()
            synchronized(recentKeys) {
                val iterator = recentKeys.entries.iterator()
                while (iterator.hasNext()) {
                    if (now - iterator.next().value > DEDUPE_WINDOW_MS) iterator.remove()
                }

                val previous = recentKeys[event.key]
                if (previous != null && now - previous <= DEDUPE_WINDOW_MS) {
                    return true
                }

                recentKeys[event.key] = now
                while (recentKeys.size > MAX_RECENT_KEYS) {
                    val oldest = recentKeys.entries.iterator()
                    if (!oldest.hasNext()) break
                    oldest.next()
                    oldest.remove()
                }
            }
        }

        return mutableEvents.tryEmit(event)
    }
}
