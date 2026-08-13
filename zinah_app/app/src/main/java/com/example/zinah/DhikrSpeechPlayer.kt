package com.example.zinah

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * يقرأ نص الذكر أو الدعاء عبر محرّك القراءة المثبّت على الهاتف.
 *
 * يفضّل التطبيق صوتًا عربيًا عالي الجودة يعمل دون اتصال، ويعطي الأولوية لأي
 * دلالة صريحة على الصوت الرجالي يقدمها محرّك الجهاز. لا يتطلب هذا المشغّل أي
 * مفتاح خدمة أو اتصال إنترنت.
 */
object DhikrSpeechPlayer : TextToSpeech.OnInitListener {

    const val ACTION_DHIKR_SPEECH_FINISHED = "com.example.zinah.DHIKR_SPEECH_FINISHED"

    private const val TAG = "DhikrSpeechPlayer"
    private const val DEBOUNCE_MS = 5_000L
    private val MALE_VOICE_HINTS = listOf("male", "masculine", "ذكر", "رجل")

    private var textToSpeech: TextToSpeech? = null
    private var applicationContext: Context? = null
    private var pendingText: String? = null
    private var isReady = false
    private var lastSpokenAt = 0L
    private var activeUtteranceId: String? = null

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
        applicationContext = context.applicationContext

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
            notifySpeechFinished()
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
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onDone(utteranceId: String) {
                if (utteranceId == activeUtteranceId) {
                    activeUtteranceId = null
                    notifySpeechFinished()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                handleSpeechError(utteranceId)
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                handleSpeechError(utteranceId)
            }
        })
        isReady = true

        pendingText?.let {
            pendingText = null
            speakInternal(it)
        }
    }

    @Synchronized
    private fun handleSpeechError(utteranceId: String) {
        if (utteranceId == activeUtteranceId) {
            Log.e(TAG, "Text-to-speech failed for $utteranceId")
            activeUtteranceId = null
            notifySpeechFinished()
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
     * لا توجد خاصية موحدة للجنس في Android Voice، لذلك نستخدم أي دلالة يقدمها
     * محرك القراءة في الاسم أو الخصائص ثم نختار أعلى جودة بأقل تأخير ومن دون شبكة.
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
                    .thenBy { if (it.isNetworkConnectionRequired) 0 else 1 }
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
        val utteranceId = "zinah_dhikr_${System.currentTimeMillis()}"
        activeUtteranceId = utteranceId
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "Failed to queue dhikr speech: $result")
            activeUtteranceId = null
            notifySpeechFinished()
        }
    }

    @Synchronized
    fun stop() {
        textToSpeech?.stop()
        pendingText = null
        activeUtteranceId = null
        notifySpeechFinished()
    }

    private fun notifySpeechFinished() {
        val context = applicationContext ?: return
        context.sendBroadcast(
            Intent(ACTION_DHIKR_SPEECH_FINISHED).setPackage(context.packageName)
        )
    }
}
