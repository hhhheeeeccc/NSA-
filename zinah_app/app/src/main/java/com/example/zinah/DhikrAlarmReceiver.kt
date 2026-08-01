package com.example.zinah

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.media.AudioAttributes
import android.media.MediaPlayer

class DhikrAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ZinahApp::AlarmWakeLock"
        )
        wakeLock.acquire(60_000L)

        try {
            showNotification(context)
            scheduleNextAlarm(context)
        } finally {
            wakeLock.release()
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "zinah_dhikr_channel_exact"
        val sharedPref = context.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)

        val soundChoice = sharedPref.getInt("soundChoice", 0)
        val soundResId = when (soundChoice) {
            0 -> R.raw.dhikr_gentle
            1 -> R.raw.dhikr_strong
            2 -> R.raw.dhikr_double
            3 -> R.raw.dhikr_deep
            else -> R.raw.dhikr_gentle
        }
        val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/$soundResId")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "إشعارات الأذكار المباشرة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة إشعارات تطبيق زينة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 200)
                lockscreenVisibility = 1
                setBypassDnd(true)
                setShowBadge(true)
                setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Combine default adhkar + custom adhkar
        val customList = getCustomAdhkar(sharedPref)
        val allItems = mutableListOf<String>()
        allItems.addAll(AdhkarData.allAdhkar)
        allItems.addAll(customList)

        val randomDhikr = if (allItems.isNotEmpty()) allItems.random() else "سبحان الله وبحمده"

        // Full-screen notification intent (appears on lock screen / side of screen)
        val fullScreenIntent = Intent(context, NotificationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("dhikr_text", randomDhikr)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Also open main app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("زينة - تذكير بذكر الله")
            .setContentText(randomDhikr)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomDhikr))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSound(soundUri)
            .setDefaults(0)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVibrate(longArrayOf(0, 200, 100, 200, 100, 200))
            .setLights(android.graphics.Color.GREEN, 1000, 500)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun getCustomAdhkar(sharedPref: android.content.SharedPreferences): List<String> {
        val count = sharedPref.getInt("customAdhkarCount", 0)
        val list = mutableListOf<String>()
        for (i in 0 until count) {
            val text = sharedPref.getString("customDhikr_$i", "") ?: ""
            if (text.isNotEmpty()) list.add(text)
        }
        return list
    }

    private fun scheduleNextAlarm(context: Context) {
        val sharedPref = context.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val intervalMinutes = sharedPref.getLong("interval", 15L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, DhikrAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    // Fallback if exact alarms are denied
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            // Handle edge case where permission was revoked
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
