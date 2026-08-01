package com.example.zinah

import android.app.Application

class ZinahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Start foreground service immediately when app process starts
        // This ensures notifications work even after app is killed by system
        DhikrForegroundService.start(this)
    }
}
