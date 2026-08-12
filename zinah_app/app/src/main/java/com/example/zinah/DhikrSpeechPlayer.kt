package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * يقرأ نص الذكر أو الدعاء الظاهر للمستخدم عبر محرّك تحويل النص إلى كلام في أندرويد.
 *
 * يحتفظ هذا الكائن بنسخة واحدة من [TextToSpeech] لتفادي التهيئة المتكررة، ويمنع
 * القراءة المزدوجة إذا تكرر وصول المنبّه خلال ثوانٍ قليلة.
 */
object DhikrSpeechPlayer : TextToSpeech.OnInitListener {

    private const val TAG = "DhikrSpeechPlayer"
    private const val DEBOUNCE_MS = 5_000L

    private var textToSpeech: TextToSpeech? = null
    private var pendingText: String? = null
    private var isReady = false
    private var lastSpokenAt = 0L

    @Synchronized
    fun speak(context: Context, text: String) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastSpokenAt < DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring duplicate dhikr speech request")
            return
        }
        lastSpokenAt = now

        if (textToSpeech == null) {
            pendingText = cleanText
            textToSpeech = TextToSpeech(context.applicationContext, this)
            return
        }

        if (!isReady) {
            pendingText = cleanText
            return
        }

        speakInternal(cleanText)
    }

    @Synchronized
    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Arabic text-to-speech initialization failed: $status")
            isReady = false
            pendingText = null
            return
        }

        val tts = textToSpeech ?: return
        val arabic = Locale("ar")
        val languageStatus = tts.setLanguage(arabic)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "Arabic voice is not installed; using the device default voice")
        }

        tts.setSpeechRate(0.85f)
        tts.setPitch(1.0f)
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        isReady = true

        pendingText?.let {
            pendingText = null
            speakInternal(it)
        }
    }

    @Synchronized
    private fun speakInternal(text: String) {
        val tts = textToSpeech ?: return
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "zinah_dhikr_${System.currentTimeMillis()}"
        )
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Failed to queue dhikr speech: $result")
        }
    }

    @Synchronized
    fun stop() {
        textToSpeech?.stop()
        pendingText = null
    }
}
