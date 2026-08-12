package com.example.zinah

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * يقرأ نص الذكر أو الدعاء الظاهر للمستخدم عبر محرّك تحويل النص إلى كلام في أندرويد.
 *
 * يفضّل التطبيق صوتًا عربيًا عالي الجودة، ويعطي الأولوية للأصوات التي يعرّفها
 * محرّك الجهاز كصوت رجالي. يحافظ على بديل مناسب إذا لم يكن هذا الصوت مثبتًا.
 */
object DhikrSpeechPlayer : TextToSpeech.OnInitListener {

    private const val TAG = "DhikrSpeechPlayer"
    private const val DEBOUNCE_MS = 5_000L
    private val MALE_VOICE_HINTS = listOf("male", "masculine", "ذكر", "رجل")

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
        configureArabicVoice(tts)
        tts.setSpeechRate(0.80f)
        tts.setPitch(0.88f)
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

    private fun configureArabicVoice(tts: TextToSpeech) {
        val arabic = Locale("ar")
        val languageStatus = tts.setLanguage(arabic)
        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            Log.w(TAG, "Arabic voice is not installed; using the device default voice")
            return
        }

        val preferredVoice = selectPreferredArabicVoice(tts) ?: return
        val result = tts.setVoice(preferredVoice)
        if (result == TextToSpeech.SUCCESS) {
            Log.d(
                TAG,
                "Using Arabic voice ${preferredVoice.name}; quality=${preferredVoice.quality}; " +
                    "network=${preferredVoice.isNetworkConnectionRequired}"
            )
        } else {
            Log.w(TAG, "Could not select preferred Arabic voice: ${preferredVoice.name}")
        }
    }

    /**
     * Android لا يوفر خاصية موحّدة للجنس في [Voice]، لذلك نستخدم أي دلالة صريحة
     * يوفرها محرّك القراءة في الاسم أو الخصائص، ثم نرتب بقية الأصوات حسب الجودة.
     */
    private fun selectPreferredArabicVoice(tts: TextToSpeech): Voice? {
        return tts.voices
            .orEmpty()
            .asSequence()
            .filter { it.locale.language.equals("ar", ignoreCase = true) }
            .maxWithOrNull(
                compareBy<Voice> { masculineHintScore(it) }
                    .thenBy { it.quality }
                    .thenBy { -it.latency }
                    .thenBy { if (it.isNetworkConnectionRequired) 1 else 0 }
            )
    }

    private fun masculineHintScore(voice: Voice): Int {
        val identifiers = buildString {
            append(voice.name.lowercase(Locale.ROOT))
            voice.features.orEmpty().forEach { feature ->
                append(' ')
                append(feature.lowercase(Locale.ROOT))
            }
        }
        return if (MALE_VOICE_HINTS.any { hint -> identifiers.contains(hint) }) 1 else 0
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
