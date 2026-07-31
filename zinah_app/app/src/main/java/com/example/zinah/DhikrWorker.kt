package com.example.zinah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class DhikrWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): Result {
        // Acquire WakeLock to wake up device from sleep
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "Zinah:DhikrWakeLock"
        )
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(1)) // 1 minute max

        try {
            showNotification()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }

        return Result.success()
    }

    private fun showNotification() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zinah_dhikr_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات الأذكار",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تذكيرات يومية بالأذكار والأدعية"
                enableVibration(true)
                lockscreenVisibility = 1
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Randomly pick between dhikr and dua
        val randomText = if (Math.random() < 0.5) {
            AdhkarData.adhkarList.random()
        } else {
            AdhkarData.duaaList.random()
        }

        // Get current interval label for the notification
        val intervalLabel = getIntervalLabel()

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("زينة - تذكير بذكر الله")
            .setContentText(randomText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 200, 100, 200))
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun getIntervalLabel(): String {
        val sharedPref = applicationContext.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val interval = sharedPref.getLong("interval", 15L)
        val isMinutes = sharedPref.getBoolean("isMinutes", true)
        val timeUnit = if (isMinutes) "دقيقة" else "ساعة"
        return "كل $interval $timeUnit"
    }
}
