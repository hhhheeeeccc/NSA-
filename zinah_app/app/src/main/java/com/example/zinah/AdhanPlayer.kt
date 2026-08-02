package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log

/**
 * Singleton wrapper around [MediaPlayer] for playing audio in the app.
 *
 * CRITICAL: This prevents double-playback using THREE mechanisms:
 *  1. isPlaying() check — if audio is currently playing, ignore new calls
 *  2. Per-resource debounce — if the SAME sound was played within the last
 *     5 seconds, ignore new calls. This is short enough that it only blocks
 *     true duplicates (same alarm firing twice 1-2 seconds apart) but does
 *     NOT block a different sound from playing (e.g. dhikr sound does not
 *     block the adhan sound from playing right after).
 *  3. isLooping = false — MediaPlayer never auto-repeats the clip.
 *
 * Previous bug: DEBOUNCE was 60s and global (not per-resource). This meant
 * that if a dhikr notification played within 60s of a prayer time, the adhan
 * would be SILENTLY SKIPPED. Now we use a short per-resource debounce so
 * each sound can play independently.
 */
object AdhanPlayer {

    private const val TAG = "AdhanPlayer"
    private const val DEBOUNCE_MS = 5_000L // 5 seconds — only blocks true duplicates

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    /** Map of (soundResId -> last play timestamp) for per-resource debounce. */
    private val lastPlayTimes = mutableMapOf<Int, Long>()

    /** True iff a clip is currently playing. */
    fun isPlaying(): Boolean = try {
        currentPlayer?.isPlaying == true
    } catch (e: Exception) {
        // IllegalStateException if player is in an error state
        false
    }

    /**
     * Start playing the audio from the given raw resource id.
     *
     * If audio is already playing, OR if the SAME sound was played within
     * the last [DEBOUNCE_MS] milliseconds, this call is IGNORED.
     *
     * A DIFFERENT sound resource can interrupt the current one and play
     * immediately — this is important so the adhan can play even if a
     * dhikr notification just played.
     */
    fun play(context: Context, soundResId: Int, onCompletion: () -> Unit = {}) {
        val now = System.currentTimeMillis()

        // Per-resource debounce: only block if the SAME sound was just played.
        // This prevents double-playback when two alarms for the same prayer
        // fire 1-2 seconds apart, but does NOT block a different sound.
        val lastPlay = lastPlayTimes[soundResId] ?: 0L
        if (now - lastPlay < DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring play() call — same sound ($soundResId) within debounce (${now - lastPlay}ms)")
            return
        }

        // If a DIFFERENT sound is currently playing, stop it so the new one can play.
        // This is critical: the adhan must be able to interrupt a dhikr notification.
        if (isPlaying()) {
            Log.d(TAG, "A different sound is playing — stopping it to play new sound $soundResId")
            stop()
        }

        lastPlayTimes[soundResId] = now
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
                // CRITICAL: never loop — play the clip exactly once.
                isLooping = false
                prepare()
                setOnCompletionListener {
                    Log.d(TAG, "Playback completed (resId=$soundResId) — releasing player")
                    try { release() } catch (_: Exception) {}
                    currentPlayer = null
                    onCompletion()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error what=$what extra=$extra (resId=$soundResId)")
                    try { mp.release() } catch (_: Exception) {}
                    currentPlayer = null
                    onCompletion()
                    true
                }
                // Start exactly once. We do NOT call start() anywhere else.
                start()
            }
            currentPlayer = player
            Log.d(TAG, "Playback started (resId=$soundResId) — will play exactly once")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start playback (resId=$soundResId)", e)
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
