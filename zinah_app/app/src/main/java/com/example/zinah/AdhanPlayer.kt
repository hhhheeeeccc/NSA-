package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Singleton wrapper around [MediaPlayer] for playing audio in the app.
 *
 * CRITICAL: This prevents double-playback by tracking whether audio is
 * currently playing. If [play] is called while audio is already playing,
 * the new call is IGNORED (no second MediaPlayer created).
 *
 * This fixes the bug where the dhikr sound was heard twice because both
 * DhikrForegroundService and MainActivity were scheduling alarms that
 * fired DhikrAlarmReceiver at roughly the same time.
 */
object AdhanPlayer {

    private const val TAG = "AdhanPlayer"

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    /** True iff a clip is currently playing. */
    fun isPlaying(): Boolean = currentPlayer?.isPlaying == true

    /**
     * Start playing the audio from the given raw resource id.
     * If audio is already playing, this call is IGNORED (no double playback).
     */
    fun play(context: Context, soundResId: Int, onCompletion: () -> Unit = {}) {
        // CRITICAL: If already playing, do NOT start a second player
        if (isPlaying()) {
            Log.d(TAG, "Already playing — ignoring duplicate play() call")
            return
        }
        stop()
        val appContext = context.applicationContext
        val uri = Uri.parse("android.resource://${appContext.packageName}/$soundResId")
        try {
            val player = MediaPlayer().apply {
                setDataSource(appContext, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                prepare()
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed")
                    release()
                    currentPlayer = null
                    onCompletion()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                    mp.release()
                    currentPlayer = null
                    onCompletion()
                    true
                }
                start()
            }
            currentPlayer = player
            Log.d(TAG, "Playback started (resId=$soundResId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback", e)
            onCompletion()
        }
    }

    /** Stop and release the current player immediately. Safe to call when nothing is playing. */
    fun stop() {
        currentPlayer?.let { p ->
            try {
                if (p.isPlaying) p.stop()
                p.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaPlayer: ${e.message}")
            }
            currentPlayer = null
        }
    }
}
