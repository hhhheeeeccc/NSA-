package com.example.zinah

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * بطاقة دعاء جانبية تظهر من الحافة اليمنى أثناء القراءة.
 *
 * تعتمد البطاقة على حركة انتقالية خفيفة بدلاً من تغطية الشاشة كاملةً، وتبقى
 * مرئية حتى اكتمال القراءة أو حتى انتهاء مهلة احتياطية محسوبة من طول النص.
 */
class DhikrOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var cardView: View? = null
    private var isDismissing = false
    private var isSpeechReceiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val fallbackDismiss = Runnable { dismissOverlay() }
    private val speechFinishedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == DhikrSpeechPlayer.ACTION_DHIKR_SPEECH_FINISHED) {
                dismissOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(DhikrSpeechPlayer.ACTION_DHIKR_SPEECH_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speechFinishedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(speechFinishedReceiver, filter)
        }
        isSpeechReceiverRegistered = true
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISMISS) {
            dismissOverlay()
            return START_NOT_STICKY
        }

        val dhikrText = intent?.getStringExtra(EXTRA_DHIKR_TEXT) ?: "سبحان الله وبحمده"
        showOverlay(dhikrText)
        scheduleFallbackDismiss(dhikrText)
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun showOverlay(text: String) {
        removeCardImmediately()
        isDismissing = false
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val displayWidth = resources.displayMetrics.widthPixels
        val cardWidth = min((displayWidth * 0.84f).toInt(), dpToPx(360))
        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = dpToPx(12)
        }

        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    Color.parseColor("#FF164B31"),
                    Color.parseColor("#FF0A3020"),
                    Color.parseColor("#FF062316")
                )
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#88E2C56B"))
            }
            elevation = dpToPx(18).toFloat()
            alpha = 0f
            scaleX = 0.98f
            scaleY = 0.98f
            translationX = (cardWidth + dpToPx(28)).toFloat()
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val accentBar = View(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    Color.parseColor("#FFFFD978"),
                    Color.parseColor("#FFCBA843")
                )
                cornerRadii = floatArrayOf(
                    dpToPx(20).toFloat(), dpToPx(20).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    dpToPx(20).toFloat(), dpToPx(20).toFloat()
                )
            }
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(4),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(18), dpToPx(18), dpToPx(22), dpToPx(16))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(12) }
        }

        val iconBackground = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#FFFFDD75"),
                    Color.parseColor("#FFD3A83B")
                )
                orientation = GradientDrawable.Orientation.TL_BR
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply {
                marginEnd = dpToPx(9)
            }
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(Color.parseColor("#FF113D29"))
            layoutParams = FrameLayout.LayoutParams(dpToPx(18), dpToPx(18), Gravity.CENTER)
        }
        iconBackground.addView(icon)

        val appName = TextView(this).apply {
            this.text = "زينة"
            setTextColor(Color.parseColor("#FFFFDF80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val readingState = TextView(this).apply {
            this.text = "●  جاري قراءة الدعاء"
            setTextColor(Color.parseColor("#BDE1FFD9"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { marginStart = dpToPx(8) }
        }
        topRow.addView(iconBackground)
        topRow.addView(appName)
        topRow.addView(readingState)

        val divider = View(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    Color.parseColor("#00D4AF37"),
                    Color.parseColor("#A8F5D66D"),
                    Color.parseColor("#00D4AF37")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { bottomMargin = dpToPx(12) }
        }

        val dhikrView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setLineSpacing(dpToPx(3).toFloat(), 1.05f)
        }

        val hint = TextView(this).apply {
            this.text = "اضغط للإخفاء"
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }

        content.addView(topRow)
        content.addView(divider)
        content.addView(dhikrView)
        content.addView(hint)
        card.addView(content)
        card.addView(accentBar)
        card.setOnClickListener { dismissOverlay() }
        cardView = card

        try {
            windowManager?.addView(card, params)
            card.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(360)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
            pulseReadingState(readingState)
        } catch (_: Exception) {
            removeCardImmediately()
        }
    }

    private fun pulseReadingState(readingState: TextView) {
        readingState.animate()
            .alpha(0.45f)
            .setDuration(700)
            .withEndAction {
                readingState.animate()
                    .alpha(1f)
                    .setDuration(700)
                    .withEndAction {
                        if (!isDismissing && cardView != null) pulseReadingState(readingState)
                    }
                    .start()
            }
            .start()
    }

    private fun scheduleFallbackDismiss(text: String) {
        handler.removeCallbacks(fallbackDismiss)
        val estimatedDurationMs = text.trim().split(Regex("\\s+")).size * 780L + 8_000L
        handler.postDelayed(fallbackDismiss, max(15_000L, min(60_000L, estimatedDurationMs)))
    }

    private fun dismissOverlay() {
        if (isDismissing) return
        isDismissing = true
        handler.removeCallbacks(fallbackDismiss)
        val card = cardView ?: run {
            stopSelf()
            return
        }
        val exitDistance = max(card.width, dpToPx(280)) + dpToPx(28)
        card.animate()
            .alpha(0f)
            .translationX(exitDistance.toFloat())
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(240)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { stopSelf() }
            .start()
    }

    private fun removeCardImmediately() {
        cardView?.let { view ->
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
            }
        }
        cardView = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (isSpeechReceiverRegistered) {
            try {
                unregisterReceiver(speechFinishedReceiver)
            } catch (_: Exception) {
            }
            isSpeechReceiverRegistered = false
        }
        removeCardImmediately()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISMISS = "com.example.zinah.DISMISS_DHIKR_OVERLAY"
        const val EXTRA_DHIKR_TEXT = "dhikr_text"
    }
}
