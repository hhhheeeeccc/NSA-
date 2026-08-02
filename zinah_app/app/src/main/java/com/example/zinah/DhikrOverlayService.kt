package com.example.zinah

import android.app.Service
import android.content.Context
import android.content.Intent
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Centered rectangular premium overlay notification service.
 *
 * Design philosophy (inspired by top-tier apps like Muslim Pro, Pillars, etc.):
 *  - Wider RECTANGULAR card centered on screen (not a square pill on the side)
 *  - Lower corner radius (~16dp) so the card reads as a rectangle, not a pill
 *  - Smooth scale+fade-in animation
 *  - Glassmorphism-like layered backgrounds (gold accent ring + emerald core)
 *  - Clean typography hierarchy: small gold label → bold white dhikr
 *  - Auto-dismiss after 8s with fade-out
 *  - Tap anywhere to dismiss
 *
 * Position: CENTER of screen (both horizontally and vertically).
 */
class DhikrOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dhikrText = intent?.getStringExtra("dhikr_text") ?: "صلي على محمد"
        showOverlay(dhikrText)

        // Auto-remove after 8 seconds (shorter than before since the card is more prominent)
        handler.postDelayed({ stopSelf() }, 8_000)
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun showOverlay(text: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // ===== Window params: CENTER of the screen =====
        // We use MATCH_PARENT width with horizontal padding so the card visually
        // sits in the center while the touch surface covers the whole screen
        // (tap anywhere to dismiss).
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // ===== Root container: full-screen transparent click-catcher =====
        // Tapping anywhere outside the card dismisses the overlay.
        val root = FrameLayout(this).apply {
            // Use a dim scrim so the card stands out (40% black)
            setBackgroundColor(Color.parseColor("#66000000"))
        }

        // ===== Outer card: RECTANGULAR with modest corner radius =====
        // Width is constrained to 86% of screen width so it always looks like
        // a rectangle on phones of any size.
        val displayWidth = resources.displayMetrics.widthPixels
        val cardWidth = (displayWidth * 0.86f).toInt()

        val card = FrameLayout(this).apply {
            val shape = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    Color.parseColor("#FF1B5E20"),  // emerald deep
                    Color.parseColor("#FF0B3D20"),  // darker
                    Color.parseColor("#FF052E16")   // darkest
                )
                shape = GradientDrawable.RECTANGLE
                // Lower radius → rectangular (not pill). 16dp reads as a soft rectangle.
                cornerRadius = dpToPx(16).toFloat()
                // Subtle gold border for premium feel
                setStroke(dpToPx(1), Color.parseColor("#66D4AF37"))
            }
            background = shape
            // Symmetric padding so the inner content is balanced
            setPadding(dpToPx(20), dpToPx(22), dpToPx(20), dpToPx(22))
            elevation = dpToPx(20).toFloat()
            // Start invisible for scale-in animation
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            layoutParams = FrameLayout.LayoutParams(
                cardWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        // ===== Inner content layout (vertical) =====
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // ===== Top row: icon in gold circle + app name =====
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(14) }
            layoutParams = lp
        }

        // Crescent icon in gold circle (kept for brand identity)
        val iconBg = FrameLayout(this).apply {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                colors = intArrayOf(
                    Color.parseColor("#FFFFD700"),
                    Color.parseColor("#FFD4AF37"),
                    Color.parseColor("#FFB8860B")
                )
                orientation = GradientDrawable.Orientation.TL_BR
            }
            background = bg
            val size = dpToPx(34)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(10)
            }
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(Color.parseColor("#FF0B3D20")) // dark emerald icon on gold bg
            val iconSize = dpToPx(18)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        iconBg.addView(icon)

        val appName = TextView(this).apply {
            this.text = "زينة"
            setTextColor(Color.parseColor("#FFFFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Small "ذكر اليوم" label
        val tagLabel = TextView(this).apply {
            this.text = "• ذكر"
            setTextColor(Color.parseColor("#AAFFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dpToPx(6) }
            layoutParams = lp
        }
        topRow.addView(iconBg)
        topRow.addView(appName)
        topRow.addView(tagLabel)

        // ===== Gold divider (full card width) =====
        val divider = View(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    Color.parseColor("#00FFD700"),
                    Color.parseColor("#FFFFD700"),
                    Color.parseColor("#00FFD700")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { bottomMargin = dpToPx(14) }
        }

        // ===== Dhikr text (the main content) =====
        val dhikrView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            // Allow the text to wrap onto multiple lines on long adhkar
            val computedMaxWidth = cardWidth - dpToPx(40) // 20dp padding on each side
            val safeMaxWidth = if (computedMaxWidth > 0) computedMaxWidth else dpToPx(280)
            setMaxWidth(safeMaxWidth)
            setLineSpacing(dpToPx(3).toFloat(), 1f)
        }

        // ===== Bottom hint: "اضغط للإغلاق" =====
        val hint = TextView(this).apply {
            this.text = "اضغط للإغلاق"
            setTextColor(Color.parseColor("#80FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
            layoutParams = lp
        }

        // Build layout
        layout.addView(topRow)
        layout.addView(divider)
        layout.addView(dhikrView)
        layout.addView(hint)

        card.addView(layout)
        root.addView(card)
        floatingView = root

        // ===== Touch: tap anywhere to dismiss =====
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_UP) {
                    stopSelf()
                    return true
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
            // Scale-in + fade-in animation
            card.animate()
                ?.alpha(1f)
                ?.scaleX(1f)
                ?.scaleY(1f)
                ?.setDuration(350)
                ?.setInterpolator(OvershootInterpolator(0.6f))
                ?.start()
            // Subtle delayed scale-up for the icon
            iconBg.scaleX = 0f
            iconBg.scaleY = 0f
            iconBg.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(400)
                .setStartDelay(150)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } catch (e: Exception) {
            // Permission might have been revoked
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fade-out animation before removing
        floatingView?.animate()
            ?.alpha(0f)
            ?.setDuration(250)
            ?.withEndAction {
                try {
                    floatingView?.let { windowManager?.removeView(it) }
                } catch (_: Exception) {}
            }
            ?.start()
        handler.removeCallbacksAndMessages(null)
    }
}
