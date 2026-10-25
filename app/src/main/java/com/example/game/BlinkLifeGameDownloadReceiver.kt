package com.example.game

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.example.MainActivity
import com.example.R

/** DownloadManager transfer survives the UI; completed transfer schedules verification. */
class BlinkLifeGameDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val stored = context.getSharedPreferences("blink_life_web_delivery", Context.MODE_PRIVATE)
            .getLong("download_id", -1L)
        if (id < 0 || id != stored) return
        val request = OneTimeWorkRequestBuilder<BlinkLifeGamePrepareWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "blink_life_verify_download", ExistingWorkPolicy.REPLACE, request
        )
    }
}

class BlinkLifeGamePrepareWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        if (BlinkLifeWebPack.isInstalled(applicationContext)) return Result.success()
        if (BlinkLifeWebPack.snapshot(applicationContext).phase != BlinkLifeWebPack.Phase.VERIFYING)
            return Result.success() // cancelled, deleted, or download not successful
        if (!BlinkLifeWebPack.verifyAndInstall(applicationContext)) return Result.failure()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "blink_life_downloads"
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "BLINK LIFE downloads", NotificationManager.IMPORTANCE_DEFAULT
            ))
        }
        if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val open = PendingIntent.getActivity(
                applicationContext, 8891,
                Intent(applicationContext, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(8891, NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("BLINK LIFE is ready")
                .setContentText("Open BLINK and tap Play BLINK LIFE.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
        }
        return Result.success()
    }
}
