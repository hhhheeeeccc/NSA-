package com.example.zinah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Schedules [AlarmManager] alarms for each prayer time.
 *
 * Each prayer gets its own request code (see [PrayerType.alarmRequestCode]) so we can
 * cancel/re-schedule them independently.
 *
 * The alarm fires [PrayerAlarmReceiver] which then:
 *   1. Acquires a partial wake lock (60s)
 *   2. Starts [AdhanForegroundService] which plays the adhan audio
 *   3. Launches [AdhanActivity] full-screen UI (if enabled in prefs)
 *   4. Re-schedules tomorrow's prayers via [scheduleNextDayIfNeeded]
 *
 * We always re-schedule every prayer time fresh — even if today's Fajr is in the past,
 * we still set it (the AlarmManager will fire it immediately, which the receiver will
 * skip if the prayer time is more than 2 minutes in the past).
 */
object PrayerTimeScheduler {

    private const val TAG = "PrayerScheduler"

    /**
     * Schedules all 5 prayers for today using the given [timings].
     * Prayers already in the past are skipped (alarmManager.setExactAndAllowWhileIdle
     * would fire them immediately otherwise).
     *
     * Should be called after fetching fresh timings from Aladhan API.
     */
    fun scheduleAll(context: Context, timings: PrayerTimings) {
        val now = Calendar.getInstance()
        for (prayer in PrayerType.entries) {
            val cal = timings.timeFor(prayer)
            // Skip prayers already passed (>2 min ago)
            if (cal.timeInMillis < now.timeInMillis - 2 * 60 * 1000L) {
                Log.d(TAG, "Skipping ${prayer.nameAr} — already passed")
                continue
            }
            scheduleOne(context, prayer, cal.timeInMillis)
        }
        // Always schedule tomorrow's Fajr in case Isha has already passed too
        scheduleTomorrowFajr(context, timings)
    }

    /**
     * Schedules a single prayer alarm.
     */
    fun scheduleOne(context: Context, prayer: PrayerType, triggerAtMillis: Long) {
        if (!PrayerTimePreferences.isFeatureEnabled(context)) {
            Log.d(TAG, "Feature disabled — not scheduling ${prayer.nameAr}")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            action = "com.example.zinah.PRAYER_ALARM"
            putExtra("prayer_key", prayer.key)
            putExtra("prayer_name", prayer.nameAr)
            putExtra("trigger_at", triggerAtMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PrayerType.alarmRequestCode(prayer),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            }
            Log.d(TAG, "Scheduled ${prayer.nameAr} for ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(triggerAtMillis))}")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling ${prayer.nameAr}", e)
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent
                )
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed for ${prayer.nameAr}", e2)
            }
        }
    }

    /**
     * Cancels all 5 prayer alarms (does not affect dhikr alarms).
     * Wrapped in try/catch — alarmManager.cancel can throw SecurityException on rare edge cases.
     */
    fun cancelAll(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (prayer in PrayerType.entries) {
                val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                    action = "com.example.zinah.PRAYER_ALARM"
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    PrayerType.alarmRequestCode(prayer),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    alarmManager.cancel(pendingIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to cancel alarm for ${prayer.nameAr}: ${e.message}")
                }
            }
            Log.d(TAG, "Cancelled all prayer alarms")
        } catch (e: Exception) {
            Log.e(TAG, "cancelAll crashed", e)
        }
    }

    /**
     * Schedules tomorrow's Fajr by adding 1 day to today's Fajr.
     * This ensures that after all of today's prayers pass, the chain restarts tomorrow.
     */
    private fun scheduleTomorrowFajr(context: Context, timings: PrayerTimings) {
        val tomorrowFajr = timings.fajr.clone() as Calendar
        tomorrowFajr.add(Calendar.DAY_OF_YEAR, 1)
        if (tomorrowFajr.timeInMillis > System.currentTimeMillis()) {
            scheduleOne(context, PrayerType.FAJR, tomorrowFajr.timeInMillis)
            Log.d(TAG, "Scheduled tomorrow's Fajr for ${
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                    .format(java.util.Date(tomorrowFajr.timeInMillis))}")
        }
    }
}
