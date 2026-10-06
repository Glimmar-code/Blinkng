package com.example.items

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notification.BlinkInAppNotification
import com.example.notification.BlinkInAppNotificationCenter
import com.example.notification.BlinkInAppNotificationDestination
import com.example.notification.BlinkNotificationHelper
import java.time.LocalDate
import kotlin.math.absoluteValue

object BlinkItemNotificationManager {
    private const val GROUP_ITEMS = "blink_items_group"
    const val CHANNEL_STEPS = "blink_items_steps"
    const val CHANNEL_WEATHER = "blink_items_weather"
    const val CHANNEL_LOCATION = "blink_items_location"

    private const val STEP_ID_BASE = 61_000
    private const val WEATHER_ID_BASE = 62_000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        manager.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ITEMS, "BLINK Items")
        )

        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_STEPS,
                    "Step milestones",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Step goals and milestone encouragement from BLINK Items"
                    group = GROUP_ITEMS
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_WEATHER,
                    "Weather alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Rain, storm and official severe-weather alerts"
                    group = GROUP_ITEMS
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_LOCATION,
                    "Live location",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows while you are actively sharing live location"
                    group = GROUP_ITEMS
                    setShowBadge(false)
                }
            )
        )
    }

    fun evaluateStepMilestone(context: Context, steps: Long) {
        if (!BlinkItemPreferences.stepsEnabled(context)) return
        if (!BlinkItemPreferences.stepMilestonesEnabled(context) &&
            !BlinkItemPreferences.stepGoalEnabled(context)
        ) return

        val today = LocalDate.now().toString()
        BlinkItemPreferences.resetDailyMilestoneIfNeeded(context, today)
        BlinkItemPreferences.saveStepSnapshot(context, today, steps)

        val goal = BlinkItemPreferences.dailyStepGoal(context)
        val milestone = BlinkItemsPolicy.nextReachedMilestone(
            steps = steps,
            lastNotifiedMilestone = BlinkItemPreferences.lastStepMilestone(context),
            goal = goal
        ) ?: return

        val isGoal = milestone >= goal
        if (isGoal && !BlinkItemPreferences.stepGoalEnabled(context)) return
        if (!isGoal && !BlinkItemPreferences.stepMilestonesEnabled(context)) return

        BlinkItemPreferences.setLastStepMilestone(context, milestone)
        val (title, body) = BlinkItemsPolicy.stepMessage(milestone, goal)
        val handledInApp = BlinkInAppNotificationCenter.publish(
            BlinkInAppNotification(
                key = "items:steps:$today:$milestone",
                title = "🚶 $title",
                body = body,
                destination = BlinkInAppNotificationDestination.ITEMS,
                targetType = "steps"
            )
        )
        if (!handledInApp) {
            notifySystem(
                context = context,
                channel = CHANNEL_STEPS,
                notificationId = STEP_ID_BASE + milestone,
                title = "BLINK Steps • $title",
                body = body
            )
        }
    }

    fun evaluateWeather(context: Context, snapshot: BlinkWeatherSnapshot) {
        if (!BlinkItemPreferences.weatherEnabled(context)) return

        if (BlinkItemPreferences.severeAlertsEnabled(context)) {
            val newest = snapshot.alerts
                .filter { it.id.isNotBlank() && it.id != BlinkItemPreferences.lastWeatherAlertId(context) }
                .maxByOrNull { BlinkItemsPolicy.weatherPriority(it.severity, it.urgency) }

            if (newest != null) {
                BlinkItemPreferences.setLastWeatherAlertId(context, newest.id)
                val title = if (newest.isOfficial) {
                    "Official weather alert • ${newest.title}"
                } else {
                    newest.title
                }
                val sourceLine = newest.source.takeIf { it.isNotBlank() }?.let { "Source: $it" }.orEmpty()
                val body = listOf(newest.description.trim(), sourceLine)
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
                    .take(1_200)

                val handledInApp = BlinkInAppNotificationCenter.publish(
                    BlinkInAppNotification(
                        key = "items:weather:${newest.id}",
                        title = "⛈️ $title",
                        body = body,
                        destination = BlinkInAppNotificationDestination.ITEMS,
                        targetType = "weather",
                        targetId = newest.id
                    )
                )
                if (!handledInApp) {
                    notifySystem(
                        context = context,
                        channel = CHANNEL_WEATHER,
                        notificationId = WEATHER_ID_BASE + newest.id.hashCode().absoluteValue % 800,
                        title = title,
                        body = body
                    )
                }
                return
            }
        }

        if (!BlinkItemPreferences.rainAlertsEnabled(context)) return
        val rainAt = snapshot.nextRainAtEpochSeconds ?: return
        val now = System.currentTimeMillis() / 1_000L
        val leadSeconds = rainAt - now
        if (leadSeconds !in 1..(90 * 60)) return
        if (BlinkItemPreferences.lastRainEvent(context) == rainAt) return

        BlinkItemPreferences.setLastRainEvent(context, rainAt)
        val minutes = (leadSeconds / 60L).coerceAtLeast(1L)
        val title = "Rain may be approaching"
        val body = "Rain is forecast in about $minutes minutes. You may want to plan ahead."
        val handledInApp = BlinkInAppNotificationCenter.publish(
            BlinkInAppNotification(
                key = "items:rain:$rainAt",
                title = "🌧️ $title",
                body = body,
                destination = BlinkInAppNotificationDestination.ITEMS,
                targetType = "weather"
            )
        )
        if (!handledInApp) {
            notifySystem(
                context = context,
                channel = CHANNEL_WEATHER,
                notificationId = WEATHER_ID_BASE + 900,
                title = title,
                body = body
            )
        }
    }

    private fun notifySystem(
        context: Context,
        channel: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlinkNotificationHelper.EXTRA_ACTION, BlinkNotificationHelper.ACTION_OPEN_ITEMS)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_blink)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
