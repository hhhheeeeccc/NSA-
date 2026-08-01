package com.example.zinah

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "android.intent.action.MY_PACKAGE_REPLACED"
        ) {
            // Start dhikr foreground service immediately after boot
            DhikrForegroundService.start(context)
            val sharedPref = context.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
            val intervalMinutes = sharedPref.getLong("interval", 15L) // Default to 15 mins

            scheduleDhikrAlarm(context, intervalMinutes)

            // Re-schedule prayer-time alarms (async because we need to fetch timings from API)
            if (PrayerTimePreferences.isFeatureEnabled(context)) {
                CoroutineScope(Dispatchers.IO).launch {
                    reSchedulePrayerTimes(context)
                }
            }
        }
    }

    private fun scheduleDhikrAlarm(context: Context, intervalMinutes: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarmIntent = Intent(context, DhikrAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, alarmIntent,
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

    /**
     * Fetches fresh prayer timings from Aladhan API and re-schedules all 5 alarms.
     * Called after device boot — must be fast and silent (no UI).
     */
    private suspend fun reSchedulePrayerTimes(context: Context) {
        val method = PrayerTimePreferences.getCalculationMethod(context)
        val useGps = !PrayerTimePreferences.useManualCity(context)
        val manualCity = PrayerTimePreferences.getManualCity(context)

        val result = if (useGps) {
            if (!LocationHelper.hasLocationPermission(context)) {
                Log.w(TAG, "No location permission after boot — skipping prayer reschedule")
                return
            }
            when (val loc = LocationHelper.getCurrentLocation(context)) {
                is LocationHelper.Result.Success -> {
                    PrayerTimePreferences.saveLocation(context, loc.latitude, loc.longitude, loc.label)
                    AdhanApiService.fetchTimingsByCoordinates(loc.latitude, loc.longitude, method)
                }
                is LocationHelper.Result.Error -> {
                    Log.w(TAG, "Location fetch failed after boot: ${loc.message}")
                    return
                }
            }
        } else {
            AdhanApiService.fetchTimingsByCity(manualCity, method)
        }

        when (result) {
            is AdhanApiService.Result.Success -> {
                PrayerTimeScheduler.scheduleAll(context, result.data)
                Log.d(TAG, "Prayer times re-scheduled after boot")
            }
            is AdhanApiService.Result.Error -> {
                Log.e(TAG, "Failed to fetch prayer times after boot: ${result.message}")
            }
        }
    }
}
