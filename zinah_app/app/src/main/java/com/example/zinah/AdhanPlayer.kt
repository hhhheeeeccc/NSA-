package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Singleton wrapper around [MediaPlayer] for playing the adhan audio.
 *
 * Design notes:
 *  - Uses USAGE_ALARM so the audio respects the user's alarm volume (not the media volume)
 *    and bypasses Do-Not-Disturb when the channel is configured to do so.
 *  - Holds a strong reference to a single [MediaPlayer] instance so we can stop it
 *    cleanly from the full-screen AdhanActivity when the user taps "إيقاف".
 *  - Calls [MediaPlayer.release] in the completion listener to free native resources.
 */
object AdhanPlayer {

    private const val TAG = "AdhanPlayer"

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    /** True iff a clip is currently playing. */
    fun isPlaying(): Boolean = currentPlayer?.isPlaying == true

    /**
     * Start playing the adhan audio from the given raw resource id.
     *
     * @param context      Any context — we use [Context.applicationContext] internally.
     * @param soundResId   Raw resource id (e.g. R.raw.adhan_makkah).
     * @param onCompletion Called when playback finishes naturally (NOT when stopped manually).
     */
    fun play(context: Context, soundResId: Int, onCompletion: () -> Unit = {}) {
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
                    Log.d(TAG, "Adhan playback completed")
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
            Log.d(TAG, "Adhan playback started (resId=$soundResId)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start adhan playback", e)
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
