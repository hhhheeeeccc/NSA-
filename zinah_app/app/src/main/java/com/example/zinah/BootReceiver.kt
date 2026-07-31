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
            val sharedPref = context.getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
            val interval = sharedPref.getLong("interval", 60L) // Default to 60 mins if not set

            val dhikrRequest = PeriodicWorkRequestBuilder<DhikrWorker>(interval, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ZinahPeriodicDhikr",
                ExistingPeriodicWorkPolicy.KEEP,
                dhikrRequest
            )
        }
    }
}
