package com.example.zinah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
            PowerManager.PARTIAL_WAKE_LOCK,
            "Zinah:DhikrWakeLock"
        )
        wakeLock.setReferenceCounted(false)
        wakeLock.acquire(TimeUnit.MINUTES.toMillis(1))

        try {
            showNotification()
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }

        return Result.success()
    }

    private fun getCustomAdhkar(): List<String> {
        val sharedPref = applicationContext.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val count = sharedPref.getInt("customAdhkarCount", 0)
        val list = mutableListOf<String>()
        for (i in 0 until count) {
            val text = sharedPref.getString("customDhikr_$i", "") ?: ""
            if (text.isNotEmpty()) list.add(text)
        }
        return list
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

        // Build all available items: adhkar + dua + custom
        val customList = getCustomAdhkar()
        val allItems = mutableListOf<String>()
        allItems.addAll(AdhkarData.adhkarList)
        allItems.addAll(AdhkarData.duaaList)
        allItems.addAll(customList)

        // Pick random item
        val randomText = if (allItems.isNotEmpty()) allItems.random() else "سبحان الله وبحمده"

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
}
