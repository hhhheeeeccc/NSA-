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
            // Restore default 1 hour schedule on boot
            val dhikrRequest = PeriodicWorkRequestBuilder<DhikrWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ZinahPeriodicDhikr",
                ExistingPeriodicWorkPolicy.KEEP,
                dhikrRequest
            )
        }
    }
}
