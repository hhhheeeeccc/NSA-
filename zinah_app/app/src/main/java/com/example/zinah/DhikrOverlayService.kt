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
import kotlin.math.abs

/**
 * Premium side overlay notification service.
 *
 * Design philosophy (inspired by top-tier apps like Muslim Pro, Pillars, etc.):
 *  - Compact pill-shaped card on the right edge (not center)
 *  - Smooth slide-in animation from the right edge
 *  - Subtle scale-up for the icon
 *  - Glassmorphism-like layered backgrounds (gold accent ring + emerald core)
 *  - Clean typography hierarchy: small gold label → bold white dhikr
 *  - Auto-dismiss after 10s with fade-out
 *  - Draggable on Y axis; tap to dismiss
 *
 * Position: RIGHT edge, vertically centered (unchanged from original — user wants
 * "side notification" not center).
 */
class DhikrOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val dhikrText = intent?.getStringExtra("dhikr_text") ?: "صلي على محمد"
        showOverlay(dhikrText)

        // Auto-remove after 10 seconds
        handler.postDelayed({ stopSelf() }, 10_000)
        return START_NOT_STICKY
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()

    private fun showOverlay(text: String) {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
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
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = dpToPx(12)
            y = 0
        }

        // ===== Outer container — emerald gradient with gold accent border =====
        val container = FrameLayout(this).apply {
            val shape = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.TL_BR
                colors = intArrayOf(
                    Color.parseColor("#FF1B5E20"),  // emerald deep
                    Color.parseColor("#FF0B3D20"),  // darker
                    Color.parseColor("#FF052E16")   // darkest
                )
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(28).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#40D4AF37")) // subtle gold border
            }
            background = shape
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            elevation = dpToPx(16).toFloat()
            // Start invisible for slide-in animation
            alpha = 0f
            translationX = dpToPx(100).toFloat()
        }

        // ===== Inner content card (gold accent ring) =====
        val innerCard = FrameLayout(this).apply {
            val innerShape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.parseColor("#0FFFFFFF")) // very subtle white overlay
                cornerRadius = dpToPx(24).toFloat()
            }
            background = innerShape
            setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(16))
        }

        // ===== Vertical layout: icon row + dhikr text =====
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // ===== Top row: icon + app name =====
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(10) }
            layoutParams = lp
        }

        // Crescent icon in gold circle
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
            val size = dpToPx(36)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(10)
            }
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(Color.parseColor("#FF0B3D20")) // dark emerald icon on gold bg
            val iconSize = dpToPx(20)
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        }
        iconBg.addView(icon)

        // App name
        val appName = TextView(this).apply {
            this.text = "زينة"
            setTextColor(Color.parseColor("#FFFFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        topRow.addView(iconBg)
        topRow.addView(appName)

        // ===== Gold divider =====
        val divider = View(this).apply {
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    Color.parseColor("#00FFD700"),
                    Color.parseColor("#FFFFD700"),
                    Color.parseColor("#00FFD700")
                )
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(120), dpToPx(1)).apply {
                bottomMargin = dpToPx(10)
            }
        }

        // ===== Dhikr text =====
        val dhikrView = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxWidth = dpToPx(200)
            setLineSpacing(dpToPx(2).toFloat(), 1f)
        }

        // Build layout
        layout.addView(topRow)
        layout.addView(divider)
        layout.addView(dhikrView)
        innerCard.addView(layout)
        container.addView(innerCard)
        floatingView = container

        // ===== Touch: drag on Y, tap to dismiss =====
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialY = 0
            private var initialTouchY = 0f
            private var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = params.y
                        initialTouchY = event.rawY
                        moved = false
                        // Scale down slightly on press (tactile feedback)
                        v.animate().scaleX(0.95f).scaleY(0.95f)
                            .setDuration(100).start()
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(100).start()
                        if (!moved) stopSelf()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - initialTouchY
                        if (abs(dy) > 10) {
                            moved = true
                            params.y = initialY + dy.toInt()
                            try {
                                windowManager?.updateViewLayout(floatingView, params)
                            } catch (_: Exception) {}
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(floatingView, params)
            // Slide-in animation + fade-in
            floatingView?.animate()
                ?.translationX(0f)
                ?.alpha(1f)
                ?.setDuration(400)
                ?.setInterpolator(OvershootInterpolator(0.8f))
                ?.start()
            // Scale-up icon for premium feel
            iconBg.scaleX = 0f
            iconBg.scaleY = 0f
            iconBg.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(500)
                .setStartDelay(200)
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
            ?.translationX(dpToPx(100).toFloat())
            ?.setDuration(300)
            ?.withEndAction {
                try {
                    floatingView?.let { windowManager?.removeView(it) }
                } catch (_: Exception) {}
            }
            ?.start()
        handler.removeCallbacksAndMessages(null)
    }
}
