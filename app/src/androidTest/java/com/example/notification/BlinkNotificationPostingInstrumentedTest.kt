package com.example.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises actual Android NotificationManager posting and the PendingIntent
 * created by BLINK. This does not substitute for end-to-end FCM delivery.
 */
@RunWith(AndroidJUnit4::class)
class BlinkNotificationPostingInstrumentedTest {
    @Test
    fun actionableNotificationsPostAndARepeatedKeyUpdatesInPlace() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        assertTrue("System notifications must be enabled for this emulator test", manager.areNotificationsEnabled())

        val channelTitle = "BLINK route smoke channel"
        val reelTitle = "BLINK route smoke reel"
        val channelId = "00000000-0000-4000-8000-000000000051"
        val reelId = "00000000-0000-4000-8000-000000000052"

        fun smokeRows() = manager.activeNotifications.filter {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?.startsWith("BLINK route smoke ") == true
        }

        try {
            BlinkNotificationHelper.showSocialNotification(
                context = context,
                title = channelTitle,
                body = "First channel update",
                targetType = "channel",
                targetId = channelId,
                stableNotificationKey = "ci_notification_channel_route"
            )
            BlinkNotificationHelper.showSocialNotification(
                context = context,
                title = reelTitle,
                body = "New reel available",
                targetType = "reel",
                targetPostId = reelId,
                stableNotificationKey = "ci_notification_reel_route"
            )

            // NotificationManager is backed by an asynchronous OS service.
            // Wait briefly for the posted notifications to become observable.
            var posted = smokeRows()
            repeat(25) {
                if (posted.size < 2) {
                    Thread.sleep(100)
                    posted = smokeRows()
                }
            }
            assertEquals("Two separate actionable notifications should be visible", 2, posted.size)
            posted.forEach { row ->
                assertNotNull("Posted notifications need a tap PendingIntent", row.notification.contentIntent)
                assertEquals(BlinkNotificationHelper.CHANNEL_SOCIAL, row.notification.channelId)
                assertTrue(row.notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
            }

            val firstId = posted.single {
                it.notification.extras.getCharSequence(Notification.EXTRA_TITLE) == channelTitle
            }.id

            BlinkNotificationHelper.showSocialNotification(
                context = context,
                title = channelTitle,
                body = "Updated channel update",
                targetType = "channel",
                targetId = channelId,
                stableNotificationKey = "ci_notification_channel_route",
                notificationCount = 2
            )

            var updated = smokeRows()
            repeat(25) {
                val newText = updated.firstOrNull { it.id == firstId }
                    ?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                if (newText != "Updated channel update") {
                    Thread.sleep(100)
                    updated = smokeRows()
                }
            }
            assertEquals("A repeat key should update in place rather than duplicate", 2, updated.size)
            val refreshed = updated.single { it.id == firstId }
            assertEquals(
                "Updated channel update",
                refreshed.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            )
            assertNotNull(refreshed.notification.contentIntent)
        } finally {
            smokeRows().forEach { manager.cancel(it.id) }
        }
    }
}
