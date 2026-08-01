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
import android.util.Log
import androidx.core.app.NotificationCompat
import android.media.AudioAttributes
import android.media.MediaPlayer

class DhikrAlarmReceiver : BroadcastReceiver() {

    private val TAG = "DhikrAlarmReceiver"

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

        val soundResId = R.raw.sali_ala_mohammad
        val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/$soundResId")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // IMPORTANT: Set channel sound to SILENT (null) to avoid double playback.
            // We play the sound manually with MediaPlayer below; if the channel also has a sound,
            // the user hears the audio TWICE (channel sound + MediaPlayer sound).
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
                // Mute the channel — MediaPlayer handles the audio
                setSound(
                    android.net.Uri.EMPTY,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
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

        // Start the overlay service for the side logo
        val overlayIntent = Intent(context, DhikrOverlayService::class.java).apply {
            putExtra("dhikr_text", randomDhikr)
        }
        context.startService(overlayIntent)

        // Play the sound ONCE using MediaPlayer (the channel is silent, so no double playback)
        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
                setOnCompletionListener { release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play dhikr sound", e)
        }
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
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
