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
import android.text.TextUtils
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
 * تنبيه دعاء جانبي أفقي ومضغوط.
 *
 * لا تستخدم البطاقة قياسات تملأ ارتفاع الشاشة: يظهر صف واحد مدمج بالقرب من
 * الحافة اليمنى، يدخل من الجانب ويختفي من الجانب ذاته عند انتهاء القراءة.
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
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun showOverlay(text: String) {
        removeCardImmediately()
        isDismissing = false
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = min((screenWidth * 0.90f).toInt(), dpToPx(390))
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
            gravity = Gravity.END or Gravity.TOP
            x = dpToPx(10)
            y = dpToPx(56)
        }

        val card = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    Color.parseColor("#FF164A31"),
                    Color.parseColor("#FF0C3322")
                )
                cornerRadius = dpToPx(18).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#75E2C45B"))
            }
            elevation = dpToPx(14).toFloat()
            alpha = 0f
            translationX = (cardWidth + dpToPx(24)).toFloat()
            scaleX = 0.98f
            scaleY = 0.98f
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val accent = View(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                colors = intArrayOf(
                    Color.parseColor("#FFFFDA74"),
                    Color.parseColor("#FFD4A83C")
                )
                cornerRadii = floatArrayOf(
                    dpToPx(18).toFloat(), dpToPx(18).toFloat(),
                    0f, 0f,
                    0f, 0f,
                    dpToPx(18).toFloat(), dpToPx(18).toFloat()
                )
            }
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(4),
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(14), dpToPx(12), dpToPx(20), dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconBackground = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#FFFFDD7B"),
                    Color.parseColor("#FFD2A73D")
                )
                orientation = GradientDrawable.Orientation.TL_BR
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(38), dpToPx(38)).apply {
                marginEnd = dpToPx(10)
            }
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(Color.parseColor("#FF123B2A"))
            layoutParams = FrameLayout.LayoutParams(dpToPx(19), dpToPx(19), Gravity.CENTER)
        }
        iconBackground.addView(icon)

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val state = TextView(this).apply {
            this.text = "زينة  •  جاري قراءة الدعاء"
            setTextColor(Color.parseColor("#FFF9D974"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }

        val dhikrView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(dpToPx(1).toFloat(), 1.0f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(3) }
        }

        val dismissLabel = TextView(this).apply {
            this.text = "×"
            setTextColor(Color.parseColor("#BFFFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            contentDescription = "إخفاء التنبيه"
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(36)).apply {
                marginStart = dpToPx(4)
            }
        }

        textColumn.addView(state)
        textColumn.addView(dhikrView)
        row.addView(iconBackground)
        row.addView(textColumn)
        row.addView(dismissLabel)
        card.addView(row)
        card.addView(accent)
        card.setOnClickListener { dismissOverlay() }
        dismissLabel.setOnClickListener { dismissOverlay() }
        cardView = card

        try {
            windowManager?.addView(card, params)
            card.animate()
                .alpha(1f)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(280)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } catch (_: Exception) {
            removeCardImmediately()
        }
    }

    private fun scheduleFallbackDismiss(text: String) {
        handler.removeCallbacks(fallbackDismiss)
        val approximateMs = text.trim().split(Regex("\\s+")).size * 720L + 6_000L
        handler.postDelayed(fallbackDismiss, max(12_000L, min(45_000L, approximateMs)))
    }

    private fun dismissOverlay() {
        if (isDismissing) return
        isDismissing = true
        handler.removeCallbacks(fallbackDismiss)
        val card = cardView ?: run {
            stopSelf()
            return
        }
        val exitDistance = max(card.width, dpToPx(260)) + dpToPx(24)
        card.animate()
            .alpha(0f)
            .translationX(exitDistance.toFloat())
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(200)
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
