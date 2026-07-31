package com.example.zinah

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restore the user's saved schedule on boot
            val sharedPref = context.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
            val interval = sharedPref.getLong("interval", 15L)
            val isMinutes = sharedPref.getBoolean("isMinutes", true)
            val timeUnit = if (isMinutes) TimeUnit.MINUTES else TimeUnit.HOURS

            val dhikrRequest = PeriodicWorkRequestBuilder<DhikrWorker>(interval, timeUnit).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ZinahPeriodicDhikr",
                ExistingPeriodicWorkPolicy.KEEP,
                dhikrRequest
            )
        }
    }
}
