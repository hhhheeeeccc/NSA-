package com.example.zinah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class DhikrForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "zinah_foreground_channel"
        private const val NOTIFICATION_ID = 9001
        private const val KEEP_ALIVE_INTERVAL = 5 * 60 * 1000L // 5 minutes

        fun start(context: Context) {
            val intent = Intent(context, DhikrForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DhikrForegroundService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Schedule periodic keep-alive alarm to ensure service stays alive
        scheduleKeepAliveAlarm()

        // Schedule the actual dhikr alarm
        scheduleDhikrAlarm()

        // Return START_STICKY so system will try to recreate if killed
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "خدمة زينة - تعمل في الخلفية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "هذه القناة تحافظ على عمل التطبيق في الخلفية"
                setShowBadge(false)
                enableVibration(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val interval = sharedPref.getLong("interval", 15L)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("زينة")
            .setContentText("التذكيرات تعمل كل $interval دقيقة")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun scheduleDhikrAlarm() {
        // Delegate to AlarmManager via AlarmScheduler
        AlarmScheduler.schedule(this)
    }

    private fun scheduleKeepAliveAlarm() {
        // This alarm fires every 5 minutes to ensure the service stays alive
        val keepAliveIntent = Intent(this, ServiceKeepAliveReceiver::class.java)
        val keepAlivePendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            keepAliveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerTime = System.currentTimeMillis() + KEEP_ALIVE_INTERVAL

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    keepAlivePendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    keepAlivePendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerTime,
                keepAlivePendingIntent
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
