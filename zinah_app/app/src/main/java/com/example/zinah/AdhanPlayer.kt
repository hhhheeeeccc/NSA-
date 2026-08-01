package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Singleton wrapper around [MediaPlayer] for playing audio in the app.
 *
 * CRITICAL: This prevents double-playback using TWO mechanisms:
 *  1. isPlaying() check — if audio is currently playing, ignore new calls
 *  2. Timestamp debounce — if audio was played within the last 10 seconds,
 *     ignore new calls (prevents two alarms firing 1-2 seconds apart from
 *     both starting playback)
 *
 * This fixes the bug where the dhikr sound was heard twice because both
 * the foreground service and the activity were scheduling alarms that
 * fired at roughly the same time.
 */
object AdhanPlayer {

    private const val TAG = "AdhanPlayer"
    private const val DEBOUNCE_MS = 10_000L // 10 seconds — ignore play() calls within this window

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    /** Timestamp (System.currentTimeMillis) of the last time play() was called. */
    @Volatile
    private var lastPlayTime: Long = 0L

    /** True iff a clip is currently playing. */
    fun isPlaying(): Boolean = currentPlayer?.isPlaying == true

    /**
     * Start playing the audio from the given raw resource id.
     *
     * If audio is already playing, OR if play() was called within the last
     * [DEBOUNCE_MS] milliseconds, this call is IGNORED.
     */
    fun play(context: Context, soundResId: Int, onCompletion: () -> Unit = {}) {
        val now = System.currentTimeMillis()

        // CRITICAL: Debounce — if we just played audio recently, ignore this call.
        // This handles the case where two alarms fire 1-2 seconds apart: the first
        // one starts playback, the second one is ignored because of the debounce window.
        if (now - lastPlayTime < DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring play() call — within debounce window (${now - lastPlayTime}ms since last play)")
            return
        }

        // If already playing, do NOT start a second player
        if (isPlaying()) {
            Log.d(TAG, "Already playing — ignoring duplicate play() call")
            return
        }

        stop()
        lastPlayTime = now
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
