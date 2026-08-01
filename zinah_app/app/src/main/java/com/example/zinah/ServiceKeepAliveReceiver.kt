package com.example.zinah

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ServiceKeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the foreground service if it was killed
        DhikrForegroundService.start(context)
    }
}
