package com.example.zinah

import android.app.Application
import android.util.Log

class ZinahApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install a global uncaught exception handler that logs the crash to logcat
        // with a clear tag, so we can see exactly what's crashing the app.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("ZinahCrash", "==== UNCAUGHT EXCEPTION on thread: ${thread.name} ====", throwable)
            Log.e("ZinahCrash", "Stack trace:", throwable)
            // Also write to System.err so it appears in logcat even if Log fails
            try {
                System.err.println("==== ZinahCrash: ${throwable.javaClass.name}: ${throwable.message} ====")
                throwable.printStackTrace()
            } catch (_: Throwable) {}
            // Pass to the previous handler so the system shows the "App crashed" dialog normally
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Start foreground service immediately when app process starts
        try {
            DhikrForegroundService.start(this)
        } catch (e: Exception) {
            Log.e("ZinahApp", "Failed to start foreground service", e)
        }
    }
}
