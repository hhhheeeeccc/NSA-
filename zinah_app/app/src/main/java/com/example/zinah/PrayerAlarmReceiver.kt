package com.example.zinah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * BroadcastReceiver fired by [AlarmManager] when a prayer time arrives.
 *
 * Responsibilities:
 *  1. Acquire a 90-second partial wake lock so the device stays awake while the adhan plays.
 *  2. Check that the master feature toggle AND the per-prayer toggle are both enabled.
 *  3. Start [AdhanForegroundService] (mediaPlayback FGS) — this keeps the adhan playing
 *     even if the user kills the app from the recents menu.
 *  4. Launch [AdhanActivity] full-screen (if enabled in prefs) so the user sees a clear UI
 *     with a STOP button.
 *  5. Show a standard heads-up notification as well, so the user is informed even if the
 *     full-screen activity can't be shown (e.g. keyguard active on Android 14+).
 */
class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PrayerAlarmReceiver"
        private const val CHANNEL_ID = "zinah_adhan_channel"
        private const val NOTIFICATION_ID = 9101
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerKey = intent.getStringExtra("prayer_key") ?: return
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        Log.d(TAG, "Prayer alarm fired: $prayerName ($prayerKey)")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "ZinahApp::PrayerAlarmWakeLock"
        )
        wakeLock.acquire(90_000L) // 90 seconds — adhan is ~3 minutes, but player has its own lifecycle

        try {
            // Master feature toggle
            if (!PrayerTimePreferences.isFeatureEnabled(context)) {
                Log.d(TAG, "Feature disabled — ignoring $prayerName alarm")
                return
            }
            // Per-prayer toggle
            val prayer = PrayerType.entries.firstOrNull { it.key == prayerKey }
            if (prayer == null) {
                Log.w(TAG, "Unknown prayer key: $prayerKey")
                return
            }
            if (!PrayerTimePreferences.isPrayerEnabled(context, prayer)) {
                Log.d(TAG, "${prayer.nameAr} is disabled by user — skipping adhan")
                return
            }

            // Start the foreground service that actually plays the audio
            AdhanForegroundService.start(context, prayer.key, prayer.nameAr)

            // Show the full-screen activity if enabled
            if (PrayerTimePreferences.isFullScreenAdhan(context)) {
                val adhanIntent = Intent(context, AdhanActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                    putExtra("prayer_key", prayer.key)
                    putExtra("prayer_name", prayer.nameAr)
                }
                try {
                    context.startActivity(adhanIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not start AdhanActivity: ${e.message}")
                }
            }

            // Show a heads-up notification as well
            showPrayerNotification(context, prayer.nameAr)
        } finally {
            wakeLock.release()
        }
    }

    private fun showPrayerNotification(context: Context, prayerName: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "مواقيت الصلاة - الأذان",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة إشعارات الأذان عند دخول وقت الصلاة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
                setShowBadge(true)
                // No built-in sound — AdhanForegroundService plays the audio with USAGE_ALARM
                setSound(
                    android.net.Uri.EMPTY,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("open_tab", "prayer_times")
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 9101, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, AdhanForegroundService::class.java).apply {
            action = AdhanForegroundService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context, 9102, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("حان وقت صلاة $prayerName")
            .setContentText("يتم تشغيل الأذان الآن")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الأذان", stopPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Could not post prayer notification: ${e.message}")
        }
    }
}
