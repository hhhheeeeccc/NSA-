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
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service of type [mediaPlayback] that plays the adhan audio.
 *
 * Why a service (and not just calling [AdhanPlayer.play] from the BroadcastReceiver)?
 *  - On Android 8+, BroadcastReceivers have only ~10 seconds to do their work. An adhan
 *    recitation takes 2–4 minutes — way too long for a receiver to keep the process alive.
 *  - Starting a foreground service promotes our process to "foreground" priority so the
 *    system won't kill it mid-adhan, even if the user closes the app from the recents menu.
 *
 * Lifecycle:
 *  - [start] is called from [PrayerAlarmReceiver].
 *  - We immediately call [startForeground] with a non-dismissable notification
 *    ("جاري تشغيل الأذان...") so the system allows us to run in the background.
 *  - We then call [AdhanPlayer.play] with a completion callback that calls [stopSelf].
 *  - The user can tap the "إيقاف الأذان" action on either this notification OR the
 *    AdhanActivity STOP button → [AdhanPlayer.stop] + [stopSelf].
 */
class AdhanForegroundService : Service() {

    companion object {
        private const val TAG = "AdhanForegroundSvc"
        private const val CHANNEL_ID = "zinah_adhan_playback_channel"
        private const val NOTIFICATION_ID = 9201

        const val ACTION_STOP = "com.example.zinah.ACTION_STOP_ADHAN"
        const val EXTRA_PRAYER_KEY = "prayer_key"
        const val EXTRA_PRAYER_NAME = "prayer_name"

        fun start(context: Context, prayerKey: String, prayerName: String) {
            val intent = Intent(context, AdhanForegroundService::class.java).apply {
                putExtra(EXTRA_PRAYER_KEY, prayerKey)
                putExtra(EXTRA_PRAYER_NAME, prayerName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle STOP action
        if (intent?.action == ACTION_STOP) {
            Log.d(TAG, "Received STOP action — stopping adhan")
            AdhanPlayer.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerKey = intent?.getStringExtra(EXTRA_PRAYER_KEY) ?: "fajr"
        val prayerName = intent?.getStringExtra(EXTRA_PRAYER_NAME) ?: "الصلاة"

        val notification = buildForegroundNotification(prayerName)
        startForeground(NOTIFICATION_ID, notification)

        // Acquire wake lock for the duration of the adhan (max 5 minutes)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZinahApp::AdhanPlaybackWakeLock"
        ).also { it.acquire(5 * 60 * 1000L) }

        val soundResId = PrayerTimePreferences.getAdhanSoundResId(this)
        AdhanPlayer.play(this, soundResId) {
            Log.d(TAG, "Adhan finished — stopping service")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        // If START_STICKY, system would restart us if killed — but we don't want that
        // because there's no way to know which prayer triggered the restart.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AdhanPlayer.stop()
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock release failed: ${e.message}")
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "تشغيل الأذان",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "خدمة لتشغيل الأذان في الخلفية عند دخول وقت الصلاة"
                setShowBadge(false)
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(prayerName: String): android.app.Notification {
        val openIntent = Intent(this, AdhanActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_PRAYER_NAME, prayerName)
        }
        val contentPi = PendingIntent.getActivity(
            this, 9201, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, AdhanForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 9202, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("جاري تشغيل الأذان")
            .setContentText("حان وقت صلاة $prayerName")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف", stopPi)
            .build()
    }
}
